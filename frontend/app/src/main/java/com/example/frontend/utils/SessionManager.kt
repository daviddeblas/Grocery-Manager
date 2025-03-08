package com.example.frontend.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.LocalDateTime

object SessionManager {
    private const val TAG = "SessionManager"
    private const val PREF_NAME = "grocery_manager_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
    private const val KEY_LAST_SYNC = "last_sync"

    private lateinit var sharedPreferences: SharedPreferences
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
            }, refresh token: ${
                if (refreshToken?.isNotEmpty() == true) "exists" else "missing"
            }")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SessionManager", e)
            // Fallback to regular SharedPreferences
            sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            initialized = true
        }
    }

    // Make sure sharedPreferences is initialized before using it
    private fun ensureInitialized() {
        if (!initialized || !::sharedPreferences.isInitialized) {
            throw IllegalStateException("SessionManager not initialized! Call init() first.")
        }
    }

    // Access token (existing)
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
                commit() // Use commit instead of apply for immediate write
            }
        }

    // Refresh token (new)
    var refreshToken: String?
        get() {
            ensureInitialized()
            return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
        }
        set(value) {
            ensureInitialized()
            with(sharedPreferences.edit()) {
                if (value != null) {
                    putString(KEY_REFRESH_TOKEN, value)
                } else {
                    remove(KEY_REFRESH_TOKEN)
                }
                commit() // Use commit instead of apply for immediate write
            }
        }

    // Store both tokens at once (convenient helper)
    fun saveTokens(accessToken: String, refreshToken: String) {
        ensureInitialized()
        with(sharedPreferences.edit()) {
            putString(KEY_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            commit() // Use commit for immediate write
        }
        Log.d(TAG, "Tokens saved successfully")
    }

    // User ID
    var userId: Long
        get() {
            ensureInitialized()
            return sharedPreferences.getLong(KEY_USER_ID, -1)
        }
        set(value) {
            ensureInitialized()
            sharedPreferences.edit().putLong(KEY_USER_ID, value).commit()
        }

    // Username
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

    // Email
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

    // Last sync timestamp
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
     * Check if user is logged in with both tokens
     * For complete security, we verify both access and refresh tokens exist
     */
    fun isLoggedIn(): Boolean {
        if (!initialized || !::sharedPreferences.isInitialized) {
            Log.e(TAG, "isLoggedIn called before initialization")
            return false
        }

        val hasAccessToken = !token.isNullOrEmpty()
        val hasRefreshToken = !refreshToken.isNullOrEmpty()

        Log.d(TAG, "Login check: Access token ${if (hasAccessToken) "exists" else "missing"}, " +
                "Refresh token ${if (hasRefreshToken) "exists" else "missing"}")

        return hasAccessToken && hasRefreshToken
    }

    /**
     * Log out user by clearing session data
     */
    fun logout() {
        ensureInitialized()
        with(sharedPreferences.edit()) {
            remove(KEY_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_EMAIL)
            // Optionally preserve lastSync if needed for offline data handling
            commit()
        }
        Log.d(TAG, "User logged out, all session data cleared")
    }
}