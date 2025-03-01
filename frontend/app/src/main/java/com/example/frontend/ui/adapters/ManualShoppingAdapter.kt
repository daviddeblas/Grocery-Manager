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
import com.example.frontend.utils.UnitHelper
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
    private lateinit var unitHelper: UnitHelper

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) TYPE_ADD else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (!::unitHelper.isInitialized) {
            unitHelper = UnitHelper(parent.context)
        }

        return when (viewType) {
            TYPE_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.shopping_item, parent, false)
                ItemViewHolder(view, onItemClick, onCheckChange, onQuantityChanged, onNameChanged, onUnitTypeChanged, unitHelper)
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
        private val unitHelper: UnitHelper
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

        // The actual item from the database
        private var originalItem: ShoppingItem? = null

        // Tracks all pending changes during edition
        private var workingItem: ShoppingItem? = null

        fun bind(item: ShoppingItem) {
            // Store the original item and create a working copy
            originalItem = item
            workingItem = item.copy()

            // Editable name configuration
            isUpdating = true
            etName.setText(workingItem?.name)
            isUpdating = false

            // Listen for name changes in real-time
            etName.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (!isUpdating && s != null) {
                        val newName = s.toString().trim()
                        workingItem = workingItem?.copy(name = newName)
                    }
                }
            })

            etName.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitNameChange()
                    etName.clearFocus()
                    hideKeyboard(etName.context, etName)
                    true
                } else false
            }

            etName.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    commitNameChange()
                }
            }

            // Quantity and unit type setup
            tvQuantityLabel.text = itemView.context.getString(R.string.quantity_label)

            val localizedUnitType = unitHelper.translateUnit(item.unitType)
            val isUnitItem = unitHelper.normalizeUnit(item.unitType) == UnitHelper.UNIT_TYPE_UNIT
            val decimals = if (isUnitItem) 0 else 2

            val big = BigDecimal(item.quantity).setScale(decimals, RoundingMode.HALF_UP)
            val qtyStr = big.toPlainString()

            isUpdating = true
            etQuantityValue.setText(qtyStr)
            isUpdating = false

            // Adjust margins based on unit type
            val layoutParams = etQuantityValue.layoutParams as? ViewGroup.MarginLayoutParams
            if (layoutParams != null) {
                layoutParams.marginStart = if (isUnitItem) -60 else 2
                etQuantityValue.layoutParams = layoutParams
            }

            tvUnitType.text = " $localizedUnitType"

            // Set up change listeners for quantity
            etQuantityValue.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitNameChange() // Save name if changed
                    saveQuantityChanges(decimals)
                    etQuantityValue.clearFocus()
                    hideKeyboard(etQuantityValue.context, etQuantityValue)
                    true
                } else false
            }

            etQuantityValue.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    commitNameChange() // Save name if changed
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
                commitNameChange() // Before each action, save the name
                onCheckChange(originalItem!!, isChecked)
            }

            itemView.setOnClickListener {
                commitNameChange()
                onItemClick(originalItem!!)
            }

            // Unit type cycling
            tvUnitType.setOnClickListener {
                commitNameChange()

                tvUnitType.animate()
                    .alpha(0.5f)
                    .setDuration(100)
                    .withEndAction {
                        val nextUnit = unitHelper.getNextUnit(originalItem!!.unitType)
                        onUnitTypeChanged(originalItem!!, nextUnit)
                        tvUnitType.animate().alpha(1.0f).setDuration(100)
                    }
            }

            // Configure decrement button
            btnDecrement.setOnClickListener {
                commitNameChange()

                val step = when (unitHelper.normalizeUnit(originalItem!!.unitType)) {
                    UnitHelper.UNIT_TYPE_UNIT -> 1.0
                    else -> 0.1
                }
                val raw = (originalItem!!.quantity - step).coerceAtLeast(0.0)
                val rounded = BigDecimal(raw).setScale(decimals, RoundingMode.HALF_UP)
                onQuantityChanged(originalItem!!, rounded.toDouble())
            }

            // Configure increment button
            btnIncrement.setOnClickListener {
                commitNameChange()

                val step = when (unitHelper.normalizeUnit(originalItem!!.unitType)) {
                    UnitHelper.UNIT_TYPE_UNIT -> 1.0
                    else -> 0.1
                }
                val raw = originalItem!!.quantity + step
                val rounded = BigDecimal(raw).setScale(decimals, RoundingMode.HALF_UP)
                onQuantityChanged(originalItem!!, rounded.toDouble())
            }
        }

        // Commits name changes before any other action
        private fun commitNameChange() {
            if (!isUpdating && workingItem != null && originalItem != null) {
                val newName = workingItem!!.name.trim()

                if (newName.isNotEmpty() && newName != originalItem!!.name) {
                    // Capitalize first letter
                    val formattedName = if (newName.length > 1) {
                        newName.substring(0, 1).uppercase() + newName.substring(1)
                    } else {
                        newName.uppercase()
                    }

                    // Update the database first
                    onNameChanged(originalItem!!, formattedName)

                    // Update model
                    originalItem = originalItem!!.copy(name = formattedName)
                    workingItem = workingItem!!.copy(name = formattedName)

                    // Update UI
                    isUpdating = true
                    etName.setText(formattedName)
                    isUpdating = false
                }
            }
        }

        private fun saveQuantityChanges(decimals: Int) {
            if (!isUpdating && originalItem != null) {
                val newQtyText = etQuantityValue.text.toString()
                val newQty = newQtyText.toDoubleOrNull() ?: return

                if (newQty != originalItem!!.quantity) {
                    val rounded = BigDecimal(newQty).setScale(decimals, RoundingMode.HALF_UP).toDouble()
                    onQuantityChanged(originalItem!!, rounded)
                }
            }
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