package com.application.brightflix.domain.model

import com.application.brightflix.core.result.RefreshState

/**
 * A movie plus whether the user has saved it.
 *
 * Favorite state is attached here, derived from one shared source, rather than tracked by
 * each screen. That is what makes Home, Search, Detail and Favorites agree by construction.
 */
data class MovieListItem(
    val movie: Movie,
    val isFavorite: Boolean,
) {
    val imdbId: String get() = movie.imdbId
}

/** One curated row on the home screen. */
data class CollectionRow(
    val id: String,
    val title: String,
    val items: List<MovieListItem>,
    /** True until the row has been fetched at least once, so it can show a skeleton. */
    val isLoading: Boolean,
)

/**
 * Everything the home screen renders, assembled from several independent sources.
 *
 * [lastUpdatedAt] is the *oldest* successful collection refresh, so a "updated N minutes
 * ago" label describes the staleness of the whole screen honestly rather than flattering it.
 */
data class HomeFeed(
    val recentlyViewed: List<MovieListItem>,
    val collections: List<CollectionRow>,
    val lastUpdatedAt: Long?,
    val refresh: RefreshState,
) {
    val hasContent: Boolean
        get() = collections.any { it.items.isNotEmpty() } || recentlyViewed.isNotEmpty()

    val isInitialLoad: Boolean
        get() = collections.all { it.isLoading } && recentlyViewed.isEmpty()
}
