package com.example.frontend.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.frontend.data.model.ShoppingItem
import com.example.frontend.data.model.ShoppingList
import com.example.frontend.data.model.SyncStatus

/*
    * Data Access Object for ShoppingItem
    * Contains all the queries for a specific ShoppingItem
 */
@Dao
interface ShoppingItemDao {
    @Insert
    suspend fun insert(item: ShoppingItem): Long

    @Delete
    suspend fun delete(item: ShoppingItem)

    @Update
    suspend fun update(item: ShoppingItem)

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY isChecked ASC, sortIndex ASC")
    fun getAllByManualOrder(listId: Int): LiveData<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY isChecked ASC, dateCreated DESC")
    fun getAllByDate(listId: Int): LiveData<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY isChecked ASC, quantity DESC")
    fun getAllByQuantity(listId: Int): LiveData<List<ShoppingItem>>

    // In order to sort the items already picked by the user, we put them last
    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY isChecked ASC, name ASC")
    fun getAllByChecked(listId: Int): LiveData<List<ShoppingItem>>

    // Check if there are any unchecked items in any list
    @Query("SELECT EXISTS(SELECT 1 FROM shopping_items WHERE isChecked = 0 LIMIT 1)")
    suspend fun hasUncheckedItems(): Boolean

    // Count of all items and unchecked items in a list
    @Query("SELECT COUNT(*) FROM shopping_items WHERE listId = :listId AND isChecked = 0")
    suspend fun countUncheckedItemsByList(listId: Int): Int

    @Query("DELETE FROM shopping_items WHERE listId = :listId")
    suspend fun deleteAllFromList(listId: Int)

    @Query("SELECT * FROM shopping_items WHERE listId = :listId")
    suspend fun getAllByShoppingListOnce(listId: Int): List<ShoppingItem>

    @Query("SELECT * FROM shopping_items WHERE syncId = :syncId LIMIT 1")
    suspend fun getItemBySyncId(syncId: String): ShoppingItem?

    @Query("SELECT * FROM shopping_items")
    suspend fun getAllItemsOnce(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_items WHERE syncStatus != 'SYNCED'")
    suspend fun getItemsToSync(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_items WHERE serverId = :serverId LIMIT 1")
    suspend fun getItemByServerId(serverId: Long): ShoppingItem?

    @Query("UPDATE shopping_items SET syncStatus = :status WHERE syncId = :syncId")
    suspend fun updateSyncStatus(syncId: String, status: SyncStatus)
}

/*
    * Data Access Object for ShoppingList
    * Contains all the queries for a list of ShoppingList
 */
@Dao
interface ShoppingListDao {
    @Insert
    suspend fun insert(list: ShoppingList): Long

    @Delete
    suspend fun delete(list: ShoppingList)

    @Query("SELECT * FROM shopping_lists")
    fun getAllLists(): LiveData<List<ShoppingList>>

    @Query("SELECT * FROM shopping_lists")
    suspend fun getAllListsOnce(): List<ShoppingList>

    @Query("SELECT * FROM shopping_lists WHERE syncStatus != 'SYNCED'")
    suspend fun getListsToSync(): List<ShoppingList>

    @Query("SELECT * FROM shopping_lists WHERE syncId = :syncId LIMIT 1")
    suspend fun getListBySyncId(syncId: String): ShoppingList?

    @Query("SELECT * FROM shopping_lists WHERE serverId = :serverId LIMIT 1")
    suspend fun getListByServerId(serverId: Long): ShoppingList?

    @Query("UPDATE shopping_lists SET syncStatus = :status WHERE syncId = :syncId")
    suspend fun updateSyncStatus(syncId: String, status: SyncStatus)

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getListById(id: Int): ShoppingList?

    @Update
    suspend fun update(list: ShoppingList)
}