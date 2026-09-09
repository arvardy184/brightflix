package com.application.brightflix.domain.model

/**
 * A movie as it appears in a list: everything needed to render a poster card, nothing more.
 *
 * [imdbId] is the app's only movie identity. Titles are neither unique nor stable, and it
 * is what OMDb's lookup endpoint consumes.
 */
data class Movie(
    val imdbId: String,
    val title: String,
    /** Kept as text: OMDb emits ranges such as "2011–2019" for series. */
    val year: String,
    val type: MovieType,
    /** Null when the API has no poster, so the UI can show a real fallback. */
    val posterUrl: String?,
)
