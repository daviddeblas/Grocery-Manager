package com.example.frontend.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "deleted_items")
data class DeletedItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalId: Int? = null,
    val syncId: String? = null,
    val entityType: String,
    val synced: Boolean = false,
    val deletedAt: LocalDateTime = LocalDateTime.now()
)