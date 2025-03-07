package com.example.frontend.utils

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

/**
 * TokenManager handles secure storage and management of access and refresh tokens.
 * It uses Android's EncryptedSharedPreferences for secure storage.
 */
class TokenManager(context: Context) {

    companion object {
        private const val TAG = "TokenManager"
        private const val FILE_NAME = "secure_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
    }

    // Create encrypted preferences for secure token storage
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Access token operations
    fun saveAccessToken(token: String) {
        securePrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
        Log.d(TAG, "Access token saved")
    }

    fun getAccessToken(): String? {
        return securePrefs.getString(KEY_ACCESS_TOKEN, null)
    }

    // Refresh token operations
    fun saveRefreshToken(token: String) {
        securePrefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
        Log.d(TAG, "Refresh token saved")
    }

    fun getRefreshToken(): String? {
        return securePrefs.getString(KEY_REFRESH_TOKEN, null)
    }

    // Save both tokens at once
    fun saveTokens(accessToken: String, refreshToken: String) {
        securePrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
        Log.d(TAG, "Both tokens saved")
    }

    // Clear tokens (logout)
    fun clearTokens() {
        securePrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .apply()
        Log.d(TAG, "Tokens cleared")
    }

    // Check if user is logged in
    fun isLoggedIn(): Boolean {
        return getAccessToken() != null && getRefreshToken() != null
    }
}