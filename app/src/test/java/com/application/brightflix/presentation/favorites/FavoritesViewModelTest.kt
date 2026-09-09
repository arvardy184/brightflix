package com.application.brightflix.presentation.favorites

import com.application.brightflix.domain.usecase.ToggleFavoriteUseCase
import com.application.brightflix.testing.FakeFavoritesRepository
import com.application.brightflix.testing.MainDispatcherRule
import com.application.brightflix.testing.movie
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val favorites = FakeFavoritesRepository()

    private fun viewModel() = FavoritesViewModel(
        favoritesRepository = favorites,
        toggleFavorite = ToggleFavoriteUseCase(favorites),
    )

    private fun TestScope.subscribed(viewModel: FavoritesViewModel) = also {
        backgroundScope.launch { viewModel.uiState.collect() }
    }

    @Test
    fun `no saved movies yields an empty state`() = runTest(mainDispatcherRule.scheduler) {
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `saved movies appear and are marked as favorites`() =
        runTest(mainDispatcherRule.scheduler) {
            favorites.add(movie("tt1", "Batman Begins"))

            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.movies.size)
            assertTrue(viewModel.uiState.value.movies.first().isFavorite)
        }

    @Test
    fun `unfavoriting removes the movie from the list`() = runTest(mainDispatcherRule.scheduler) {
        favorites.add(movie("tt1", "Batman Begins"))
        favorites.add(movie("tt2", "Interstellar"))
        val viewModel = viewModel()
        subscribed(viewModel)
        advanceUntilIdle()

        viewModel.onToggleFavorite(viewModel.uiState.value.movies.first { it.imdbId == "tt1" })
        advanceUntilIdle()

        assertEquals(listOf("tt2"), viewModel.uiState.value.movies.map { it.imdbId })
        assertFalse(favorites.isFavorite("tt1"))
    }

    @Test
    fun `a movie saved on another screen appears here without any refresh`() =
        runTest(mainDispatcherRule.scheduler) {
            val viewModel = viewModel()
            subscribed(viewModel)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isEmpty)

            favorites.add(movie("tt9", "Added Elsewhere"))
            advanceUntilIdle()

            assertEquals(listOf("tt9"), viewModel.uiState.value.movies.map { it.imdbId })
        }
}
