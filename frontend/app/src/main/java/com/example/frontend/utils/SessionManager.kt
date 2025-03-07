package com.example.frontend.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.LocalDateTime

object SessionManager {
    private const val PREF_NAME = "grocery_manager_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token" // New key for refresh token
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
    private const val KEY_LAST_SYNC = "last_sync"

    private lateinit var sharedPreferences: SharedPreferences

    /**
     * Initialize the SessionManager with application context
     */
    fun init(context: Context) {
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
    }

    // Access token (existing)
    var token: String?
        get() = sharedPreferences.getString(KEY_TOKEN, null)
        set(value) {
            with(sharedPreferences.edit()) {
                if (value != null) {
                    putString(KEY_TOKEN, value)
                } else {
                    remove(KEY_TOKEN)
                }
                apply()
            }
        }

    // Refresh token (new)
    var refreshToken: String?
        get() = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
        set(value) {
            with(sharedPreferences.edit()) {
                if (value != null) {
                    putString(KEY_REFRESH_TOKEN, value)
                } else {
                    remove(KEY_REFRESH_TOKEN)
                }
                apply()
            }
        }

    // Store both tokens at once (convenient helper)
    fun saveTokens(accessToken: String, refreshToken: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    // Keep all your existing properties
    var userId: Long
        get() = sharedPreferences.getLong(KEY_USER_ID, -1)
        set(value) {
            sharedPreferences.edit().putLong(KEY_USER_ID, value).apply()
        }

    var username: String?
        get() = sharedPreferences.getString(KEY_USERNAME, null)
        set(value) {
            with(sharedPreferences.edit()) {
                if (value != null) {
                    putString(KEY_USERNAME, value)
                } else {
                    remove(KEY_USERNAME)
                }
                apply()
            }
        }

    var email: String?
        get() = sharedPreferences.getString(KEY_EMAIL, null)
        set(value) {
            with(sharedPreferences.edit()) {
                if (value != null) {
                    putString(KEY_EMAIL, value)
                } else {
                    remove(KEY_EMAIL)
                }
                apply()
            }
        }

    var lastSync: LocalDateTime?
        get() {
            val timestamp = sharedPreferences.getString(KEY_LAST_SYNC, null)
            return if (timestamp != null) LocalDateTime.parse(timestamp) else null
        }
        set(value) {
            with(sharedPreferences.edit()) {
                if (value != null) {
                    putString(KEY_LAST_SYNC, value.toString())
                } else {
                    remove(KEY_LAST_SYNC)
                }
                apply()
            }
        }

    /**
     * Check if user is logged in with both tokens
     * For complete security, we verify both access and refresh tokens exist
     */
    fun isLoggedIn(): Boolean {
        return !token.isNullOrEmpty() && !refreshToken.isNullOrEmpty()
    }

    /**
     * Log out user by clearing session data
     */
    fun logout() {
        with(sharedPreferences.edit()) {
            remove(KEY_TOKEN)
            remove(KEY_REFRESH_TOKEN) // Also clear refresh token
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_EMAIL)
            // Optionally preserve lastSync if needed for offline data handling
            apply()
        }
    }
}