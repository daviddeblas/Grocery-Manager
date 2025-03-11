package com.example.frontend.utils

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * It's an utility class that manages keyboard behavior and item scrolling.
 * - Detects keyboard visibility changes
 * - Tracks which EditText currently has focus
 * - Automatically scrolls to keep focused items visible above the keyboard
 */
class KeyboardUtils(private val activity: Activity, private val recyclerView: RecyclerView) {

    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var lastFocusedEditText: EditText? = null
    private var lastFocusedItemPosition: Int = -1
    private var keyboardHeight: Int = 0
    private var isKeyboardVisible = false

    /**
     * Initializes touch and layout listeners to detect keyboard visibility
     * changes and EditText focus events.
     */
    fun setup() {
        val rootView = activity.findViewById<View>(android.R.id.content)

        // Set up a listener on RecyclerView to detect when an EditText receives focus
        setupRecyclerViewTouchListener()

        // Monitor global layout changes to detect keyboard appearance/disappearance
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.height

            // Calculate keyboard height
            val calculatedKeyboardHeight = screenHeight - r.bottom
            val wasKeyboardVisible = isKeyboardVisible

            // Check if keyboard is visible
            isKeyboardVisible = calculatedKeyboardHeight > screenHeight * 0.15

            if (isKeyboardVisible) {
                keyboardHeight = calculatedKeyboardHeight

                // If keyboard just appeared, adjust scrolling
                if (!wasKeyboardVisible) {
                    adjustScrollPositionForKeyboard()
                }
            }
        }

        rootView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    /**
     * Sets up a touch listener on the RecyclerView to detect EditText focus changes.
     */
    private fun setupRecyclerViewTouchListener() {
        // Traverse all visible ViewHolders to detect EditText fields
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                // We don't capture the event, just observe it
                for (i in 0 until rv.childCount) {
                    val child = rv.getChildAt(i)
                    val position = rv.getChildAdapterPosition(child)

                    findEditTextsInView(child).forEach { editText ->
                        editText.setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                lastFocusedEditText = editText
                                lastFocusedItemPosition = position

                                // If keyboard is already visible, adjust immediately
                                if (isKeyboardVisible) {
                                    adjustScrollPositionForKeyboard()
                                }
                            }
                        }
                    }
                }
                return false
            }
        })
    }

    private fun findEditTextsInView(view: View): List<EditText> {
        val result = mutableListOf<EditText>()
        if (view is EditText) {
            result.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(findEditTextsInView(view.getChildAt(i)))
            }
        }
        return result
    }

    /**
     * This method calculates the necessary scroll distance to keep the
     * focused item in view when the keyboard appears.
     */
    private fun adjustScrollPositionForKeyboard() {
        // Do nothing if no item has focus
        if (lastFocusedItemPosition == -1 || lastFocusedEditText == null) return

        // Get the position of the focused item
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

        // If the focused item is not visible, scroll to it
        if (lastFocusedItemPosition < firstVisiblePosition || lastFocusedItemPosition > lastVisiblePosition) {
            recyclerView.smoothScrollToPosition(lastFocusedItemPosition)
            return
        }

        // Get the coordinates of the focused item
        val itemView = layoutManager.findViewByPosition(lastFocusedItemPosition) ?: return
        val rect = Rect()
        itemView.getGlobalVisibleRect(rect)

        // Calculate the ideal position for the item to be visible above the keyboard
        val screenHeight = activity.window.decorView.height
        val keyboardTop = screenHeight - keyboardHeight

        // If the item is below the keyboard, scroll to make it visible
        if (rect.bottom > keyboardTop) {
            val scrollDistance = rect.bottom - keyboardTop + 50 // 50px extra margin
            recyclerView.smoothScrollBy(0, scrollDistance)
        }
    }

    /**
     * Cleans up resources when the activity is destroyed (avoid memory leaks).
     */
    fun cleanup() {
        val rootView = activity.findViewById<View>(android.R.id.content)
        globalLayoutListener?.let {
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
    }
}