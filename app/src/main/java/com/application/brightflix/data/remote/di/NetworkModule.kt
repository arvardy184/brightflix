package com.application.brightflix.data.remote.di

import android.content.Context
import com.application.brightflix.BuildConfig
import com.application.brightflix.core.di.ApiKey
import com.application.brightflix.data.remote.api.ApiKeyInterceptor
import com.application.brightflix.data.remote.api.OmdbApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val HTTP_CACHE_BYTES = 10L * 1024 * 1024 // 10 MB
private const val TIMEOUT_SECONDS = 20L

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @ApiKey
    fun providesApiKey(): String = BuildConfig.OMDB_API_KEY

    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        // OMDb adds fields over time; unknown ones must never break deserialization.
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun providesOkHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // Response bodies are logged only in debug builds — a release build must not
            // write API payloads to logcat.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(BuildConfig.OMDB_API_KEY))
            .addInterceptor(logging)
            // A disk cache is a second line of defence for OMDb's daily request quota,
            // behind the app's own TTL-gated caching in MovieRepository.
            .cache(Cache(context.cacheDir.resolve("http_cache"), HTTP_CACHE_BYTES))
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun providesRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.OMDB_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun providesOmdbApi(retrofit: Retrofit): OmdbApi = retrofit.create(OmdbApi::class.java)
}
