package com.example.frontend.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend.R
import com.example.frontend.data.model.ShoppingList
import com.example.frontend.ui.adapters.ShoppingListAdapter
import com.example.frontend.viewmodel.ShoppingViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * ShoppingListActivity displays a list of shopping lists.
 * The user can add a new list, delete an existing list,
 * or tap on a list to open the MainActivity with the selected list.
 */
class ShoppingListActivity : AppCompatActivity() {

    private lateinit var viewModel: ShoppingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping_lists)

        setupToolbar()

        // RecyclerView = allows to display a list of items (list of shopping lists).
        setupRecyclerView()

        // The plus button to add a new item.
        setupFab()

        // Initialize the ViewModel and observe shopping lists.
        viewModel = ViewModelProvider(this)[ShoppingViewModel::class.java]
        viewModel.allLists.observe(this) { lists ->
            // Update the adapter's list when data changes.
            shoppingListAdapter.submitList(lists)
        }
    }

    private val shoppingListAdapter: ShoppingListAdapter by lazy {
        ShoppingListAdapter(
            onClick = { list ->
                // Open MainActivity with the selected list ID.
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("LIST_ID", list.id)
                startActivity(intent)
            },
            onDeleteList = { list ->
                // Confirm deletion before removing the list.
                confirmAndDeleteList(list)
            }
        )
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarList)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "My Grocery Lists"
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewLists)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = shoppingListAdapter
    }

    /** When the user clicks on the plus button: display a dialog for adding a new shopping list. */
    private fun setupFab() {
        val fabAddList = findViewById<FloatingActionButton>(R.id.fabAddList)
        fabAddList.setOnClickListener { showAddListDialog() }
    }

    private fun showAddListDialog() {
        val input = EditText(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("New Shopping List")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    ViewModelProvider(this)[ShoppingViewModel::class.java].addList(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Displays a confirmation dialog before deleting a shopping list. */
    private fun confirmAndDeleteList(list: ShoppingList) {
        ViewModelProvider(this)[ShoppingViewModel::class.java].allLists.observe(this) { lists ->
            if (lists.size <= 1) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Cannot delete")
                    .setMessage("You must keep at least one list.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete this list?")
                    .setMessage("Are you sure you want to delete \"${list.name}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        ViewModelProvider(this)[ShoppingViewModel::class.java].deleteList(list)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}
