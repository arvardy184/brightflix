package com.application.brightflix.data.remote

import com.application.brightflix.core.di.ApiKey
import com.application.brightflix.core.result.AppError
import com.application.brightflix.core.result.AppResult
import com.application.brightflix.data.remote.api.OmdbApi
import com.application.brightflix.data.remote.dto.MovieDetailDto
import com.application.brightflix.data.remote.dto.SearchResponseDto
import com.application.brightflix.data.remote.mapper.parsedTotalResults
import com.application.brightflix.data.remote.mapper.toDomainOrNull
import com.application.brightflix.data.remote.mapper.toMovies
import com.application.brightflix.domain.model.MovieDetail
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.SearchPage
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only place that talks to OMDb.
 *
 * Its job is to turn two kinds of failure into one typed [AppResult]:
 *
 *  1. **Transport failures** — thrown exceptions, mapped to [AppError] by cause.
 *  2. **Logical failures returned as HTTP 200** — OMDb signals these with
 *     `Response: "False"` and an `Error` string, so the status code alone never tells you
 *     whether a request succeeded.
 *
 * No Retrofit or OkHttp type escapes this class.
 *
 * Retrofit's suspend functions are already main-safe (OkHttp dispatches on its own pool),
 * so no explicit dispatcher switch is needed here.
 */
@Singleton
class MovieRemoteDataSource @Inject constructor(
    private val api: OmdbApi,
    @param:ApiKey private val apiKey: String,
) {

    suspend fun search(query: String, page: Int, type: MovieType?): AppResult<SearchPage> {
        if (apiKey.isBlank()) return AppResult.Failure(AppError.MissingApiKey)

        return safeCall {
            val dto = api.search(query = query, page = page, type = type?.apiValue)

            if (dto.isSuccessful) {
                AppResult.Success(
                    SearchPage(
                        movies = dto.toMovies(),
                        totalResults = dto.parsedTotalResults(),
                        page = page,
                    ),
                )
            } else {
                when (val error = dto.error.toAppError()) {
                    // A search that matched nothing is a successful request with zero
                    // results, not a failure. Treating it as an error would show a retry
                    // button for a query that will never match.
                    AppError.NotFound -> AppResult.Success(SearchPage.empty(page))
                    else -> AppResult.Failure(error)
                }
            }
        }
    }

    suspend fun detail(imdbId: String): AppResult<MovieDetail> {
        if (apiKey.isBlank()) return AppResult.Failure(AppError.MissingApiKey)

        return safeCall {
            val dto = api.detail(imdbId = imdbId)

            if (dto.isSuccessful) {
                // A record we cannot map has no identity or title, so it cannot be shown.
                dto.toDomainOrNull()
                    ?.let { AppResult.Success(it) }
                    ?: AppResult.Failure(AppError.NotFound)
            } else {
                AppResult.Failure(dto.error.toAppError())
            }
        }
    }
}

private val SearchResponseDto.isSuccessful: Boolean
    get() = response.equals("True", ignoreCase = true)

private val MovieDetailDto.isSuccessful: Boolean
    get() = response.equals("True", ignoreCase = true)

/** Maps OMDb's human-readable `Error` strings to typed errors. */
private fun String?.toAppError(): AppError = when {
    this == null -> AppError.Unknown
    contains("not found", ignoreCase = true) -> AppError.NotFound
    // OMDb's wording for an unrecognised ID; semantically the same as "not found".
    contains("Incorrect IMDb ID", ignoreCase = true) -> AppError.NotFound
    contains("Too many results", ignoreCase = true) -> AppError.TooManyResults
    contains("API key", ignoreCase = true) -> AppError.MissingApiKey
    else -> AppError.Api(this)
}

/**
 * Maps transport exceptions to [AppError].
 *
 * [CancellationException] is rethrown first and deliberately: swallowing it would break
 * structured concurrency, so a cancelled search would look like a failed one and leave
 * stale state behind. Ordering matters throughout — [SocketTimeoutException] and
 * [UnknownHostException] both extend [IOException], so they must be caught before it.
 */
private inline fun <T> safeCall(block: () -> AppResult<T>): AppResult<T> = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: UnknownHostException) {
    AppResult.Failure(AppError.Offline)
} catch (_: ConnectException) {
    AppResult.Failure(AppError.Offline)
} catch (_: SocketTimeoutException) {
    AppResult.Failure(AppError.Timeout)
} catch (http: HttpException) {
    AppResult.Failure(AppError.Server(http.code()))
} catch (_: IOException) {
    AppResult.Failure(AppError.Offline)
} catch (_: Exception) {
    AppResult.Failure(AppError.Unknown)
}
