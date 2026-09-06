package com.gemmaassistant.app.data

import com.gemmaassistant.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatDao) {
    fun observeAll(): Flow<List<ChatMessageEntity>> = dao.observeAll()
    suspend fun addMessage(role: String, content: String, source: String, atMillis: Long = TimeUtils.nowMillis()) =
        dao.insert(ChatMessageEntity(role = role, content = content, source = source, timestampMillis = atMillis))
    suspend fun clear() = dao.clearAll()
}

/** Local audit trail of agent file/network actions — see [AgentActivityLogEntity]. */
class AgentActivityRepository(private val dao: AgentActivityLogDao) {
    fun observeRecent(): Flow<List<AgentActivityLogEntity>> = dao.observeRecent()

    suspend fun log(kind: String, target: String, detail: String, atMillis: Long = TimeUtils.nowMillis()) {
        dao.insert(AgentActivityLogEntity(kind = kind, target = target, detail = detail, timestampMillis = atMillis))
    }

    suspend fun clear() = dao.clearAll()
}
