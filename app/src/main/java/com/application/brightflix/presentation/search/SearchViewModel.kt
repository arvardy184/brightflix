package com.application.brightflix.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.brightflix.core.network.NetworkMonitor
import com.application.brightflix.core.result.AppResult
import com.application.brightflix.domain.model.MovieListItem
import com.application.brightflix.domain.model.SearchPage
import com.application.brightflix.domain.repository.FavoritesRepository
import com.application.brightflix.domain.usecase.SearchMoviesUseCase
import com.application.brightflix.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Live search.
 *
 * The first page is driven by a debounced flow; subsequent pages are loaded imperatively.
 * That split is what makes the interesting bug possible, and closing it is a hard
 * requirement rather than a nicety — see [loadNextPage].
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchMovies: SearchMoviesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    favoritesRepository: FavoritesRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val queryInput = MutableStateFlow("")

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /**
     * The pagination job, retained so a new query can cancel it.
     *
     * Without this, a page-2 request for "batman" stays alive after the user types
     * "inception" and appends Batman results to Inception's list.
     */
    private var nextPageJob: Job? = null

    /** Guards against a retry racing the debounced pipeline. */
    private var retryJob: Job? = null

    /**
     * The query the current results belong to.
     *
     * Checked before any append. Cancellation alone is not sufficient: a request that has
     * already returned and is resuming cannot be cancelled, so this second guard makes the
     * invariant hold even when that race is lost.
     */
    private var activeQuery: String = ""

    private var favoriteIds: Set<String> = emptySet()

    init {
        queryInput
            .debounce(DEBOUNCE_MILLIS)
            .map(String::trim)
            .distinctUntilChanged()
            // Guard 1: abandon any in-flight pagination the moment the query changes.
            .onEach { nextPageJob?.cancel() }
            // flatMapLatest cancels the previous first-page request, so a slow response for
            // an abandoned query can never overwrite a newer one.
            .flatMapLatest(::firstPageFlow)
            .onEach(::applyFirstPage)
            .launchIn(viewModelScope)

        // Favorite state is derived from the shared source rather than tracked here, so a
        // toggle made on any other screen is reflected in these results immediately.
        favoritesRepository.observeFavoriteIds()
            .onEach { ids ->
                favoriteIds = ids
                _uiState.update { state ->
                    state.copy(
                        movies = state.movies.map { it.copy(isFavorite = it.imdbId in ids) },
                    )
                }
            }
            .launchIn(viewModelScope)

        networkMonitor.isOnline
            .onEach { online -> _uiState.update { it.copy(isOffline = !online) } }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        queryInput.value = query
    }

    fun onClearQuery() {
        onQueryChange("")
    }

    /** Re-runs the first page for the current query after a failure. */
    fun retry() {
        nextPageJob?.cancel()
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            val query = queryInput.value.trim()
            activeQuery = query
            applyFirstPage(FirstPageOutcome.Loading)
            applyFirstPage(FirstPageOutcome.Loaded(query, searchMovies(query, page = FIRST_PAGE)))
        }
    }

    /**
     * Appends the next page of results.
     *
     * Re-entrant calls are ignored: a lazy list can fire its "near the end" signal on
     * consecutive frames, and without the [SearchUiState.isLoadingNextPage] guard that
     * would request the same page several times.
     */
    fun loadNextPage() {
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoadingNextPage) return

        val requestedQuery = activeQuery
        val requestedPage = state.currentPage + 1

        // Claim the guard synchronously, before launching. Setting it inside the coroutine
        // would be too late: the launched body has not run yet when a second call arrives
        // in the same frame, so every re-entrant call would slip through and request the
        // same page again.
        _uiState.update { it.copy(isLoadingNextPage = true, nextPageError = null) }

        nextPageJob = viewModelScope.launch {
            when (val result = searchMovies(requestedQuery, page = requestedPage)) {
                is AppResult.Success -> {
                    // Guard 2: drop a response that belongs to a query the user has moved on
                    // from, even though it arrived after the switch.
                    if (requestedQuery != activeQuery) return@launch
                    appendPage(result.data)
                }

                is AppResult.Failure -> {
                    if (requestedQuery != activeQuery) return@launch
                    _uiState.update {
                        it.copy(isLoadingNextPage = false, nextPageError = result.error)
                    }
                }
            }
        }
    }

    fun onToggleFavorite(item: MovieListItem) {
        viewModelScope.launch { toggleFavorite(item.movie) }
    }

    private fun firstPageFlow(query: String): Flow<FirstPageOutcome> = flow {
        activeQuery = query

        if (query.length < SearchMoviesUseCase.MIN_QUERY_LENGTH) {
            emit(FirstPageOutcome.Idle)
            return@flow
        }

        emit(FirstPageOutcome.Loading)
        emit(FirstPageOutcome.Loaded(query, searchMovies(query, page = FIRST_PAGE)))
    }

    private fun applyFirstPage(outcome: FirstPageOutcome) = when (outcome) {
        FirstPageOutcome.Idle -> _uiState.update {
            it.copy(
                stage = SearchUiState.Stage.Idle,
                movies = emptyList(),
                totalResults = 0,
                currentPage = 0,
                isLoadingNextPage = false,
                nextPageError = null,
            )
        }

        FirstPageOutcome.Loading -> _uiState.update {
            it.copy(
                stage = SearchUiState.Stage.Loading,
                movies = emptyList(),
                totalResults = 0,
                currentPage = 0,
                isLoadingNextPage = false,
                nextPageError = null,
            )
        }

        is FirstPageOutcome.Loaded -> when (val result = outcome.result) {
            is AppResult.Success -> _uiState.update {
                it.copy(
                    // An empty result set is a successful request, not an error.
                    stage = if (result.data.movies.isEmpty()) {
                        SearchUiState.Stage.Empty
                    } else {
                        SearchUiState.Stage.Content
                    },
                    movies = result.data.movies.map { movie ->
                        MovieListItem(movie = movie, isFavorite = movie.imdbId in favoriteIds)
                    },
                    totalResults = result.data.totalResults,
                    currentPage = FIRST_PAGE,
                    isLoadingNextPage = false,
                    nextPageError = null,
                )
            }

            is AppResult.Failure -> _uiState.update {
                it.copy(
                    stage = SearchUiState.Stage.Error(result.error),
                    movies = emptyList(),
                    totalResults = 0,
                    currentPage = 0,
                    isLoadingNextPage = false,
                    nextPageError = null,
                )
            }
        }
    }

    private fun appendPage(page: SearchPage) {
        _uiState.update { state ->
            // De-duplicate defensively: OMDb can repeat a title across page boundaries, and
            // a duplicate key would crash a lazy list.
            val existingIds = state.movies.mapTo(mutableSetOf()) { it.imdbId }
            val newItems = page.movies
                .filter { it.imdbId !in existingIds }
                .map { MovieListItem(movie = it, isFavorite = it.imdbId in favoriteIds) }

            state.copy(
                movies = state.movies + newItems,
                totalResults = page.totalResults,
                currentPage = page.page,
                isLoadingNextPage = false,
                nextPageError = null,
            )
        }
    }

    private sealed interface FirstPageOutcome {
        data object Idle : FirstPageOutcome
        data object Loading : FirstPageOutcome
        data class Loaded(val query: String, val result: AppResult<SearchPage>) : FirstPageOutcome
    }

    private companion object {
        /**
         * Long enough that typing a word produces one request instead of six, short enough
         * that results still feel live.
         */
        const val DEBOUNCE_MILLIS = 400L
        const val FIRST_PAGE = 1
    }
}
