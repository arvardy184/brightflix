package com.application.brightflix.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.brightflix.core.network.NetworkMonitor
import com.application.brightflix.core.result.AppError
import com.application.brightflix.core.result.RefreshState
import com.application.brightflix.core.result.Staleness
import com.application.brightflix.domain.model.MovieDetail
import com.application.brightflix.domain.repository.MovieRepository
import com.application.brightflix.domain.repository.RecentlyViewedRepository
import com.application.brightflix.domain.usecase.MovieDetailState
import com.application.brightflix.domain.usecase.ObserveMovieDetailUseCase
import com.application.brightflix.domain.usecase.ToggleFavoriteUseCase
import com.application.brightflix.presentation.navigation.MovieDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MovieDetailUiState {

    data object Loading : MovieDetailUiState

    /** Nothing cached and the fetch failed. */
    data class Error(val error: AppError) : MovieDetailUiState

    data class Content(
        val detail: MovieDetail,
        val isFavorite: Boolean,
        val staleness: Staleness?,
        val isRefreshing: Boolean,
    ) : MovieDetailUiState
}

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeMovieDetail: ObserveMovieDetailUseCase,
    private val movieRepository: MovieRepository,
    private val recentlyViewedRepository: RecentlyViewedRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    /**
     * The screen reconstructs itself from this ID alone, which is what makes the
     * destination deep-linkable and safe across process death.
     */
    private val imdbId: String = checkNotNull(savedStateHandle[MovieDetailRoute.IMDB_ID_ARG]) {
        "MovieDetailViewModel requires an ${MovieDetailRoute.IMDB_ID_ARG} argument"
    }

    private val detailState = observeMovieDetail(imdbId)

    val uiState: StateFlow<MovieDetailUiState> = combine(
        detailState,
        networkMonitor.isOnline,
    ) { state, isOnline -> state.toUiState(isOnline) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MovieDetailUiState.Loading,
        )

    init {
        // TTL-gated: re-opening a movie within the cache window costs no API request.
        viewModelScope.launch { movieRepository.refreshMovieDetail(imdbId, force = false) }

        // Recorded once per screen instance. `take(1)` is what guarantees that — the
        // underlying flow re-emits on every favorite change and refresh, and recording on
        // each emission would keep rewriting the history timestamp.
        detailState
            .map { it.cached.data }
            .filterNotNull()
            .take(1)
            .onEach { recentlyViewedRepository.record(it.toMovie()) }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch { movieRepository.refreshMovieDetail(imdbId, force = true) }
    }

    fun retry() = refresh()

    fun onToggleFavorite() {
        val detail = (uiState.value as? MovieDetailUiState.Content)?.detail ?: return
        viewModelScope.launch { toggleFavorite(detail.toMovie()) }
    }

    private fun MovieDetailState.toUiState(isOnline: Boolean): MovieDetailUiState {
        val detail = cached.data

        return when {
            // Cached content is shown even while a refresh fails — the user came here to
            // read about a movie, and an error screen would take that away.
            detail != null -> MovieDetailUiState.Content(
                detail = detail,
                isFavorite = isFavorite,
                staleness = Staleness.from(
                    lastUpdatedAt = cached.lastUpdatedAt,
                    refresh = cached.refresh,
                    isOffline = !isOnline,
                ),
                isRefreshing = cached.isRefreshing,
            )

            cached.refresh is RefreshState.Failed ->
                MovieDetailUiState.Error((cached.refresh as RefreshState.Failed).error)

            else -> MovieDetailUiState.Loading
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
