package com.example.frontend.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.frontend.data.model.StoreLocation
import com.example.frontend.data.model.SyncStatus

@Dao
interface StoreLocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(store: StoreLocation): Long

    @Delete
    suspend fun delete(store: StoreLocation)

    @Query("SELECT * FROM store_locations")
    fun getAllStores(): LiveData<List<StoreLocation>>

    @Query("SELECT * FROM store_locations WHERE geofenceId = :geofenceId LIMIT 1")
    suspend fun getStoreByGeofenceId(geofenceId: String): StoreLocation?

    @Query("SELECT * FROM store_locations")
    suspend fun getAllStoresOnce(): List<StoreLocation>

    @Query("SELECT * FROM store_locations WHERE syncStatus != 'SYNCED'")
    suspend fun getStoresToSync(): List<StoreLocation>

    @Query("SELECT * FROM store_locations WHERE syncId = :syncId LIMIT 1")
    suspend fun getStoreBySyncId(syncId: String): StoreLocation?

    @Query("SELECT * FROM store_locations WHERE serverId = :serverId LIMIT 1")
    suspend fun getStoreByServerId(serverId: Long): StoreLocation?

    @Query("UPDATE store_locations SET syncStatus = :status WHERE syncId = :syncId")
    suspend fun updateSyncStatus(syncId: String, status: SyncStatus)

    @Update
    suspend fun update(store: StoreLocation)
}
