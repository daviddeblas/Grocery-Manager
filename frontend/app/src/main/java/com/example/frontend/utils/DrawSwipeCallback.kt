package com.example.frontend.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend.R
import com.example.frontend.data.model.ShoppingItem
import com.example.frontend.ui.adapters.ManualShoppingAdapter

/**
 * DragSwipeCallback handles both drag & drop and swipe actions on a RecyclerView.
 *
 * @param adapter The adapter for the shopping items, which must implement methods to move or remove items.
 * @param onSwiped Callback invoked when an item is swiped (for deletion).
 * @param onReorderDone Callback invoked after the user has finished reordering items.
 */
class DragSwipeCallback(
    private val adapter: ManualShoppingAdapter,
    private val onSwiped: (ShoppingItem) -> Unit,
    private val onReorderDone: () -> Unit
) : ItemTouchHelper.SimpleCallback(
    // Enable dragging in up and down directions.
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    // Enable swiping to the left and right.
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {

    /** Called when an item is dragged and dropped. */
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // Do not allow moving add view (last view)
        if (viewHolder.bindingAdapterPosition == adapter.items.size ||
            target.bindingAdapterPosition == adapter.items.size) {
            return false
        }

        val fromPos = viewHolder.bindingAdapterPosition
        val toPos = target.bindingAdapterPosition

        // Prevent moving checked items or moving items to positions occupied by checked items
        if (fromPos < adapter.items.size && toPos < adapter.items.size) {
            val fromItem = adapter.items[fromPos]
            val toItem = adapter.items[toPos]
            if (fromItem.isChecked || toItem.isChecked) {
                return false
            }
        }

        adapter.moveItemInList(fromPos, toPos)
        return true
    }

    /** Removes the item from the adapter and triggers the onSwiped callback. */
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val pos = viewHolder.bindingAdapterPosition
        if (pos == adapter.items.size) {
            // Just refresh the element to cancel the swipe animation
            notifyItemChanged(pos)
            return
        }

        val removedItem = adapter.removeItemAt(pos) ?: return
        onSwiped(removedItem)
    }

    private fun notifyItemChanged(position: Int) {
        adapter.notifyItemChanged(position)
    }

    /**
     * Called when the drag operation is finished and the item is released.
     * It signals that reordering is complete and triggers an update for sort indexes.
     */
    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        // Notify that reordering is done so that sort indexes can be updated in the database.
        onReorderDone()
    }

    /** It draws a red rectangle behind the swiped item, along with a trash icon. */
    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        // Check that it is not the add element (last element)
        if (viewHolder.bindingAdapterPosition == adapter.items.size) {
            return
        }

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView

            // Créer un objet Paint pour l'arrière-plan rouge.
            val backgroundPaint = Paint().apply { color = Color.RED }

            if (dX > 0) {
                // Swiping to the right: draw a rectangle from the left edge to dX.
                c.drawRect(
                    itemView.left.toFloat(),
                    itemView.top.toFloat(),
                    itemView.left + dX,
                    itemView.bottom.toFloat(),
                    backgroundPaint
                )
            } else {
                // Swiping to the left: draw a rectangle from the right edge plus dX to the right edge.
                c.drawRect(
                    itemView.right + dX,
                    itemView.top.toFloat(),
                    itemView.right.toFloat(),
                    itemView.bottom.toFloat(),
                    backgroundPaint
                )
            }

            // Draw the delete icon.
            val icon = recyclerView.context.getDrawable(R.drawable.ic_delete_24dp) ?: return
            val itemHeight = itemView.bottom - itemView.top
            val iconMargin = (itemHeight - icon.intrinsicHeight) / 2
            val iconTop = itemView.top + iconMargin
            val iconBottom = iconTop + icon.intrinsicHeight

            if (dX > 0) {
                // For right swipe, position the icon near the left edge.
                val iconLeft = itemView.left + iconMargin
                val iconRight = iconLeft + icon.intrinsicWidth
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            } else {
                // For left swipe, position the icon near the right edge.
                val iconRight = itemView.right - iconMargin
                val iconLeft = iconRight - icon.intrinsicWidth
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            }
            icon.draw(c)
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        // Allow scanning for all elements except the last one (because it is the add element !!)
        return true
    }
}