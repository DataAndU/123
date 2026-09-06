package com.minijarvis.app.data

import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow

/** Local audit trail of agent file/network actions — see [AgentActivityLogEntity]. */
class AgentActivityRepository(private val dao: AgentActivityLogDao) {
    fun observeRecent(): Flow<List<AgentActivityLogEntity>> = dao.observeRecent()

    suspend fun log(kind: String, target: String, detail: String, atMillis: Long = TimeUtils.nowMillis()) {
        dao.insert(AgentActivityLogEntity(kind = kind, target = target, detail = detail, timestampMillis = atMillis))
    }

    suspend fun clear() = dao.clearAll()
}
