package com.example.frontend.repository

import android.content.Context
import android.util.Log
import com.example.frontend.api.APIClient
import com.example.frontend.api.model.*
import com.example.frontend.data.db.AppDatabase
import com.example.frontend.data.model.ShoppingItem
import com.example.frontend.data.model.ShoppingList
import com.example.frontend.data.model.StoreLocation
import com.example.frontend.data.model.SyncStatus
import com.example.frontend.utils.SessionManager
import com.example.frontend.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.*

class SyncManager(context: Context) {
    companion object {
        private const val TAG = "SyncManager"
    }

    private val db = AppDatabase.getDatabase(context)
    private val shoppingItemDao = db.shoppingItemDao()
    private val shoppingListDao = db.shoppingListDao()
    private val storeLocationDao = db.storeLocationDao()

    private val syncService = APIClient.syncService
    private val authViewModel = AuthViewModel(application = context.applicationContext as android.app.Application)

    /**
     * Synchronizes local data with the server.
     * - Ensures the user is logged in before proceeding.
     * - Prepares local data for synchronization.
     * - Calls the synchronization API and handles possible authentication issues.
     * - Processes the server response and updates the local database accordingly.
     * - Marks synchronized items and deleted entries as processed.
     */
    suspend fun synchronize(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Début de la synchronisation...")

            // 1. Ensure the user is logged in before proceeding
            if (!SessionManager.isLoggedIn()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            // 2. Prepare data to be sent to the server
            val localLists = mapLocalListsToSync()
            val localItems = mapLocalItemsToSync()
            val localStores = mapLocalStoresToSync()

            // Retrieve locally deleted items that haven't been synced yet
            val deletedItems = db.deletedItemDao().getUnsyncedItems()


            // Create the synchronization request payload
            val syncRequest = SyncRequest(
                lastSyncTimestamp = SessionManager.lastSync,
                shoppingLists = localLists,
                shoppingItems = localItems,
                storeLocations = localStores,
                deletedItems = deletedItems.map {
                    DeletedItemSync(
                        syncId = it.syncId,
                        originalId = it.originalId?.toLong(),
                        entityType = it.entityType,
                        deletedAt = it.deletedAt ?: LocalDateTime.now()
                    )
                }
            )

            // 3. Call the sync API
            val response = try {
                syncService.synchronize(syncRequest)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling sync API", e)
                return@withContext Result.failure(e)
            }

            // 4. Handle unsuccessful API responses
            if (!response.isSuccessful) {
                //  If error 401 (Unauthorized), try refreshing the authentication token
                if (response.code() == 401) {
                    Log.d(TAG, "Token expired, trying to refresh")

                    val refreshResult = authViewModel.refreshToken()

                    if (refreshResult.isSuccess) {
                        Log.d(TAG, "Token refreshed successfully, sync resumed")

                        // Retry synchronization with the refreshed token
                        val retryResponse = try {
                            syncService.synchronize(syncRequest)
                        } catch (e: Exception) {
                            return@withContext Result.failure(e)
                        }

                        // If retry still fails, return failure
                        if (!retryResponse.isSuccessful) {
                            return@withContext Result.failure(Exception("Synchronization failed: ${retryResponse.code()}"))
                        }

                        val body = retryResponse.body()
                        if (body != null) {
                            processServerResponse(body)
                        } else {
                            Log.e("Error", "Response body is null")
                        }

                        // Mark deleted items as synchronized
                        if (deletedItems.isNotEmpty()) {
                            val deletedItemIds = db.deletedItemDao().getUnsyncedItems().map { it.id }
                            if (deletedItemIds.isNotEmpty()) {
                                db.deletedItemDao().markAsSynced(deletedItemIds)
                                db.deletedItemDao().deleteSyncedItems()
                            }
                        }

                        return@withContext Result.success(true)
                    } else {
                        return@withContext Result.failure(Exception("Failed to refresh token"))
                    }
                }

                return@withContext Result.failure(Exception("Synchronization error: ${response.code()}"))
            }

            val syncResponse = response.body() ?: return@withContext Result.failure(
                Exception("Empty sync response")
            )

            // 5. Process the successful API response
            try {
                processServerResponse(syncResponse)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing server response", e)
            }

            // 6. Mark synchronized items as processed
            try {
                markListsAsSynced(localLists.mapNotNull { it.syncId })
                markItemsAsSynced(localItems.mapNotNull { it.syncId })
                markStoresAsSynced(localStores.mapNotNull { it.syncId })
            } catch (e: Exception) {
                Log.e(TAG, "Error marking items as synced", e)
            }

            // 7. Handle deleted items by marking them as synced and removing them from the local database
            if (deletedItems.isNotEmpty()) {
                try {
                    val deletedItemIds = db.deletedItemDao().getUnsyncedItems().map { it.id }
                    if (deletedItemIds.isNotEmpty()) {
                        db.deletedItemDao().markAsSynced(deletedItemIds)
                        db.deletedItemDao().deleteSyncedItems()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error while handling deleted items", e)
                }
            }

            Log.d(TAG, "Synchronization completed successfully")
            return@withContext Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Global synchronization error", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Processes the server response after synchronization.
     * - Updates existing entities based on the server data.
     * - Adds new entities received from the server.
     * - Saves the last synchronization timestamp for future sync operations.
     */
    private suspend fun processServerResponse(syncResponse: SyncResponse) {
        Log.d(TAG, "Traitement de la réponse du serveur...")

        // 1.  Update existing lists, items, and stores with the latest server data
        updateExistingEntities(syncResponse)

        // 2. Add new lists, items, and stores that were received from the server
        addNewEntities(syncResponse)

        // 3. Save the last synchronization timestamp for future sync operations
        SessionManager.lastSync = syncResponse.serverTimestamp

        Log.d(TAG, "Last sync: ${syncResponse.serverTimestamp}")
    }

    /**
     * Updates existing shopping lists, items, and store locations in the local database
     * based on data received from the server.
     * - If an entity exists locally (identified by syncId), it is updated with server data.
     * - If an entity does not exist locally, it will be handled separately in `addNewEntities`.
     */
    private suspend fun updateExistingEntities(syncResponse: SyncResponse) {
        //Update existing shopping lists
        syncResponse.shoppingLists.forEach { listSync ->
            listSync.syncId?.let { syncId ->
                val existingList = shoppingListDao.getListBySyncId(syncId)
                if (existingList != null) {
                    // Update local list with the latest data from the server
                    shoppingListDao.update(existingList.copy(
                        name = listSync.name,
                        serverId = listSync.id,
                        updatedAt = listSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED  // Mark as synchronized
                    ))
                }
            }
        }

        // Update existing shopping item
        syncResponse.shoppingItems.forEach { itemSync ->
            itemSync.syncId?.let { syncId ->
                val existingItem = shoppingItemDao.getItemBySyncId(syncId)
                if (existingItem != null) {
                    // Update local item with the latest data from the server
                    shoppingItemDao.update(existingItem.copy(
                        name = itemSync.name,
                        quantity = itemSync.quantity,
                        unitType = itemSync.unitType,
                        isChecked = itemSync.checked,
                        sortIndex = itemSync.sortIndex,
                        serverId = itemSync.id,
                        updatedAt = itemSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED  // Mark as synchronized
                    ))
                } else {
                    Log.d(TAG, "Item with syncId $syncId not found locally, will be added as new")
                }
            }
        }

        // Update existing store locations
        syncResponse.storeLocations.forEach { storeSync ->
            storeSync.syncId?.let { syncId ->
                val existingStore = storeLocationDao.getStoreBySyncId(syncId)
                if (existingStore != null) {
                    // Update local store with the latest data from the server
                    storeLocationDao.update(existingStore.copy(
                        name = storeSync.name,
                        address = storeSync.address,
                        latitude = storeSync.latitude,
                        longitude = storeSync.longitude,
                        serverId = storeSync.id,
                        updatedAt = storeSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED  // Mark as synchronized
                    ))
                }
            }
        }
    }

    /**
     * Adds new shopping lists, items, and stores received from the server to the local database.
     * - Ensures that only new entities (not already existing locally) are added.
     * - First processes shopping lists, then shopping items and stores to maintain relationships.
     */
    private suspend fun addNewEntities(syncResponse: SyncResponse) {
        // Retrieve all locally stored syncIds to avoid duplicates
        val localListSyncIds = shoppingListDao.getAllListsOnce().mapNotNull { it.syncId }
        val localItemSyncIds = shoppingItemDao.getAllItemsOnce().mapNotNull { it.syncId }
        val localStoreSyncIds = storeLocationDao.getAllStoresOnce().mapNotNull { it.syncId }

        // Filter out lists that do not exist locally
        val newLists = syncResponse.shoppingLists.filter { it.syncId !in localListSyncIds }

        // Process the new lists first, as items will need a reference to a list
        processServerLists(newLists)

        //  Filter out new items and stores that are not already stored locally
        val newItems = syncResponse.shoppingItems.filter { it.syncId !in localItemSyncIds }
        val newStores = syncResponse.storeLocations.filter { it.syncId !in localStoreSyncIds }

        // Process the new items and stores
        processServerItems(newItems)
        processServerStores(newStores)
    }

    /**
     * Only selects lists that have been modified and require synchronization.
     */
    private suspend fun mapLocalListsToSync(): List<ShoppingListSync> {
        // Only take lists that need to be synced
        val lists = shoppingListDao.getListsToSync()

        return lists.map { list ->
            ShoppingListSync(
                id = list.serverId,
                name = list.name,
                syncId = list.syncId ?: UUID.randomUUID().toString(),
                createdAt = list.updatedAt ?: LocalDateTime.now(),
                updatedAt = list.updatedAt ?: LocalDateTime.now(),
                lastSynced = SessionManager.lastSync,
                version = null
            )
        }
    }

    /**
     * Ensures each item is linked to a valid shopping list.
     * Only selects items that have been modified and require synchronization.
     */
    private suspend fun mapLocalItemsToSync(): List<ShoppingItemSync> {
        // Only take items that need to be synced
        val items = shoppingItemDao.getItemsToSync()

        return items.map { item ->
            // Find the list this item belongs to
            val list = shoppingListDao.getListById(item.listId)

            ShoppingItemSync(
                id = item.serverId,
                name = item.name,
                quantity = item.quantity,
                unitType = item.unitType,
                checked = item.isChecked,
                sortIndex = item.sortIndex,
                shoppingListId = list?.serverId ?: -1L,
                syncId = item.syncId ?: UUID.randomUUID().toString(),
                createdAt = item.updatedAt ?: LocalDateTime.now(),
                updatedAt = item.updatedAt ?: LocalDateTime.now(),
                lastSynced = SessionManager.lastSync,
                version = null
            )
        }
    }

    /**
     * Only selects stores that have been modified and require synchronization.
     */
    private suspend fun mapLocalStoresToSync(): List<StoreLocationSync> {
        // Only take stores that need to be synced
        val stores = storeLocationDao.getStoresToSync()

        return stores.map { store ->
            StoreLocationSync(
                id = store.serverId,
                name = store.name,
                address = store.address,
                latitude = store.latitude,
                longitude = store.longitude,
                geofenceId = store.geofenceId,
                syncId = store.syncId ?: UUID.randomUUID().toString(),
                createdAt = store.updatedAt ?: LocalDateTime.now(),
                updatedAt = store.updatedAt ?: LocalDateTime.now(),
                lastSynced = SessionManager.lastSync,
                version = null
            )
        }
    }

    /**
     * Synchronizes shopping lists received from the server with the local database
     * - If a list already exists locally (identified by syncId), it is updated.
     * - If a list doesn't exist locally, it is created.
     */
    private suspend fun processServerLists(lists: List<ShoppingListSync>) {
        for (listSync in lists) {
            // Check if list already exists locally by syncId
            val existingList = listSync.syncId?.let { syncId: String ->
                shoppingListDao.getListBySyncId(syncId)
            }

            if (existingList != null) {
                // Update the existing list with the latest server data
                shoppingListDao.update(
                    existingList.copy(
                        name = listSync.name,
                        serverId = listSync.id,
                        updatedAt = listSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED
                    )
                )
            } else {
                // Insert a new shopping list into the local database
                shoppingListDao.insert(
                    ShoppingList(
                        name = listSync.name,
                        syncId = listSync.syncId,
                        serverId = listSync.id,
                        updatedAt = listSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED
                    )
                )
            }
        }
    }

    /**
     * Synchronizes shopping items received from the server with the local database.
     * - If an item with the same syncId already exists locally, it is updated.
     * - Otherwise, a new item is inserted into the corresponding shopping list.
     * - If insertion fails, an existing item with a similar name is updated instead.
     */
    private suspend fun processServerItems(items: List<ShoppingItemSync>) {
        for (itemSync in items) {
            // Check if the item already exists locally using syncId
            val existingItem = itemSync.syncId?.let { syncId ->
                shoppingItemDao.getItemBySyncId(syncId)
            }

            if (existingItem != null) {
                // Update the existing item with the latest server data
                shoppingItemDao.update(
                    existingItem.copy(
                        name = itemSync.name,
                        quantity = itemSync.quantity,
                        unitType = itemSync.unitType,
                        isChecked = itemSync.checked,
                        sortIndex = itemSync.sortIndex,
                        serverId = itemSync.id,
                        updatedAt = itemSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED
                    )
                )
            } else {
                //Find the corresponding local shopping list for this item
                val localList = if (itemSync.shoppingListId > 0) {
                    shoppingListDao.getListByServerId(itemSync.shoppingListId)
                } else {
                    // If no valid server ID is provided, assign it to the first available list
                    shoppingListDao.getAllListsOnce().firstOrNull()
                }

                if (localList != null) {
                    try {
                        // Insert a new item into the database
                        shoppingItemDao.insert(
                            ShoppingItem(
                                name = itemSync.name,
                                quantity = itemSync.quantity,
                                unitType = itemSync.unitType,
                                isChecked = itemSync.checked,
                                sortIndex = itemSync.sortIndex,
                                syncId = itemSync.syncId,
                                serverId = itemSync.id,
                                listId = localList.id,
                                updatedAt = itemSync.updatedAt,
                                syncStatus = SyncStatus.SYNCED
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error inserting item ${itemSync.name} with syncId ${itemSync.syncId}", e)

                        // If insertion fails, find a similar item by name and update it instead
                        val similarItems = shoppingItemDao.getAllByShoppingListOnce(localList.id)
                            .filter { it.name.equals(itemSync.name, ignoreCase = true) }

                        if (similarItems.isNotEmpty()) {
                            Log.d(TAG, "Found similar items with the same name, updating the first one")
                            val itemToUpdate = similarItems.first()
                            shoppingItemDao.update(
                                itemToUpdate.copy(
                                    quantity = itemSync.quantity,
                                    unitType = itemSync.unitType,
                                    isChecked = itemSync.checked,
                                    sortIndex = itemSync.sortIndex,
                                    syncId = itemSync.syncId,
                                    serverId = itemSync.id,
                                    updatedAt = itemSync.updatedAt,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Synchronizes store locations received from the server with the local database.
     * - If a store already exists locally (determined by syncId, geofenceId, or location), it is updated.
     * - Otherwise, a new store is inserted.
     */
    private suspend fun processServerStores(stores: List<StoreLocationSync>) {
        for (storeSync in stores) {
            // Check if the store already exists locally using syncId
            var existingStore = storeSync.syncId?.let { syncId ->
                storeLocationDao.getStoreBySyncId(syncId)
            }

            // If not found, try matching by geofenceId
            if (existingStore == null && storeSync.geofenceId.isNotEmpty()) {
                existingStore = storeLocationDao.getStoreByGeofenceId(storeSync.geofenceId)
            }

            //  If still not found, check by location coordinates (small margin of error)
            if (existingStore == null) {
                val allStores = storeLocationDao.getAllStoresOnce()
                existingStore = allStores.find { store ->
                    Math.abs(store.latitude - storeSync.latitude) < 0.0001 &&
                            Math.abs(store.longitude - storeSync.longitude) < 0.0001
                }
            }

            // If still not found, check by similar name and address
            if (existingStore == null) {
                val allStores = storeLocationDao.getAllStoresOnce()
                existingStore = allStores.find { store ->
                    store.name.equals(storeSync.name, ignoreCase = true) &&
                            store.address.equals(storeSync.address, ignoreCase = true)
                }
            }

            // If still not found,Update the existing store with the latest server data
            // If the server geofenceId is empty, keep the local one
            if (existingStore != null) {
                val updatedStore = existingStore.copy(
                    name = storeSync.name,
                    address = storeSync.address,
                    latitude = storeSync.latitude,
                    longitude = storeSync.longitude,
                    geofenceId = if (storeSync.geofenceId.isNotEmpty()) storeSync.geofenceId else existingStore.geofenceId,
                    syncId = storeSync.syncId ?: existingStore.syncId,
                    serverId = storeSync.id,
                    updatedAt = storeSync.updatedAt,
                    syncStatus = SyncStatus.SYNCED
                )

                storeLocationDao.update(updatedStore)
            } else {
                // Insert a new store into the database
                storeLocationDao.insert(
                    StoreLocation(
                        name = storeSync.name,
                        address = storeSync.address,
                        latitude = storeSync.latitude,
                        longitude = storeSync.longitude,
                        geofenceId = storeSync.geofenceId,
                        syncId = storeSync.syncId,
                        serverId = storeSync.id,
                        updatedAt = storeSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED
                    )
                )
            }
        }
    }

    private suspend fun markItemsAsSynced(syncIds: List<String>) {
        for (syncId in syncIds) {
            try {
                shoppingItemDao.updateSyncStatus(syncId, SyncStatus.SYNCED)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating item sync status $syncId", e)
            }
        }
    }

    private suspend fun markListsAsSynced(syncIds: List<String>) {
        for (syncId in syncIds) {
            try {
                shoppingListDao.updateSyncStatus(syncId, SyncStatus.SYNCED)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating list sync status $syncId", e)
            }
        }
    }

    private suspend fun markStoresAsSynced(syncIds: List<String>) {
        for (syncId in syncIds) {
            try {
                storeLocationDao.updateSyncStatus(syncId, SyncStatus.SYNCED)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating store sync status $syncId", e)
            }
        }
    }
}