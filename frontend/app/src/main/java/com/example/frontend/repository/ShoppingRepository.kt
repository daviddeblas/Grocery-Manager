package com.example.frontend.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.example.frontend.data.db.AppDatabase
import com.example.frontend.data.model.ShoppingItem
import com.example.frontend.data.model.ShoppingList
import com.example.frontend.data.model.SortMode
import com.example.frontend.data.model.SyncStatus
import com.example.frontend.data.model.DeletedItem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

class ShoppingRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val itemDao = db.shoppingItemDao()
    private val listDao = db.shoppingListDao()
    private val deletedItemDao = db.deletedItemDao()

    // Articles

    suspend fun addLocalItem(item: ShoppingItem) {
        return withContext(Dispatchers.IO) {
            val itemWithSyncId = if (item.syncId == null) {
                item.copy(
                    syncId = UUID.randomUUID().toString(),
                    syncStatus = SyncStatus.LOCAL_ONLY,
                    updatedAt = LocalDateTime.now()
                )
            } else {
                item
            }
            itemDao.insert(itemWithSyncId).toInt()
        }
    }


    suspend fun updateLocalItem(item: ShoppingItem) {
        withContext(Dispatchers.IO) {
            // If already synced, mark as locally modified
            val syncStatus = if (item.syncStatus == SyncStatus.SYNCED)
                SyncStatus.MODIFIED_LOCALLY
            else
                item.syncStatus

            val updatedItem = item.copy(
                syncStatus = syncStatus,
                updatedAt = LocalDateTime.now()
            )
            itemDao.update(updatedItem)
        }
    }

    suspend fun deleteLocalItem(item: ShoppingItem) {
        withContext(Dispatchers.IO) {
            itemDao.delete(item)

            // Register the item as deleted
            if (item.syncId != null) {
                val deletedItem = DeletedItem(
                    originalId = item.id,
                    syncId = item.syncId,
                    entityType = "SHOPPING_ITEM"
                )
                deletedItemDao.insert(deletedItem)
            }
        }
    }

    // Get items and sort them in a specific way
    fun getItemsByListAndSort(listId: Int, sortMode: SortMode): LiveData<List<ShoppingItem>> {
        return when (sortMode) {
            SortMode.CUSTOM -> itemDao.getAllByManualOrder(listId)
            SortMode.DATE -> itemDao.getAllByDate(listId)
            SortMode.QUANTITY -> itemDao.getAllByQuantity(listId)
            SortMode.CHECKED -> itemDao.getAllByChecked(listId)
        }
    }

    suspend fun hasUncheckedItems(): Boolean {
        return withContext(Dispatchers.IO) {
            itemDao.hasUncheckedItems()
        }
    }

    // Lists

    val allLists: LiveData<List<ShoppingList>> = listDao.getAllLists()

    suspend fun addList(name: String): Int {
        return withContext(Dispatchers.IO) {
            val list = ShoppingList(
                name = name,
                syncId = UUID.randomUUID().toString(),
                syncStatus = SyncStatus.LOCAL_ONLY,
                updatedAt = LocalDateTime.now()
            )
            listDao.insert(list).toInt()
        }
    }

    suspend fun deleteList(list: ShoppingList) {
        withContext(Dispatchers.IO) {
            val items = itemDao.getAllByShoppingListOnce(list.id)

            // Save deleted items with syncId
            for (item in items) {
                if (item.syncId != null) {
                    val deletedItem = DeletedItem(
                        originalId = item.id,
                        syncId = item.syncId,
                        entityType = "SHOPPING_ITEM"
                    )
                    deletedItemDao.insert(deletedItem)
                }
            }

            itemDao.deleteAllFromList(list.id)
            listDao.delete(list)

            // Save list deletion
            if (list.syncId != null) {
                val deletedItem = DeletedItem(
                    originalId = list.id,
                    syncId = list.syncId,
                    entityType = "SHOPPING_LIST"
                )
                deletedItemDao.insert(deletedItem)
            }
        }
    }

    suspend fun getOrCreateDefaultListId(): Int {
        // Get all lists
        val lists = listDao.getAllListsOnce()
        return if (lists.isEmpty()) {
            // List by default
            val newId = listDao.insert(ShoppingList(name = "My first list"))
            newId.toInt()
        } else {
            lists[0].id
        }
    }

}
