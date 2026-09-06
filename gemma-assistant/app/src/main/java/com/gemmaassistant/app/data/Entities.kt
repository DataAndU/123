package com.gemmaassistant.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * All persisted data. Every table lives only inside the encrypted, on-device
 * SQLCipher database created in [AppDatabase] — nothing here is ever synced
 * or uploaded anywhere.
 */

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "assistant"
    val content: String,
    val source: String, // "text" or "voice"
    val timestampMillis: Long
)

/**
 * A local, on-device audit trail of every file and network action the AI
 * agent takes — so "access everything, don't ask before reading" stays
 * transparent even without a prompt at the time. Never synced anywhere.
 */
@Entity(tableName = "agent_activity_log")
data class AgentActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String, // "file_read", "file_write", "file_delete", "file_search", "file_list", "web_fetch"
    val target: String, // file path or URL
    val detail: String, // e.g. HTTP method + status, or bytes read
    val timestampMillis: Long
)
