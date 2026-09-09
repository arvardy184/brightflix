package com.application.brightflix.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.application.brightflix.R
import com.application.brightflix.domain.model.CollectionRow
import com.application.brightflix.domain.model.MovieListItem
import com.application.brightflix.ui.components.ErrorState
import com.application.brightflix.ui.components.MoviePosterCard
import com.application.brightflix.ui.components.MoviePosterCardSkeleton
import com.application.brightflix.ui.components.SectionHeader
import com.application.brightflix.ui.components.ShimmerBox
import com.application.brightflix.ui.components.StaleDataBanner
import com.application.brightflix.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // "Surprise Me" is a one-off navigation event delivered over a channel, so it fires
    // exactly once and does not repeat across configuration changes.
    LaunchedEffect(viewModel, onMovieClick) {
        viewModel.surpriseMovie.collect(onMovieClick)
    }

    Column(modifier = modifier.fillMaxSize()) {
        HomeTopBar(
            canSurpriseMe = (uiState as? HomeUiState.Content)?.canSurpriseMe == true,
            onSurpriseMe = viewModel::onSurpriseMe,
        )

        when (val state = uiState) {
            HomeUiState.Loading -> HomeSkeleton()

            is HomeUiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(error = state.error, onRetry = { viewModel.refresh() })
            }

            is HomeUiState.Content -> HomeContent(
                state = state,
                onRefresh = { viewModel.refresh() },
                onMovieClick = onMovieClick,
                onToggleFavorite = viewModel::onToggleFavorite,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    canSurpriseMe: Boolean,
    onSurpriseMe: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.home_title)) },
        actions = {
            IconButton(onClick = onSurpriseMe, enabled = canSurpriseMe) {
                Icon(
                    imageVector = Icons.Outlined.Casino,
                    // The label explains *why* it is unavailable rather than going silent.
                    contentDescription = if (canSurpriseMe) {
                        stringResource(R.string.home_surprise_me_description)
                    } else {
                        stringResource(R.string.home_surprise_me_unavailable)
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    onRefresh: () -> Unit,
    onMovieClick: (String) -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Content is never removed while refreshing; the banner only explains that what
            // is on screen may be out of date.
            if (state.staleness != null) {
                item(key = "staleness", contentType = "banner") {
                    StaleDataBanner(staleness = state.staleness, onRetry = onRefresh)
                }
            }

            // Omitted entirely when empty: an empty "Recently viewed" row on first launch
            // would be noise rather than information.
            if (state.recentlyViewed.isNotEmpty()) {
                item(key = "recent_header", contentType = "header") {
                    SectionHeader(stringResource(R.string.home_section_recently_viewed))
                }
                item(key = "recent_row", contentType = "row") {
                    MovieCarousel(
                        items = state.recentlyViewed,
                        onMovieClick = onMovieClick,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }

            item(key = "featured_header", contentType = "header") {
                SectionHeader(stringResource(R.string.home_section_featured))
            }

            items(
                items = state.collections,
                key = { it.id },
                contentType = { "collection" },
            ) { collection ->
                CollectionSection(
                    collection = collection,
                    onMovieClick = onMovieClick,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun CollectionSection(
    collection: CollectionRow,
    onMovieClick: (String) -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
) {
    Column {
        Text(
            text = collection.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )

        when {
            collection.isLoading -> CarouselSkeleton()
            // A collection that genuinely returned nothing simply renders no row rather
            // than an empty-state block that would fragment the screen.
            collection.items.isEmpty() -> Unit
            else -> MovieCarousel(
                items = collection.items,
                onMovieClick = onMovieClick,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun MovieCarousel(
    items: List<MovieListItem>,
    onMovieClick: (String) -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(
            items = items,
            key = { it.imdbId },
            contentType = { "movie" },
        ) { item ->
            MoviePosterCard(
                item = item,
                onClick = { onMovieClick(it.imdbId) },
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier.width(Spacing.posterWidth),
            )
        }
    }
}

@Composable
private fun CarouselSkeleton() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        userScrollEnabled = false,
    ) {
        items(count = SKELETON_ITEMS, contentType = { "skeleton" }) {
            MoviePosterCardSkeleton(modifier = Modifier.width(Spacing.posterWidth))
        }
    }
}

/** Mirrors the real home layout, so the first paint does not jump when data lands. */
@Composable
private fun HomeSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        repeat(SKELETON_SECTIONS) {
            Column {
                ShimmerBox(
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        .fillMaxWidth(0.45f)
                        .height(20.dp),
                )
                CarouselSkeleton()
            }
        }
    }
}

private const val SKELETON_ITEMS = 4
private const val SKELETON_SECTIONS = 3
