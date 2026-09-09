package com.application.brightflix.domain.model

/**
 * The kind of title a record describes.
 *
 * [UNKNOWN] exists so an unrecognised value from the API degrades gracefully instead of
 * throwing — OMDb is free to add types this app has never heard of.
 */
enum class MovieType(val apiValue: String?) {
    MOVIE("movie"),
    SERIES("series"),
    EPISODE("episode"),
    GAME("game"),
    UNKNOWN(null),
    ;

    companion object {
        fun fromApiValue(value: String?): MovieType =
            entries.firstOrNull { it.apiValue != null && it.apiValue.equals(value, ignoreCase = true) }
                ?: UNKNOWN
    }
}
