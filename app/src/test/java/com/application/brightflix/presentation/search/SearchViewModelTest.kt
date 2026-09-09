package com.application.brightflix.presentation.search

import com.application.brightflix.core.result.AppError
import com.application.brightflix.core.result.AppResult
import com.application.brightflix.domain.model.Movie
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.SearchPage
import com.application.brightflix.domain.usecase.SearchMoviesUseCase
import com.application.brightflix.domain.usecase.ToggleFavoriteUseCase
import com.application.brightflix.testing.FakeFavoritesRepository
import com.application.brightflix.testing.FakeMovieRepository
import com.application.brightflix.testing.FakeNetworkMonitor
import com.application.brightflix.testing.MainDispatcherRule
import com.application.brightflix.testing.movie
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Live search behaviour.
 *
 * The debounce assertions matter commercially: OMDb allows 1000 requests a day, and a
 * naive implementation spends six of them typing the word "batman".
 *
 * The stale-append assertions matter for correctness: the first-page pipeline and the
 * pagination path are separate, so results for an abandoned query can otherwise land on
 * the list for a newer one.
 */
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMovieRepository()
    private val favorites = FakeFavoritesRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private fun viewModel() = SearchViewModel(
        searchMovies = SearchMoviesUseCase(repository),
        toggleFavorite = ToggleFavoriteUseCase(favorites),
        favoritesRepository = favorites,
        networkMonitor = networkMonitor,
    )

    // region debounce

    @Test
    fun `typing a word issues exactly one request, not one per keystroke`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, "tt1" to "Batman Begins")) }
            val viewModel = viewModel()

            listOf("b", "ba", "bat", "batm", "batma", "batman").forEach { keystroke ->
                viewModel.onQueryChange(keystroke)
                advanceTimeBy(50)
            }
            advanceUntilIdle()

            assertEquals(
                "six keystrokes inside the debounce window must collapse into one request",
                1,
                repository.searchCallCount,
            )
            assertEquals("batman", repository.lastQuery)
        }

    @Test
    fun `pausing between words issues one request per settled query`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, "tt1" to "A")) }
            val viewModel = viewModel()

            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            viewModel.onQueryChange("inception")
            advanceUntilIdle()

            assertEquals(2, repository.searchCallCount)
        }

    @Test
    fun `a single character never reaches the network`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()

        viewModel.onQueryChange("b")
        advanceUntilIdle()

        assertEquals(0, repository.searchCallCount)
        assertEquals(SearchUiState.Stage.Idle, viewModel.uiState.value.stage)
    }

    @Test
    fun `re-typing the identical query does not repeat the request`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, "tt1" to "A")) }
            val viewModel = viewModel()

            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            viewModel.onQueryChange("batman ")
            advanceUntilIdle()

            assertEquals("trailing whitespace is not a new query", 1, repository.searchCallCount)
        }

    // endregion

    // region stages

    @Test
    fun `the initial state is idle`() = runTest(mainDispatcherRule.scheduler) {
        assertEquals(SearchUiState.Stage.Idle, viewModel().uiState.value.stage)
    }

    @Test
    fun `a request that matches nothing is Empty, not Error`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(SearchPage.empty(page)) }
            val viewModel = viewModel()

            viewModel.onQueryChange("zzzzzzzz")
            advanceUntilIdle()

            assertEquals(SearchUiState.Stage.Empty, viewModel.uiState.value.stage)
        }

    @Test
    fun `a failure surfaces as an error stage`() = runTest(mainDispatcherRule.scheduler) {
        repository.searchHandler = { _, _ -> AppResult.Failure(AppError.Offline) }
        val viewModel = viewModel()

        viewModel.onQueryChange("batman")
        advanceUntilIdle()

        assertEquals(SearchUiState.Stage.Error(AppError.Offline), viewModel.uiState.value.stage)
    }

    @Test
    fun `retry re-issues the failed query and can recover`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, _ -> AppResult.Failure(AppError.Offline) }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()

            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, "tt1" to "Batman Begins")) }
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(SearchUiState.Stage.Content, viewModel.uiState.value.stage)
            assertEquals(1, viewModel.uiState.value.movies.size)
        }

    @Test
    fun `clearing the query returns to idle and drops results`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, "tt1" to "Batman Begins")) }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()

            viewModel.onClearQuery()
            advanceUntilIdle()

            assertEquals(SearchUiState.Stage.Idle, viewModel.uiState.value.stage)
            assertEquals(emptyList<Any>(), viewModel.uiState.value.movies)
        }

    // endregion

    // region pagination

    @Test
    fun `the next page appends and pagination stops at the reported total`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page ->
                when (page) {
                    1 -> AppResult.Success(SearchPage(movies = movies(1..10), totalResults = 15, page = 1))
                    else -> AppResult.Success(SearchPage(movies = movies(11..15), totalResults = 15, page = 2))
                }
            }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canLoadMore)

            viewModel.loadNextPage()
            advanceUntilIdle()

            assertEquals(15, viewModel.uiState.value.movies.size)
            assertFalse("all results are loaded", viewModel.uiState.value.canLoadMore)
        }

    @Test
    fun `repeated load-more taps while a page is in flight issue one request`() =
        runTest(mainDispatcherRule.scheduler) {
            val gate = CompletableDeferred<Unit>()
            repository.searchHandler = { _, page ->
                if (page == 1) {
                    AppResult.Success(SearchPage(movies = movies(1..10), totalResults = 50, page = 1))
                } else {
                    gate.await()
                    AppResult.Success(SearchPage(movies = movies(11..20), totalResults = 50, page = 2))
                }
            }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            val callsAfterFirstPage = repository.searchCallCount

            viewModel.loadNextPage()
            viewModel.loadNextPage()
            viewModel.loadNextPage()
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "a lazy list fires its load-more signal on consecutive frames",
                callsAfterFirstPage + 1,
                repository.searchCallCount,
            )
            assertEquals(20, viewModel.uiState.value.movies.size)
        }

    @Test
    fun `a failed next page keeps existing results and reports an inline error`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page ->
                if (page == 1) {
                    AppResult.Success(SearchPage(movies = movies(1..10), totalResults = 50, page = 1))
                } else {
                    AppResult.Failure(AppError.Timeout)
                }
            }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()

            viewModel.loadNextPage()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("results already on screen must survive", 10, state.movies.size)
            assertEquals(SearchUiState.Stage.Content, state.stage)
            assertEquals(AppError.Timeout, state.nextPageError)
        }

    @Test
    fun `duplicate records across page boundaries are not appended twice`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page ->
                if (page == 1) {
                    AppResult.Success(SearchPage(movies = movies(1..10), totalResults = 50, page = 1))
                } else {
                    // OMDb can repeat a title across pages; duplicate keys crash a lazy list.
                    AppResult.Success(SearchPage(movies = movies(10..14), totalResults = 50, page = 2))
                }
            }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()

            viewModel.loadNextPage()
            advanceUntilIdle()

            val ids = viewModel.uiState.value.movies.map { it.imdbId }
            assertEquals("ids must be unique", ids.size, ids.distinct().size)
        }

    // endregion

    // region stale-append correctness (spec 13.1)

    @Test
    fun `results for an abandoned query never append to a newer query`() =
        runTest(mainDispatcherRule.scheduler) {
            val stalePage = CompletableDeferred<Unit>()
            repository.searchHandler = { query, page ->
                when {
                    query == "batman" && page == 1 ->
                        AppResult.Success(SearchPage(movies = movies(1..10, "Batman"), totalResults = 50, page = 1))

                    query == "batman" && page == 2 -> {
                        stalePage.await()
                        AppResult.Success(SearchPage(movies = movies(11..20, "Batman"), totalResults = 50, page = 2))
                    }

                    else ->
                        AppResult.Success(SearchPage(movies = movies(1..3, "Inception"), totalResults = 3, page = 1))
                }
            }
            val viewModel = viewModel()

            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            viewModel.loadNextPage() // stalls on the gate

            viewModel.onQueryChange("inception")
            advanceUntilIdle()

            stalePage.complete(Unit) // the abandoned page finally returns
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(
                "page 2 of \"batman\" must never appear under \"inception\"",
                state.movies.none { it.movie.title.contains("Batman") },
            )
            assertEquals(3, state.movies.size)
            assertFalse(state.isLoadingNextPage)
        }

    @Test
    fun `a stale page is rejected even when the request cannot be cancelled`() =
        runTest(mainDispatcherRule.scheduler) {
            val stalePage = CompletableDeferred<Unit>()
            repository.searchHandler = { query, page ->
                when {
                    query == "batman" && page == 1 ->
                        AppResult.Success(SearchPage(movies = movies(1..10, "Batman"), totalResults = 50, page = 1))

                    query == "batman" && page == 2 -> {
                        // Uncancellable: cancellation alone cannot save us here, so this
                        // exercises the query-token guard specifically.
                        withContext(NonCancellable) { stalePage.await() }
                        AppResult.Success(SearchPage(movies = movies(11..20, "Batman"), totalResults = 50, page = 2))
                    }

                    else ->
                        AppResult.Success(SearchPage(movies = movies(1..3, "Inception"), totalResults = 3, page = 1))
                }
            }
            val viewModel = viewModel()

            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            viewModel.loadNextPage()

            viewModel.onQueryChange("inception")
            advanceUntilIdle()

            stalePage.complete(Unit)
            advanceUntilIdle()

            assertTrue(
                "the query-token check must reject the stale page",
                viewModel.uiState.value.movies.none { it.movie.title.contains("Batman") },
            )
            assertEquals(3, viewModel.uiState.value.movies.size)
        }

    // endregion

    // region cross-screen state

    @Test
    fun `favorite state comes from the shared source, not from the results themselves`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page ->
                AppResult.Success(pageOf(page, "tt1" to "Batman Begins", "tt2" to "The Dark Knight"))
            }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.movies.none { it.isFavorite })

            // Simulates a toggle made on another screen.
            favorites.add(movie("tt1"))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.movies.single { it.imdbId == "tt1" }.isFavorite)
            assertFalse(viewModel.uiState.value.movies.single { it.imdbId == "tt2" }.isFavorite)
        }

    @Test
    fun `toggling a favorite from search updates the shared source`() =
        runTest(mainDispatcherRule.scheduler) {
            repository.searchHandler = { _, page -> AppResult.Success(pageOf(page, "tt1" to "Batman Begins")) }
            val viewModel = viewModel()
            viewModel.onQueryChange("batman")
            advanceUntilIdle()

            viewModel.onToggleFavorite(viewModel.uiState.value.movies.first())
            advanceUntilIdle()

            assertTrue(favorites.isFavorite("tt1"))
            assertTrue(viewModel.uiState.value.movies.first().isFavorite)
        }

    @Test
    fun `going offline is reflected in state`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOffline)

        networkMonitor.setOnline(false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOffline)
    }

    @Test
    fun `no pagination error is reported before any page has failed`() =
        runTest(mainDispatcherRule.scheduler) {
            assertNull(viewModel().uiState.value.nextPageError)
        }

    // endregion

    private fun movies(range: IntRange, titlePrefix: String = "Movie"): List<Movie> =
        range.map { index ->
            Movie(
                imdbId = "tt$index",
                title = "$titlePrefix $index",
                year = "2005",
                type = MovieType.MOVIE,
                posterUrl = null,
            )
        }

    private fun pageOf(page: Int, vararg entries: Pair<String, String>) = SearchPage(
        movies = entries.map { (id, title) ->
            Movie(imdbId = id, title = title, year = "2005", type = MovieType.MOVIE, posterUrl = null)
        },
        totalResults = entries.size,
        page = page,
    )
}
