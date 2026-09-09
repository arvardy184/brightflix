package com.application.brightflix.domain.usecase

import com.application.brightflix.core.result.AppResult
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.domain.model.SearchPage
import com.application.brightflix.domain.repository.MovieRepository
import javax.inject.Inject

/**
 * Owns the app's search policy, so it holds regardless of which caller invokes a search.
 *
 * Two rules live here rather than in the UI:
 *
 *  - **Queries shorter than [MIN_QUERY_LENGTH] never reach the network.** Single characters
 *    return noise and burn OMDb's daily quota. The search screen also debounces, but this
 *    guarantee does not depend on the UI getting that right.
 *  - **Results are restricted to films.** This is a movie app; series and game records
 *    would be noise.
 */
class SearchMoviesUseCase @Inject constructor(
    private val movieRepository: MovieRepository,
) {

    suspend operator fun invoke(query: String, page: Int): AppResult<SearchPage> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            return AppResult.Success(SearchPage.empty(page))
        }
        return movieRepository.search(query = trimmed, page = page, type = MovieType.MOVIE)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
