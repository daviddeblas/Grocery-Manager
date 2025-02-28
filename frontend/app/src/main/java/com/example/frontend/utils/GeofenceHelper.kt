package com.example.frontend.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.frontend.receiver.GeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Allows to create and manage geofences around specific locations.
 * When the user enters or exits a geofence, a notification is triggered.
 * Also manage permissions before adding geofences.
 */
class GeofenceHelper(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)


    /**
     * When a geofence transition occurs (ENTER or EXIT), this PendingIntent triggers
     * the GeofenceBroadcastReceiver to handle the event.
     */
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun addGeofence(latitude: Double, longitude: Double, radius: Float, geofenceId: String) {
        val fineLocationGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundLocationGranted =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            } else true

        if (!fineLocationGranted || !backgroundLocationGranted) {
            Log.e("GeofenceHelper", "Location permissions are not granted.")
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        // Only trigger on ENTER events, not immediately when created
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        Log.d("GeofenceHelper", "Request: lat=$latitude, lng=$longitude, radius=$radius, id=$geofenceId")

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("GeofenceHelper", "Geofence added successfully.")
            }
            .addOnFailureListener { exception ->
                Log.e("GeofenceHelper", "Failed to add geofence: ${exception.message}")
            }
    }

    fun removeGeofence(geofenceId: String) {
        geofencingClient.removeGeofences(listOf(geofenceId))
            .addOnSuccessListener {
                Log.d("GeofenceHelper", "Geofence removed successfully: $geofenceId")
            }
            .addOnFailureListener { exception ->
                Log.e("GeofenceHelper", "Failed to remove geofence: $geofenceId, ${exception.message}")
            }
    }
}