package com.gemmaassistant.app.control

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

sealed class CallResult {
    object PlacedDirectly : CallResult()
    object OpenedDialer : CallResult()
    data class ContactNotFound(val name: String) : CallResult()
}

sealed class SmsResult {
    object Sent : SmsResult()
    data class ContactNotFound(val name: String) : SmsResult()
    object MissingPermission : SmsResult()
}

/**
 * Places calls and sends texts. Prefers a direct action (no confirmation
 * tap) when the matching dangerous permission is granted, and always falls
 * back to opening the dialer/messaging app pre-filled otherwise, so the
 * feature degrades gracefully rather than silently failing.
 */
class PhoneActionsManager(private val context: Context, private val contactsHelper: ContactsHelper) {

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private val looksLikeNumber = Regex("""^[+\d][\d\s\-()]{2,}$""")

    private fun resolveNumber(target: String): String? {
        val trimmed = target.trim()
        if (looksLikeNumber.matches(trimmed)) return trimmed
        return contactsHelper.findNumberByName(trimmed)
    }

    fun call(target: String): CallResult {
        val number = resolveNumber(target) ?: return CallResult.ContactNotFound(target)
        return if (hasPermission(Manifest.permission.CALL_PHONE)) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CallResult.PlacedDirectly
        } else {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CallResult.OpenedDialer
        }
    }

    fun sendSms(target: String, message: String): SmsResult {
        val number = resolveNumber(target) ?: return SmsResult.ContactNotFound(target)
        if (!hasPermission(Manifest.permission.SEND_SMS)) return SmsResult.MissingPermission
        @Suppress("DEPRECATION")
        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(number, null, parts, null, null)
        return SmsResult.Sent
    }

    /** Most recent incoming SMS, optionally filtered by sender name/number containing [fromContaining]. Needs READ_SMS. */
    fun lastIncomingMessage(fromContaining: String? = null): Pair<String, String>? {
        if (!hasPermission(Manifest.permission.READ_SMS)) return null
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            while (cursor.moveToNext()) {
                val address = cursor.getString(addressIndex) ?: continue
                val body = cursor.getString(bodyIndex) ?: continue
                if (fromContaining.isNullOrBlank() || address.contains(fromContaining, ignoreCase = true)) {
                    return address to body
                }
            }
        }
        return null
    }
}
