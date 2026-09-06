package com.minijarvis.app.llm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.minijarvis.app.core.MiniJarvisApp

/** Handles the Allow/Deny taps on a background confirmation notification (see WakeWordService). */
class ConfirmationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val approved = intent.getBooleanExtra(EXTRA_APPROVED, false)
        val container = (context.applicationContext as MiniJarvisApp).container
        container.confirmationGate.respond(id, approved)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_APPROVED = "approved"
        const val NOTIFICATION_ID = 4300
        const val ACTION_RESPOND = "com.minijarvis.app.action.AGENT_CONFIRM_RESPONSE"
    }
}
