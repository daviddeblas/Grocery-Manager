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

    @POST("/api/auth/refreshtoken")
    suspend fun refreshToken(@Body refreshRequest: TokenRefreshRequest): Response<TokenRefreshResponse>

    @POST("/api/auth/signout")
    suspend fun logout(): Response<MessageResponse>
}