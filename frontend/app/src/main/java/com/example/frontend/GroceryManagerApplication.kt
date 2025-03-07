package com.example.frontend

import android.app.Application
import com.example.frontend.api.APIClient
import com.example.frontend.services.SyncScheduler
import com.example.frontend.utils.SessionManager

class GroceryManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        APIClient.initialize(this)

        // Initialize the session manager
        SessionManager.init(this)

        // Schedule synchronizations if user is logged in
        if (SessionManager.isLoggedIn()) {
            SyncScheduler.scheduleSyncWork(this)
        }
    }
}