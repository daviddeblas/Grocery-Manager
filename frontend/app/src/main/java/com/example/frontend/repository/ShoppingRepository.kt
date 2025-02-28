package com.example.frontend.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.example.frontend.data.db.AppDatabase
import com.example.frontend.data.model.ShoppingItem
import com.example.frontend.data.model.ShoppingList
import com.example.frontend.data.model.SortMode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShoppingRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val itemDao = db.shoppingItemDao()
    private val listDao = db.shoppingListDao()

    // Articles

    suspend fun addLocalItem(item: ShoppingItem) {
        withContext(Dispatchers.IO) {
            itemDao.insert(item)
        }
    }

    suspend fun deleteLocalItem(item: ShoppingItem) {
        withContext(Dispatchers.IO) {
            itemDao.delete(item)
        }
    }

    suspend fun updateLocalItem(item: ShoppingItem) {
        withContext(Dispatchers.IO) {
            itemDao.update(item)
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

    suspend fun addList(name: String) {
        withContext(Dispatchers.IO) {
            listDao.insert(ShoppingList(name = name))
        }
    }

    suspend fun deleteList(list: ShoppingList) {
        withContext(Dispatchers.IO) {
            // Erase all items from the list
            itemDao.deleteAllFromList(list.id)
            // Erase the list
            listDao.delete(list)
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
