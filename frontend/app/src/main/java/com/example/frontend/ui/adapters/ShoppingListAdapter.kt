package com.example.frontend.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend.R
import com.example.frontend.data.model.ShoppingList

/**
 * ShoppingListAdapter displays each shopping list in a card layout.
 * */
class ShoppingListAdapter(
    private val onClick: (ShoppingList) -> Unit,
    private val onDeleteList: (ShoppingList) -> Unit
) : ListAdapter<ShoppingList, ShoppingListAdapter.ListViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shopping_list, parent, false)
        return ListViewHolder(view, onClick, onDeleteList)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** ListViewHolder holds the views for a single shopping list item. */
    class ListViewHolder(
        itemView: View,
        private val onClick: (ShoppingList) -> Unit,
        private val onDeleteList: (ShoppingList) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvListName: TextView = itemView.findViewById(R.id.tvListName)
        private val btnDeleteList: ImageButton = itemView.findViewById(R.id.btnDeleteList)

        fun bind(list: ShoppingList) {
            tvListName.text = list.name
            itemView.setOnClickListener { onClick(list) }
            btnDeleteList.setOnClickListener { onDeleteList(list) }
        }
    }

    /**
     * DiffUtil callback for optimizing list updates.
     */
    class DiffCallback : DiffUtil.ItemCallback<ShoppingList>() {
        override fun areItemsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean =
            oldItem == newItem
    }
}
