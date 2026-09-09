package com.application.brightflix.domain.model

/**
 * One page of search results.
 *
 * [totalResults] is what makes pagination terminate correctly: the caller compares it
 * against the number of results accumulated so far to decide whether more pages exist.
 */
data class SearchPage(
    val movies: List<Movie>,
    val totalResults: Int,
    val page: Int,
) {
    companion object {
        fun empty(page: Int): SearchPage = SearchPage(
            movies = emptyList(),
            totalResults = 0,
            page = page,
        )
    }
}
