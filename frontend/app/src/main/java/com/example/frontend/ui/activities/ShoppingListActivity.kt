package com.example.frontend.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend.R
import com.example.frontend.data.model.ShoppingList
import com.example.frontend.data.model.StoreLocation
import com.example.frontend.ui.adapters.ShoppingListAdapter
import com.example.frontend.ui.adapters.StoreLocationAdapter
import com.example.frontend.utils.GeofenceHelper
import com.example.frontend.viewmodel.ShoppingViewModel
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.UUID
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.frontend.services.SyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ShoppingListActivity manages shopping lists and geofenced store locations.
 *
 * Features:
 * - Manage shopping lists (create, delete, open in MainActivity).
 * - Manage store locations (add via Google Places, remove).
 * - Automatically geofence added stores and send notifications on entry/exit.
 */
class ShoppingListActivity : AppCompatActivity() {

    private val viewModel: ShoppingViewModel by lazy { ViewModelProvider(this)[ShoppingViewModel::class.java] }

    // Geofence helper to add or remove geofences
    private lateinit var geofenceHelper: GeofenceHelper

    companion object {
        private const val TAG = "ShoppingListActivity"

        // Request codes for permissions
        private const val REQUEST_CODE_NOTIF = 200
        private const val REQUEST_CODE_FINE_LOCATION = 201
        private const val REQUEST_CODE_BACKGROUND_LOCATION = 202
    }

    /**
     * If the user selects a place, we create a store and add a geofence (circle around the area)
     * with a unique ID.
     */
    private val autocompleteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            RESULT_OK -> {
                result.data?.let { data ->
                    val place = Autocomplete.getPlaceFromIntent(data)
                    val location = place.location
                    if (location != null) {
                        val storeName = place.name ?: ""
                        val storeAddress = place.address ?: ""
                        if (storeName.isNotBlank()) {
                            // Check duplicates in local DB
                            val existingStore = viewModel.allStores.value?.firstOrNull { st ->
                                // We compare latitude/longitude (with a small margin of error)
                                st.name.equals(storeName, ignoreCase = true) ||
                                        (Math.abs(st.latitude - location.latitude) < 0.0001
                                                && Math.abs(st.longitude - location.longitude) < 0.0001)
                            }

                            if (existingStore != null) {
                                Toast.makeText(this, getString(R.string.store_already_exists, storeName), Toast.LENGTH_SHORT).show()
                                return@let
                            }

                            // Create a new store
                            val geofenceUniqueId = UUID.randomUUID().toString()
                            val store = StoreLocation(
                                name = storeName,
                                address = storeAddress,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                geofenceId = geofenceUniqueId
                            )
                            Log.d(TAG, "Store added: $store")

                            // Insert the store in DB
                            viewModel.addStore(store)

                            // Add geofence
                            geofenceHelper.addGeofence(
                                store.latitude,
                                store.longitude,
                                200f,
                                geofenceUniqueId
                            )

                            // IMPORTANT: Trigger immediate sync after adding the store
                            SyncScheduler.requestImmediateSync(this)

                            Toast.makeText(this, getString(R.string.store_added, storeName), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            AutocompleteActivity.RESULT_ERROR -> {
                result.data?.let {
                    val status = Autocomplete.getStatusFromIntent(it)
                    Log.e(TAG, "Autocomplete error: ${status.statusMessage}")
                }
            }
            RESULT_CANCELED -> {
                Log.d(TAG, "Autocomplete canceled.")
            }
        }
    }

    // Adapters
    private val shoppingListAdapter: ShoppingListAdapter by lazy {
        ShoppingListAdapter(
            onClick = { list ->
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("LIST_ID", list.id)
                startActivity(intent)
            },
            onDeleteList = { list ->
                confirmAndDeleteList(list)
            }
        )
    }

    // When user deletes a store, we remove the geofence first, then remove from DB
    private val storeLocationAdapter: StoreLocationAdapter by lazy {
        StoreLocationAdapter { store ->
            geofenceHelper.removeGeofence(store.geofenceId)
            viewModel.deleteStore(store)
        }
    }

    /**
     * Initializes the activity, sets up UI components, and requests necessary permissions.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping_lists)

        geofenceHelper = GeofenceHelper(this)

        // Request all permissions in a chained manner (notification, location)
        requestAllPermissionsChained()

        // Initialize Google Places API if needed
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        setupToolbar()

        // RecyclerView allows to display a list of items (list of shopping lists)
        setupRecyclerView()

        // The plus button to add a new item
        setupFab()

        // The store button to add a new store
        setupFabManageStores()
        setupStoresRecyclerView()

        // Observe the shopping lists
        viewModel.allLists.observe(this) { lists ->
            // Update the adapter's list when data changes
            shoppingListAdapter.submitList(lists)
        }

        // Configure swipe to refresh
        setupSwipeRefresh()

        observeSyncState()
    }

    /**
     * Requests all required permissions in a chained sequence.
     */
    private fun requestAllPermissionsChained() {
        requestNotifPermission {
            requestFineLocation {
                requestBackgroundLocation()
            }
        }
    }

    /**
     * Requests notification permission on Android 13+ or calls completion handler on older versions.
     */
    private fun requestNotifPermission(onComplete: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIF
                )
            } else {
                onComplete()
            }
        } else {
            // On older Android versions (< 13), no runtime permission for notifications
            onComplete()
        }
    }

    /**
     * Requests fine location permission.
     *
     * @param onComplete Function to call after permission is handled
     */
    private fun requestFineLocation(onComplete: () -> Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_FINE_LOCATION
            )
        } else {
            onComplete()
        }
    }

    /**
     * Requests background location permission on Android 10+
     */
    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val bgGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!bgGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQUEST_CODE_BACKGROUND_LOCATION
            )
        }
    }

    /**
     * Handles permission request results and continues permission chain if needed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isEmpty()) return // No result

        when (requestCode) {
            REQUEST_CODE_NOTIF -> {
                // Whether granted or denied, we now request fine location
                requestFineLocation {
                    // Then background loc
                    requestBackgroundLocation()
                }
            }
            REQUEST_CODE_FINE_LOCATION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Now request background
                    requestBackgroundLocation()
                } else {
                    Toast.makeText(this, "Fine location denied.", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_CODE_BACKGROUND_LOCATION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Background location granted.")
                } else {
                    Toast.makeText(this, "Background location denied.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarList)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.grocery_lists_title)
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewLists)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = shoppingListAdapter
    }

    /**
     * Sets up the FAB to add a new shopping list.
     */
    private fun setupFab() {
        val fabAddList = findViewById<FloatingActionButton>(R.id.fabAddList)
        fabAddList.setOnClickListener { showAddListDialog() }
    }

    /**
     * Shows a dialog to add a new shopping list.
     */
    private fun showAddListDialog() {
        val input = EditText(this)

        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_list_title)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                var name = input.text.toString()
                if (name.isNotBlank()) {
                    if (name.length > 1) {
                        name = name.substring(0, 1).uppercase() + name.substring(1)
                    } else {
                        name = name.uppercase()
                    }
                    viewModel.addList(name)
                    // Trigger a sync after adding
                    SyncScheduler.requestImmediateSync(this)
                }
            }
            .setNegativeButton(R.string.msg_cancel, null)
            .show()
    }

    /**
     * Shows a confirmation dialog for deleting a shopping list.
     *
     * @param list The shopping list to delete
     */
    private fun confirmAndDeleteList(list: ShoppingList) {
        val currentLists = viewModel.allLists.value ?: emptyList()

        if (currentLists.size <= 1) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cannot_delete_list)
                .setMessage(getString(R.string.must_keep_one_list))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_list)
                .setMessage(getString(R.string.delete_list_confirm_message, list.name))
                .setPositiveButton(R.string.delete_confirm) { _, _ ->
                    val finalCount = viewModel.allLists.value?.size ?: 0
                    if (finalCount > 1) {
                        viewModel.deleteList(list)

                        // Trigger immediate sync after deletion
                        SyncScheduler.requestImmediateSync(this)
                    } else {
                        Toast.makeText(this, R.string.last_list_error, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.msg_cancel, null)
                .show()
        }
    }

    /**
     * Sets up the FAB to add a new store location.
     */
    private fun setupFabManageStores() {
        val fabManageStores = findViewById<FloatingActionButton>(R.id.fabManageStores)
        fabManageStores.setOnClickListener { launchPlaceAutocomplete() }
    }

    /**
     * Launches the Google Places Autocomplete activity to select a store location.
     */
    private fun launchPlaceAutocomplete() {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LOCATION
        )
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
            .build(this)
        autocompleteLauncher.launch(intent)
    }

    /**
     * Sets up the RecyclerView for store locations.
     */
    private fun setupStoresRecyclerView() {
        val tvStoresTitle = findViewById<TextView>(R.id.tvStoresTitle)
        val recyclerViewStores = findViewById<RecyclerView>(R.id.recyclerViewStores)
        recyclerViewStores.layoutManager = LinearLayoutManager(this)
        recyclerViewStores.adapter = storeLocationAdapter

        // Observe the store list
        viewModel.allStores.observe(this) { stores ->
            storeLocationAdapter.submitList(stores)
            val visibility = if (stores.isNotEmpty()) View.VISIBLE else View.GONE
            tvStoresTitle.visibility = visibility
            recyclerViewStores.visibility = visibility
        }
    }

    /**
     * Sets up the swipe-to-refresh functionality.
     */
    private fun setupSwipeRefresh() {
        val swipeRefreshLayout = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)

        // Define the loading spinner colors
        swipeRefreshLayout.setColorSchemeResources(
            R.color.purple_500,
            R.color.teal_200,
            R.color.purple_700
        )

        // Configure the refresh listener
        swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                try {
                    // Request immediate synchronization
                    SyncScheduler.requestImmediateSync(this@ShoppingListActivity)

                    // Wait a moment to give the impression that something is happening
                    delay(1000)
                } finally {
                    // Always hide the refresh indicator
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    /**
     * Observes synchronization state and shows appropriate messages.
     */
    private fun observeSyncState() {
        // Display a toast or notification when synchronization is complete
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(SyncScheduler.SYNC_WORK_NAME)
            .observe(this) { workInfos ->
                workInfos?.firstOrNull()?.let { workInfo ->
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            Toast.makeText(this, R.string.sync_success, Toast.LENGTH_SHORT).show()
                        }
                        WorkInfo.State.FAILED -> {
                            Toast.makeText(this, R.string.sync_failed, Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            // Ignore other states
                        }
                    }
                }
            }
    }
}