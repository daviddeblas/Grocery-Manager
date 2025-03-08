package com.example.frontend.data.db

import androidx.room.*
import com.example.frontend.data.model.DeletedItem

@Dao
interface DeletedItemDao {
    @Insert
    suspend fun insert(deletedItem: DeletedItem): Long

    @Query("SELECT * FROM deleted_items WHERE synced = 0")
    suspend fun getUnsyncedItems(): List<DeletedItem>

    @Query("UPDATE deleted_items SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Int>)

    @Query("DELETE FROM deleted_items WHERE synced = 1")
    suspend fun deleteSyncedItems()
}