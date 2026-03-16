package com.mediagent.app.data.api

import okhttp3.Interceptor
import okhttp3.Response

class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("x-api-key", apiKey)
            .build()
        return chain.proceed(request)
    }
}
