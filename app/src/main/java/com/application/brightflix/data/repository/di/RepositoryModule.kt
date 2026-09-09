package com.application.brightflix.data.repository.di

import com.application.brightflix.data.repository.FavoritesRepositoryImpl
import com.application.brightflix.data.repository.MovieRepositoryImpl
import com.application.brightflix.data.repository.RecentlyViewedRepositoryImpl
import com.application.brightflix.domain.repository.FavoritesRepository
import com.application.brightflix.domain.repository.MovieRepository
import com.application.brightflix.domain.repository.RecentlyViewedRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds repository interfaces (domain) to their implementations (data).
 *
 * This is the dependency inversion that keeps the domain layer free of Room and Retrofit,
 * and lets every use case be tested against a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMovieRepository(impl: MovieRepositoryImpl): MovieRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindRecentlyViewedRepository(
        impl: RecentlyViewedRepositoryImpl,
    ): RecentlyViewedRepository
}
