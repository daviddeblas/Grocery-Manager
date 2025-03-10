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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.*

/**
 * Manages the synchronization of local data with the server.
 * Implements intelligent sync strategies including two-phase synchronization when needed.
 */
class SyncManager(context: Context) {
    companion object {
        private const val TAG = "SyncManager"
    }

    private val db = AppDatabase.getDatabase(context)
    private val shoppingItemDao = db.shoppingItemDao()
    private val shoppingListDao = db.shoppingListDao()
    private val storeLocationDao = db.storeLocationDao()

    private val syncService = APIClient.syncService

    /**
     * Synchronizes local data with the server.
     *
     * This method handles all synchronization scenarios, including:
     */
    suspend fun synchronize(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting synchronization")

            // 1. Check if the user is logged in
            if (!SessionManager.isLoggedIn()) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            // 2. Analyze what needs to be synchronized
            val listsToSync = shoppingListDao.getListsToSync()

            // Identify new lists (created offline) that contain items
            val newListsWithItems = listsToSync.any { list ->
                list.serverId == null &&
                        shoppingItemDao.getAllByShoppingListOnce(list.id).isNotEmpty()
            }

            // If there are new lists with items, we need a two-phase synchronization
            if (newListsWithItems) {
                Log.d(TAG, "Detected offline lists with items -> using two-phase synchronization")
                return@withContext synchronizeInTwoPhases()
            } else {
                Log.d(TAG, "No offline lists with items -> using standard synchronization")
                return@withContext standardSynchronize()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synchronization error", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Performs a one-step synchronization for all data.
     * Used when there are no dependencies between lists and items.
     */
    private suspend fun standardSynchronize(): Result<Boolean> {
        try {
            // Prepare data for synchronization
            val localLists = mapLocalListsToSync()
            val localItems = mapLocalItemsToSync()
            val localStores = mapLocalStoresToSync()
            val deletedItems = db.deletedItemDao().getUnsyncedItems()

            // Build the sync request
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

            // Call the sync API
            val response = try {
                syncService.synchronize(syncRequest)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling sync API", e)
                return Result.failure(e)
            }

            if (!response.isSuccessful) {
                return Result.failure(Exception("Synchronization failed: ${response.code()}"))
            }

            // Process the server response
            val syncResponse = response.body() ?: return Result.failure(
                Exception("Empty synchronization response")
            )

            processServerResponse(syncResponse)

            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Standard synchronization error", e)
            return Result.failure(e)
        }
    }

    /**
     * Performs a two-step synchronization process.
     *
     * This method is used when items depend on lists created offline.
     * - Phase 1: Synchronizes lists first to obtain their server IDs.
     * - Phase 2: Synchronizes items and stores once the lists exist on the server.
     */
    private suspend fun synchronizeInTwoPhases(): Result<Boolean> {
        try {
            Log.d(TAG, "Phase 1: Synchronizing lists")

            // PHASE 1: Sync lists
            val listsToSync = shoppingListDao.getListsToSync()
            val listSyncIds = mutableSetOf<String>()

            val listsSyncRequest = SyncRequest(
                lastSyncTimestamp = SessionManager.lastSync,
                shoppingLists = listsToSync.map { list ->
                    val syncId = list.syncId ?: UUID.randomUUID().toString()
                    listSyncIds.add(syncId)
                    ShoppingListSync(
                        id = list.serverId,
                        name = list.name,
                        syncId = syncId,
                        createdAt = list.updatedAt ?: LocalDateTime.now(),
                        updatedAt = list.updatedAt ?: LocalDateTime.now(),
                        lastSynced = SessionManager.lastSync,
                        version = null
                    )
                },
                shoppingItems = emptyList(),
                storeLocations = emptyList(),
                deletedItems = emptyList()
            )

            // Send the synchronization request for lists
            val listsResponse = try {
                syncService.synchronize(listsSyncRequest)
            } catch (e: Exception) {
                Log.e(TAG, "List synchronization failed", e)
                return Result.failure(e)
            }

            if (!listsResponse.isSuccessful) {
                return Result.failure(Exception("List synchronization failed: ${listsResponse.code()}"))
            }

            // Process the response and update local lists with their server IDs
            val listsResponseBody = listsResponse.body()
            listsResponseBody?.shoppingLists?.forEach { serverList ->
                if (serverList.syncId != null && serverList.id != null) {
                    val localList = shoppingListDao.getListBySyncId(serverList.syncId)
                    if (localList != null) {
                        val updatedList = localList.copy(
                            serverId = serverList.id,
                            syncStatus = SyncStatus.SYNCED
                        )
                        shoppingListDao.update(updatedList)
                        Log.d(TAG, "List ${localList.id} updated with server ID ${serverList.id}")
                    }
                }
            }

            // Short delay to allow server processing
            delay(200)

            Log.d(TAG, "Phase 2: Synchronizing items and stores")

            // PHASE 2: Sync items and stores
            val itemsToSync = shoppingItemDao.getItemsToSync()
            val deletedItems = db.deletedItemDao().getUnsyncedItems()

            // Convert items to sync models, linking them to server-side lists
            val itemSyncs = itemsToSync.mapNotNull { item ->
                val list = shoppingListDao.getListById(item.listId)
                if (list?.serverId == null) {
                    Log.w(TAG, "Item ${item.id} references list ${item.listId} without a server ID")
                    return@mapNotNull null
                }

                ShoppingItemSync(
                    id = item.serverId,
                    name = item.name,
                    quantity = item.quantity,
                    unitType = item.unitType,
                    checked = item.isChecked,
                    sortIndex = item.sortIndex,
                    shoppingListId = list.serverId,
                    syncId = item.syncId ?: UUID.randomUUID().toString(),
                    createdAt = item.updatedAt ?: LocalDateTime.now(),
                    updatedAt = item.updatedAt ?: LocalDateTime.now(),
                    lastSynced = SessionManager.lastSync,
                    version = null
                )
            }

            val itemsAndStoresSyncRequest = SyncRequest(
                lastSyncTimestamp = SessionManager.lastSync,
                shoppingLists = emptyList(),
                shoppingItems = itemSyncs,
                storeLocations = mapLocalStoresToSync(),
                deletedItems = deletedItems.map {
                    DeletedItemSync(
                        syncId = it.syncId,
                        originalId = it.originalId?.toLong(),
                        entityType = it.entityType,
                        deletedAt = it.deletedAt ?: LocalDateTime.now()
                    )
                }
            )

            // Execute the second synchronization request
            val itemsResponse = try {
                syncService.synchronize(itemsAndStoresSyncRequest)
            } catch (e: Exception) {
                Log.e(TAG, "Item synchronization failed", e)
                return Result.failure(e)
            }

            if (!itemsResponse.isSuccessful) {
                return Result.failure(Exception("Item synchronization failed: ${itemsResponse.code()}"))
            }

            // Process server response
            val itemsResponseBody = itemsResponse.body() ?: return Result.failure(
                Exception("Empty response for item synchronization")
            )

            // Update local data based on server response
            processServerItems(itemsResponseBody.shoppingItems)
            processServerStores(itemsResponseBody.storeLocations)

            // Mark deleted items as synced
            if (deletedItems.isNotEmpty()) {
                val deletedItemIds = deletedItems.map { it.id }
                db.deletedItemDao().markAsSynced(deletedItemIds)
                db.deletedItemDao().deleteSyncedItems()
            }

            // Update last sync timestamp
            SessionManager.lastSync = itemsResponseBody.serverTimestamp

            Log.d(TAG, "Two-phase synchronization completed successfully")
            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Two-phase synchronization error", e)
            return Result.failure(e)
        }
    }

    /**
     * Processes the server response after synchronization.
     * - Updates existing entities based on the server data.
     * - Adds new entities received from the server.
     * - Saves the last synchronization timestamp for future sync operations.
     */
    private suspend fun processServerResponse(syncResponse: SyncResponse) {
        Log.d(TAG, "Processing server response...")

        // 1. Update existing lists, items, and stores with the latest server data
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

        // Filter out new items and stores that are not already stored locally
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
     * Only includes items that have been modified locally and require synchronization.
     */
    private suspend fun mapLocalItemsToSync(): List<ShoppingItemSync> {
        // Get items that need to be synced
        val items = shoppingItemDao.getItemsToSync()
        Log.d(TAG, "Mapping ${items.size} items for sync")

        return items.mapNotNull { item ->
            // Find the list this item belongs to
            val list = shoppingListDao.getListById(item.listId)

            if (list == null) {
                Log.w(TAG, "Cannot find list with ID ${item.listId} for item ${item.id}")
                return@mapNotNull null
            }

            // If list has no serverId, we have a problem
            if (list.serverId == null) {
                Log.w(TAG, "List ${list.id} has no serverId, cannot sync item ${item.id}")
                return@mapNotNull null
            }

            ShoppingItemSync(
                id = item.serverId,
                name = item.name,
                quantity = item.quantity,
                unitType = item.unitType,
                checked = item.isChecked,
                sortIndex = item.sortIndex,
                shoppingListId = list.serverId,
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
     * Processes shopping lists received from the server, adding them to the local database.
     * If a list already exists locally (identified by syncId), it is updated.
     * Otherwise, a new list is created in the local database.
     */
    private suspend fun processServerLists(lists: List<ShoppingListSync>) {
        for (listSync in lists) {
            // Check if list already exists locally by syncId
            val existingList = listSync.syncId?.let { syncId: String ->
                shoppingListDao.getListBySyncId(syncId)
            }

            if (existingList != null) {
                // Update the existing list with the latest server data
                val updatedList = existingList.copy(
                    name = listSync.name,
                    serverId = listSync.id,
                    updatedAt = listSync.updatedAt,
                    syncStatus = SyncStatus.SYNCED
                )

                shoppingListDao.update(updatedList)
            } else if (listSync.syncId != null) {
                // Insert a new shopping list with the server data
                val newList = ShoppingList(
                    name = listSync.name,
                    syncId = listSync.syncId,
                    serverId = listSync.id,
                    updatedAt = listSync.updatedAt,
                    syncStatus = SyncStatus.SYNCED
                )
                shoppingListDao.insert(newList)
            }
        }
    }

    /**
     * Processes shopping items received from the server, adding them to the local database.
     * If an item already exists locally (identified by syncId), it is updated.
     * Otherwise, a new item is created in the local database.
     */
    private suspend fun processServerItems(items: List<ShoppingItemSync>) {
        // Create a map from list server ID to local ID
        val listServerToLocalIdMap = mutableMapOf<Long, Int>()
        val allLists = shoppingListDao.getAllListsOnce()

        for (list in allLists) {
            if (list.serverId != null) {
                listServerToLocalIdMap[list.serverId] = list.id
            }
        }

        for (itemSync in items) {
            // Find the local list ID for this item's server list ID
            val localListId = listServerToLocalIdMap[itemSync.shoppingListId]

            if (localListId == null) {
                Log.w(TAG, "Cannot find local list for server list ID ${itemSync.shoppingListId}, skipping item ${itemSync.name}")
                continue
            }

            // Check if the item already exists locally using syncId
            val existingItem = itemSync.syncId?.let { syncId ->
                shoppingItemDao.getItemBySyncId(syncId)
            }

            if (existingItem != null) {
                // Update existing item
                val updatedItem = existingItem.copy(
                    name = itemSync.name,
                    quantity = itemSync.quantity,
                    unitType = itemSync.unitType,
                    isChecked = itemSync.checked,
                    sortIndex = itemSync.sortIndex,
                    listId = localListId,
                    serverId = itemSync.id,
                    updatedAt = itemSync.updatedAt,
                    syncStatus = SyncStatus.SYNCED
                )
                shoppingItemDao.update(updatedItem)
            } else if (itemSync.syncId != null) {
                // Insert a new item into the database
                val newItem = ShoppingItem(
                    name = itemSync.name,
                    quantity = itemSync.quantity,
                    unitType = itemSync.unitType,
                    isChecked = itemSync.checked,
                    sortIndex = itemSync.sortIndex,
                    listId = localListId,
                    syncId = itemSync.syncId,
                    serverId = itemSync.id,
                    updatedAt = itemSync.updatedAt,
                    syncStatus = SyncStatus.SYNCED,
                    dateCreated = System.currentTimeMillis()
                )
                shoppingItemDao.insert(newItem)
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