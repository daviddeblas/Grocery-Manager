package com.example.frontend.api.model

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime

data class LoginRequest(
    val username: String,
    val password: String
)

data class SignupRequest(
    val username: String,
    val email: String,
    val password: String
)

data class JwtResponse(
    val token: String,                // Access token
    val refreshToken: String,         // Refresh token
    val type: String,                 // Token type (Bearer)
    val id: Long,                     // User ID
    val username: String,
    val email: String
)

data class TokenRefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class TokenRefreshResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("tokenType") val tokenType: String = "Bearer"
)

data class MessageResponse(
    val message: String
)

// Sync models
data class SyncRequest(
    @SerializedName("lastSyncTimestamp") val lastSyncTimestamp: LocalDateTime?,
    @SerializedName("shoppingLists") val shoppingLists: List<ShoppingListSync>,
    @SerializedName("shoppingItems") val shoppingItems: List<ShoppingItemSync>,
    @SerializedName("storeLocations") val storeLocations: List<StoreLocationSync>,
    @SerializedName("deletedItems") val deletedItems: List<DeletedItemSync>
)

data class SyncResponse(
    @SerializedName("serverTimestamp") val serverTimestamp: LocalDateTime,
    @SerializedName("shoppingLists") val shoppingLists: List<ShoppingListSync>,
    @SerializedName("shoppingItems") val shoppingItems: List<ShoppingItemSync>,
    @SerializedName("storeLocations") val storeLocations: List<StoreLocationSync>
)

data class ShoppingListSync(
    @SerializedName("id") val id: Long?,
    @SerializedName("name") val name: String,
    @SerializedName("syncId") val syncId: String?,
    @SerializedName("createdAt") val createdAt: LocalDateTime?,
    @SerializedName("updatedAt") val updatedAt: LocalDateTime?,
    @SerializedName("lastSynced") val lastSynced: LocalDateTime?,
    @SerializedName("version") val version: Long?
)

data class ShoppingItemSync(
    @SerializedName("id") val id: Long?,
    @SerializedName("name") val name: String,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("unitType") val unitType: String,
    @SerializedName("checked") val checked: Boolean,
    @SerializedName("sortIndex") val sortIndex: Int,
    @SerializedName("shoppingListId") val shoppingListId: Long,
    @SerializedName("syncId") val syncId: String?,
    @SerializedName("createdAt") val createdAt: LocalDateTime?,
    @SerializedName("updatedAt") val updatedAt: LocalDateTime?,
    @SerializedName("lastSynced") val lastSynced: LocalDateTime?,
    @SerializedName("version") val version: Long?
)

data class StoreLocationSync(
    @SerializedName("id") val id: Long?,
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("geofenceId") val geofenceId: String,
    @SerializedName("syncId") val syncId: String?,
    @SerializedName("createdAt") val createdAt: LocalDateTime?,
    @SerializedName("updatedAt") val updatedAt: LocalDateTime?,
    @SerializedName("lastSynced") val lastSynced: LocalDateTime?,
    @SerializedName("version") val version: Long?
)

data class DeletedItemSync(
    @SerializedName("syncId") val syncId: String?,
    @SerializedName("originalId") val originalId: Long?,
    @SerializedName("entityType") val entityType: String,
    @SerializedName("deletedAt") val deletedAt: LocalDateTime
)