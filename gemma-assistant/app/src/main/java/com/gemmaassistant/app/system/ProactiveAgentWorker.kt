package com.gemmaassistant.app.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gemmaassistant.app.core.AppContainer
import com.gemmaassistant.app.core.GemmaAssistantApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic, rule-based background checks — deliberately NOT the local LLM.
 * Running a multi-hundred-MB neural net every 30–60 minutes purely to
 * "notice things" would be a real, indefensible battery cost; these checks
 * are cheap device-state reads instead. The LLM (when loaded) stays
 * reserved for when you're actively talking to it.
 *
 * Each rule posts to a fixed notification ID so a still-true condition
 * replaces its own notification on the next run rather than stacking — but
 * it will resurface each run until resolved or the setting is turned off.
 */
class ProactiveAgentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as GemmaAssistantApp).container
        if (!container.assistantSettingsStore.isProactiveEnabled.first()) return Result.success()

        checkFlashlightLeftOnWithLowBattery(container)
        checkStorageRunningLow()

        return Result.success()
    }

    private suspend fun checkFlashlightLeftOnWithLowBattery(container: AppContainer) {
        if (!container.assistantSettingsStore.isFlashlightOnAsFarAsWeKnow()) return
        val batteryPct = readBatteryPercent(applicationContext)
        if (batteryPct !in 1..15) return

        val autoApply = container.assistantSettingsStore.isAutoApplySafeActionsEnabled.first()
        if (autoApply && container.systemControlManager.setFlashlight(false)) {
            container.assistantSettingsStore.setFlashlightOnAsFarAsWeKnow(false)
            notify(NOTIFICATION_ID_FLASHLIGHT, "Battery at $batteryPct% — turned off your flashlight", "It was left on since you last asked me to turn it on.")
        } else {
            notify(NOTIFICATION_ID_FLASHLIGHT, "Battery at $batteryPct% and the flashlight is on", "Consider turning it off, or turn on \"auto-apply safe suggestions\" in Settings so I can do it for you.")
        }
    }

    private fun checkStorageRunningLow() {
        val stat = StatFs(applicationContext.filesDir.path)
        val availableBytes = stat.availableBytes
        val totalBytes = stat.totalBytes
        if (totalBytes <= 0) return
        val availablePct = (availableBytes * 100 / totalBytes).toInt()
        if (availablePct in 0..5) {
            notify(
                NOTIFICATION_ID_STORAGE,
                "Storage is almost full ($availablePct% free)",
                "This can affect the local model and everything else on your phone — consider freeing up space."
            )
        }
    }

    private fun readBatteryPercent(context: Context): Int {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    private fun notify(id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(applicationContext, GemmaAssistantApp.PROACTIVE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — the suggestion is simply not shown this run.
        }
    }

    companion object {
        private const val WORK_NAME = "proactive_agent_check"
        private const val NOTIFICATION_ID_FLASHLIGHT = 5001
        private const val NOTIFICATION_ID_STORAGE = 5002

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProactiveAgentWorker>(60, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
