package com.example.frontend.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.frontend.repository.SyncManager
import com.example.frontend.utils.NetworkUtils
import com.example.frontend.utils.SessionManager
import com.example.frontend.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val syncManager = SyncManager(appContext)
    private val authViewModel = AuthViewModel(application = applicationContext as android.app.Application)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Vérifier si l'utilisateur est connecté
            if (!SessionManager.isLoggedIn()) {
                return@withContext Result.failure()
            }

            // Vérifier la connectivité réseau
            if (!NetworkUtils.isNetworkAvailable(applicationContext)) {
                return@withContext Result.retry()
            }

            // Tenter de synchroniser
            val syncResult = syncManager.synchronize()

            if (syncResult.isSuccess) {
                return@withContext Result.success()
            } else {
                val error = syncResult.exceptionOrNull()

                // Si l'erreur est liée à l'authentification (401), tenter de rafraîchir le token
                if (error?.message?.contains("401") == true) {
                    val refreshResult = authViewModel.refreshToken()

                    if (refreshResult.isSuccess) {
                        // Réessayer la synchronisation avec le nouveau token
                        val retrySyncResult = syncManager.synchronize()

                        return@withContext if (retrySyncResult.isSuccess) {
                            Result.success()
                        } else {
                            Result.retry()
                        }
                    } else {
                        // Échec du rafraîchissement du token, l'utilisateur doit se reconnecter
                        SessionManager.logout()
                        return@withContext Result.failure()
                    }
                }

                // Échec non lié à l'authentification, réessayer plus tard
                return@withContext Result.retry()
            }
        } catch (e: Exception) {
            // Erreur inattendue
            return@withContext Result.retry()
        }
    }
}