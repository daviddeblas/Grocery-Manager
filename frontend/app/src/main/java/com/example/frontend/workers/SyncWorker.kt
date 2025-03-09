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

/**
 * A background worker that synchronizes local data with the server using WorkManager.
 * - Runs in the background even when the app is closed.
 * - Uses coroutines for asynchronous execution.
 * - Handles authentication, network availability, and synchronization retries.
 */
class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    /** Manages synchronization operations with the server. */
    private val syncManager = SyncManager(appContext)

    /** Handles authentication, including token refresh if needed. */
    private val authViewModel = AuthViewModel(application = applicationContext as android.app.Application)

    /**
     * Performs background synchronization work:
     * - Runs in a background thread (`Dispatchers.IO`).
     * - Ensures the user is authenticated before syncing.
     * - Checks for an internet connection before making API requests.
     * - Handles authentication failures (refreshes token if expired).
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Check if the user is logged in
            if (!SessionManager.isLoggedIn()) {
                return@withContext Result.failure()
            }

            // 2. Check network connectivity
            if (!NetworkUtils.isNetworkAvailable(applicationContext)) {
                return@withContext Result.retry()
            }

            // 3. Attempt synchronization
            val syncResult = syncManager.synchronize()

            if (syncResult.isSuccess) {
                return@withContext Result.success()
            } else {
                val error = syncResult.exceptionOrNull()

                // 4. If the error is related to authentication (401 Unauthorized), refresh the token
                if (error?.message?.contains("401") == true) {
                    val refreshResult = authViewModel.refreshToken()

                    if (refreshResult.isSuccess) {
                        // 5. Retry synchronization with the new token
                        val retrySyncResult = syncManager.synchronize()

                        return@withContext if (retrySyncResult.isSuccess) {
                            Result.success()
                        } else {
                            Result.retry()
                        }
                    } else {
                        // 6. If token refresh fails, log out the user and return failure
                        SessionManager.logout()
                        return@withContext Result.failure()
                    }
                }

                // 7. If the error is not authentication-related, retry later
                return@withContext Result.retry()
            }
        } catch (e: Exception) {
            // 8. Handle unexpected errors and retry later
            return@withContext Result.retry()
        }
    }
}