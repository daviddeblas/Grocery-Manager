package com.example.frontend.api

import com.example.frontend.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Don't add a token for auth requests
        if (originalRequest.url.encodedPath.contains("/api/auth/")) {
            return chain.proceed(originalRequest)
        }

        // Get token from SessionManager
        val token = SessionManager.token

        // If no token, proceed without modification
        if (token.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        // Add token to Authorization header
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(newRequest)
    }
}