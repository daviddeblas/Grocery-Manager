package com.example.frontend.api

import com.example.frontend.api.model.ShoppingItemSync
import retrofit2.Response
import retrofit2.http.*

interface ShoppingItemService {

    @GET("api/shopping-items/list/{listId}")
    suspend fun getItemsByList(@Path("listId") listId: Long): Response<List<ShoppingItemSync>>

    @GET("api/shopping-items/{id}")
    suspend fun getItemById(@Path("id") id: Long): Response<ShoppingItemSync>

    @POST("api/shopping-items")
    suspend fun createItem(@Body item: ShoppingItemSync): Response<ShoppingItemSync>

    @PUT("api/shopping-items/{id}")
    suspend fun updateItem(@Path("id") id: Long, @Body item: ShoppingItemSync): Response<ShoppingItemSync>

    @DELETE("api/shopping-items/{id}")
    suspend fun deleteItem(@Path("id") id: Long): Response<Void>
}