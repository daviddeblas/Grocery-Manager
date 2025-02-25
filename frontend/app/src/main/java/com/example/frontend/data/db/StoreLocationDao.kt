package com.example.frontend.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.frontend.data.model.StoreLocation

@Dao
interface StoreLocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(store: StoreLocation): Long

    @Delete
    suspend fun delete(store: StoreLocation)

    @Query("SELECT * FROM store_locations")
    fun getAllStores(): LiveData<List<StoreLocation>>
}
