package com.application.brightflix.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.application.brightflix.R
import com.application.brightflix.domain.model.MovieDetail
import com.application.brightflix.ui.components.ErrorState
import com.application.brightflix.ui.components.PosterImage
import com.application.brightflix.ui.components.StaleDataBanner
import com.application.brightflix.ui.theme.Spacing

@Composable
fun MovieDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MovieDetailScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onToggleFavorite = viewModel::onToggleFavorite,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun MovieDetailScreenContent(
    uiState: MovieDetailUiState,
    onNavigateBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (uiState is MovieDetailUiState.Content) {
                FavoriteFab(
                    isFavorite = uiState.isFavorite,
                    movieTitle = uiState.detail.title,
                    onToggle = onToggleFavorite,
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState) {
                MovieDetailUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is MovieDetailUiState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorState(error = uiState.error, onRetry = onRetry)
                }

                is MovieDetailUiState.Content -> DetailBody(
                    state = uiState,
                    onRetry = onRetry,
                    bottomPadding = padding.calculateBottomPadding(),
                )
            }

            // Kept outside the scroll container so back is always reachable, including
            // while the screen is loading or showing an error.
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .padding(Spacing.sm)
                    .align(Alignment.TopStart),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBody(
    state: MovieDetailUiState.Content,
    onRetry: () -> Unit,
    bottomPadding: Dp,
) {
    val detail = state.detail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HeroPoster(detail)

        // The movie stays fully readable offline; this only explains that the record may
        // be out of date.
        state.staleness?.let { staleness ->
            StaleDataBanner(staleness = staleness, onRetry = onRetry)
        }

        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(top = Spacing.lg)
                    .semantics { heading() },
            )

            MetadataRow(detail)

            detail.imdbRating?.let { rating ->
                ImdbRating(rating = rating, votes = detail.imdbVotes)
            }

            if (detail.genres.isNotEmpty()) {
                GenreChips(detail.genres)
            }

            // Every section below is omitted when its data is absent, so the screen never
            // shows "N/A" — the mapper already turned those into nulls.
            detail.plot?.let { plot ->
                DetailSection(stringResource(R.string.detail_plot)) {
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            detail.director?.let { director ->
                LabelledValue(stringResource(R.string.detail_director), director)
            }

            if (detail.writers.isNotEmpty()) {
                LabelledValue(
                    stringResource(R.string.detail_writers),
                    detail.writers.joinToString(", "),
                )
            }

            if (detail.cast.isNotEmpty()) {
                LabelledValue(
                    stringResource(R.string.detail_cast),
                    detail.cast.joinToString(", "),
                )
            }

            if (detail.ratings.isNotEmpty()) {
                DetailSection(stringResource(R.string.detail_ratings)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        detail.ratings.forEach { rating ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = rating.source,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = rating.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            detail.awards?.let { awards ->
                LabelledValue(stringResource(R.string.detail_awards), awards)
            }

            detail.boxOffice?.let { boxOffice ->
                LabelledValue(stringResource(R.string.detail_box_office), boxOffice)
            }

            detail.released?.let { released ->
                LabelledValue(stringResource(R.string.detail_released), released)
            }

            if (detail.languages.isNotEmpty()) {
                LabelledValue(
                    stringResource(R.string.detail_languages),
                    detail.languages.joinToString(", "),
                )
            }

            if (detail.countries.isNotEmpty()) {
                LabelledValue(
                    stringResource(R.string.detail_countries),
                    detail.countries.joinToString(", "),
                )
            }
        }

        // Enough room for the FAB not to cover the last line of content.
        Box(modifier = Modifier.padding(bottom = bottomPadding + Spacing.xxl + Spacing.xl))
    }
}

@Composable
private fun HeroPoster(detail: MovieDetail) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_ASPECT_RATIO),
    ) {
        PosterImage(
            url = detail.posterUrl,
            title = detail.title,
            contentDescription = stringResource(R.string.detail_poster_description, detail.title),
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
        )

        // A scrim so the back button and the title below stay legible over any artwork.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun MetadataRow(detail: MovieDetail) {
    // Only the parts that exist are joined, so the separator never dangles.
    val parts = buildList {
        if (detail.year.isNotBlank()) add(detail.year)
        detail.rated?.let(::add)
        detail.runtimeMinutes?.let { add(stringResource(R.string.detail_runtime, it)) }
    }

    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImdbRating(rating: Double, votes: Int?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.detail_imdb_rating, rating.toString()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        votes?.let {
            Text(
                text = stringResource(R.string.detail_imdb_votes, formatVotes(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GenreChips(genres: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        genres.take(MAX_GENRE_CHIPS).forEach { genre ->
            AssistChip(onClick = {}, label = { Text(genre) }, enabled = false)
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = Spacing.sm)
                .semantics { heading() },
        )
        content()
    }
}

@Composable
private fun LabelledValue(label: String, value: String) {
    DetailSection(label) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FavoriteFab(
    isFavorite: Boolean,
    movieTitle: String,
    onToggle: () -> Unit,
) {
    val label = if (isFavorite) {
        stringResource(R.string.favorite_remove, movieTitle)
    } else {
        stringResource(R.string.favorite_add, movieTitle)
    }

    ExtendedFloatingActionButton(
        onClick = onToggle,
        icon = {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                // The FAB's text already conveys the action; labelling both would make a
                // screen reader announce it twice.
                contentDescription = null,
            )
        },
        text = {
            Text(
                stringResource(
                    if (isFavorite) R.string.favorite_saved_short else R.string.favorite_save_short,
                ),
            )
        },
        // The full, movie-specific label lives here so the control is meaningful alone.
        modifier = Modifier.semantics { contentDescription = label },
    )
}

private fun formatVotes(votes: Int): String = "%,d".format(votes)

private const val HERO_ASPECT_RATIO = 0.85f
private const val MAX_GENRE_CHIPS = 4
