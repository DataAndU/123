package com.minijarvis.app.control

import android.content.Context
import android.provider.ContactsContract

/** Resolves a spoken contact name to a phone number, on-device via ContactsContract only. */
class ContactsHelper(private val context: Context) {

    fun findNumberByName(spokenName: String): String? {
        val name = spokenName.trim()
        if (name.isEmpty()) return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            var containsMatch: String? = null
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex) ?: continue
                val number = cursor.getString(numberIndex) ?: continue
                if (displayName.equals(name, ignoreCase = true)) return number
                if (containsMatch == null && displayName.contains(name, ignoreCase = true)) containsMatch = number
            }
            return containsMatch
        }
        return null
    }
}
