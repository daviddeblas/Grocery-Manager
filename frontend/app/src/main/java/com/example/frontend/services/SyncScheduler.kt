package com.example.frontend.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.frontend.workers.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * Responsible for scheduling periodic synchronization tasks.
 */
object SyncScheduler {
    const val SYNC_WORK_NAME = "synchronization_work"

    /**
     * Schedules a periodic synchronization job.
     * - Runs every 15 minutes to sync data with the server.
     * - Requires an active network connection to execute.
     * - Replaces any existing scheduled synchronization task.
     */
    fun scheduleSyncWork(context: Context) {
        // Define constraints: only run when the device has an active network connection.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create a periodic work request
        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES, // Intervalle minimum
            5, TimeUnit.MINUTES // Flexibilité
        )
            .setConstraints(constraints)
            .build()

        // Enqueue the periodic work request, replacing any previously scheduled work.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // Update if already scheduled
            syncWorkRequest
        )
    }

    // Trigger an immediate synchronization.
    fun requestImmediateSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(syncWorkRequest)
    }
}