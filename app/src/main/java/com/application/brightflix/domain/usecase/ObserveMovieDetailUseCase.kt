package com.application.brightflix.domain.usecase

import com.application.brightflix.core.result.Cached
import com.application.brightflix.domain.model.MovieDetail
import com.application.brightflix.domain.repository.FavoritesRepository
import com.application.brightflix.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Cached detail together with whether this movie is currently saved. */
data class MovieDetailState(
    val cached: Cached<MovieDetail>,
    val isFavorite: Boolean,
)

/**
 * Joins a movie's cached detail with its favorite state.
 *
 * Favorite state comes from the same shared source every other screen reads, so tapping
 * the heart here updates Home, Search and Favorites in the same frame — and the detail
 * screen can never show a movie as saved while a list shows it as unsaved.
 */
class ObserveMovieDetailUseCase @Inject constructor(
    private val movieRepository: MovieRepository,
    private val favoritesRepository: FavoritesRepository,
) {

    operator fun invoke(imdbId: String): Flow<MovieDetailState> = combine(
        movieRepository.observeMovieDetail(imdbId),
        favoritesRepository.observeFavoriteIds(),
    ) { cached, favoriteIds ->
        MovieDetailState(cached = cached, isFavorite = imdbId in favoriteIds)
    }
}
