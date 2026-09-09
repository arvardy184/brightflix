package com.application.brightflix.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.lifecycle.SavedStateHandle
import com.application.brightflix.core.result.AppResult
import com.application.brightflix.core.result.Cached
import com.application.brightflix.domain.model.MovieDetail
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.SearchPage
import com.application.brightflix.domain.usecase.ObserveMovieDetailUseCase
import com.application.brightflix.domain.usecase.SearchMoviesUseCase
import com.application.brightflix.domain.usecase.ToggleFavoriteUseCase
import com.application.brightflix.presentation.detail.MovieDetailScreenContent
import com.application.brightflix.presentation.detail.MovieDetailViewModel
import com.application.brightflix.presentation.favorites.FavoritesScreenContent
import com.application.brightflix.presentation.favorites.FavoritesViewModel
import com.application.brightflix.presentation.navigation.MovieDetailRoute
import com.application.brightflix.presentation.search.SearchScreenContent
import com.application.brightflix.presentation.search.SearchViewModel
import com.application.brightflix.testing.FakeFavoritesRepository
import com.application.brightflix.testing.FakeMovieRepository
import com.application.brightflix.testing.FakeNetworkMonitor
import com.application.brightflix.testing.FakeRecentlyViewedRepository
import com.application.brightflix.testing.movie
import com.application.brightflix.ui.theme.BrightflixTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The critical user journey, end to end through real ViewModels and real Compose UI:
 *
 * search → see a result → open detail → favorite it → find it under Favorites.
 *
 * Repositories are fakes, so no network or database is involved, but every layer above
 * them is the production implementation.
 *
 * Runs on the JVM under Robolectric so `./gradlew test` genuinely executes it.
 */
@RunWith(RobolectricTestRunner::class)
class CriticalJourneyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val movieRepository = FakeMovieRepository()
    private val favoritesRepository = FakeFavoritesRepository()
    private val recentlyViewedRepository = FakeRecentlyViewedRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private val batman = movie("tt0372784", "Batman Begins")

    @Test
    fun `a movie favorited from detail appears under favorites`() {
        movieRepository.searchHandler = { _, page ->
            AppResult.Success(SearchPage(movies = listOf(batman), totalResults = 1, page = page))
        }
        movieRepository.setDetail(
            batman.imdbId,
            Cached(data = batmanDetail(), lastUpdatedAt = 1_000L),
        )

        setJourneyContent()

        // 1. Search for the movie. The debounce is real, so wait for results rather than
        // assuming a fixed delay.
        composeTestRule.onNodeWithText("Search movies").performTextInput("batman")
        composeTestRule.waitUntil(TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithText("Batman Begins").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Open its detail screen.
        composeTestRule.onAllNodesWithText("Batman Begins").onFirst().performClick()
        composeTestRule.waitUntil(TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasContentDescription("Add Batman Begins to favorites"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // 3. Favorite it. The label must name the movie and the action.
        composeTestRule
            .onAllNodes(hasContentDescription("Add Batman Begins to favorites"))
            .onFirst()
            .performClick()

        composeTestRule.waitUntil(TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasContentDescription("Remove Batman Begins from favorites"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // 4. The shared source of truth was updated, not just the screen.
        assertTrue(favoritesRepository.currentFavoriteIds().contains(batman.imdbId))

        // 5. Navigate to Favorites and confirm the movie is there.
        composeTestRule.onNodeWithContentDescription("Go back").performClick()
        composeTestRule.waitUntil(TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithText("Favorites").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Batman Begins").onFirst().assertExists()
    }

    /**
     * A minimal three-screen host.
     *
     * Real Navigation Compose is exercised by the app itself; here the point is the
     * ViewModel-and-UI behaviour, so screen switching is kept trivial to avoid testing the
     * navigation library.
     */
    private fun setJourneyContent() {
        val searchViewModel = SearchViewModel(
            searchMovies = SearchMoviesUseCase(movieRepository),
            toggleFavorite = ToggleFavoriteUseCase(favoritesRepository),
            favoritesRepository = favoritesRepository,
            networkMonitor = networkMonitor,
        )
        val detailViewModel = MovieDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(MovieDetailRoute.IMDB_ID_ARG to batman.imdbId),
            ),
            observeMovieDetail = ObserveMovieDetailUseCase(movieRepository, favoritesRepository),
            movieRepository = movieRepository,
            recentlyViewedRepository = recentlyViewedRepository,
            toggleFavorite = ToggleFavoriteUseCase(favoritesRepository),
            networkMonitor = networkMonitor,
        )
        val favoritesViewModel = FavoritesViewModel(
            favoritesRepository = favoritesRepository,
            toggleFavorite = ToggleFavoriteUseCase(favoritesRepository),
        )

        composeTestRule.setContent {
            BrightflixTheme {
                var screen by remember { mutableStateOf(Screen.SEARCH) }

                when (screen) {
                    Screen.SEARCH -> SearchPane(searchViewModel) { screen = Screen.DETAIL }
                    Screen.DETAIL -> DetailPane(detailViewModel) { screen = Screen.FAVORITES }
                    Screen.FAVORITES -> FavoritesPane(favoritesViewModel)
                }
            }
        }
    }

    @Composable
    private fun SearchPane(viewModel: SearchViewModel, onOpenMovie: () -> Unit) {
        val state by viewModel.uiState.collectAsState()
        SearchScreenContent(
            uiState = state,
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::onClearQuery,
            onLoadNextPage = viewModel::loadNextPage,
            onRetry = viewModel::retry,
            onToggleFavorite = viewModel::onToggleFavorite,
            onMovieClick = { onOpenMovie() },
        )
    }

    @Composable
    private fun DetailPane(viewModel: MovieDetailViewModel, onBack: () -> Unit) {
        val state by viewModel.uiState.collectAsState()
        MovieDetailScreenContent(
            uiState = state,
            onNavigateBack = onBack,
            onToggleFavorite = viewModel::onToggleFavorite,
            onRetry = viewModel::retry,
        )
    }

    @Composable
    private fun FavoritesPane(viewModel: FavoritesViewModel) {
        val state by viewModel.uiState.collectAsState()
        FavoritesScreenContent(
            uiState = state,
            onMovieClick = {},
            onToggleFavorite = viewModel::onToggleFavorite,
        )
    }

    private enum class Screen { SEARCH, DETAIL, FAVORITES }

    private fun batmanDetail() = MovieDetail(
        imdbId = batman.imdbId,
        title = batman.title,
        year = "2005",
        type = MovieType.MOVIE,
        posterUrl = null,
        rated = "PG-13",
        released = "15 Jun 2005",
        runtimeMinutes = 140,
        genres = listOf("Action", "Crime", "Drama"),
        director = "Christopher Nolan",
        writers = listOf("Bob Kane"),
        cast = listOf("Christian Bale"),
        plot = "A young Bruce Wayne becomes Batman.",
        languages = listOf("English"),
        countries = listOf("United States"),
        awards = "Nominated for 1 Oscar.",
        imdbRating = 8.2,
        imdbVotes = 1_500_000,
        metascore = 70,
        ratings = emptyList(),
        boxOffice = "$206,863,479",
    )

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}

/** Reads the current favorite IDs synchronously, for a plain assertion. */
private fun FakeFavoritesRepository.currentFavoriteIds(): Set<String> =
    runBlocking { observeFavoriteIds().first() }
