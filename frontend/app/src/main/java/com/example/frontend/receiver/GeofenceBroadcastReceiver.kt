package com.example.frontend.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.frontend.notification.NotificationHelper
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

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
        Log.d("GeofenceReceiver", "Triggering geofences: ${geofencingEvent.triggeringGeofences}")
        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.i("GeofenceReceiver", "Entered geofence")
                NotificationHelper(context).sendGeofenceNotification("You are near a store! Don't forget your shopping list.")
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.i("GeofenceReceiver", "Exited geofence")
            }
            else -> Log.w("GeofenceReceiver", "Other geofence transition: $transition")
        }
    }
}