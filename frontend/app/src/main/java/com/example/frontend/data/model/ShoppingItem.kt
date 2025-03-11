package com.example.frontend.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantity: Double,
    val unitType: String,
    val isChecked: Boolean = false,
    val sortIndex: Int = 0,
    val dateCreated: Long = System.currentTimeMillis(),
    val listId: Int,
    val syncId: String? = null,
    val serverId: Long? = null,
    val updatedAt: LocalDateTime? = LocalDateTime.now(), // Update date
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

