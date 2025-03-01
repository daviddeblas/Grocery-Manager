package com.example.frontend.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend.R
import com.example.frontend.data.model.ShoppingItem
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ManualShoppingAdapter is a RecyclerView adapter for displaying shopping items.
 * It supports item click (and drag and drop), checkbox toggle, and quantity adjustment
 * via increment/decrement buttons.
 */
class ManualShoppingAdapter(
    private val onItemClick: (ShoppingItem) -> Unit,
    private val onCheckChange: (ShoppingItem, Boolean) -> Unit,
    private val onQuantityChanged: (ShoppingItem, Double) -> Unit,
    private val onNameChanged: (ShoppingItem, String) -> Unit,
    private val onUnitTypeChanged: (ShoppingItem, String) -> Unit,
    private val onAddNewItem: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_ADD = 1
    }

    val items = mutableListOf<ShoppingItem>()
    private val unitTypes = arrayOf("pcs", "kg", "L")

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) TYPE_ADD else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.shopping_item, parent, false)
                ItemViewHolder(view, onItemClick, onCheckChange, onQuantityChanged, onNameChanged, onUnitTypeChanged, unitTypes)
            }
            TYPE_ADD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.add_item_view, parent, false)
                AddItemViewHolder(view, onAddNewItem)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ItemViewHolder -> holder.bind(items[position])
            is AddItemViewHolder -> holder.bind()
        }
    }

    override fun getItemCount(): Int = items.size + 1

    /**
     * Used to change the order of the data when sorted !
     */
    fun setData(newItems: List<ShoppingItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /**
     * Called when an item is moved via drag & drop.
     */
    fun moveItemInList(fromPos: Int, toPos: Int) {
        if (fromPos < items.size && toPos < items.size) {
            val item = items.removeAt(fromPos)
            items.add(toPos, item)
            notifyItemMoved(fromPos, toPos)
        }
    }

    /**
     * Removes the item at the given position and returns it.
     */
    fun removeItemAt(pos: Int): ShoppingItem? {
        return if (pos < items.size) {
            val removed = items.removeAt(pos)
            notifyItemRemoved(pos)
            removed
        } else null
    }

    /** Binds item data to the UI and sets up click listeners. */
    class ItemViewHolder(
        itemView: View,
        private val onItemClick: (ShoppingItem) -> Unit,
        private val onCheckChange: (ShoppingItem, Boolean) -> Unit,
        private val onQuantityChanged: (ShoppingItem, Double) -> Unit,
        private val onNameChanged: (ShoppingItem, String) -> Unit,
        private val onUnitTypeChanged: (ShoppingItem, String) -> Unit,
        private val unitTypes: Array<String>
    ) : RecyclerView.ViewHolder(itemView) {

        private val cbDone: CheckBox = itemView.findViewById(R.id.cbDone)
        private val etName: EditText = itemView.findViewById(R.id.etName)
        private val tvQuantityLabel: TextView = itemView.findViewById(R.id.tvQuantityLabel)
        private val etQuantityValue: EditText = itemView.findViewById(R.id.etQuantityValue)
        private val tvUnitType: TextView = itemView.findViewById(R.id.tvUnitType)
        private val btnIncrement: ImageButton = itemView.findViewById(R.id.btnIncrement)
        private val btnDecrement: ImageButton = itemView.findViewById(R.id.btnDecrement)

        // To avoid recursive callbacks during editing
        private var isUpdating = false
        private var currentItem: ShoppingItem? = null

        fun bind(item: ShoppingItem) {
            currentItem = item

            // Editable name configuration
            isUpdating = true
            etName.setText(item.name)
            isUpdating = false

            // Listener configuration for name
            etName.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveNameChanges()
                    etName.clearFocus()
                    hideKeyboard(etName.context, etName)
                    true
                } else false
            }

            etName.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    saveNameChanges()
                }
            }

            tvQuantityLabel.text = "Quantity:"

            val decimals = if (item.unitType == "pcs") 0 else 2
            // Round the quantity value.
            val big = BigDecimal(item.quantity).setScale(decimals, RoundingMode.HALF_UP)
            val qtyStr = big.toPlainString()

            isUpdating = true
            // Display the quantity with its unit.
            etQuantityValue.setText(qtyStr)
            isUpdating = false

            // Adjust margins based on unit type
            val layoutParams = etQuantityValue.layoutParams as? ViewGroup.MarginLayoutParams
            if (layoutParams != null) {
                if (item.unitType == "pcs") {
                    // For pcs: reduce the left margin to bring the text closer to the label
                    layoutParams.marginStart = -60
                } else {
                    // For kg, L: restore the normal margin
                    layoutParams.marginStart = 2
                }
                etQuantityValue.layoutParams = layoutParams
            }

            tvUnitType.text = " ${item.unitType}"

            // Added a click listener on the unit type
            tvUnitType.setOnClickListener {
                // Add a feedback animation
                tvUnitType.animate()
                    .alpha(0.5f)
                    .setDuration(100)
                    .withEndAction {
                        cycleUnitType(item)
                        tvUnitType.animate().alpha(1.0f).setDuration(100)
                    }
            }

            // Configuring the listener for quantity
            etQuantityValue.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveQuantityChanges(decimals)
                    etQuantityValue.clearFocus()
                    hideKeyboard(etQuantityValue.context, etQuantityValue)
                    true
                } else false
            }

            etQuantityValue.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    saveQuantityChanges(decimals)
                }
            }

            // Configure the state of the checkbox and its listener
            cbDone.setOnCheckedChangeListener(null)
            cbDone.isChecked = item.isChecked

            // When item is checked -> strike through text and fade it
            if (item.isChecked) {
                etName.paintFlags = etName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                etName.alpha = 0.6f  // Make text appear faded
            } else {
                etName.paintFlags = etName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                etName.alpha = 1.0f  // Normal appearance
            }

            cbDone.setOnCheckedChangeListener { _, isChecked ->
                onCheckChange(item, isChecked)
            }

            itemView.setOnClickListener { onItemClick(item) }

            // Configure decrement button.
            btnDecrement.setOnClickListener {
                val step = when (item.unitType) {
                    "pcs" -> 1.0
                    "kg", "L" -> 0.1
                    else -> 1.0
                }
                val raw = (item.quantity - step).coerceAtLeast(0.0)
                val rounded = BigDecimal(raw).setScale(decimals, RoundingMode.HALF_UP)
                onQuantityChanged(item, rounded.toDouble())
            }

            // Configure increment button
            btnIncrement.setOnClickListener {
                val step = when (item.unitType) {
                    "pcs" -> 1.0
                    "kg", "L" -> 0.1
                    else -> 1.0
                }
                val raw = item.quantity + step
                val rounded = BigDecimal(raw).setScale(decimals, RoundingMode.HALF_UP)
                onQuantityChanged(item, rounded.toDouble())
            }
        }

        private fun saveNameChanges() {
            if (!isUpdating && currentItem != null) {
                var newName = etName.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentItem?.name) {
                    // Transform the first letter into uppercase
                    if (newName.length > 1) {
                        newName = newName.substring(0, 1).uppercase() + newName.substring(1)
                    } else {
                        newName = newName.uppercase()
                    }
                    onNameChanged(currentItem!!, newName)
                }
            }
        }

        private fun saveQuantityChanges(decimals: Int) {
            if (!isUpdating && currentItem != null) {
                val newQtyText = etQuantityValue.text.toString()
                val newQty = newQtyText.toDoubleOrNull() ?: return

                if (newQty != currentItem?.quantity) {
                    val rounded = BigDecimal(newQty).setScale(decimals, RoundingMode.HALF_UP).toDouble()
                    onQuantityChanged(currentItem!!, rounded)
                }
            }
        }

        // In order to switch directly from pcs -> kg -> L
        private fun cycleUnitType(item: ShoppingItem) {
            val currentIndex = unitTypes.indexOf(item.unitType)
            val nextIndex = (currentIndex + 1) % unitTypes.size
            // Get the new unit type
            val newUnitType = unitTypes[nextIndex]
            onUnitTypeChanged(item, newUnitType)
        }

        private fun hideKeyboard(context: Context, view: View) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    /** ViewHolder in order to add a new item in the list */
    class AddItemViewHolder(
        itemView: View,
        private val onAddNewItem: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val etNewItem: EditText = itemView.findViewById(R.id.etNewItem)
        private val imgAddItem: ImageView = itemView.findViewById(R.id.imgAddItem)

        fun bind() {
            // Configure the field to transform the first letter into uppercase
            etNewItem.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

            etNewItem.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    addNewItem()
                    true
                } else false
            }

            imgAddItem.setOnClickListener {
                addNewItem()
            }

            itemView.setOnClickListener {
                etNewItem.requestFocus()
                showKeyboard(etNewItem.context, etNewItem)
            }
        }

        private fun addNewItem() {
            var itemName = etNewItem.text.toString().trim()
            if (itemName.isNotEmpty()) {
                if (itemName.length > 1) {
                    itemName = itemName.substring(0, 1).uppercase() + itemName.substring(1)
                } else {
                    itemName = itemName.uppercase()
                }

                onAddNewItem(itemName)
                etNewItem.text.clear()
                etNewItem.clearFocus()
                hideKeyboard(etNewItem.context, etNewItem)
            } else {
                // If the field is empty, simply give focus
                etNewItem.requestFocus()
                showKeyboard(etNewItem.context, etNewItem)
            }
        }

        private fun showKeyboard(context: Context, view: View) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }

        private fun hideKeyboard(context: Context, view: View) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}