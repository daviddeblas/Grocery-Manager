package com.example.frontend.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "store_locations")
data class StoreLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val geofenceId: String,
    val syncId: String? = null,
    val serverId: Long? = null,
    val updatedAt: LocalDateTime? = LocalDateTime.now(),  // Update date
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)