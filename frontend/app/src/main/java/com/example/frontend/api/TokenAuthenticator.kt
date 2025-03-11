package com.example.frontend.api

import android.content.Context
import com.example.frontend.api.model.TokenRefreshRequest
import com.example.frontend.utils.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * The class handles 401 Unauthorized responses by attempting to refresh the access token.
 * If the refresh is successful, it updates the tokens and retries the original request.
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

        // Check if we have a refresh token
        val refreshToken = SessionManager.refreshToken ?: return null

        // Try to get a new access token
        return runBlocking {
            try {
                // Create a temporary service without authentication
                val tempRetrofit = createTemporaryRetrofit()
                val authService = tempRetrofit.create(AuthService::class.java)

                // Make refresh token request
                val refreshResponse = authService.refreshToken(TokenRefreshRequest(refreshToken))

                if (refreshResponse.isSuccessful) {
                    val newTokens = refreshResponse.body()!!

                    // Save the new tokens
                    SessionManager.saveTokens(newTokens.accessToken, newTokens.refreshToken)

                    // Retry original request with new token
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .header("X-Retry-Count", (retryCount + 1).toString())
                        .build()
                } else {
                    // If refresh fails, force logout
                    SessionManager.logout()
                    null
                }
            } catch (e: Exception) {
                // On error, force logout
                SessionManager.logout()
                null
            }
        }
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