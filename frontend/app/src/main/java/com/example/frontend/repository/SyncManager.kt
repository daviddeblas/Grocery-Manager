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

class SyncManager(private val context: Context) {
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
     * Synchronise les données locales avec le serveur
     */
    suspend fun synchronize(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Début de la synchronisation...")

            // Vérifier si l'utilisateur est connecté
            if (!SessionManager.isLoggedIn()) {
                Log.e(TAG, "Synchronisation impossible : utilisateur non connecté")
                return@withContext Result.failure(Exception("Utilisateur non connecté"))
            }

            // 1. Préparer les données pour la synchronisation
            val localLists = mapLocalListsToSync()
            val localItems = mapLocalItemsToSync()
            val localStores = mapLocalStoresToSync()

            // Récupérer les éléments supprimés
            val deletedItems = db.deletedItemDao().getUnsyncedItems()
            Log.d(TAG, "frontend: Items supprimés à synchroniser: ${deletedItems.size} - SyncIDs: ${deletedItems.map { it.syncId }}")

            Log.d(TAG, "Données à synchroniser: ${localLists.size} listes, ${localItems.size} articles, ${localStores.size} magasins, ${deletedItems.size} éléments supprimés")

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

            // 2. Appeler l'API de synchronisation
            val response = syncService.synchronize(syncRequest)



            if (!response.isSuccessful) {
                // Si erreur 401, tenter de rafraîchir le token
                if (response.code() == 401) {
                    Log.d(TAG, "Token expiré, tentative de rafraîchissement...")

                    val refreshResult = authViewModel.refreshToken()

                    if (refreshResult.isSuccess) {
                        Log.d(TAG, "Token rafraîchi avec succès, reprise de la synchronisation")

                        // Réessayer la synchronisation avec le nouveau token
                        val retryResponse = syncService.synchronize(syncRequest)

                        if (!retryResponse.isSuccessful) {
                            Log.e(TAG, "Échec de la synchronisation après rafraîchissement du token: ${retryResponse.code()}")
                            return@withContext Result.failure(Exception("Échec de la synchronisation: ${retryResponse.code()}"))
                        }

                        processServerResponse(retryResponse.body()!!)

                        // Marquer les éléments supprimés comme synchronisés
                        if (deletedItems.isNotEmpty()) {
                            val deletedItemIds = db.deletedItemDao().getUnsyncedItems().map { it.id }
                            db.deletedItemDao().markAsSynced(deletedItemIds)
                            db.deletedItemDao().deleteSyncedItems()
                        }

                        return@withContext Result.success(true)
                    } else {
                        Log.e(TAG, "Échec du rafraîchissement du token")
                        return@withContext Result.failure(Exception("Échec du rafraîchissement du token"))
                    }
                }

                Log.e(TAG, "Erreur de synchronisation: ${response.code()}")
                return@withContext Result.failure(Exception("Erreur de synchronisation: ${response.code()}"))
            }

            val syncResponse = response.body() ?: return@withContext Result.failure(
                Exception("Réponse de synchronisation vide")
            )

            // 3. Traiter la réponse du serveur
            processServerResponse(syncResponse)

            // 4. Marquer les éléments comme synchronisés
            markListsAsSynced(localLists.mapNotNull { it.syncId })
            markItemsAsSynced(localItems.mapNotNull { it.syncId })
            markStoresAsSynced(localStores.mapNotNull { it.syncId })

            if (response.isSuccessful) {
                // Traiter la réponse
                processServerResponse(syncResponse)

                // Marquer les éléments supprimés comme synchronisés
                if (deletedItems.isNotEmpty()) {
                    val deletedItemIds = db.deletedItemDao().getUnsyncedItems().map { it.id }
                    if (deletedItemIds.isNotEmpty()) {
                        Log.d(TAG, "Marquage de ${deletedItemIds.size} éléments supprimés comme synchronisés")
                        db.deletedItemDao().markAsSynced(deletedItemIds)
                        db.deletedItemDao().deleteSyncedItems()
                    }
                }

                return@withContext Result.success(true)
            }

            return@withContext Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur de synchronisation", e)
            return@withContext Result.failure(e)
        }
    }

    private suspend fun processServerResponse(syncResponse: SyncResponse) {
        Log.d(TAG, "Traitement de la réponse du serveur...")

        // 1. Mettre à jour les éléments existants
        updateExistingEntities(syncResponse)

        // 2. Traiter les nouveaux éléments
        addNewEntities(syncResponse)

        // 3. Enregistrer la date de dernière synchronisation
        SessionManager.lastSync = syncResponse.serverTimestamp

        Log.d(TAG, "Synchronisation réussie, dernière synchronisation: ${syncResponse.serverTimestamp}")
    }

    private suspend fun updateExistingEntities(syncResponse: SyncResponse) {
        // Mettre à jour les listes existantes
        syncResponse.shoppingLists.forEach { listSync ->
            listSync.syncId?.let { syncId ->
                val existingList = shoppingListDao.getListBySyncId(syncId)
                if (existingList != null) {
                    // Mettre à jour avec les données du serveur
                    shoppingListDao.update(existingList.copy(
                        name = listSync.name,
                        serverId = listSync.id,
                        updatedAt = listSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED  // Important: marquer comme synchronisé
                    ))
                }
            }
        }

        // Mettre à jour les articles existants
        syncResponse.shoppingItems.forEach { itemSync ->
            itemSync.syncId?.let { syncId ->
                val existingItem = shoppingItemDao.getItemBySyncId(syncId)
                if (existingItem != null) {
                    // Mettre à jour avec les données du serveur
                    shoppingItemDao.update(existingItem.copy(
                        name = itemSync.name,
                        quantity = itemSync.quantity,
                        unitType = itemSync.unitType,
                        isChecked = itemSync.checked,
                        sortIndex = itemSync.sortIndex,
                        serverId = itemSync.id,
                        updatedAt = itemSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED  // Important: marquer comme synchronisé
                    ))
                    Log.d(TAG, "Updated existing item: ${existingItem.id} (${existingItem.name}) with syncId $syncId")
                } else {
                    Log.d(TAG, "Item with syncId $syncId not found locally, will be added as new")
                }
            }
        }

        // Mettre à jour les magasins existants
        syncResponse.storeLocations.forEach { storeSync ->
            storeSync.syncId?.let { syncId ->
                val existingStore = storeLocationDao.getStoreBySyncId(syncId)
                if (existingStore != null) {
                    // Mettre à jour avec les données du serveur
                    storeLocationDao.update(existingStore.copy(
                        name = storeSync.name,
                        address = storeSync.address,
                        latitude = storeSync.latitude,
                        longitude = storeSync.longitude,
                        serverId = storeSync.id,
                        updatedAt = storeSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED  // Important: marquer comme synchronisé
                    ))
                }
            }
        }
    }

    private suspend fun addNewEntities(syncResponse: SyncResponse) {
        // Récupérer tous les syncIds locaux pour éviter les doublons
        val localListSyncIds = shoppingListDao.getAllListsOnce().mapNotNull { it.syncId }
        val localItemSyncIds = shoppingItemDao.getAllItemsOnce().mapNotNull { it.syncId }
        val localStoreSyncIds = storeLocationDao.getAllStoresOnce().mapNotNull { it.syncId }

        // Filtrer les listes qui n'existent pas déjà localement
        val newLists = syncResponse.shoppingLists.filter { it.syncId !in localListSyncIds }

        // Traiter d'abord les listes car les articles en dépendent
        processServerLists(newLists)

        // Filtrer les articles et magasins qui n'existent pas déjà localement
        val newItems = syncResponse.shoppingItems.filter { it.syncId !in localItemSyncIds }
        val newStores = syncResponse.storeLocations.filter { it.syncId !in localStoreSyncIds }

        Log.d(TAG, "Nouvelles données à ajouter: ${newLists.size} listes, ${newItems.size} articles, ${newStores.size} magasins")

        // Ensuite traiter les articles et magasins
        processServerItems(newItems)
        processServerStores(newStores)
    }

    private suspend fun mapLocalListsToSync(): List<ShoppingListSync> {
        // Ne prendre que les listes qui ont besoin d'être synchronisées
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

    private suspend fun mapLocalItemsToSync(): List<ShoppingItemSync> {
        // Ne prendre que les articles qui ont besoin d'être synchronisés
        val items = shoppingItemDao.getItemsToSync()

        return items.map { item ->
            // Trouver la liste à laquelle appartient cet article
            val list = shoppingListDao.getListById(item.listId)

            ShoppingItemSync(
                id = item.serverId,
                name = item.name,
                quantity = item.quantity,
                unitType = item.unitType,
                checked = item.isChecked,
                sortIndex = item.sortIndex,
                shoppingListId = list?.serverId ?: -1L,  // Si pas de serverId, utiliser -1 temporairement
                syncId = item.syncId ?: UUID.randomUUID().toString(),
                createdAt = item.updatedAt ?: LocalDateTime.now(),
                updatedAt = item.updatedAt ?: LocalDateTime.now(),
                lastSynced = SessionManager.lastSync,
                version = null
            )
        }
    }

    private suspend fun mapLocalStoresToSync(): List<StoreLocationSync> {
        // Ne prendre que les magasins qui ont besoin d'être synchronisés
        val stores = storeLocationDao.getStoresToSync()
        Log.d(TAG, "frontend: Stores à synchroniser: ${stores.size} - IDs: ${stores.map { it.id }}")
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

    // Fonctions de traitement des données reçues du serveur

    private suspend fun processServerLists(lists: List<ShoppingListSync>) {
        for (listSync in lists) {
            // Vérifier si la liste existe déjà localement par syncId
            val existingList = listSync.syncId?.let { syncId: String ->
                shoppingListDao.getListBySyncId(syncId)
            }

            if (existingList != null) {
                // Mettre à jour la liste existante avec les données du serveur
                shoppingListDao.update(
                    existingList.copy(
                        name = listSync.name,
                        serverId = listSync.id,
                        updatedAt = listSync.updatedAt,
                        syncStatus = SyncStatus.SYNCED
                    )
                )
            } else {
                // Créer une nouvelle liste
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

    private suspend fun processServerItems(items: List<ShoppingItemSync>) {
        for (itemSync in items) {
            // Double check that this item doesn't already exist locally
            val existingItem = itemSync.syncId?.let { syncId ->
                shoppingItemDao.getItemBySyncId(syncId)
            }

            if (existingItem != null) {
                // Item already exists, update it instead of creating a new one
                Log.d(TAG, "Item with syncId ${itemSync.syncId} already exists locally, updating")
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
                // Find the corresponding local list
                val localList = if (itemSync.shoppingListId > 0) {
                    shoppingListDao.getListByServerId(itemSync.shoppingListId)
                } else {
                    // If no valid server ID, use the first list
                    shoppingListDao.getAllListsOnce().firstOrNull()
                }

                if (localList != null) {
                    try {
                        // Create a new item
                        Log.d(TAG, "Creating new item from server: ${itemSync.name} with syncId ${itemSync.syncId}")
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
                        // If insertion fails, try to update any existing item with the same name
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

    private suspend fun processServerStores(stores: List<StoreLocationSync>) {
        for (storeSync in stores) {
            // First check if the store already exists by syncId
            var existingStore = storeSync.syncId?.let { syncId ->
                storeLocationDao.getStoreBySyncId(syncId)
            }

            // If not found by syncId, try finding by geofenceId
            if (existingStore == null && storeSync.geofenceId.isNotEmpty()) {
                existingStore = storeLocationDao.getStoreByGeofenceId(storeSync.geofenceId)
            }

            // If still not found, try finding by similar location (coordinates within a small range)
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

            if (existingStore != null) {
                // Update the existing store with the server data
                // and maintain the existing geofenceId if the server one is empty
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

                Log.d(TAG, "Updating existing store: ${existingStore.id} (${existingStore.name}) with sync data")
                storeLocationDao.update(updatedStore)
            } else {
                // Create a new store only if we're sure it doesn't exist
                Log.d(TAG, "Creating new store from sync: ${storeSync.name}")
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

    // Marquer les éléments comme synchronisés

    private suspend fun markItemsAsSynced(syncIds: List<String>) {
        for (syncId in syncIds) {
            try {
                shoppingItemDao.updateSyncStatus(syncId, SyncStatus.SYNCED)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de la mise à jour du statut de synchronisation de l'article $syncId", e)
            }
        }
    }

    private suspend fun markListsAsSynced(syncIds: List<String>) {
        for (syncId in syncIds) {
            try {
                shoppingListDao.updateSyncStatus(syncId, SyncStatus.SYNCED)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de la mise à jour du statut de synchronisation de la liste $syncId", e)
            }
        }
    }

    private suspend fun markStoresAsSynced(syncIds: List<String>) {
        for (syncId in syncIds) {
            try {
                storeLocationDao.updateSyncStatus(syncId, SyncStatus.SYNCED)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de la mise à jour du statut de synchronisation du magasin $syncId", e)
            }
        }
    }
}