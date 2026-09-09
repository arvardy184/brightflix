package com.application.brightflix.domain.model

/** A rating from one source, e.g. "Rotten Tomatoes" → "94%". */
data class Rating(
    val source: String,
    val value: String,
)

/**
 * The full record backing the detail screen.
 *
 * Every optional field is genuinely nullable (or an empty list) rather than holding the
 * literal `"N/A"` string the API uses. That normalisation happens once, in the mapper, so
 * the UI can decide to omit a section by a plain null check and no screen ever renders
 * "N/A" to a user.
 */
data class MovieDetail(
    val imdbId: String,
    val title: String,
    val year: String,
    val type: MovieType,
    val posterUrl: String?,
    val rated: String?,
    val released: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val director: String?,
    val writers: List<String>,
    val cast: List<String>,
    val plot: String?,
    val languages: List<String>,
    val countries: List<String>,
    val awards: String?,
    val imdbRating: Double?,
    val imdbVotes: Int?,
    val metascore: Int?,
    val ratings: List<Rating>,
    val boxOffice: String?,
) {
    /** The list-shaped projection of this record, for favorites and view history. */
    fun toMovie(): Movie = Movie(
        imdbId = imdbId,
        title = title,
        year = year,
        type = type,
        posterUrl = posterUrl,
    )
}
