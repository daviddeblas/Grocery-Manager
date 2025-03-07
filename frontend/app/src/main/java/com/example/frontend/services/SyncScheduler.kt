package com.example.frontend.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.frontend.workers.SyncWorker
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val SYNC_WORK_NAME = "synchronization_work"

    // Planifier une synchronisation périodique
    fun scheduleSyncWork(context: Context) {
        // Contraintes: exécuter uniquement si le réseau est disponible
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Demande de travail périodique: toutes les 15 minutes
        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES, // Intervalle minimum
            5, TimeUnit.MINUTES // Flexibilité
        )
            .setConstraints(constraints)
            .build()

        // Enregistrer le travail avec la politique de remplacement
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE, // Remplacer si déjà planifié
            syncWorkRequest
        )
    }

    // Annuler les synchronisations planifiées
    fun cancelSyncWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }

    // Déclencher une synchronisation immédiate
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