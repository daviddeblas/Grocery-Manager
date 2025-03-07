package com.example.frontend.api

import com.example.frontend.api.model.SyncRequest
import com.example.frontend.api.model.SyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SyncService {

    @POST("/api/sync")
    suspend fun synchronize(@Body syncRequest: SyncRequest): Response<SyncResponse>
}