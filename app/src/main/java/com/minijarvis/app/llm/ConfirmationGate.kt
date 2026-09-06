package com.minijarvis.app.llm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class ConfirmationRequest(
    val id: String,
    val title: String,
    val description: String
)

/**
 * The one thing standing between "the agent can read anything" and "the
 * agent can silently change or send anything": every mutating action
 * (writing/deleting a file, a non-GET network request) must go through
 * [requestConfirmation] and get an explicit yes before it runs. Works from
 * both a foreground screen (ChatScreen shows an AlertDialog) and the
 * background wake-word service (posts an actionable notification) since
 * both observe the same [pending] state and both resolve it through
 * [respond] — whichever the user actually sees and taps.
 */
class ConfirmationGate {

    private val _pending = MutableStateFlow<ConfirmationRequest?>(null)
    val pending: StateFlow<ConfirmationRequest?> = _pending

    private var awaiting: CompletableDeferred<Boolean>? = null

    suspend fun requestConfirmation(title: String, description: String): Boolean {
        val request = ConfirmationRequest(UUID.randomUUID().toString(), title, description)
        val deferred = CompletableDeferred<Boolean>()
        awaiting = deferred
        _pending.value = request
        val result = withTimeoutOrNull(TIMEOUT_MILLIS) { deferred.await() } ?: false
        _pending.value = null
        return result
    }

    fun respond(id: String, approved: Boolean) {
        val current = _pending.value ?: return
        if (current.id != id) return
        awaiting?.complete(approved)
        awaiting = null
        _pending.value = null
    }

    companion object {
        private const val TIMEOUT_MILLIS = 2 * 60 * 1000L // unanswered after 2 minutes = treated as denied
    }
}
