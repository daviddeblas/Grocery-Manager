package com.example.frontend.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.frontend.data.model.ShoppingItem
import com.example.frontend.data.model.ShoppingList

/*
    * Data Access Object for ShoppingItem
    * Contains all the queries for a specific ShoppingItem
 */
@Dao
interface ShoppingItemDao {
    @Insert
    suspend fun insert(item: ShoppingItem)

    @Delete
    suspend fun delete(item: ShoppingItem)

    @Update
    suspend fun update(item: ShoppingItem)

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY sortIndex ASC")
    fun getAllByManualOrder(listId: Int): LiveData<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY dateCreated DESC")
    fun getAllByDate(listId: Int): LiveData<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY quantity DESC")
    fun getAllByQuantity(listId: Int): LiveData<List<ShoppingItem>>

    // In order to sort the items already picked by the user, we put them last
    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY isChecked ASC, name ASC")
    fun getAllByChecked(listId: Int): LiveData<List<ShoppingItem>>

    @Query("DELETE FROM shopping_items WHERE listId = :listId")
    suspend fun deleteAllFromList(listId: Int)
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

    // Nouveau : requête « directe »
    @Query("SELECT * FROM shopping_lists")
    suspend fun getAllListsOnce(): List<ShoppingList>
}
