package com.application.brightflix.data.local.database

import androidx.room.TypeConverter
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.Rating
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Converters for the few non-primitive columns.
 *
 * JSON is used rather than a delimiter because genre and cast values legitimately contain
 * commas, which would corrupt a naive `split(",")` round trip.
 *
 * Decoding is defensive: a row written by an older schema must degrade to an empty list
 * rather than crash the app on read.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun toStringList(value: String): List<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), value)
    }.getOrDefault(emptyList())

    @TypeConverter
    fun fromRatingList(value: List<Rating>): String =
        json.encodeToString(ListSerializer(RatingSurrogate.serializer()), value.map(::RatingSurrogate))

    @TypeConverter
    fun toRatingList(value: String): List<Rating> = runCatching {
        json.decodeFromString(ListSerializer(RatingSurrogate.serializer()), value)
            .map { Rating(source = it.source, value = it.value) }
    }.getOrDefault(emptyList())

    @TypeConverter
    fun fromMovieType(value: MovieType): String = value.name

    @TypeConverter
    fun toMovieType(value: String): MovieType =
        runCatching { MovieType.valueOf(value) }.getOrDefault(MovieType.UNKNOWN)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Serialization surrogate for [Rating].
 *
 * Exists so the domain model stays free of serialization annotations — persistence format
 * is a data-layer concern and should not dictate the shape of a domain type.
 */
@kotlinx.serialization.Serializable
private data class RatingSurrogate(val source: String, val value: String) {
    constructor(rating: Rating) : this(rating.source, rating.value)
}
