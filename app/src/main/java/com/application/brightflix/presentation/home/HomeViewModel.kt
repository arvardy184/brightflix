package com.application.brightflix.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.brightflix.core.network.NetworkMonitor
import com.application.brightflix.core.result.AppError
import com.application.brightflix.core.result.RefreshState
import com.application.brightflix.core.result.Staleness
import com.application.brightflix.domain.model.CollectionRow
import com.application.brightflix.domain.model.HomeFeed
import com.application.brightflix.domain.model.MovieListItem
import com.application.brightflix.domain.repository.MovieRepository
import com.application.brightflix.domain.usecase.ObserveHomeFeedUseCase
import com.application.brightflix.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the home screen renders. */
sealed interface HomeUiState {

    /** Cold start with nothing cached. */
    data object Loading : HomeUiState

    /** Nothing cached *and* the refresh failed — the only case that earns a full-screen error. */
    data class Error(val error: AppError) : HomeUiState

    data class Content(
        val recentlyViewed: List<MovieListItem>,
        val collections: List<CollectionRow>,
        val isRefreshing: Boolean,
        /** Non-null when showing cached content after a failed or skipped refresh. */
        val staleness: Staleness?,
        val isOffline: Boolean,
        val canSurpriseMe: Boolean,
    ) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeHomeFeed: ObserveHomeFeedUseCase,
    private val movieRepository: MovieRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)

    /**
     * Emits the IMDb ID chosen by "Surprise Me".
     *
     * A [Channel] rather than state: navigating is a one-off event, and holding it in state
     * would re-trigger the navigation on every configuration change.
     */
    private val surpriseChannel = Channel<String>(Channel.BUFFERED)
    val surpriseMovie: Flow<String> = surpriseChannel.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        observeHomeFeed(),
        networkMonitor.isOnline,
        isRefreshing,
    ) { feed, isOnline, refreshing ->
        feed.toUiState(isOnline = isOnline, isRefreshing = refreshing)
    }.stateIn(
        scope = viewModelScope,
        // Survives a configuration change without re-running the whole pipeline, and stops
        // observing shortly after the screen goes away.
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState.Loading,
    )

    init {
        // Respects the cache TTL — a warm cache costs no requests on launch.
        refresh(force = false)
    }

    /**
     * @param force true for pull-to-refresh, which bypasses the TTL so the user can always
     *   demand fresh data.
     */
    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                movieRepository.refreshCollections(force = force)
            } finally {
                // In a finally block so a cancelled refresh cannot strand the spinner.
                isRefreshing.value = false
            }
        }
    }

    fun onToggleFavorite(item: MovieListItem) {
        viewModelScope.launch { toggleFavorite(item.movie) }
    }

    fun onSurpriseMe() {
        viewModelScope.launch {
            movieRepository.randomCachedMovie()?.let { surpriseChannel.send(it.imdbId) }
        }
    }

    private fun HomeFeed.toUiState(isOnline: Boolean, isRefreshing: Boolean): HomeUiState = when {
        // Content wins over everything: a failed refresh must never replace movies the user
        // can already see with an error screen.
        hasContent -> HomeUiState.Content(
            recentlyViewed = recentlyViewed,
            collections = collections,
            isRefreshing = isRefreshing,
            staleness = Staleness.from(
                lastUpdatedAt = lastUpdatedAt,
                refresh = refresh,
                isOffline = !isOnline,
            ),
            isOffline = !isOnline,
            canSurpriseMe = collections.any { it.items.isNotEmpty() },
        )

        refresh is RefreshState.Failed -> HomeUiState.Error((refresh as RefreshState.Failed).error)

        else -> HomeUiState.Loading
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
