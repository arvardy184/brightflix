package com.application.brightflix.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.application.brightflix.core.result.AppError
import com.application.brightflix.presentation.favorites.FavoritesScreenContent
import com.application.brightflix.presentation.favorites.FavoritesUiState
import com.application.brightflix.presentation.search.SearchScreenContent
import com.application.brightflix.presentation.search.SearchUiState
import com.application.brightflix.ui.theme.BrightflixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Empty and error states.
 *
 * These are the screens users hit when something goes wrong, and they are the easiest to
 * regress unnoticed because the happy path keeps working.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenStatesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `an untouched search screen invites the user to start`() {
        setSearchContent(SearchUiState())

        composeTestRule.onNodeWithText("Find your next movie").assertIsDisplayed()
    }

    @Test
    fun `a search with no matches explains that, rather than showing an error`() {
        setSearchContent(SearchUiState(query = "zzzz", stage = SearchUiState.Stage.Empty))

        composeTestRule.onNodeWithText("No movies found").assertIsDisplayed()
    }

    @Test
    fun `an offline search points the user at what still works`() {
        setSearchContent(SearchUiState(isOffline = true))

        // Search is the one feature that cannot work offline, so this must not be a dead end.
        composeTestRule.onNodeWithText("Search needs a connection").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "You can still browse your favorites and recently viewed movies while offline.",
            )
            .assertIsDisplayed()
    }

    @Test
    fun `a failure offers a retry that actually re-runs the search`() {
        var retries = 0
        setSearchContent(
            uiState = SearchUiState(
                query = "batman",
                stage = SearchUiState.Stage.Error(AppError.Timeout),
            ),
            onRetry = { retries++ },
        )

        composeTestRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `an unconfigured api key is reported plainly and offers no useless retry`() {
        setSearchContent(
            SearchUiState(
                query = "batman",
                stage = SearchUiState.Stage.Error(AppError.MissingApiKey),
            ),
        )

        composeTestRule
            .onNodeWithText("No OMDb API key is configured. See the README setup steps.")
            .assertIsDisplayed()
        // Retrying cannot fix a missing key, so the button is deliberately absent.
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun `an over-broad query is treated as guidance, not as a failure`() {
        setSearchContent(
            SearchUiState(
                query = "th",
                stage = SearchUiState.Stage.Error(AppError.TooManyResults),
            ),
        )

        composeTestRule.onNodeWithText("Too many matches").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun `an empty favorites screen explains how to fill it`() {
        composeTestRule.setContent {
            BrightflixTheme {
                FavoritesScreenContent(
                    uiState = FavoritesUiState(),
                    onMovieClick = {},
                    onToggleFavorite = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Your favorite movies will appear here")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Tap the heart on any movie to save it for later.")
            .assertIsDisplayed()
    }

    private fun setSearchContent(
        uiState: SearchUiState,
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BrightflixTheme {
                SearchScreenContent(
                    uiState = uiState,
                    onQueryChange = {},
                    onClearQuery = {},
                    onLoadNextPage = {},
                    onRetry = onRetry,
                    onToggleFavorite = {},
                    onMovieClick = {},
                )
            }
        }
    }
}
