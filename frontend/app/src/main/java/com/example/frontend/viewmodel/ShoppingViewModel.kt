package com.example.frontend.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.frontend.data.model.ShoppingItem
import com.example.frontend.data.model.ShoppingList
import com.example.frontend.data.model.SortMode
import com.example.frontend.repository.ShoppingRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * ShoppingViewModel is responsible for managing the UI-related data for shopping lists and items.
 * It communicates with the ShoppingRepository to perform CRUD operations and holds the state for
 * the currently selected list and the chosen sort mode.
 */
class ShoppingViewModel(application: Application) : AndroidViewModel(application) {

    // Repository that handles data operations
    private val repository = ShoppingRepository(application)

    val allLists: LiveData<List<ShoppingList>> = repository.allLists
    private val _currentListId = MutableLiveData<Int>()
    val currentListId: LiveData<Int> = _currentListId

    fun setCurrentListId(listId: Int) {
        _currentListId.value = listId
    }

    // LiveData for the selected sort mode. Defaults to CUSTOM.
    private val _sortMode = MutableLiveData<SortMode>(SortMode.CUSTOM)
    val sortMode: LiveData<SortMode> = _sortMode

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    /**
     * It retrieves the sorted list of shopping items from the repository whenever the current list ID
     * or sort mode changes.
     */
    val allItems: LiveData<List<ShoppingItem>> = MediatorLiveData<List<ShoppingItem>>().apply {
        fun updateSource() {
            val lId = _currentListId.value ?: return
            val sMode = _sortMode.value ?: SortMode.CUSTOM
            val source = repository.getItemsByListAndSort(lId, sMode)
            addSource(source) { value = it }
        }
        _currentListId.observeForever { updateSource() }
        _sortMode.observeForever { updateSource() }
    }

    // --- CRUD operations for shopping items ---

    /**
     * Adds a new shopping item to the current list.
     *
     * @param name The name of the item.
     * @param quantity The quantity of the item.
     * @param unit The unit type (e.g., "kg", "L", "pcs").
     */
    fun addItem(name: String, quantity: Double, unit: String) = viewModelScope.launch {
        // Ensure a current list is selected.
        val listId = _currentListId.value ?: return@launch
        val newItem = ShoppingItem(
            name = name,
            quantity = quantity,
            unitType = unit,
            listId = listId
        )
        repository.addLocalItem(newItem)
    }

    /**
     * Deletes a shopping item.
     *
     * @param item The shopping item to be deleted.
     */
    fun deleteItem(item: ShoppingItem) = viewModelScope.launch {
        repository.deleteLocalItem(item)
    }

    /**
     * Updates an existing shopping item.
     *
     * @param item The updated shopping item.
     */
    fun updateItem(item: ShoppingItem) = viewModelScope.launch {
        repository.updateLocalItem(item)
    }

    // --- CRUD operations for shopping lists ---

    /**
     * Adds a new shopping list.
     *
     * @param name The name of the new list.
     */
    fun addList(name: String) = viewModelScope.launch {
        repository.addList(name)
    }

    /**
     * Deletes an existing shopping list.
     *
     * @param list The shopping list to be deleted.
     */
    fun deleteList(list: ShoppingList) = viewModelScope.launch {
        repository.deleteList(list)
    }

    /**
     * Retrieves the default shopping list ID.
     * If no list exists, it creates a default list and returns its ID.
     *
     * @return The ID of the default shopping list.
     */
    fun getOrCreateDefaultListId(): Int = runBlocking {
        repository.getOrCreateDefaultListId()
    }
}
