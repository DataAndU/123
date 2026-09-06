package com.minijarvis.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Generates and stores the local database's encryption passphrase.
 *
 * The passphrase itself is protected at rest by [EncryptedSharedPreferences],
 * whose key material lives only in the device's hardware-backed Android
 * Keystore (via [MasterKey]). Nothing here ever touches the network — the
 * passphrase is created once on first launch with [SecureRandom] and never
 * leaves the device.
 */
class PassphraseStore(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Returns the on-device database passphrase, generating one on first use. */
    fun getOrCreateDatabasePassphrase(): CharArray {
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) {
            return existing.toCharArray()
        }
        val generated = generateRandomPassphrase()
        prefs.edit().putString(KEY_DB_PASSPHRASE, String(generated)).apply()
        return generated
    }

    /** Wipes the stored passphrase. Used only by the in-app "erase all data" action. */
    fun clear() {
        prefs.edit().remove(KEY_DB_PASSPHRASE).apply()
    }

    private fun generateRandomPassphrase(): CharArray {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }.toCharArray()
    }

    private companion object {
        const val ENCRYPTED_PREFS_FILE = "minijarvis_secure_prefs"
        const val KEY_DB_PASSPHRASE = "db_passphrase_v1"
    }
}
