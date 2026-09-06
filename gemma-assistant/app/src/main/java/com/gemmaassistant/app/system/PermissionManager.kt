package com.gemmaassistant.app.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Single source of truth for which runtime permission each optional
 * capability needs. Every capability is fully functional without it being
 * granted — it simply stays unavailable (with a clear message) until the
 * user opts in from Settings.
 */
object PermissionManager {

    val VOICE = arrayOf(Manifest.permission.RECORD_AUDIO)
    val NOTIFICATIONS: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyArray()

    /** Phone & message control — each still degrades gracefully without it (see PhoneActionsManager). */
    val CALLING = arrayOf(Manifest.permission.CALL_PHONE)
    val MESSAGING = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)
    val CONTACTS = arrayOf(Manifest.permission.READ_CONTACTS)

    fun hasAll(context: Context, permissions: Array<String>): Boolean =
        permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}
