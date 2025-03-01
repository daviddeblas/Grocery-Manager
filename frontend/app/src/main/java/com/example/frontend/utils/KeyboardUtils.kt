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
 * Utilitaire pour gérer le comportement du clavier et le défilement des éléments
 * Ce gestionnaire s'inspire du comportement de Google Keep
 */
class KeyboardUtils(private val activity: Activity, private val recyclerView: RecyclerView) {

    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var lastFocusedEditText: EditText? = null
    private var lastFocusedItemPosition: Int = -1
    private var keyboardHeight: Int = 0
    private var isKeyboardVisible = false

    fun setup() {
        val rootView = activity.findViewById<View>(android.R.id.content)

        // Utiliser un listener sur le RecyclerView pour détecter quand un EditText reçoit le focus
        setupRecyclerViewTouchListener()

        // Surveiller les changements globaux de layout pour détecter l'apparition/disparition du clavier
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.height

            // Calculer la hauteur du clavier
            val calculatedKeyboardHeight = screenHeight - r.bottom
            val wasKeyboardVisible = isKeyboardVisible

            // Vérifier si le clavier est visible (utiliser un seuil pour éviter les faux positifs)
            isKeyboardVisible = calculatedKeyboardHeight > screenHeight * 0.15

            if (isKeyboardVisible) {
                keyboardHeight = calculatedKeyboardHeight

                // Si le clavier vient d'apparaître, ajuster le défilement
                if (!wasKeyboardVisible) {
                    adjustScrollPositionForKeyboard()
                }
            }
        }

        rootView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    private fun setupRecyclerViewTouchListener() {
        // Parcourir tous les ViewHolders visibles pour détecter les EditText
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                // On ne capture pas l'événement, on l'observe seulement
                for (i in 0 until rv.childCount) {
                    val child = rv.getChildAt(i)
                    val position = rv.getChildAdapterPosition(child)

                    findEditTextsInView(child).forEach { editText ->
                        editText.setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                lastFocusedEditText = editText
                                lastFocusedItemPosition = position

                                // Si le clavier est déjà visible, ajuster immédiatement
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

    private fun adjustScrollPositionForKeyboard() {
        // Ne rien faire si aucun élément n'a le focus
        if (lastFocusedItemPosition == -1 || lastFocusedEditText == null) return

        // Obtenir la position de l'élément en focus
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

        // Si l'élément en focus n'est pas visible, faire défiler jusqu'à lui
        if (lastFocusedItemPosition < firstVisiblePosition || lastFocusedItemPosition > lastVisiblePosition) {
            recyclerView.smoothScrollToPosition(lastFocusedItemPosition)
            return
        }

        // Obtenir les coordonnées de l'élément en focus
        val itemView = layoutManager.findViewByPosition(lastFocusedItemPosition) ?: return
        val rect = Rect()
        itemView.getGlobalVisibleRect(rect)

        // Calculer la position idéale pour que l'élément soit visible au-dessus du clavier
        val screenHeight = activity.window.decorView.height
        val keyboardTop = screenHeight - keyboardHeight

        // Si l'élément est sous le clavier, faire défiler pour le rendre visible
        if (rect.bottom > keyboardTop) {
            val scrollDistance = rect.bottom - keyboardTop + 50 // 50px de marge supplémentaire
            recyclerView.smoothScrollBy(0, scrollDistance)
        }
    }

    /**
     * Appelé lorsque l'activité est détruite pour éviter les fuites de mémoire
     */
    fun cleanup() {
        val rootView = activity.findViewById<View>(android.R.id.content)
        globalLayoutListener?.let {
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
    }
}