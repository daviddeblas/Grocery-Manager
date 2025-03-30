package com.example.frontend.api

import com.example.frontend.api.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {

    @POST("/api/auth/signin")
    suspend fun login(@Body loginRequest: LoginRequest): Response<JwtResponse>

    @POST("/api/auth/signup")
    suspend fun register(@Body signupRequest: SignupRequest): Response<MessageResponse>

    @POST("/api/auth/signout")
    suspend fun logout(): Response<MessageResponse>

    /**
     * Send the user's login credentials to their email
     */
    @POST("/api/auth/send-credentials")
    suspend fun sendCredentials(@Body request: ForgotPasswordRequest): Response<MessageResponse>
}