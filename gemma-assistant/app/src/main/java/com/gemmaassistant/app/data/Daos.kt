package com.gemmaassistant.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert suspend fun insert(entity: ChatMessageEntity): Long
    @Query("SELECT * FROM chat_messages ORDER BY timestampMillis ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>
    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface AgentActivityLogDao {
    @Insert suspend fun insert(entity: AgentActivityLogEntity): Long
    @Query("SELECT * FROM agent_activity_log ORDER BY timestampMillis DESC LIMIT 500")
    fun observeRecent(): Flow<List<AgentActivityLogEntity>>
    @Query("DELETE FROM agent_activity_log")
    suspend fun clearAll()
}
