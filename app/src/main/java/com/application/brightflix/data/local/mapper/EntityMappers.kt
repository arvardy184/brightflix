package com.application.brightflix.data.local.mapper

import com.application.brightflix.data.local.entity.CollectionMovieEntity
import com.application.brightflix.data.local.entity.FavoriteMovieEntity
import com.application.brightflix.data.local.entity.MovieDetailEntity
import com.application.brightflix.data.local.entity.RecentlyViewedEntity
import com.application.brightflix.domain.model.Movie
import com.application.brightflix.domain.model.MovieDetail

/**
 * Entity ↔ domain conversion.
 *
 * Keeps Room types out of the domain layer. Timestamps are supplied by the caller rather
 * than read from a clock here, so cache-expiry behaviour stays deterministic under test.
 */

fun FavoriteMovieEntity.toDomain(): Movie = Movie(
    imdbId = imdbId,
    title = title,
    year = year,
    type = type,
    posterUrl = posterUrl,
)

fun Movie.toFavoriteEntity(addedAt: Long): FavoriteMovieEntity = FavoriteMovieEntity(
    imdbId = imdbId,
    title = title,
    year = year,
    type = type,
    posterUrl = posterUrl,
    addedAt = addedAt,
)

fun RecentlyViewedEntity.toDomain(): Movie = Movie(
    imdbId = imdbId,
    title = title,
    year = year,
    type = type,
    posterUrl = posterUrl,
)

fun Movie.toRecentlyViewedEntity(viewedAt: Long): RecentlyViewedEntity = RecentlyViewedEntity(
    imdbId = imdbId,
    title = title,
    year = year,
    type = type,
    posterUrl = posterUrl,
    viewedAt = viewedAt,
)

fun CollectionMovieEntity.toDomain(): Movie = Movie(
    imdbId = imdbId,
    title = title,
    year = year,
    type = type,
    posterUrl = posterUrl,
)

fun Movie.toCollectionEntity(collectionId: String, position: Int): CollectionMovieEntity =
    CollectionMovieEntity(
        collectionId = collectionId,
        imdbId = imdbId,
        title = title,
        year = year,
        type = type,
        posterUrl = posterUrl,
        position = position,
    )

fun MovieDetailEntity.toDomain(): MovieDetail = MovieDetail(
    imdbId = imdbId,
    title = title,
    year = year,
    type = type,
    posterUrl = posterUrl,
    rated = rated,
    released = released,
    runtimeMinutes = runtimeMinutes,
    genres = genres,
    director = director,
    writers = writers,
    cast = cast,
    plot = plot,
    languages = languages,
    countries = countries,
    awards = awards,
    imdbRating = imdbRating,
    imdbVotes = imdbVotes,
    metascore = metascore,
    ratings = ratings,
    boxOffice = boxOffice,
)

fun MovieDetail.toEntity(cachedAt: Long): MovieDetailEntity = MovieDetailEntity(
    imdbId = imdbId,
    title = title,
    year = year,
    type = type,
    posterUrl = posterUrl,
    rated = rated,
    released = released,
    runtimeMinutes = runtimeMinutes,
    genres = genres,
    director = director,
    writers = writers,
    cast = cast,
    plot = plot,
    languages = languages,
    countries = countries,
    awards = awards,
    imdbRating = imdbRating,
    imdbVotes = imdbVotes,
    metascore = metascore,
    ratings = ratings,
    boxOffice = boxOffice,
    cachedAt = cachedAt,
)
