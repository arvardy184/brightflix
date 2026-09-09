package com.application.brightflix.core.di

import javax.inject.Qualifier

/**
 * Dispatchers are injected rather than referenced as [kotlinx.coroutines.Dispatchers]
 * statics so tests can swap in a deterministic test dispatcher and control virtual time.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** The OMDb API key, sourced from `local.properties` via `BuildConfig`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiKey
