package com.example.frontend.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.example.frontend.data.db.AppDatabase
import com.example.frontend.data.model.StoreLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.frontend.data.model.DeletedItem

class StoreLocationRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val storeDao = db.storeLocationDao()

    val allStores: LiveData<List<StoreLocation>> = storeDao.getAllStores()

    suspend fun addStore(store: StoreLocation) {
        withContext(Dispatchers.IO) {
            storeDao.insert(store)
        }
    }

    suspend fun deleteStore(store: StoreLocation) {
        withContext(Dispatchers.IO) {
            storeDao.delete(store)

            // If the store has a syncId, record the deletion for sync
            if (store.syncId != null) {
                val deletedItem = DeletedItem(
                    originalId = store.id,
                    syncId = store.syncId,
                    entityType = "STORE_LOCATION"
                )
                db.deletedItemDao().insert(deletedItem)
            }
        }
    }
    suspend fun getStoreByGeofenceId(geofenceId: String): StoreLocation? {
        return withContext(Dispatchers.IO) {
            storeDao.getStoreByGeofenceId(geofenceId)
        }
    }
}
