package com.application.brightflix.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the OMDb API. These never leave the data layer.
 *
 * Two properties of the API drive their shape:
 *  - Field names are PascalCase, and a few are camelCase (`imdbID`), so every name is
 *    mapped explicitly.
 *  - Failures are returned with HTTP 200 and signalled by `Response: "False"` plus an
 *    `Error` string, so both fields exist on every response type.
 *
 * Every field is nullable with a default: OMDb omits fields freely depending on the title,
 * and a missing field must never fail deserialization.
 */
@Serializable
data class SearchResponseDto(
    @SerialName("Search") val search: List<SearchItemDto>? = null,
    @SerialName("totalResults") val totalResults: String? = null,
    @SerialName("Response") val response: String? = null,
    @SerialName("Error") val error: String? = null,
)

@Serializable
data class SearchItemDto(
    @SerialName("imdbID") val imdbId: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Year") val year: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Poster") val poster: String? = null,
)

@Serializable
data class MovieDetailDto(
    @SerialName("imdbID") val imdbId: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Year") val year: String? = null,
    @SerialName("Rated") val rated: String? = null,
    @SerialName("Released") val released: String? = null,
    @SerialName("Runtime") val runtime: String? = null,
    @SerialName("Genre") val genre: String? = null,
    @SerialName("Director") val director: String? = null,
    @SerialName("Writer") val writer: String? = null,
    @SerialName("Actors") val actors: String? = null,
    @SerialName("Plot") val plot: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("Country") val country: String? = null,
    @SerialName("Awards") val awards: String? = null,
    @SerialName("Poster") val poster: String? = null,
    @SerialName("Ratings") val ratings: List<RatingDto>? = null,
    @SerialName("Metascore") val metascore: String? = null,
    @SerialName("imdbRating") val imdbRating: String? = null,
    @SerialName("imdbVotes") val imdbVotes: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("BoxOffice") val boxOffice: String? = null,
    @SerialName("Response") val response: String? = null,
    @SerialName("Error") val error: String? = null,
)

@Serializable
data class RatingDto(
    @SerialName("Source") val source: String? = null,
    @SerialName("Value") val value: String? = null,
)
