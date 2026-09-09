package com.application.brightflix.data.remote.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the OMDb API key to every outgoing request.
 *
 * Centralised here so no call site can omit it and the key never appears in the Retrofit
 * interface signatures.
 */
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.newBuilder()
            .addQueryParameter("apikey", apiKey)
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
