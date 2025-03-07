package com.example.frontend.api

import com.example.frontend.api.model.StoreLocationSync
import retrofit2.Response
import retrofit2.http.*

interface StoreLocationService {

    @GET("api/stores")
    suspend fun getAllStores(): Response<List<StoreLocationSync>>

    @GET("api/stores/{id}")
    suspend fun getStoreById(@Path("id") id: Long): Response<StoreLocationSync>

    @GET("api/stores/nearby")
    suspend fun getNearbyStores(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 1.0
    ): Response<List<StoreLocationSync>>

    @POST("api/stores")
    suspend fun createStore(@Body store: StoreLocationSync): Response<StoreLocationSync>

    @PUT("api/stores/{id}")
    suspend fun updateStore(@Path("id") id: Long, @Body store: StoreLocationSync): Response<StoreLocationSync>

    @DELETE("api/stores/{id}")
    suspend fun deleteStore(@Path("id") id: Long): Response<Void>
}