package com.example.frontend.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.LocalDateTime

/**
 * Handles user session management, including securely storing and retrieving authentication tokens,
 * user details, and synchronization timestamps using `EncryptedSharedPreferences`.
 */
object SessionManager {
    private const val TAG = "SessionManager"

    /** Preferences file name for storing session data */
    private const val PREF_NAME = "grocery_manager_prefs"

    /** Keys for storing session-related information */
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
    private const val KEY_LAST_SYNC = "last_sync"

    /** SharedPreferences instance (encrypted) */
    private lateinit var sharedPreferences: SharedPreferences

    /** Flag to check if the SessionManager has been initialized */
    private var initialized = false

    /**
     * Initialize the SessionManager with application context
     */
    fun init(context: Context) {
        if (initialized) {
            Log.d(TAG, "SessionManager already initialized")
            return
        }

        try {
            // Create a master key for encryption
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            // Initialize encrypted SharedPreferences
            sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            initialized = true
            Log.d(TAG, "SessionManager initialized successfully")

            // Log the current login state
            Log.d(TAG, "Current login state: ${isLoggedIn()}, access token: ${
                if (token?.isNotEmpty() == true) "exists" else "missing"
            }")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SessionManager", e)
            // Fallback to regular SharedPreferences
            sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            initialized = true
        }
    }

    /** Ensures that the SessionManager has been initialized before accessing data. */
    private fun ensureInitialized() {
        if (!initialized || !::sharedPreferences.isInitialized) {
            throw IllegalStateException("SessionManager not initialized! Call init() first.")
        }
    }

    // Access token (Retrieves the stored JWT access token)
    var token: String?
        get() {
            ensureInitialized()
            return sharedPreferences.getString(KEY_TOKEN, null)
        }
        set(value) {
            ensureInitialized()
            with(sharedPreferences.edit()) {
                if (value != null) {
                    putString(KEY_TOKEN, value)
                } else {
                    remove(KEY_TOKEN)
                }
                commit() // Immediate write
            }
        }

    // Saves both the access token and refresh token
    fun saveTokens(accessToken: String) {
        ensureInitialized()
        with(sharedPreferences.edit()) {
            putString(KEY_TOKEN, accessToken)
            commit() // Use commit for immediate write
        }
        Log.d(TAG, "Tokens saved successfully")
    }

    // User ID (retrieved from session storage)
    var userId: Long
        get() {
            ensureInitialized()
            return sharedPreferences.getLong(KEY_USER_ID, -1)
        }
        set(value) {
            ensureInitialized()
            sharedPreferences.edit().putLong(KEY_USER_ID, value).commit()
        }

    // Username (stored securely in session).
    var username: String?
        get() {
            ensureInitialized()
            return sharedPreferences.getString(KEY_USERNAME, null)
        }
        set(value) {
            ensureInitialized()
            with(sharedPreferences.edit()) {
                if (value != null) {
                    putString(KEY_USERNAME, value)
                } else {
                    remove(KEY_USERNAME)
                }
                commit()
            }
        }

    // User Email (stored securely in session)
    var email: String?
        get() {
            ensureInitialized()
            return sharedPreferences.getString(KEY_EMAIL, null)
        }
        set(value) {
            ensureInitialized()
            with(sharedPreferences.edit()) {
                if (value != null) {
                    putString(KEY_EMAIL, value)
                } else {
                    remove(KEY_EMAIL)
                }
                commit()
            }
        }

    // Last Synchronization Timestamp (used for sync operations)
    var lastSync: LocalDateTime?
        get() {
            ensureInitialized()
            val timestamp = sharedPreferences.getString(KEY_LAST_SYNC, null)
            return if (timestamp != null) LocalDateTime.parse(timestamp) else null
        }
        set(value) {
            ensureInitialized()
            with(sharedPreferences.edit()) {
                if (value != null) {
                    putString(KEY_LAST_SYNC, value.toString())
                } else {
                    remove(KEY_LAST_SYNC)
                }
                commit()
            }
        }

    /**
     * Checks if the user is logged in
     * Requires both an access token and a refresh token
     */
    fun isLoggedIn(): Boolean {
        if (!initialized || !::sharedPreferences.isInitialized) {
            Log.e(TAG, "isLoggedIn called before initialization")
            return false
        }

        val hasAccessToken = !token.isNullOrEmpty()
        Log.d(TAG, "Login check: Access token ${if (hasAccessToken) "exists" else "missing"}")

        return hasAccessToken
    }

    /**
     * Log out user by clearing session data
     */
    fun logout() {
        ensureInitialized()
        with(sharedPreferences.edit()) {
            remove(KEY_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_EMAIL)
            commit()
        }
        Log.d(TAG, "User logged out, all session data cleared")
    }
}