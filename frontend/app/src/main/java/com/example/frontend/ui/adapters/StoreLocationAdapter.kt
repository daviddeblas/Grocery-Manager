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
import com.example.frontend.data.model.StoreLocation

/**
 * StoreLocationAdapter displays each store location in a card layout.
 */
class StoreLocationAdapter(
    private val onDelete: (StoreLocation) -> Unit
) : ListAdapter<StoreLocation, StoreLocationAdapter.StoreViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_store_location, parent, false)
        return StoreViewHolder(view, onDelete)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StoreViewHolder(
        itemView: View,
        private val onDelete: (StoreLocation) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvStoreName: TextView = itemView.findViewById(R.id.tvStoreName)
        private val tvStoreAddress: TextView = itemView.findViewById(R.id.tvStoreAddress)
        private val btnDeleteStore: ImageButton = itemView.findViewById(R.id.btnDeleteStore)

        fun bind(store: StoreLocation) {
            tvStoreName.text = store.name
            tvStoreAddress.text = store.address

            btnDeleteStore.setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                    .setTitle(R.string.delete_store)
                    .setMessage(itemView.context.getString(R.string.delete_store_confirmation, store.name))
                    .setPositiveButton(R.string.delete_confirm) { _, _ ->
                        onDelete(store)
                    }
                    .setNegativeButton(R.string.msg_cancel, null)
                    .show()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<StoreLocation>() {
        override fun areItemsTheSame(oldItem: StoreLocation, newItem: StoreLocation): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: StoreLocation, newItem: StoreLocation): Boolean {
            return oldItem == newItem
        }
    }
}
