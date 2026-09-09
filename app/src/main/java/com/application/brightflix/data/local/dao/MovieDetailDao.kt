package com.application.brightflix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.application.brightflix.data.local.entity.MovieDetailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDetailDao {

    @Query("SELECT * FROM movie_details WHERE imdbId = :imdbId")
    fun observe(imdbId: String): Flow<MovieDetailEntity?>

    @Query("SELECT cachedAt FROM movie_details WHERE imdbId = :imdbId")
    suspend fun cachedAt(imdbId: String): Long?

    @Upsert
    suspend fun upsert(entity: MovieDetailEntity)
}
