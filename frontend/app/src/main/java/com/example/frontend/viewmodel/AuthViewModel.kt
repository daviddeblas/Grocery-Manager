package com.example.frontend.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.frontend.api.APIClient
import com.example.frontend.api.model.ForgotPasswordRequest
import com.example.frontend.api.model.JwtResponse
import com.example.frontend.api.model.LoginRequest
import com.example.frontend.api.model.MessageResponse
import com.example.frontend.api.model.SignupRequest
import com.example.frontend.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * This ViewModel handles authentication-related operations, including:
 * - Login & Signup with API calls.
 * - Managing JWT tokens (access & refresh tokens).
 * - Refreshing authentication tokens to maintain session validity.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authService = APIClient.authService

    /**
     * Performs user login using username and password.
     *
     * - Sends a POST request to the authentication API.
     *
     * - If successful:
     *   - Stores tokens securely using SessionManager.
     *   - Saves user details (ID, username, email).
     * - If unsuccessful:
     *   - Returns a failure result with the HTTP response code.
     */
    suspend fun login(username: String, password: String): Result<JwtResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authService.login(LoginRequest(username, password))

                if (response.isSuccessful) {
                    val jwtResponse = response.body()!!

                    SessionManager.saveTokens(jwtResponse.token)

                    // Save user information
                    SessionManager.userId = jwtResponse.id
                    SessionManager.username = jwtResponse.username
                    SessionManager.email = jwtResponse.email

                    Result.success(jwtResponse)
                } else {
                    Result.failure(Exception("Connection failure: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Registers a new user.
     */
    suspend fun signup(signupRequest: SignupRequest): Response<MessageResponse> {
        return withContext(Dispatchers.IO) {
            authService.register(signupRequest)
        }
    }

    suspend fun logout(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // Notify the server of the disconnection
                val response = authService.logout()

                // Clear local session data
                SessionManager.logout()

                Result.success(true)
            } catch (e: Exception) {
                // Clear session data anyway in case of error
                SessionManager.logout()
                Result.failure(e)
            }
        }
    }

    fun isLoggedIn(): Boolean {
        return SessionManager.isLoggedIn()
    }

    /**
     * Sends the user's login credentials to their email.
     *
     */
    suspend fun sendCredentials(email: String): Result<MessageResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authService.sendCredentials(ForgotPasswordRequest(email))

                if (response.isSuccessful) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}