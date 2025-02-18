package com.example.frontend.ui.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend.R
import com.example.frontend.data.model.ShoppingItem
import com.example.frontend.data.model.SortMode
import com.example.frontend.ui.adapters.ManualShoppingAdapter
import com.example.frontend.utils.DragSwipeCallback
import com.example.frontend.viewmodel.ShoppingViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    // ViewModel and UI components
    private lateinit var viewModel: ShoppingViewModel
    private lateinit var textViewEmpty: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var manualAdapter: ManualShoppingAdapter
    private var itemTouchHelper: ItemTouchHelper? = null

    // Data variables
    private var originalList: List<ShoppingItem> = emptyList()
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up the title and ensure the toolbar is below the status bar !
        setupToolbar()

        // It allows to keep the data if the activity is destroyed or recreated
        viewModel = ViewModelProvider(this)[ShoppingViewModel::class.java]

        // Get the list ID from the Intent extras, or create a default list if none is provided
        initializeListId()

        // RecyclerView = allows to display a list of items
        setupRecyclerView()

        // For automatic refresh of the UI when the data changes (LiveData)
        setupObservers()

        // The plus button to add a new item
        setupFab()
    }

    /** Configures the Toolbar as the ActionBar and applies window insets only to it */
    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Grocery Manager"
    }

    /** Initializes the current list ID or a default if none is provided */
    private fun initializeListId() {
        // intent = the data sent to the activity when it starts
        val listId = intent.getIntExtra("LIST_ID", -1)
        if (listId == -1) {
            // Force the creation of a default list if none is provided with runBlocking
            val defaultId = runBlocking { viewModel.getOrCreateDefaultListId() }
            viewModel.setCurrentListId(defaultId)
        } else {
            viewModel.setCurrentListId(listId)
        }
    }

    /** Sets up the RecyclerView with a LinearLayoutManager and the ManualShoppingAdapter */
    private fun setupRecyclerView() {
        textViewEmpty = findViewById(R.id.textViewEmpty)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        manualAdapter = ManualShoppingAdapter(
            onItemClick = { item -> showEditDialog(item) },
            onCheckChange = { item, isChecked ->
                viewModel.updateItem(item.copy(isChecked = isChecked))
            },
            onQuantityChanged = { item, newQty ->
                viewModel.updateItem(item.copy(quantity = newQty))
            }
        )
        recyclerView.adapter = manualAdapter
    }

    /** Observes the LiveData from the ViewModel and updates the UI accordingly */
    private fun setupObservers() {
        // Observe the list of shopping items
        viewModel.allItems.observe(this) { items ->
            originalList = items
            val filtered = filterList(originalList, searchQuery)
            manualAdapter.setData(filtered)
            textViewEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }

        // Observe the sort mode to manage drag & drop
        viewModel.sortMode.observe(this) { mode ->
            if (mode == SortMode.CUSTOM) {
                attachDragSwipe()
            } else {
                itemTouchHelper?.attachToRecyclerView(null)
            }
        }
    }

    /** Sets up the Floating Action Button to open the "Add Item" dialog */
    private fun setupFab() {
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener { showAddItemDialog() }
    }

    /** Filters the provided list of items based on the search query (case-insensitive) */
    private fun filterList(items: List<ShoppingItem>, query: String): List<ShoppingItem> {
        if (query.isBlank()) return items
        val lower = query.lowercase()
        return items.filter { it.name.lowercase().contains(lower) }
    }

    /** Attaches drag & drop and swipe-to-delete functionality to the RecyclerView */
    private fun attachDragSwipe() {
        val callback = DragSwipeCallback(
            adapter = manualAdapter,
            onSwiped = { removedItem ->
                viewModel.deleteItem(removedItem)
                Snackbar.make(recyclerView, "Deleted: ${removedItem.name}", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        viewModel.addItem(removedItem.name, removedItem.quantity, removedItem.unitType)
                    }
                    .show()
            },
            onReorderDone = {
                // Update sortIndex for each item after reordering
                for ((index, item) in manualAdapter.items.withIndex()) {
                    viewModel.updateItem(item.copy(sortIndex = index))
                }
            }
        )
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
    }

    /** Inflates the menu and sets up the SearchView listener */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty()
                val filtered = filterList(originalList, searchQuery)
                manualAdapter.setData(filtered)
                textViewEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    /** Handles sort options selected from the menu */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sort_manual -> {
                viewModel.setSortMode(SortMode.CUSTOM)
                true
            }
            R.id.menu_sort_date -> {
                viewModel.setSortMode(SortMode.DATE)
                true
            }
            R.id.menu_sort_quantity -> {
                viewModel.setSortMode(SortMode.QUANTITY)
                true
            }
            R.id.menu_sort_picked -> {
                viewModel.setSortMode(SortMode.CHECKED)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Opens a dialog for adding a new shopping item */
    private fun showAddItemDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_item, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etItemName)
        val etQuantity = dialogView.findViewById<TextInputEditText>(R.id.etQuantity)
        val spinnerUnit = dialogView.findViewById<Spinner>(R.id.spinnerUnit)

        // Set up spinner with unit types from resources
        ArrayAdapter.createFromResource(
            this,
            R.array.unit_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerUnit.adapter = adapter
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.title_add_dialog))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.msg_add)) { _, _ ->
                val name = etName.text?.toString().orEmpty()
                val qty = etQuantity.text?.toString()?.toDoubleOrNull() ?: 0.0
                val unit = spinnerUnit.selectedItem.toString()
                viewModel.addItem(name, qty, unit)
            }
            .setNegativeButton(getString(R.string.msg_cancel), null)
            .show()
    }

    /** Opens a dialog for editing an existing shopping item */
    private fun showEditDialog(item: ShoppingItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_item, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etItemName)
        val etQuantity = dialogView.findViewById<TextInputEditText>(R.id.etQuantity)
        val spinnerUnit = dialogView.findViewById<Spinner>(R.id.spinnerUnit)

        // Pre-populate dialog fields with the item's current data
        etName.setText(item.name)
        etQuantity.setText(item.quantity.toString())

        ArrayAdapter.createFromResource(
            this,
            R.array.unit_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerUnit.adapter = adapter
        }

        // In order to select the correct unit type (kg, L
        val idx = resources.getStringArray(R.array.unit_types).indexOf(item.unitType)
        if (idx >= 0) spinnerUnit.setSelection(idx)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Item")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text?.toString().orEmpty()
                val newQty = etQuantity.text?.toString()?.toDoubleOrNull() ?: 0.0
                val newUnit = spinnerUnit.selectedItem.toString()
                viewModel.updateItem(item.copy(name = newName, quantity = newQty, unitType = newUnit))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
