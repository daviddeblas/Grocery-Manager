package com.example.frontend.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend.R
import com.example.frontend.api.APIClient
import com.example.frontend.api.model.LoginRequest
import com.example.frontend.api.model.SignupRequest
import com.example.frontend.data.model.ShoppingItem
import com.example.frontend.data.model.SortMode
import com.example.frontend.services.SyncScheduler
import com.example.frontend.ui.adapters.ManualShoppingAdapter
import com.example.frontend.utils.DragSwipeCallback
import com.example.frontend.utils.KeyboardUtils
import com.example.frontend.utils.SessionManager
import com.example.frontend.utils.UnitHelper
import com.example.frontend.viewmodel.ShoppingViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    // ViewModel and UI components
    private lateinit var viewModel: ShoppingViewModel
    private lateinit var textViewEmpty: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var manualAdapter: ManualShoppingAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    private lateinit var keyboardUtils: KeyboardUtils

    // Data variables
    private var originalList: List<ShoppingItem> = emptyList()
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up the title and ensure the toolbar is below the status bar !
        setupToolbar()

        // Ajoutez un bouton pour tester l'authentification (optionnel)
        findViewById<Button>(R.id.btnTestAuth)?.setOnClickListener {
            testAuthentication()
        }

        // It allows to keep the data if the activity is destroyed or recreated
        viewModel = ViewModelProvider(this)[ShoppingViewModel::class.java]

        // Get the list ID from the Intent extras, or create a default list if none is provided
        initializeListId()

        // RecyclerView = allows to display a list of items
        setupRecyclerView()

        // Setup buttons for checking/unchecking all items
        setupCheckAllButtons()

        // For automatic refresh of the UI when the data changes (LiveData)
        setupObservers()

        // Setup the keyboard behavior for the activity
        setupKeyboardBehavior()
    }

    private fun testAuthentication() {
        lifecycleScope.launch {
            try {
                // Afficher un message de test
                Toast.makeText(this@MainActivity, "Test d'authentification...", Toast.LENGTH_SHORT).show()

                // 1. Tester l'inscription
                val signupResponse = try {
                    APIClient.authService.register(SignupRequest("testuser", "test@example.com", "password123"))
                } catch (e: Exception) {
                    Log.e("AUTH_TEST", "Erreur d'inscription: ${e.message}")
                    Toast.makeText(this@MainActivity, "Erreur d'inscription: ${e.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                Log.d("AUTH_TEST", "Réponse d'inscription: ${signupResponse.isSuccessful}")

                // 2. Tester la connexion
                val loginResponse = try {
                    APIClient.authService.login(LoginRequest("testuser", "password123"))
                } catch (e: Exception) {
                    Log.e("AUTH_TEST", "Erreur de connexion: ${e.message}")
                    Toast.makeText(this@MainActivity, "Erreur de connexion: ${e.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                if (loginResponse.isSuccessful) {
                    val jwtResponse = loginResponse.body()
                    Log.d("AUTH_TEST", "Token: ${jwtResponse?.token?.take(20)}...")

                    // Sauvegarder les tokens
                    SessionManager.saveTokens(jwtResponse?.token ?: "", jwtResponse?.refreshToken ?: "")

                    // 3. Tester un appel protégé
                    try {
                        val listsResponse = APIClient.shoppingListService.getAllLists()
                        Log.d("AUTH_TEST", "Listes récupérées: ${listsResponse.isSuccessful}")
                        Toast.makeText(this@MainActivity,
                            "Test réussi! Listes: ${listsResponse.body()?.size ?: 0}",
                            Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Log.e("AUTH_TEST", "Erreur d'accès aux listes: ${e.message}")
                        Toast.makeText(this@MainActivity,
                            "Erreur d'accès aux listes: ${e.message}",
                            Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e("AUTH_TEST", "Échec de connexion: ${loginResponse.code()}")
                    Toast.makeText(this@MainActivity,
                        "Échec de connexion: ${loginResponse.code()}",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("AUTH_TEST", "Erreur globale: ${e.message}")
                Toast.makeText(this@MainActivity, "Erreur globale: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupKeyboardBehavior() {
        keyboardUtils = KeyboardUtils(this, recyclerView)
        keyboardUtils.setup()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::keyboardUtils.isInitialized) {
            keyboardUtils.cleanup()
        }
    }


    /**
     * Hide the keyboard when clicked outside of an EditText element.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = android.graphics.Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Configures the Toolbar as the ActionBar and applies window insets only to it */
    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)
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

        val unitHelper = UnitHelper(this)

        manualAdapter = ManualShoppingAdapter(
            onItemClick = { item -> showEditDialog(item) },
            onCheckChange = { item, isChecked ->
                viewModel.updateItem(item.copy(isChecked = isChecked))
                // Déclencher la synchronisation après une mise à jour
                SyncScheduler.requestImmediateSync(this)
            },
            onQuantityChanged = { item, newQty ->
                viewModel.updateItem(item.copy(quantity = newQty))
                // Déclencher la synchronisation après une mise à jour
                SyncScheduler.requestImmediateSync(this)
            },
            onNameChanged = { item, newName ->
                viewModel.updateItem(item.copy(name = newName))
                // Déclencher la synchronisation après une mise à jour
                SyncScheduler.requestImmediateSync(this)
            },
            onUnitTypeChanged = { item, newUnitType ->
                val isNewUnitType = unitHelper.normalizeUnit(newUnitType) == UnitHelper.UNIT_TYPE_UNIT
                val wasUnitType = unitHelper.normalizeUnit(item.unitType) == UnitHelper.UNIT_TYPE_UNIT

                val newQty = if (isNewUnitType && !wasUnitType) {
                    Math.round(item.quantity).toDouble()
                } else {
                    item.quantity
                }
                viewModel.updateItem(item.copy(unitType = newUnitType, quantity = newQty))
                // Déclencher la synchronisation après une mise à jour
                SyncScheduler.requestImmediateSync(this)
            },
            onAddNewItem = { name ->
                val defaultUnit = unitHelper.getLocalizedUnitName(UnitHelper.UNIT_TYPE_UNIT)
                viewModel.addItem(name, 1.0, defaultUnit)
                // Déclencher la synchronisation après l'ajout
                SyncScheduler.requestImmediateSync(this)
            }
        )
        recyclerView.adapter = manualAdapter
    }

    private fun setupCheckAllButtons() {
        val btnCheckAll = findViewById<Button>(R.id.btnCheckAll)

        btnCheckAll.setOnClickListener {
            val hasUncheckedItems = originalList.any { !it.isChecked }

            if (hasUncheckedItems) {
                // Check all elements
                for (item in originalList) {
                    if (!item.isChecked) {
                        viewModel.updateItem(item.copy(isChecked = true))
                    }
                }
                btnCheckAll.setText(R.string.uncheck_all)
            } else {
                // Uncheck all elements
                for (item in originalList) {
                    if (item.isChecked) {
                        viewModel.updateItem(item.copy(isChecked = false))
                    }
                }
                btnCheckAll.setText(R.string.check_all)
            }

            // Manually refresh the adapter after update
            manualAdapter.setData(originalList)
        }
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
                Snackbar.make(recyclerView, getString(R.string.deleted_item, removedItem.name), Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) {
                        viewModel.addItem(removedItem.name, removedItem.quantity, removedItem.unitType)
                        // Sync après le rétablissement
                        SyncScheduler.requestImmediateSync(this)
                    }
                    .show()
                // Sync après la suppression
                SyncScheduler.requestImmediateSync(this)
            },
            onReorderDone = {
                // Update sortIndex for each item after reordering
                for ((index, item) in manualAdapter.items.withIndex()) {
                    viewModel.updateItem(item.copy(sortIndex = index))
                }
                // Sync après réorganisation
                SyncScheduler.requestImmediateSync(this)
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Opens a dialog for adding a new shopping item */
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

        // In order to select the correct unit type (kg, L)
        val idx = resources.getStringArray(R.array.unit_types).indexOf(item.unitType)
        if (idx >= 0) spinnerUnit.setSelection(idx)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_item)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = etName.text?.toString().orEmpty()
                val newQty = etQuantity.text?.toString()?.toDoubleOrNull() ?: 0.0
                val newUnit = spinnerUnit.selectedItem.toString()
                viewModel.updateItem(item.copy(name = newName, quantity = newQty, unitType = newUnit))
                // Trigger a sync after modification
                SyncScheduler.requestImmediateSync(this)
            }
            .setNegativeButton(R.string.msg_cancel, null)
            .create()

        // Configure the window to adjust when the keyboard appears
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        // Add event handlers to ensure fields are not obfuscated
        etName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
            }
        }

        etQuantity.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
            }
        }

        dialog.show()
    }
}