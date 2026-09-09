package com.application.brightflix.data.repository

import com.application.brightflix.core.time.TimeProvider
import com.application.brightflix.data.local.dao.RecentlyViewedDao
import com.application.brightflix.data.local.entity.RecentlyViewedEntity
import com.application.brightflix.data.local.mapper.toDomain
import com.application.brightflix.data.local.mapper.toRecentlyViewedEntity
import com.application.brightflix.domain.model.Movie
import com.application.brightflix.domain.repository.RecentlyViewedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentlyViewedRepositoryImpl @Inject constructor(
    private val recentlyViewedDao: RecentlyViewedDao,
    private val timeProvider: TimeProvider,
) : RecentlyViewedRepository {

    override fun observeRecentlyViewed(): Flow<List<Movie>> =
        recentlyViewedDao.observeAll().map { entities -> entities.map(RecentlyViewedEntity::toDomain) }

    override suspend fun record(movie: Movie) {
        // Upsert first, then trim: deduplication comes from the primary key, and trimming
        // afterwards guarantees the row just written is among those kept.
        recentlyViewedDao.upsert(movie.toRecentlyViewedEntity(viewedAt = timeProvider.nowMillis()))
        recentlyViewedDao.trimTo(RecentlyViewedRepository.MAX_ENTRIES)
    }
}
