package com.application.brightflix.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.application.brightflix.R
import com.application.brightflix.core.result.AppError
import com.application.brightflix.domain.model.MovieListItem
import com.application.brightflix.ui.components.EmptyState
import com.application.brightflix.ui.components.ErrorState
import com.application.brightflix.ui.components.MoviePosterCard
import com.application.brightflix.ui.components.MoviePosterCardSkeleton
import com.application.brightflix.ui.components.asMessage
import com.application.brightflix.ui.theme.Spacing

@Composable
fun SearchScreen(
    onMovieClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreenContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onLoadNextPage = viewModel::loadNextPage,
        onRetry = viewModel::retry,
        onToggleFavorite = viewModel::onToggleFavorite,
        onMovieClick = onMovieClick,
        modifier = modifier,
    )
}

/**
 * Stateless so it can be previewed and tested without a ViewModel or a network.
 */
@Composable
fun SearchScreenContent(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
    onMovieClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onClearQuery = onClearQuery,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (val stage = uiState.stage) {
                SearchUiState.Stage.Idle -> IdleOrOfflineState(isOffline = uiState.isOffline)

                SearchUiState.Stage.Loading -> SkeletonGrid()

                SearchUiState.Stage.Empty -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.empty_no_results_title),
                    subtitle = stringResource(R.string.empty_no_results_subtitle),
                    modifier = Modifier.align(Alignment.Center),
                )

                is SearchUiState.Stage.Error -> SearchErrorState(
                    error = stage.error,
                    isOffline = uiState.isOffline,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

                SearchUiState.Stage.Content -> ResultsGrid(
                    uiState = uiState,
                    onLoadNextPage = onLoadNextPage,
                    onToggleFavorite = onToggleFavorite,
                    onMovieClick = onMovieClick,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        label = { Text(stringResource(R.string.search_field_label)) },
        placeholder = { Text(stringResource(R.string.search_field_placeholder)) },
        singleLine = true,
        leadingIcon = {
            // Decorative: the field's own label already describes its purpose.
            Icon(Icons.Outlined.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
        // Results already arrive as the user types; the action just dismisses the keyboard.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
    )
}

@Composable
private fun IdleOrOfflineState(isOffline: Boolean) {
    if (isOffline) {
        // Search is the one feature that cannot work offline, so this state points the user
        // at the two that can rather than leaving them at a dead end.
        EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.empty_offline_search_title),
            subtitle = stringResource(R.string.empty_offline_search_subtitle),
        )
    } else {
        EmptyState(
            icon = Icons.Outlined.Search,
            title = stringResource(R.string.empty_search_title),
            subtitle = stringResource(R.string.empty_search_subtitle),
        )
    }
}

@Composable
private fun SearchErrorState(
    error: AppError,
    isOffline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        error == AppError.TooManyResults -> EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = stringResource(R.string.empty_too_many_results_title),
            subtitle = stringResource(R.string.empty_too_many_results_subtitle),
            modifier = modifier,
        )

        isOffline || error == AppError.Offline -> EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.empty_offline_search_title),
            subtitle = stringResource(R.string.empty_offline_search_subtitle),
            modifier = modifier,
        )

        else -> ErrorState(error = error, onRetry = onRetry, modifier = modifier)
    }
}

@Composable
private fun ResultsGrid(
    uiState: SearchUiState,
    onLoadNextPage: () -> Unit,
    onToggleFavorite: (MovieListItem) -> Unit,
    onMovieClick: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()

    // derivedStateOf keeps this off the recomposition path: scroll position changes on every
    // frame, but this only produces a new value when the answer actually flips.
    val shouldLoadMore by remember(uiState.canLoadMore) {
        derivedStateOf {
            if (!uiState.canLoadMore) return@derivedStateOf false
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisible >= gridState.layoutInfo.totalItemsCount - PREFETCH_DISTANCE
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadNextPage()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = uiState.movies,
            // Stable identity: keeps scroll position and avoids re-composing every tile
            // when a page is appended.
            key = { it.imdbId },
            contentType = { "movie" },
        ) { item ->
            MoviePosterCard(
                item = item,
                onClick = { onMovieClick(it.imdbId) },
                onToggleFavorite = onToggleFavorite,
            )
        }

        if (uiState.isLoadingNextPage) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                PaginationFooter { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
            }
        }

        if (uiState.nextPageError != null) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                // Inline: existing results stay on screen, so this never becomes a
                // full-screen error over content the user is already reading.
                PaginationFooter {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = uiState.nextPageError.asMessage(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onLoadNextPage) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaginationFooter(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A skeleton grid that mirrors the real layout so content arrival does not shift the page. */
@Composable
private fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = SKELETON_COUNT, contentType = { "skeleton" }) {
            MoviePosterCardSkeleton()
        }
    }
}

/** Start loading the next page slightly before the user reaches the end. */
private const val PREFETCH_DISTANCE = 4
private const val SKELETON_COUNT = 6
