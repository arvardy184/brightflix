package com.application.brightflix.domain.usecase

import com.application.brightflix.core.result.AppError
import com.application.brightflix.core.result.AppResult
import com.application.brightflix.core.result.Cached
import com.application.brightflix.core.result.RefreshState
import com.application.brightflix.domain.model.CuratedCollections
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.SearchPage
import com.application.brightflix.testing.FakeFavoritesRepository
import com.application.brightflix.testing.FakeMovieRepository
import com.application.brightflix.testing.FakeRecentlyViewedRepository
import com.application.brightflix.testing.movie
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    private val favorites = FakeFavoritesRepository()
    private val toggleFavorite = ToggleFavoriteUseCase(favorites)

    @Test
    fun `toggling adds when absent and removes when present`() = runTest {
        val batman = movie("tt1", "Batman Begins")

        assertTrue("first toggle should save", toggleFavorite(batman))
        assertTrue(favorites.isFavorite("tt1"))

        assertFalse("second toggle should unsave", toggleFavorite(batman))
        assertFalse(favorites.isFavorite("tt1"))
    }

    @Test
    fun `toggling one movie does not affect another`() = runTest {
        toggleFavorite(movie("tt1"))
        toggleFavorite(movie("tt2"))
        toggleFavorite(movie("tt1"))

        assertFalse(favorites.isFavorite("tt1"))
        assertTrue(favorites.isFavorite("tt2"))
    }
}

class SearchMoviesUseCaseTest {

    private val repository = FakeMovieRepository()
    private val searchMovies = SearchMoviesUseCase(repository)

    @Test
    fun `a one character query never reaches the network`() = runTest {
        val result = searchMovies(query = "b", page = 1)

        assertEquals(0, repository.searchCallCount)
        assertEquals(emptyList<Any>(), (result as AppResult.Success).data.movies)
    }

    @Test
    fun `a whitespace-only query never reaches the network`() = runTest {
        searchMovies(query = "   ", page = 1)

        assertEquals(0, repository.searchCallCount)
    }

    @Test
    fun `the query is trimmed before it is sent`() = runTest {
        searchMovies(query = "  batman  ", page = 1)

        assertEquals("batman", repository.lastQuery)
    }

    @Test
    fun `results are restricted to films`() = runTest {
        searchMovies(query = "batman", page = 1)

        assertEquals(MovieType.MOVIE, repository.lastType)
    }

    @Test
    fun `a repository failure is passed through unchanged`() = runTest {
        repository.searchHandler = { _, _ -> AppResult.Failure(AppError.Offline) }

        assertEquals(
            AppResult.Failure(AppError.Offline),
            searchMovies(query = "batman", page = 1),
        )
    }

    @Test
    fun `the requested page is forwarded`() = runTest {
        repository.searchHandler = { _, page -> AppResult.Success(SearchPage.empty(page)) }

        searchMovies(query = "batman", page = 3)

        assertEquals(3, repository.lastPage)
    }
}

class ObserveMovieDetailUseCaseTest {

    private val movies = FakeMovieRepository()
    private val favorites = FakeFavoritesRepository()
    private val observeDetail = ObserveMovieDetailUseCase(movies, favorites)

    @Test
    fun `favorite state is joined onto the detail stream`() = runTest {
        favorites.add(movie("tt1"))

        assertTrue(observeDetail("tt1").first().isFavorite)
        assertFalse(observeDetail("tt2").first().isFavorite)
    }

    @Test
    fun `cached content and refresh state are passed through`() = runTest {
        movies.setDetail(
            "tt1",
            Cached(data = null, lastUpdatedAt = 500L, refresh = RefreshState.Failed(AppError.Offline)),
        )

        val state = observeDetail("tt1").first()

        assertEquals(RefreshState.Failed(AppError.Offline), state.cached.refresh)
        assertEquals(500L, state.cached.lastUpdatedAt)
    }
}

class ObserveHomeFeedUseCaseTest {

    private val movies = FakeMovieRepository()
    private val favorites = FakeFavoritesRepository()
    private val recentlyViewed = FakeRecentlyViewedRepository()
    private val observeHomeFeed = ObserveHomeFeedUseCase(movies, favorites, recentlyViewed)

    private val firstCollection = CuratedCollections.ALL[0]
    private val secondCollection = CuratedCollections.ALL[1]

    @Test
    fun `a row is loading until it has been fetched at least once`() = runTest {
        val feed = observeHomeFeed().first()

        assertTrue(feed.collections.all { it.isLoading })
        assertTrue(feed.isInitialLoad)
    }

    @Test
    fun `favorite state is applied consistently across every row and history`() = runTest {
        favorites.add(movie("tt1"))
        movies.setCollection(
            firstCollection.id,
            Cached(data = listOf(movie("tt1"), movie("tt2")), lastUpdatedAt = 100L),
        )
        movies.setCollection(
            secondCollection.id,
            Cached(data = listOf(movie("tt1")), lastUpdatedAt = 200L),
        )
        recentlyViewed.record(movie("tt1"))

        val feed = observeHomeFeed().first()

        val allRenderedTt1 = feed.collections.flatMap { it.items }.filter { it.imdbId == "tt1" } +
            feed.recentlyViewed.filter { it.imdbId == "tt1" }
        assertEquals(3, allRenderedTt1.size)
        assertTrue(
            "the same movie must not appear saved in one row and unsaved in another",
            allRenderedTt1.all { it.isFavorite },
        )
        assertTrue(feed.collections.flatMap { it.items }.single { it.imdbId == "tt2" }.isFavorite.not())
    }

    @Test
    fun `collection titles and ordering come from the curated catalogue`() = runTest {
        val feed = observeHomeFeed().first()

        assertEquals(CuratedCollections.ALL.map { it.title }, feed.collections.map { it.title })
        assertEquals(3, feed.collections.size)
    }

    @Test
    fun `the oldest refresh timestamp represents the screen`() = runTest {
        movies.setCollection(firstCollection.id, Cached(data = listOf(movie("tt1")), lastUpdatedAt = 900L))
        movies.setCollection(secondCollection.id, Cached(data = listOf(movie("tt2")), lastUpdatedAt = 100L))

        assertEquals(
            "reporting the newest timestamp would overstate how fresh the screen is",
            100L,
            observeHomeFeed().first().lastUpdatedAt,
        )
    }

    @Test
    fun `a single failing row is surfaced without hiding the rows that succeeded`() = runTest {
        movies.setCollection(firstCollection.id, Cached(data = listOf(movie("tt1")), lastUpdatedAt = 100L))
        movies.setCollection(
            secondCollection.id,
            Cached(data = null, lastUpdatedAt = null, refresh = RefreshState.Failed(AppError.Offline)),
        )

        val feed = observeHomeFeed().first()

        assertEquals(RefreshState.Failed(AppError.Offline), feed.refresh)
        assertTrue("content from healthy rows must survive", feed.hasContent)
        assertEquals(1, feed.collections.first { it.id == firstCollection.id }.items.size)
    }

    @Test
    fun `an in-progress refresh takes precedence over a failure`() = runTest {
        movies.setCollection(
            firstCollection.id,
            Cached(data = null, lastUpdatedAt = null, refresh = RefreshState.InProgress),
        )
        movies.setCollection(
            secondCollection.id,
            Cached(data = null, lastUpdatedAt = null, refresh = RefreshState.Failed(AppError.Offline)),
        )

        assertEquals(RefreshState.InProgress, observeHomeFeed().first().refresh)
    }

    @Test
    fun `an empty screen reports no update timestamp`() = runTest {
        assertNull(observeHomeFeed().first().lastUpdatedAt)
    }
}

class RecentlyViewedContractTest {

    private val recentlyViewed = FakeRecentlyViewedRepository()

    @Test
    fun `re-viewing a movie moves it to the top without duplicating it`() = runTest {
        recentlyViewed.record(movie("tt1", "Batman Begins"))
        recentlyViewed.record(movie("tt2", "Interstellar"))
        recentlyViewed.record(movie("tt1", "Batman Begins"))

        val entries = recentlyViewed.observeRecentlyViewed().first()

        assertEquals(2, entries.size)
        assertEquals("tt1", entries.first().imdbId)
    }
}
