package com.application.brightflix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.application.brightflix.data.local.entity.FavoriteMovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteMovieEntity>>

    /**
     * The app's single source of truth for favorite state.
     *
     * Every screen that renders a movie combines its own data with this stream, so a
     * toggle anywhere updates every screen at once and no screen can drift out of sync.
     * Returning only IDs keeps it cheap enough to observe everywhere.
     */
    @Query("SELECT imdbId FROM favorites ORDER BY addedAt DESC")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE imdbId = :imdbId)")
    suspend fun exists(imdbId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteMovieEntity)

    @Query("DELETE FROM favorites WHERE imdbId = :imdbId")
    suspend fun delete(imdbId: String)
}
