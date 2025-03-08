package com.example.frontend

import android.app.Application
import android.util.Log
import com.example.frontend.api.APIClient
import com.example.frontend.services.SyncScheduler
import com.example.frontend.utils.SessionManager

class GroceryManagerApplication : Application() {
    companion object {
        private const val TAG = "GroceryManagerApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize components in the correct order
        Log.d(TAG, "Initializing application components...")

        // First initialize session manager
        try {
            SessionManager.init(this)
            Log.d(TAG, "SessionManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SessionManager", e)
        }

        // Then initialize API client
        try {
            APIClient.initialize(this)
            Log.d(TAG, "APIClient initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing APIClient", e)
        }

        // Schedule synchronizations if user is logged in
        try {
            if (SessionManager.isLoggedIn()) {
                Log.d(TAG, "User is logged in, scheduling sync work")
                SyncScheduler.scheduleSyncWork(this)
            } else {
                Log.d(TAG, "User is not logged in, skipping sync scheduling")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling sync work", e)
        }

        Log.d(TAG, "Application initialization complete")
    }
}