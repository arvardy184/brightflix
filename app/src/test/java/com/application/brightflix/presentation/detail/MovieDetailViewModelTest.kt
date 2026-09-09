package com.application.brightflix.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.application.brightflix.core.result.AppError
import com.application.brightflix.core.result.Cached
import com.application.brightflix.core.result.RefreshState
import com.application.brightflix.core.result.Staleness
import com.application.brightflix.domain.model.MovieDetail
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.usecase.ObserveMovieDetailUseCase
import com.application.brightflix.domain.usecase.ToggleFavoriteUseCase
import com.application.brightflix.presentation.navigation.MovieDetailRoute
import com.application.brightflix.testing.FakeFavoritesRepository
import com.application.brightflix.testing.FakeMovieRepository
import com.application.brightflix.testing.FakeNetworkMonitor
import com.application.brightflix.testing.FakeRecentlyViewedRepository
import com.application.brightflix.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovieDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val movies = FakeMovieRepository()
    private val favorites = FakeFavoritesRepository()
    private val recentlyViewed = FakeRecentlyViewedRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private fun viewModel(imdbId: String = "tt1") = MovieDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(MovieDetailRoute.IMDB_ID_ARG to imdbId)),
        observeMovieDetail = ObserveMovieDetailUseCase(movies, favorites),
        movieRepository = movies,
        recentlyViewedRepository = recentlyViewed,
        toggleFavorite = ToggleFavoriteUseCase(favorites),
        networkMonitor = networkMonitor,
    )

    private fun TestScope.subscribed(viewModel: MovieDetailViewModel) = also {
        backgroundScope.launch { viewModel.uiState.collect() }
    }

    @Test
    fun `nothing cached yet shows loading and triggers a fetch`() =
        runTest(mainDispatcherRule.scheduler) {
            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            assertEquals(MovieDetailUiState.Loading, viewModel.uiState.value)
            assertEquals(1, movies.refreshDetailCallCount)
        }

    @Test
    fun `cached detail renders as content`() = runTest(mainDispatcherRule.scheduler) {
        movies.setDetail("tt1", Cached(data = detail("tt1", "Batman Begins"), lastUpdatedAt = 500L))

        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MovieDetailUiState.Content
        assertEquals("Batman Begins", state.detail.title)
        assertFalse(state.isFavorite)
    }

    @Test
    fun `a failed refresh keeps the cached movie on screen`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.setDetail(
                "tt1",
                Cached(
                    data = detail("tt1", "Batman Begins"),
                    lastUpdatedAt = 500L,
                    refresh = RefreshState.Failed(AppError.Offline),
                ),
            )

            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value as MovieDetailUiState.Content
            assertEquals("Batman Begins", state.detail.title)
            assertNotNull(state.staleness)
            assertEquals(Staleness.Reason.OFFLINE, state.staleness?.reason)
        }

    @Test
    fun `a failed fetch with nothing cached shows an error`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.setDetail(
                "tt1",
                Cached(data = null, lastUpdatedAt = null, refresh = RefreshState.Failed(AppError.NotFound)),
            )

            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            assertEquals(MovieDetailUiState.Error(AppError.NotFound), viewModel.uiState.value)
        }

    @Test
    fun `opening a movie records it in history exactly once`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.setDetail("tt1", Cached(data = detail("tt1", "Batman Begins"), lastUpdatedAt = 1L))

            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            // Favorite toggles and refreshes both re-emit the underlying flow; none of that
            // should re-record the view.
            viewModel.onToggleFavorite()
            advanceUntilIdle()
            movies.setDetail("tt1", Cached(data = detail("tt1", "Batman Begins"), lastUpdatedAt = 2L))
            advanceUntilIdle()

            assertEquals(1, recentlyViewed.observeRecentlyViewed().first().size)
        }

    @Test
    fun `favoriting from detail is reflected immediately and shared`() =
        runTest(mainDispatcherRule.scheduler) {
            movies.setDetail("tt1", Cached(data = detail("tt1", "Batman Begins"), lastUpdatedAt = 1L))
            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            viewModel.onToggleFavorite()
            advanceUntilIdle()

            assertTrue((viewModel.uiState.value as MovieDetailUiState.Content).isFavorite)
            assertTrue(favorites.isFavorite("tt1"))

            viewModel.onToggleFavorite()
            advanceUntilIdle()

            assertFalse((viewModel.uiState.value as MovieDetailUiState.Content).isFavorite)
            assertFalse(favorites.isFavorite("tt1"))
        }

    @Test
    fun `retry forces a refresh past the cache TTL`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()
        val callsAfterInit = movies.refreshDetailCallCount

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(callsAfterInit + 1, movies.refreshDetailCallCount)
    }

    private fun detail(imdbId: String, title: String) = MovieDetail(
        imdbId = imdbId,
        title = title,
        year = "2005",
        type = MovieType.MOVIE,
        posterUrl = null,
        rated = null,
        released = null,
        runtimeMinutes = null,
        genres = emptyList(),
        director = null,
        writers = emptyList(),
        cast = emptyList(),
        plot = null,
        languages = emptyList(),
        countries = emptyList(),
        awards = null,
        imdbRating = null,
        imdbVotes = null,
        metascore = null,
        ratings = emptyList(),
        boxOffice = null,
    )
}
