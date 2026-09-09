package com.application.brightflix.presentation.home

import com.application.brightflix.core.result.AppError
import com.application.brightflix.core.result.AppResult
import com.application.brightflix.core.result.Cached
import com.application.brightflix.core.result.RefreshState
import com.application.brightflix.core.result.Staleness
import com.application.brightflix.domain.model.CuratedCollections
import com.application.brightflix.domain.usecase.ObserveHomeFeedUseCase
import com.application.brightflix.domain.usecase.ToggleFavoriteUseCase
import com.application.brightflix.testing.FakeFavoritesRepository
import com.application.brightflix.testing.FakeMovieRepository
import com.application.brightflix.testing.FakeNetworkMonitor
import com.application.brightflix.testing.FakeRecentlyViewedRepository
import com.application.brightflix.testing.MainDispatcherRule
import com.application.brightflix.testing.movie
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The home screen's central rule: cached content outranks a failed refresh.
 *
 * A user who has movies on screen must keep them when a refresh fails — replacing them
 * with a full-screen error would be a regression the offline story exists to prevent.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val movies = FakeMovieRepository()
    private val favorites = FakeFavoritesRepository()
    private val recentlyViewed = FakeRecentlyViewedRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private val firstCollection = CuratedCollections.ALL[0]

    private fun viewModel() = HomeViewModel(
        observeHomeFeed = ObserveHomeFeedUseCase(movies, favorites, recentlyViewed),
        movieRepository = movies,
        toggleFavorite = ToggleFavoriteUseCase(favorites),
        networkMonitor = networkMonitor,
    )

    /** `WhileSubscribed` needs a collector before the state flow produces anything. */
    private fun TestScope.subscribed(viewModel: HomeViewModel) = also {
        backgroundScope.launch { viewModel.uiState.collect() }
    }

    @Test
    fun `a cold start with nothing cached shows loading`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `a warm cache renders content`() = runTest(mainDispatcherRule.scheduler) {
        movies.setCollection(
            firstCollection.id,
            Cached(data = listOf(movie("tt1", "Batman Begins")), lastUpdatedAt = 500L),
        )

        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        assertEquals(3, state.collections.size)
        assertEquals(1, state.collections.first { it.id == firstCollection.id }.items.size)
    }

    @Test
    fun `a failed refresh with cached content keeps the content and flags staleness`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.setCollection(
                firstCollection.id,
                Cached(
                    data = listOf(movie("tt1", "Batman Begins")),
                    lastUpdatedAt = 500L,
                    refresh = RefreshState.Failed(AppError.Offline),
                ),
            )

            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(
                "a failed refresh must never replace visible content with an error screen",
                state is HomeUiState.Content,
            )
            val content = state as HomeUiState.Content
            assertEquals(1, content.collections.first { it.id == firstCollection.id }.items.size)
            assertNotNull("the user should be told the data is saved, not live", content.staleness)
            assertEquals(Staleness.Reason.OFFLINE, content.staleness?.reason)
        }

    @Test
    fun `a failed refresh with nothing cached shows a recoverable error`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.setCollection(
                firstCollection.id,
                Cached(data = null, lastUpdatedAt = null, refresh = RefreshState.Failed(AppError.Offline)),
            )

            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            assertEquals(HomeUiState.Error(AppError.Offline), viewModel.uiState.value)
        }

    @Test
    fun `fresh content shows no staleness banner`() = runTest(mainDispatcherRule.scheduler) {
        movies.setCollection(
            firstCollection.id,
            Cached(data = listOf(movie("tt1")), lastUpdatedAt = 500L, refresh = RefreshState.Idle),
        )

        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertNull((viewModel.uiState.value as HomeUiState.Content).staleness)
    }

    @Test
    fun `launching respects the cache TTL but pull-to-refresh forces past it`() =
        runTest(mainDispatcherRule.scheduler) {
            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            assertEquals(1, movies.refreshCollectionsCallCount)
            assertEquals(
                "a warm cache on launch must not spend API quota",
                false,
                movies.lastForcedRefresh,
            )

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(2, movies.refreshCollectionsCallCount)
            assertEquals(
                "pull-to-refresh must always be able to fetch fresh data",
                true,
                movies.lastForcedRefresh,
            )
        }

    @Test
    fun `the refreshing flag clears after a failed refresh`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.setCollection(firstCollection.id, Cached(data = listOf(movie("tt1")), lastUpdatedAt = 1L))
            movies.refreshCollectionsResult = AppResult.Failure(AppError.Offline)

            val viewModel = viewModel()
            subscribed(viewModel)
            viewModel.refresh()
            advanceUntilIdle()

            assertFalse(
                "a stranded spinner would be a permanently broken screen",
                (viewModel.uiState.value as HomeUiState.Content).isRefreshing,
            )
        }

    @Test
    fun `surprise me emits a movie drawn from the cache`() = runTest(mainDispatcherRule.scheduler) {
        movies.setCollection(firstCollection.id, Cached(data = listOf(movie("tt1")), lastUpdatedAt = 1L))
        movies.randomMovie = movie("tt7", "A Surprise")

        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        val emitted = async { viewModel.surpriseMovie.first() }
        viewModel.onSurpriseMe()
        advanceUntilIdle()

        assertEquals("tt7", emitted.await())
    }

    @Test
    fun `surprise me is unavailable while nothing is cached`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.setCollection(firstCollection.id, Cached(data = emptyList(), lastUpdatedAt = 1L))
            recentlyViewed.record(movie("tt1"))

            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            assertFalse((viewModel.uiState.value as HomeUiState.Content).canSurpriseMe)
        }

    @Test
    fun `favoriting from home updates the shared source`() = runTest(mainDispatcherRule.scheduler) {
        movies.setCollection(
            firstCollection.id,
            Cached(data = listOf(movie("tt1", "Batman Begins")), lastUpdatedAt = 1L),
        )
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        val item = (viewModel.uiState.value as HomeUiState.Content)
            .collections.first { it.id == firstCollection.id }.items.first()
        viewModel.onToggleFavorite(item)
        advanceUntilIdle()

        assertTrue(favorites.isFavorite("tt1"))
        assertTrue(
            (viewModel.uiState.value as HomeUiState.Content)
                .collections.first { it.id == firstCollection.id }.items.first().isFavorite,
        )
    }

    @Test
    fun `recently viewed movies appear on home`() = runTest(mainDispatcherRule.scheduler) {
        recentlyViewed.record(movie("tt1", "Batman Begins"))

        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        assertEquals(1, state.recentlyViewed.size)
        assertEquals("Batman Begins", state.recentlyViewed.first().movie.title)
    }

    private fun TestScope.async(block: suspend () -> String) =
        kotlinx.coroutines.CompletableDeferred<String>().also { deferred ->
            backgroundScope.launch { deferred.complete(block()) }
        }
}
