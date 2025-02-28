package com.example.frontend.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.frontend.notification.NotificationHelper
import com.example.frontend.repository.ShoppingRepository
import com.example.frontend.repository.StoreLocationRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Broadcast receiver that listens for geofence transitions.
 * When the user enters a geofence, a notification is triggered.
 *
 * Note: We use coroutine here because BroadcastReceiver has a limited
 * lifecycle (less than 10 sec) and it allows us to avoid blocking the main thread.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("GeofenceReceiver", "onReceive called")
        if (context == null || intent == null) return

        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            val errorMessage = "Error receiving geofence event: ${geofencingEvent.errorCode}"
            Log.e("GeofenceReceiver", errorMessage)
            return
        }

        val transition = geofencingEvent.geofenceTransition
        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.i("GeofenceReceiver", "Entered geofence")

            // Geofence enter event is now in a coroutine
            // (Dispatcher.IO for operation involving the database)
            CoroutineScope(Dispatchers.IO).launch {
                processGeofenceEnter(context, geofencingEvent)
            }
        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Log.i("GeofenceReceiver", "Exited geofence")
        } else {
            Log.w("GeofenceReceiver", "Other geofence transition: $transition")
        }
    }

    private suspend fun processGeofenceEnter(context: Context, geofencingEvent: GeofencingEvent) {
        val triggeringGeofences = geofencingEvent.triggeringGeofences
        if (triggeringGeofences.isNullOrEmpty()) {
            Log.w("GeofenceReceiver", "No triggering geofences found")
            return
        }

        val storeRepo = StoreLocationRepository(context)
        val shoppingRepo = ShoppingRepository(context)

        // Check if there are any unchecked items
        val hasUncheckedItems = shoppingRepo.hasUncheckedItems()
        if (!hasUncheckedItems) {
            Log.i("GeofenceReceiver", "No unchecked items, skipping notification")
            return
        }

        // Get the first triggering geofence
        val geofenceId = triggeringGeofences[0].requestId

        // Find the store associated with this geofence
        val store = storeRepo.getStoreByGeofenceId(geofenceId)

        val message = if (store != null) {
            "You are near ${store.name}! Don't forget your shopping list."
        } else {
            "You are near a store! Don't forget your shopping list."
        }

        // Send the notification
        NotificationHelper(context).sendGeofenceNotification(message)
    }
}