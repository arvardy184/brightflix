package com.application.brightflix.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.Rating

/**
 * Room entities.
 *
 * Each stores only the fields a screen actually needs rather than mirroring the whole API
 * response. `imdbId` is the primary key everywhere, which is what makes deduplication a
 * property of the schema rather than something application code has to remember.
 */

/** A movie the user saved. Room owns this data outright; it is never fetched. */
@Entity(tableName = "favorites")
data class FavoriteMovieEntity(
    @PrimaryKey val imdbId: String,
    val title: String,
    val year: String,
    val type: MovieType,
    val posterUrl: String?,
    val addedAt: Long,
)

/**
 * A movie the user opened.
 *
 * The primary key on [imdbId] is deliberate: re-opening a movie updates the existing row's
 * [viewedAt] instead of appending a duplicate, so "no duplicates in history" is enforced by
 * the schema and cannot be broken by a caller.
 */
@Entity(tableName = "recently_viewed")
data class RecentlyViewedEntity(
    @PrimaryKey val imdbId: String,
    val title: String,
    val year: String,
    val type: MovieType,
    val posterUrl: String?,
    val viewedAt: Long,
)

/**
 * A cached full detail record.
 *
 * [cachedAt] drives the TTL: detail is near-immutable, so a long TTL is both correct and
 * the single biggest saving against OMDb's daily request quota.
 */
@Entity(tableName = "movie_details")
data class MovieDetailEntity(
    @PrimaryKey val imdbId: String,
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
    val cachedAt: Long,
)

/**
 * One movie within a curated home collection.
 *
 * The composite key allows the same movie to appear in more than one collection, and
 * [position] preserves the order the API returned rather than relying on insertion order.
 */
@Entity(
    tableName = "collection_movies",
    primaryKeys = ["collectionId", "imdbId"],
    indices = [Index("collectionId")],
)
data class CollectionMovieEntity(
    val collectionId: String,
    val imdbId: String,
    val title: String,
    val year: String,
    val type: MovieType,
    val posterUrl: String?,
    val position: Int,
)

/**
 * When each collection last refreshed successfully.
 *
 * Kept separate from [CollectionMovieEntity] so a collection that legitimately returns zero
 * results still records a successful refresh — deriving freshness from the rows themselves
 * would make an empty collection look permanently stale and retry on every launch.
 */
@Entity(tableName = "collection_refresh")
data class CollectionRefreshEntity(
    @PrimaryKey val collectionId: String,
    val refreshedAt: Long,
)
