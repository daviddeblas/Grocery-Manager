package com.example.frontend.api

import android.content.Context
import android.util.Log
import com.example.frontend.utils.SessionManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Handles JWT authentication for API requests.
 */
class TokenAuthenticator(private val context: Context) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry if retry count exceeds 3
        val retryCount = response.request.header("X-Retry-Count")?.toIntOrNull() ?: 0
        if (retryCount >= 3) {
            return null
        }

        // Don't retry auth endpoints to avoid loops
        if (response.request.url.encodedPath.contains("/api/auth/")) {
            return null
        }

        // If error 404, we simply log out the user
        Log.e("TokenAuthenticator", "JWT authentication failed: ${response.code}")
        SessionManager.logout()

        // Returning null tells OkHttp not to retry the request
        return null
    }

    // Create a temporary Retrofit instance without authentication
    private fun createTemporaryRetrofit(): retrofit2.Retrofit {
        val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }

        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        return retrofit2.Retrofit.Builder()
            .baseUrl(APIClient.BASE_URL)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(APIClient.gson))
            .build()
    }
}