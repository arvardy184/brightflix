package com.application.brightflix

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hosts the Hilt dependency graph.
 *
 * Also configures Coil's singleton image loader with a **separate** OkHttp client from the
 * one used for OMDb. That separation is deliberate: the API client carries an interceptor
 * that appends the OMDb key to every request, and poster URLs point at a third-party CDN —
 * sharing the client would send our API key to Amazon on every image load.
 */
@HiltAndroidApp
class BrightflixApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_FRACTION)
                    .build()
            }
            // Posters are immutable and re-shown constantly across Home, Search and
            // Favorites, so a disk cache saves both bandwidth and visible loading time.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()

    private companion object {
        const val MEMORY_CACHE_FRACTION = 0.20
        const val DISK_CACHE_BYTES = 64L * 1024 * 1024
    }
}
