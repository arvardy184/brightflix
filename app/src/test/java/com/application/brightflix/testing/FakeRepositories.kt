package com.application.brightflix.testing

import com.application.brightflix.core.result.AppResult
import com.application.brightflix.core.result.Cached
import com.application.brightflix.domain.model.Movie
import com.application.brightflix.domain.model.MovieDetail
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.SearchPage
import com.application.brightflix.domain.repository.FavoritesRepository
import com.application.brightflix.domain.repository.MovieRepository
import com.application.brightflix.domain.repository.RecentlyViewedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory repository fakes.
 *
 * Preferred over mocks: they reproduce the real behavioural contract (deduplication,
 * ordering, capping) so a test that passes against a fake is evidence about the system,
 * not just about which methods were called.
 */

class FakeFavoritesRepository : FavoritesRepository {

    private val favorites = MutableStateFlow<Map<String, Movie>>(emptyMap())

    override fun observeFavorites(): Flow<List<Movie>> = favorites.map { it.values.toList() }

    override fun observeFavoriteIds(): Flow<Set<String>> = favorites.map { it.keys }

    override suspend fun isFavorite(imdbId: String): Boolean = favorites.value.containsKey(imdbId)

    override suspend fun add(movie: Movie) {
        favorites.update { it + (movie.imdbId to movie) }
    }

    override suspend fun remove(imdbId: String) {
        favorites.update { it - imdbId }
    }
}

class FakeRecentlyViewedRepository : RecentlyViewedRepository {

    private val entries = MutableStateFlow<List<Movie>>(emptyList())

    override fun observeRecentlyViewed(): Flow<List<Movie>> = entries

    /** Mirrors the real contract: dedupe by id, newest first, capped. */
    override suspend fun record(movie: Movie) {
        entries.update { current ->
            (listOf(movie) + current.filterNot { it.imdbId == movie.imdbId })
                .take(RecentlyViewedRepository.MAX_ENTRIES)
        }
    }
}

class FakeMovieRepository : MovieRepository {

    /** Scriptable so a test can stall a call and control interleaving. */
    var searchHandler: suspend (query: String, page: Int) -> AppResult<SearchPage> =
        { _, page -> AppResult.Success(SearchPage.empty(page)) }

    var searchCallCount: Int = 0
        private set
    var lastQuery: String? = null
        private set
    var lastPage: Int? = null
        private set
    var lastType: MovieType? = null
        private set

    var randomMovie: Movie? = null
    var refreshCollectionsCallCount: Int = 0
        private set
    var lastForcedRefresh: Boolean? = null
        private set
    var refreshCollectionsResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshDetailResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshDetailCallCount: Int = 0
        private set

    private val collections = mutableMapOf<String, MutableStateFlow<Cached<List<Movie>>>>()
    private val details = mutableMapOf<String, MutableStateFlow<Cached<MovieDetail>>>()

    override suspend fun search(query: String, page: Int, type: MovieType?): AppResult<SearchPage> {
        searchCallCount++
        lastQuery = query
        lastPage = page
        lastType = type
        return searchHandler(query, page)
    }

    override fun observeMovieDetail(imdbId: String): Flow<Cached<MovieDetail>> = detailFlow(imdbId)

    override suspend fun refreshMovieDetail(imdbId: String, force: Boolean): AppResult<Unit> {
        refreshDetailCallCount++
        return refreshDetailResult
    }

    override fun observeCollection(collectionId: String): Flow<Cached<List<Movie>>> =
        collectionFlow(collectionId)

    override suspend fun refreshCollections(force: Boolean): AppResult<Unit> {
        refreshCollectionsCallCount++
        lastForcedRefresh = force
        return refreshCollectionsResult
    }

    override suspend fun randomCachedMovie(): Movie? = randomMovie

    fun setCollection(collectionId: String, cached: Cached<List<Movie>>) {
        collectionFlow(collectionId).value = cached
    }

    fun setDetail(imdbId: String, cached: Cached<MovieDetail>) {
        detailFlow(imdbId).value = cached
    }

    private fun collectionFlow(collectionId: String) =
        collections.getOrPut(collectionId) { MutableStateFlow(Cached.empty()) }

    private fun detailFlow(imdbId: String) =
        details.getOrPut(imdbId) { MutableStateFlow(Cached.empty()) }
}
