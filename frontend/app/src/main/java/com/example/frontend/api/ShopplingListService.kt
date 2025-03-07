package com.example.frontend.api

import com.example.frontend.api.model.ShoppingListSync
import retrofit2.Response
import retrofit2.http.*

interface ShoppingListService {

    @GET("api/shopping-lists")
    suspend fun getAllLists(): Response<List<ShoppingListSync>>

    @GET("api/shopping-lists/{id}")
    suspend fun getListById(@Path("id") id: Long): Response<ShoppingListSync>

    @POST("api/shopping-lists")
    suspend fun createList(@Body list: ShoppingListSync): Response<ShoppingListSync>

    @PUT("api/shopping-lists/{id}")
    suspend fun updateList(@Path("id") id: Long, @Body list: ShoppingListSync): Response<ShoppingListSync>

    @DELETE("api/shopping-lists/{id}")
    suspend fun deleteList(@Path("id") id: Long): Response<Void>
}