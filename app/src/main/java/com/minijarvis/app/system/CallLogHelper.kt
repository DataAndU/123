package com.minijarvis.app.system

import android.content.Context
import android.provider.CallLog
import com.minijarvis.app.data.CallLogEntryEntity

/**
 * Reads the device's own call log content provider (no network involved)
 * and maps it into local entities for [com.minijarvis.app.data.CallLogRepository]
 * to persist inside the encrypted database. Caller must have already
 * confirmed READ_CALL_LOG is granted via [PermissionManager].
 */
class CallLogHelper(private val context: Context) {

    fun readRecent(limit: Int = 200): List<CallLogEntryEntity> {
        val entries = mutableListOf<CallLogEntryEntity>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE
        )
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit"
        ) ?: return entries

        cursor.use {
            val numberIdx = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeIdx = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val durationIdx = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val dateIdx = it.getColumnIndexOrThrow(CallLog.Calls.DATE)

            while (it.moveToNext()) {
                val type = when (it.getInt(typeIdx)) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    CallLog.Calls.REJECTED_TYPE -> "rejected"
                    CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
                    else -> "unknown"
                }
                entries += CallLogEntryEntity(
                    number = it.getString(numberIdx) ?: "",
                    displayName = it.getString(nameIdx),
                    type = type,
                    durationSeconds = it.getInt(durationIdx),
                    timestampMillis = it.getLong(dateIdx)
                )
            }
        }
        return entries
    }
}
