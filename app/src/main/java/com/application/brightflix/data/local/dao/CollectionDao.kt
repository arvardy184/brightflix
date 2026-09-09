package com.application.brightflix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.application.brightflix.data.local.entity.CollectionMovieEntity
import com.application.brightflix.data.local.entity.CollectionRefreshEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collection_movies WHERE collectionId = :collectionId ORDER BY position ASC")
    fun observeCollection(collectionId: String): Flow<List<CollectionMovieEntity>>

    @Query("SELECT refreshedAt FROM collection_refresh WHERE collectionId = :collectionId")
    suspend fun refreshedAt(collectionId: String): Long?

    /**
     * Observable form of [refreshedAt].
     *
     * A null value means the collection has never been fetched, which is what distinguishes
     * "still loading" from "fetched successfully and genuinely empty".
     */
    @Query("SELECT refreshedAt FROM collection_refresh WHERE collectionId = :collectionId")
    fun observeRefreshedAt(collectionId: String): Flow<Long?>

    /**
     * Replaces a collection's contents atomically.
     *
     * Delete-then-insert rather than upsert: a title that has dropped out of the API's
     * results must disappear from the row too, which an upsert alone would never do.
     */
    @Transaction
    suspend fun replaceCollection(
        collectionId: String,
        movies: List<CollectionMovieEntity>,
        refreshedAt: Long,
    ) {
        deleteCollection(collectionId)
        insertAll(movies)
        setRefreshedAt(CollectionRefreshEntity(collectionId, refreshedAt))
    }

    /**
     * Backs the "Surprise Me" action.
     *
     * Reads from data already on the device, so it costs no API request and works offline.
     */
    @Query("SELECT * FROM collection_movies ORDER BY RANDOM() LIMIT 1")
    suspend fun randomMovie(): CollectionMovieEntity?

    @Query("DELETE FROM collection_movies WHERE collectionId = :collectionId")
    suspend fun deleteCollection(collectionId: String)

    @Upsert
    suspend fun insertAll(movies: List<CollectionMovieEntity>)

    @Upsert
    suspend fun setRefreshedAt(entity: CollectionRefreshEntity)
}
