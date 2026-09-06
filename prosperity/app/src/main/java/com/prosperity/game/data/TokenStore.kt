package com.prosperity.game.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the JWT and the configured server address on-device, encrypted at rest.
 * The server address is user-editable (shown on the login screen) since a
 * self-hosted DigitalOcean deployment has no fixed, predictable URL.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "prosperity_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun setToken(token: String?) {
        prefs.edit().apply {
            if (token == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, token)
        }.apply()
    }

    fun getServerUrl(): String {
        val stored = prefs.getString(KEY_SERVER_URL, null) ?: DEFAULT_SERVER_URL
        return if (stored.endsWith("/")) stored else "$stored/"
    }

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url.trim()).apply()
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_SERVER_URL = "server_url"

        /** Android emulator's loopback alias to the host machine — convenient for local dev. */
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:4000/"
    }
}
