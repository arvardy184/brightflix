package com.application.brightflix.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.application.brightflix.data.local.dao.CollectionDao
import com.application.brightflix.data.local.dao.FavoriteDao
import com.application.brightflix.data.local.dao.MovieDetailDao
import com.application.brightflix.data.local.dao.RecentlyViewedDao
import com.application.brightflix.data.local.entity.CollectionMovieEntity
import com.application.brightflix.data.local.entity.CollectionRefreshEntity
import com.application.brightflix.data.local.entity.FavoriteMovieEntity
import com.application.brightflix.data.local.entity.MovieDetailEntity
import com.application.brightflix.data.local.entity.RecentlyViewedEntity

/**
 * The local database.
 *
 * Two kinds of data live here and they are not equivalent:
 *
 *  - **Owned data** — favorites and view history. Room is their single source of truth and
 *    losing them is data loss.
 *  - **Cached data** — movie details and collection rows. Reconstructible from the API;
 *    losing them costs a refresh.
 *
 * The distinction matters for migrations: a destructive migration would be acceptable for
 * the cached tables and never for the owned ones, which is why schemas are exported and
 * real migrations are expected from version 2 onward.
 */
@Database(
    entities = [
        FavoriteMovieEntity::class,
        RecentlyViewedEntity::class,
        MovieDetailEntity::class,
        CollectionMovieEntity::class,
        CollectionRefreshEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BrightflixDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentlyViewedDao(): RecentlyViewedDao
    abstract fun movieDetailDao(): MovieDetailDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val NAME = "brightflix.db"
    }
}
