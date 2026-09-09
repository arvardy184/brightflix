package com.application.brightflix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import com.application.brightflix.ui.theme.Spacing

/**
 * A movie poster with every failure mode handled.
 *
 * OMDb frequently has no artwork, and image requests fail. The container therefore owns a
 * fixed size from the caller and never depends on the image to establish it, so a missing
 * or broken poster leaves the layout untouched instead of collapsing a row.
 *
 * @param contentDescription pass null when a nearby label already names the movie, so a
 *   screen reader does not announce the title twice.
 */
@Composable
fun PosterImage(
    url: String?,
    title: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            PosterFallback(title)
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ShimmerBox(Modifier.fillMaxSize(), shape) },
                error = { PosterFallback(title) },
            )
        }
    }
}

/**
 * Shown when there is no artwork.
 *
 * Displays the title rather than a bare icon, so the tile still identifies the movie.
 */
@Composable
private fun PosterFallback(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Movie,
            // Decorative: the title beneath already names the movie.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.padding(Spacing.lg),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(Spacing.sm),
        )
    }
}
