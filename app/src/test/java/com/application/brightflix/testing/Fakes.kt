package com.application.brightflix.testing

import com.application.brightflix.core.network.NetworkMonitor
import com.application.brightflix.core.time.TimeProvider
import com.application.brightflix.data.remote.api.OmdbApi
import com.application.brightflix.data.remote.dto.MovieDetailDto
import com.application.brightflix.data.remote.dto.SearchItemDto
import com.application.brightflix.data.remote.dto.SearchResponseDto
import com.application.brightflix.domain.model.Movie
import com.application.brightflix.domain.model.MovieType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A clock the test controls, so cache-TTL behaviour is deterministic. */
class FakeTimeProvider(var now: Long = 1_000_000L) : TimeProvider {
    override fun nowMillis(): Long = now

    fun advanceBy(millis: Long) {
        now += millis
    }
}

class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {
    private val online = MutableStateFlow(initiallyOnline)
    override val isOnline: Flow<Boolean> = online

    fun setOnline(value: Boolean) {
        online.value = value
    }
}

/**
 * A scriptable OMDb API.
 *
 * Faking at the Retrofit-interface boundary rather than at the data source keeps the real
 * response-mapping and error-classification logic under test.
 */
class FakeOmdbApi : OmdbApi {

    var searchResponse: SearchResponseDto = SearchResponseDto(response = "True", totalResults = "0")
    var detailResponse: MovieDetailDto = MovieDetailDto(response = "True")
    var throwOnCall: Throwable? = null

    var searchCallCount: Int = 0
        private set
    var detailCallCount: Int = 0
        private set
    var lastQuery: String? = null
        private set
    var lastPage: Int? = null
        private set

    /** Per-query scripted responses, for tests that exercise several collections. */
    val responsesByQuery: MutableMap<String, SearchResponseDto> = mutableMapOf()

    override suspend fun search(query: String, page: Int, type: String?): SearchResponseDto {
        searchCallCount++
        lastQuery = query
        lastPage = page
        throwOnCall?.let { throw it }
        return responsesByQuery[query] ?: searchResponse
    }

    override suspend fun detail(imdbId: String, plot: String): MovieDetailDto {
        detailCallCount++
        throwOnCall?.let { throw it }
        return detailResponse
    }

    fun reset() {
        searchCallCount = 0
        detailCallCount = 0
        throwOnCall = null
        responsesByQuery.clear()
    }
}

fun searchResponseOf(vararg movies: Pair<String, String>, total: Int = movies.size) =
    SearchResponseDto(
        search = movies.map { (id, title) ->
            SearchItemDto(imdbId = id, title = title, year = "2005", type = "movie", poster = "N/A")
        },
        totalResults = total.toString(),
        response = "True",
    )

fun detailResponseOf(imdbId: String, title: String) = MovieDetailDto(
    imdbId = imdbId,
    title = title,
    year = "2005",
    type = "movie",
    response = "True",
)

fun movie(imdbId: String, title: String = "Movie $imdbId") = Movie(
    imdbId = imdbId,
    title = title,
    year = "2005",
    type = MovieType.MOVIE,
    posterUrl = null,
)
