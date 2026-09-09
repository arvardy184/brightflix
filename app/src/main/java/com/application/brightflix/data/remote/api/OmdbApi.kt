package com.application.brightflix.data.remote.api

import com.application.brightflix.data.remote.dto.MovieDetailDto
import com.application.brightflix.data.remote.dto.SearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The OMDb API surface.
 *
 * OMDb exposes a single endpoint whose behaviour is selected by query parameters, so both
 * calls target the base URL and differ only in what they send.
 *
 * The API key is not a parameter here — an OkHttp interceptor attaches it to every
 * request, so it cannot be forgotten at a call site and does not clutter the signatures.
 */
interface OmdbApi {

    /**
     * Search by title. OMDb returns a fixed 10 results per page.
     *
     * @param type restricts results to `movie`, `series`, or `episode`; null means any.
     */
    @GET(".")
    suspend fun search(
        @Query("s") query: String,
        @Query("page") page: Int,
        @Query("type") type: String?,
    ): SearchResponseDto

    /** Look up one title by its IMDb ID. */
    @GET(".")
    suspend fun detail(
        @Query("i") imdbId: String,
        @Query("plot") plot: String = "full",
    ): MovieDetailDto
}
