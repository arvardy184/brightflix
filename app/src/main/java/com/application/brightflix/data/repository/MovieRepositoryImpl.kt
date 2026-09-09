package com.application.brightflix.data.repository

import com.application.brightflix.core.result.AppResult
import com.application.brightflix.core.result.Cached
import com.application.brightflix.core.result.RefreshState
import com.application.brightflix.core.time.TimeProvider
import com.application.brightflix.domain.model.CuratedCollections
import com.application.brightflix.data.local.dao.CollectionDao
import com.application.brightflix.data.local.dao.MovieDetailDao
import com.application.brightflix.data.local.entity.CollectionMovieEntity
import com.application.brightflix.data.local.mapper.toCollectionEntity
import com.application.brightflix.data.local.mapper.toDomain
import com.application.brightflix.data.local.mapper.toEntity
import com.application.brightflix.data.remote.MovieRemoteDataSource
import com.application.brightflix.domain.model.CuratedCollection
import com.application.brightflix.domain.model.Movie
import com.application.brightflix.domain.model.MovieDetail
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.SearchPage
import com.application.brightflix.domain.repository.MovieRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Movie data with a tiered cache.
 *
 * Detail and collections are cached in Room and read cache-first; search is not cached at
 * all. See [MovieRepository] for why that asymmetry is deliberate.
 *
 * Refresh state is held in memory rather than in the database because it describes an
 * in-flight operation, not a fact about the data. Persisting it would leave a stale
 * "refreshing" flag behind if the process died mid-request.
 */
@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val remoteDataSource: MovieRemoteDataSource,
    private val movieDetailDao: MovieDetailDao,
    private val collectionDao: CollectionDao,
    private val timeProvider: TimeProvider,
) : MovieRepository {

    private val detailRefreshStates = MutableStateFlow<Map<String, RefreshState>>(emptyMap())
    private val collectionRefreshStates = MutableStateFlow<Map<String, RefreshState>>(emptyMap())

    // Search: straight through to the network, never cached.
    override suspend fun search(query: String, page: Int, type: MovieType?): AppResult<SearchPage> =
        remoteDataSource.search(query = query, page = page, type = type)

    override fun observeMovieDetail(imdbId: String): Flow<Cached<MovieDetail>> = combine(
        movieDetailDao.observe(imdbId),
        detailRefreshStates.map { it[imdbId] ?: RefreshState.Idle }.distinctUntilChanged(),
    ) { entity, refresh ->
        Cached(
            data = entity?.toDomain(),
            lastUpdatedAt = entity?.cachedAt,
            refresh = refresh,
        )
    }

    override suspend fun refreshMovieDetail(imdbId: String, force: Boolean): AppResult<Unit> {
        // The single biggest saving against OMDb's daily quota: a cache hit within the TTL
        // returns without issuing any request at all.
        val cachedAt = movieDetailDao.cachedAt(imdbId)
        if (!force && cachedAt != null && isFresh(cachedAt, DETAIL_TTL_MILLIS)) {
            return AppResult.Success(Unit)
        }

        setDetailRefresh(imdbId, RefreshState.InProgress)

        return when (val result = remoteDataSource.detail(imdbId)) {
            is AppResult.Success -> {
                movieDetailDao.upsert(result.data.toEntity(cachedAt = timeProvider.nowMillis()))
                setDetailRefresh(imdbId, RefreshState.Idle)
                AppResult.Success(Unit)
            }

            is AppResult.Failure -> {
                // The cached row is left untouched, so observeMovieDetail keeps emitting
                // the old content alongside this error rather than blanking the screen.
                setDetailRefresh(imdbId, RefreshState.Failed(result.error))
                result
            }
        }
    }

    override fun observeCollection(collectionId: String): Flow<Cached<List<Movie>>> = combine(
        collectionDao.observeCollection(collectionId),
        collectionDao.observeRefreshedAt(collectionId),
        collectionRefreshStates.map { it[collectionId] ?: RefreshState.Idle }.distinctUntilChanged(),
    ) { rows, refreshedAt, refresh ->
        Cached(
            // A null refresh timestamp means "never fetched", which is what separates a
            // first load from a collection that was fetched and is legitimately empty.
            data = if (refreshedAt != null) rows.map(CollectionMovieEntity::toDomain) else null,
            lastUpdatedAt = refreshedAt,
            refresh = refresh,
        )
    }

    override suspend fun refreshCollections(force: Boolean): AppResult<Unit> = coroutineScope {
        val results = CuratedCollections.ALL
            .map { collection -> async { refreshCollection(collection, force) } }
            .awaitAll()

        // One failing collection must not blank the whole home screen. Only a total
        // failure is reported as a failure.
        if (results.any { it is AppResult.Success }) {
            AppResult.Success(Unit)
        } else {
            results.filterIsInstance<AppResult.Failure>().firstOrNull() ?: AppResult.Success(Unit)
        }
    }

    override suspend fun randomCachedMovie(): Movie? =
        collectionDao.randomMovie()?.toDomain()

    private suspend fun refreshCollection(
        collection: CuratedCollection,
        force: Boolean,
    ): AppResult<Unit> {
        val refreshedAt = collectionDao.refreshedAt(collection.id)
        if (!force && refreshedAt != null && isFresh(refreshedAt, COLLECTION_TTL_MILLIS)) {
            return AppResult.Success(Unit)
        }

        setCollectionRefresh(collection.id, RefreshState.InProgress)

        return when (
            val result = remoteDataSource.search(
                query = collection.query,
                page = FIRST_PAGE,
                type = collection.type,
            )
        ) {
            is AppResult.Success -> {
                collectionDao.replaceCollection(
                    collectionId = collection.id,
                    movies = result.data.movies.mapIndexed { index, movie ->
                        movie.toCollectionEntity(collectionId = collection.id, position = index)
                    },
                    refreshedAt = timeProvider.nowMillis(),
                )
                setCollectionRefresh(collection.id, RefreshState.Idle)
                AppResult.Success(Unit)
            }

            is AppResult.Failure -> {
                setCollectionRefresh(collection.id, RefreshState.Failed(result.error))
                result
            }
        }
    }

    private fun isFresh(timestamp: Long, ttlMillis: Long): Boolean =
        timeProvider.nowMillis() - timestamp < ttlMillis

    private fun setDetailRefresh(imdbId: String, state: RefreshState) {
        detailRefreshStates.update { it + (imdbId to state) }
    }

    private fun setCollectionRefresh(collectionId: String, state: RefreshState) {
        collectionRefreshStates.update { it + (collectionId to state) }
    }

    private companion object {
        const val FIRST_PAGE = 1

        /**
         * Detail is effectively immutable — cast, plot and runtime do not change — so a
         * long TTL is both correct and the biggest single reduction in API calls.
         */
        val DETAIL_TTL_MILLIS: Long = TimeUnit.DAYS.toMillis(7)

        /** Collections are re-queried daily; pull-to-refresh bypasses this. */
        val COLLECTION_TTL_MILLIS: Long = TimeUnit.HOURS.toMillis(24)
    }
}
