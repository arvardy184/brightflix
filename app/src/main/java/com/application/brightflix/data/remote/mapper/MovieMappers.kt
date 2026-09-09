package com.application.brightflix.data.remote.mapper

import com.application.brightflix.data.remote.dto.MovieDetailDto
import com.application.brightflix.data.remote.dto.RatingDto
import com.application.brightflix.data.remote.dto.SearchItemDto
import com.application.brightflix.data.remote.dto.SearchResponseDto
import com.application.brightflix.domain.model.Movie
import com.application.brightflix.domain.model.MovieDetail
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.Rating

/** OMDb's null literal. */
private const val NOT_AVAILABLE = "N/A"

/**
 * Maps OMDb wire models to domain models.
 *
 * Two invariants hold for everything in this file:
 *
 *  1. **Nothing throws.** Malformed or absent input yields null or an empty list. A single
 *     unparseable field must never cost the user the whole screen.
 *  2. **"N/A" never escapes.** It is the API's null, and it is converted here exactly once
 *     so no screen has to know that convention.
 *
 * Records missing an identity or a title return null; callers drop them. A movie that
 * cannot be identified cannot be opened, and one that cannot be named cannot be rendered.
 */

private fun String?.orNullIfUnavailable(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && !it.equals(NOT_AVAILABLE, ignoreCase = true) }

/** Splits OMDb's comma-delimited fields, e.g. "Action, Crime, Drama". */
private fun String?.toDelimitedList(): List<String> =
    orNullIfUnavailable()
        ?.split(',')
        ?.map(String::trim)
        ?.filter { it.isNotEmpty() && !it.equals(NOT_AVAILABLE, ignoreCase = true) }
        .orEmpty()

/** Parses "142 min" to 142 by reading the leading digits. */
private fun String?.toRuntimeMinutes(): Int? =
    orNullIfUnavailable()?.takeWhile(Char::isDigit)?.toIntOrNull()

/** Parses "2,845,132" to 2845132. */
private fun String?.toIntIgnoringGrouping(): Int? =
    orNullIfUnavailable()?.replace(",", "")?.toIntOrNull()

fun SearchItemDto.toDomainOrNull(): Movie? {
    val id = imdbId.orNullIfUnavailable() ?: return null
    val name = title.orNullIfUnavailable() ?: return null
    return Movie(
        imdbId = id,
        title = name,
        year = year.orNullIfUnavailable().orEmpty(),
        type = MovieType.fromApiValue(type),
        posterUrl = poster.orNullIfUnavailable(),
    )
}

fun MovieDetailDto.toDomainOrNull(): MovieDetail? {
    val id = imdbId.orNullIfUnavailable() ?: return null
    val name = title.orNullIfUnavailable() ?: return null
    return MovieDetail(
        imdbId = id,
        title = name,
        year = year.orNullIfUnavailable().orEmpty(),
        type = MovieType.fromApiValue(type),
        posterUrl = poster.orNullIfUnavailable(),
        rated = rated.orNullIfUnavailable(),
        released = released.orNullIfUnavailable(),
        runtimeMinutes = runtime.toRuntimeMinutes(),
        genres = genre.toDelimitedList(),
        director = director.orNullIfUnavailable(),
        writers = writer.toDelimitedList(),
        cast = actors.toDelimitedList(),
        plot = plot.orNullIfUnavailable(),
        languages = language.toDelimitedList(),
        countries = country.toDelimitedList(),
        awards = awards.orNullIfUnavailable(),
        imdbRating = imdbRating.orNullIfUnavailable()?.toDoubleOrNull(),
        imdbVotes = imdbVotes.toIntIgnoringGrouping(),
        metascore = metascore.toIntIgnoringGrouping(),
        ratings = ratings.orEmpty().mapNotNull(RatingDto::toDomainOrNull),
        boxOffice = boxOffice.orNullIfUnavailable(),
    )
}

fun RatingDto.toDomainOrNull(): Rating? {
    val ratingSource = source.orNullIfUnavailable() ?: return null
    val ratingValue = value.orNullIfUnavailable() ?: return null
    return Rating(source = ratingSource, value = ratingValue)
}

/** Search results, with unusable records dropped rather than rendered as blanks. */
fun SearchResponseDto.toMovies(): List<Movie> =
    search.orEmpty().mapNotNull(SearchItemDto::toDomainOrNull)

fun SearchResponseDto.parsedTotalResults(): Int = totalResults.toIntIgnoringGrouping() ?: 0
