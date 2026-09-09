package com.application.brightflix.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 *
 * Destinations are declared as serializable types rather than string patterns, so argument
 * names and types are checked at compile time instead of failing at runtime on a typo.
 */

@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

@Serializable
data object FavoritesRoute

/**
 * The movie detail destination.
 *
 * Carries only an IMDb ID. Passing the whole movie would bloat the saved state, go stale
 * against the cache, and break deep links — the detail screen reconstructs everything it
 * needs from this one value.
 */
@Serializable
data class MovieDetailRoute(val imdbId: String) {
    companion object {
        /**
         * Navigation stores type-safe route arguments under their property names, so the
         * ViewModel can read this key straight from its [androidx.lifecycle.SavedStateHandle].
         */
        const val IMDB_ID_ARG = "imdbId"
    }
}
