package com.application.brightflix.domain.model

/**
 * A named row on the home screen, backed by a fixed OMDb search query.
 *
 * These are **curated by the app, not ranked by the API**. OMDb exposes no popularity,
 * trending, or rating-order data, so a row labelled "Trending" or "Top Rated" would be
 * fabricated. Titles here describe a theme instead of claiming a ranking.
 */
data class CuratedCollection(
    val id: String,
    val title: String,
    val query: String,
    val type: MovieType,
)
