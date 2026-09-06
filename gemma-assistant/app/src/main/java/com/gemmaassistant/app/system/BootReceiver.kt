package com.gemmaassistant.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gemmaassistant.app.assistant.WakeWordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts wake-word listening after a reboot, only if the user had it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = AssistantSettingsStore(appContext).isWakeWordEnabled.first()
                if (enabled) WakeWordService.start(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
