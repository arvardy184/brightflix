package com.application.brightflix.domain.usecase

import com.application.brightflix.core.result.Cached
import com.application.brightflix.core.result.RefreshState
import com.application.brightflix.domain.model.CollectionRow
import com.application.brightflix.domain.model.CuratedCollection
import com.application.brightflix.domain.model.CuratedCollections
import com.application.brightflix.domain.model.HomeFeed
import com.application.brightflix.domain.model.Movie
import com.application.brightflix.domain.model.MovieListItem
import com.application.brightflix.domain.repository.FavoritesRepository
import com.application.brightflix.domain.repository.MovieRepository
import com.application.brightflix.domain.repository.RecentlyViewedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Assembles the home screen from five independent sources: three curated collections, view
 * history, and favorite state.
 *
 * Composing them here rather than in the ViewModel means the screen observes one stream and
 * cannot render a half-updated combination — for example collections that already know a
 * movie is saved while the history row still shows it unsaved.
 */
class ObserveHomeFeedUseCase @Inject constructor(
    private val movieRepository: MovieRepository,
    private val favoritesRepository: FavoritesRepository,
    private val recentlyViewedRepository: RecentlyViewedRepository,
) {

    operator fun invoke(): Flow<HomeFeed> = combine(
        collectionsFlow(),
        recentlyViewedRepository.observeRecentlyViewed(),
        favoritesRepository.observeFavoriteIds(),
    ) { collections, recentlyViewed, favoriteIds ->
        HomeFeed(
            recentlyViewed = recentlyViewed.toListItems(favoriteIds),
            collections = collections.map { (collection, cached) ->
                CollectionRow(
                    id = collection.id,
                    title = collection.title,
                    items = cached.data.orEmpty().toListItems(favoriteIds),
                    // Null data means never fetched — the row shows a skeleton rather than
                    // an empty state it has not earned yet.
                    isLoading = cached.data == null,
                )
            },
            // The oldest refresh across all rows, so "updated N minutes ago" describes the
            // staleness of the whole screen rather than flattering it with the newest.
            lastUpdatedAt = collections.mapNotNull { it.second.lastUpdatedAt }.minOrNull(),
            refresh = aggregateRefresh(collections.map { it.second.refresh }),
        )
    }

    private fun collectionsFlow(): Flow<List<Pair<CuratedCollection, Cached<List<Movie>>>>> =
        combine(
            CuratedCollections.ALL.map { collection ->
                movieRepository.observeCollection(collection.id).map { collection to it }
            },
        ) { it.toList() }

    private fun List<Movie>.toListItems(favoriteIds: Set<String>): List<MovieListItem> =
        map { MovieListItem(movie = it, isFavorite = it.imdbId in favoriteIds) }

    /**
     * Reduces per-row refresh states to one.
     *
     * A single failed row is reported so the screen can show a "showing saved data" banner,
     * but it never hides the rows that did succeed.
     */
    private fun aggregateRefresh(states: List<RefreshState>): RefreshState = when {
        states.any { it == RefreshState.InProgress } -> RefreshState.InProgress
        else -> states.filterIsInstance<RefreshState.Failed>().firstOrNull() ?: RefreshState.Idle
    }
}
