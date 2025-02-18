package com.example.frontend.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantity: Double,
    val unitType: String,
    val isChecked: Boolean = false,
    val sortIndex: Int = 0,
    val dateCreated: Long = System.currentTimeMillis(),
    val listId: Int
)

