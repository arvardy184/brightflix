package com.application.brightflix.data.remote

import com.application.brightflix.core.result.AppError
import com.application.brightflix.core.result.AppResult
import com.application.brightflix.data.remote.api.OmdbApi
import com.application.brightflix.data.remote.dto.MovieDetailDto
import com.application.brightflix.data.remote.dto.SearchItemDto
import com.application.brightflix.data.remote.dto.SearchResponseDto
import com.application.brightflix.domain.model.MovieType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * OMDb reports logical failures with HTTP 200 and a `Response: "False"` body, so the
 * status code alone never tells you whether a request succeeded. These tests pin down the
 * translation from that convention — and from transport exceptions — to typed errors.
 */
class MovieRemoteDataSourceTest {

    private val api = FakeOmdbApi()
    private val dataSource = MovieRemoteDataSource(api = api, apiKey = "test-key")

    // region OMDb's HTTP-200 failures

    @Test
    fun `a search matching nothing is a success with zero results, not a failure`() = runTest {
        api.searchResponse = failedSearch(error = "Movie not found!")

        val result = dataSource.search("zzzzzzzz", page = 1, type = null)

        assertTrue("expected Success, got $result", result is AppResult.Success)
        assertEquals(emptyList<Any>(), (result as AppResult.Success).data.movies)
        assertEquals(0, result.data.totalResults)
    }

    @Test
    fun `too many results is guidance, not a generic failure`() = runTest {
        api.searchResponse = failedSearch(error = "Too many results.")

        val result = dataSource.search("th", page = 1, type = null)

        assertEquals(AppResult.Failure(AppError.TooManyResults), result)
    }

    @Test
    fun `an invalid api key is reported as a configuration problem`() = runTest {
        api.searchResponse = failedSearch(error = "Invalid API key!")

        val result = dataSource.search("batman", page = 1, type = null)

        assertEquals(AppResult.Failure(AppError.MissingApiKey), result)
    }

    @Test
    fun `an unrecognised omdb error is preserved verbatim`() = runTest {
        api.searchResponse = failedSearch(error = "Something odd happened.")

        val result = dataSource.search("batman", page = 1, type = null)

        assertEquals(AppResult.Failure(AppError.Api("Something odd happened.")), result)
    }

    @Test
    fun `an incorrect imdb id is treated as not found`() = runTest {
        api.detailResponse = MovieDetailDto(response = "False", error = "Incorrect IMDb ID.")

        val result = dataSource.detail("tt-nonsense")

        assertEquals(AppResult.Failure(AppError.NotFound), result)
    }

    // endregion

    // region success mapping

    @Test
    fun `a successful search maps results and total count`() = runTest {
        api.searchResponse = SearchResponseDto(
            search = listOf(
                SearchItemDto("tt0372784", "Batman Begins", "2005", "movie", "N/A"),
                SearchItemDto("tt0468569", "The Dark Knight", "2008", "movie", "http://p.jpg"),
            ),
            totalResults = "142",
            response = "True",
        )

        val result = dataSource.search("batman", page = 1, type = MovieType.MOVIE)

        val page = (result as AppResult.Success).data
        assertEquals(2, page.movies.size)
        assertEquals(142, page.totalResults)
        assertEquals(1, page.page)
        assertEquals("tt0372784", page.movies.first().imdbId)
    }

    @Test
    fun `unusable records are dropped rather than rendered as blanks`() = runTest {
        api.searchResponse = SearchResponseDto(
            search = listOf(
                SearchItemDto("tt1", "Real Movie", "2005", "movie", "N/A"),
                SearchItemDto(imdbId = null, title = "No Identity"),
                SearchItemDto(imdbId = "tt3", title = null),
            ),
            totalResults = "3",
            response = "True",
        )

        val result = dataSource.search("batman", page = 1, type = null)

        assertEquals(1, (result as AppResult.Success).data.movies.size)
    }

    // endregion

    // region transport failures

    @Test
    fun `an unknown host means offline`() = runTest {
        api.throwOnCall = UnknownHostException("no dns")

        assertEquals(
            AppResult.Failure(AppError.Offline),
            dataSource.search("batman", page = 1, type = null),
        )
    }

    @Test
    fun `a socket timeout is distinguished from being offline`() = runTest {
        api.throwOnCall = SocketTimeoutException("too slow")

        assertEquals(
            AppResult.Failure(AppError.Timeout),
            dataSource.search("batman", page = 1, type = null),
        )
    }

    @Test
    fun `an http error preserves its status code`() = runTest {
        api.throwOnCall = HttpException(
            Response.error<Any>(503, "".toResponseBody("application/json".toMediaType())),
        )

        assertEquals(
            AppResult.Failure(AppError.Server(503)),
            dataSource.search("batman", page = 1, type = null),
        )
    }

    @Test
    fun `cancellation propagates instead of being swallowed as a failure`() = runTest {
        api.throwOnCall = CancellationException("cancelled")

        var propagated = false
        try {
            dataSource.search("batman", page = 1, type = null)
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(
            "CancellationException must not be converted into an AppResult.Failure — " +
                "swallowing it breaks structured concurrency",
            propagated,
        )
    }

    // endregion

    @Test
    fun `a blank api key fails fast without issuing a request`() = runTest {
        val unconfigured = MovieRemoteDataSource(api = api, apiKey = "")

        val result = unconfigured.search("batman", page = 1, type = null)

        assertEquals(AppResult.Failure(AppError.MissingApiKey), result)
        assertEquals(0, api.callCount)
    }

    private fun failedSearch(error: String) =
        SearchResponseDto(response = "False", error = error)

    private class FakeOmdbApi : OmdbApi {
        var searchResponse: SearchResponseDto = SearchResponseDto(response = "True")
        var detailResponse: MovieDetailDto = MovieDetailDto(response = "True")
        var throwOnCall: Throwable? = null
        var callCount: Int = 0

        override suspend fun search(query: String, page: Int, type: String?): SearchResponseDto {
            callCount++
            throwOnCall?.let { throw it }
            return searchResponse
        }

        override suspend fun detail(imdbId: String, plot: String): MovieDetailDto {
            callCount++
            throwOnCall?.let { throw it }
            return detailResponse
        }
    }
}
