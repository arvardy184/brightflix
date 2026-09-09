package com.application.brightflix.domain.repository

import com.application.brightflix.core.result.AppResult
import com.application.brightflix.core.result.Cached
import com.application.brightflix.domain.model.Movie
import com.application.brightflix.domain.model.MovieDetail
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.SearchPage
import kotlinx.coroutines.flow.Flow

/**
 * Movie data, from wherever it happens to live.
 *
 * The tiering is deliberate and is the app's offline strategy:
 *
 *  - **Detail and collections** are cached in Room and observed as [Cached], so the UI can
 *    keep showing content when a refresh fails instead of collapsing into an error screen.
 *  - **Search is not cached** and returns a plain [AppResult]. Live search wants fresh
 *    results, and arbitrary typed queries have a near-zero cache hit rate, so caching them
 *    would add real complexity for no user benefit.
 */
interface MovieRepository {

    /**
     * Searches OMDb. Never reads or writes the cache — see the note above.
     *
     * A query that matches nothing returns an empty [SearchPage], not a failure.
     */
    suspend fun search(query: String, page: Int, type: MovieType?): AppResult<SearchPage>

    /** Emits cached detail immediately, then again as a refresh progresses or fails. */
    fun observeMovieDetail(imdbId: String): Flow<Cached<MovieDetail>>

    /**
     * Refreshes one movie's detail.
     *
     * @param force bypasses the TTL. Used by pull-to-refresh, so a user can always demand
     *   fresh data regardless of cache age.
     */
    suspend fun refreshMovieDetail(imdbId: String, force: Boolean): AppResult<Unit>

    fun observeCollection(collectionId: String): Flow<Cached<List<Movie>>>

    /** Refreshes every curated collection. Partial failure does not abort the rest. */
    suspend fun refreshCollections(force: Boolean): AppResult<Unit>

    /**
     * A random movie from data already on the device.
     *
     * Returns null when nothing is cached yet. Costs no API request and works offline.
     */
    suspend fun randomCachedMovie(): Movie?
}
