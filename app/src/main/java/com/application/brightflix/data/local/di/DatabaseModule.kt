package com.application.brightflix.data.local.di

import android.content.Context
import androidx.room.Room
import com.application.brightflix.data.local.dao.CollectionDao
import com.application.brightflix.data.local.dao.FavoriteDao
import com.application.brightflix.data.local.dao.MovieDetailDao
import com.application.brightflix.data.local.dao.RecentlyViewedDao
import com.application.brightflix.data.local.database.BrightflixDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): BrightflixDatabase =
        Room.databaseBuilder(
            context,
            BrightflixDatabase::class.java,
            BrightflixDatabase.NAME,
        ).build()
    // Deliberately no fallbackToDestructiveMigration: favorites are user-owned data, and
    // silently dropping them on a schema change would be data loss. Version 2 gets a real
    // migration.

    @Provides
    fun providesFavoriteDao(database: BrightflixDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun providesRecentlyViewedDao(database: BrightflixDatabase): RecentlyViewedDao =
        database.recentlyViewedDao()

    @Provides
    fun providesMovieDetailDao(database: BrightflixDatabase): MovieDetailDao =
        database.movieDetailDao()

    @Provides
    fun providesCollectionDao(database: BrightflixDatabase): CollectionDao = database.collectionDao()
}
