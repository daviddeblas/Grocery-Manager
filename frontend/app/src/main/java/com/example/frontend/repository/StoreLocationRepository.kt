package com.example.frontend.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.example.frontend.data.db.AppDatabase
import com.example.frontend.data.model.StoreLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        }
    }
}
