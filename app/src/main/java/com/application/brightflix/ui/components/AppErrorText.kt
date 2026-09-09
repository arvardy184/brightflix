package com.application.brightflix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.application.brightflix.R
import com.application.brightflix.core.result.AppError

/**
 * Turns a typed error into something a person can read.
 *
 * This is the *only* place errors become words, and it lives in the UI layer so every
 * message is a translatable resource. Nothing below this point decides what a user sees —
 * which is how exception names, HTTP codes and stack traces are kept off the screen.
 */
@Composable
fun AppError.asMessage(): String = when (this) {
    AppError.Offline -> stringResource(R.string.error_offline)
    AppError.Timeout -> stringResource(R.string.error_timeout)
    AppError.NotFound -> stringResource(R.string.error_not_found)
    AppError.TooManyResults -> stringResource(R.string.error_too_many_results)
    AppError.MissingApiKey -> stringResource(R.string.error_missing_api_key)
    is AppError.Server -> stringResource(R.string.error_server)
    // The raw OMDb text is deliberately discarded: it is written for developers, and
    // showing it would leak API wording into the product.
    is AppError.Api -> stringResource(R.string.error_generic)
    AppError.Unknown -> stringResource(R.string.error_generic)
}

/**
 * Whether retrying could plausibly help.
 *
 * A missing API key and an over-broad query are not fixed by tapping Retry, so offering
 * the button would just invite the user to fail again.
 */
val AppError.isRetryable: Boolean
    get() = when (this) {
        AppError.MissingApiKey, AppError.TooManyResults, AppError.NotFound -> false
        else -> true
    }
