package com.example.frontend.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
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
    private val onQuantityChanged: (ShoppingItem, Double) -> Unit
) : RecyclerView.Adapter<ManualShoppingAdapter.ViewHolder>() {

    val items = mutableListOf<ShoppingItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.shopping_item, parent, false)
        return ViewHolder(view, onItemClick, onCheckChange, onQuantityChanged)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

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
        if (fromPos in items.indices && toPos in items.indices) {
            val item = items.removeAt(fromPos)
            items.add(toPos, item)
            notifyItemMoved(fromPos, toPos)
        }
    }

    /**
     * Removes the item at the given position and returns it.
     */
    fun removeItemAt(pos: Int): ShoppingItem? {
        return if (pos in items.indices) {
            val removed = items.removeAt(pos)
            notifyItemRemoved(pos)
            removed
        } else null
    }

    /** Binds item data to the UI and sets up click listeners. */
    class ViewHolder(
        itemView: View,
        private val onItemClick: (ShoppingItem) -> Unit,
        private val onCheckChange: (ShoppingItem, Boolean) -> Unit,
        private val onQuantityChanged: (ShoppingItem, Double) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val cbDone: CheckBox = itemView.findViewById(R.id.cbDone)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvQuantityLabel: TextView = itemView.findViewById(R.id.tvQuantityLabel)
        private val tvQuantityValue: TextView = itemView.findViewById(R.id.tvQuantityValue)
        private val btnIncrement: ImageButton = itemView.findViewById(R.id.btnIncrement)
        private val btnDecrement: ImageButton = itemView.findViewById(R.id.btnDecrement)

        fun bind(item: ShoppingItem) {
            tvName.text = item.name
            tvQuantityLabel.text = "Quantity : "

            val decimals = if (item.unitType == "pcs") 0 else 2

            // Round the quantity value.
            val big = BigDecimal(item.quantity).setScale(decimals, RoundingMode.HALF_UP)
            val qtyStr = big.toPlainString()

            // Display the quantity with its unit.
            tvQuantityValue.text = "$qtyStr ${item.unitType}"

            // Set the checkbox state and its listener.
            cbDone.setOnCheckedChangeListener(null)
            cbDone.isChecked = item.isChecked
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
    }
}
