package com.application.brightflix.core.result

/**
 * A failure the user can be told about, decoupled from the exception that caused it.
 *
 * Technical detail (stack traces, HTTP codes, exception names) stops here. The
 * presentation layer maps these cases to localized strings; nothing lower in the stack
 * decides what a user reads.
 */
sealed interface AppError {

    /** No usable network connection. */
    data object Offline : AppError

    /** The request was issued but the server did not answer in time. */
    data object Timeout : AppError

    /** The server answered with a failing HTTP status. */
    data class Server(val code: Int) : AppError

    /** The requested title does not exist. Distinct from a failure: retrying will not help. */
    data object NotFound : AppError

    /**
     * The query matched more results than OMDb will return. The user recovers by typing a
     * more specific title, so this is guidance rather than an error, and carries no retry.
     */
    data object TooManyResults : AppError

    /**
     * No OMDb API key is configured. Self-diagnosing for anyone who clones the repository
     * without completing setup, instead of surfacing as a confusing generic failure.
     */
    data object MissingApiKey : AppError

    /**
     * OMDb reported a logical failure. Note that OMDb returns these with HTTP 200, so
     * this is not derivable from the status code.
     */
    data class Api(val message: String) : AppError

    /** Anything unclassified. Surfaced to the user as a generic failure. */
    data object Unknown : AppError
}

/**
 * The outcome of an operation that can fail in a way worth showing the user.
 *
 * Preferred over [kotlin.Result] because failures here are a closed, exhaustively
 * matchable set rather than an arbitrary [Throwable].
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
