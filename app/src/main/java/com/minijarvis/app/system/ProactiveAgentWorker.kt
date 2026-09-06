package com.minijarvis.app.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.core.MiniJarvisApp
import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Periodic, rule-based background checks — deliberately NOT the local LLM.
 * Running a multi-hundred-MB neural net every 30–60 minutes purely to
 * "notice things" would be a real, indefensible battery cost for a personal
 * assistant; these checks are cheap arithmetic over data already in the
 * local database, which is exactly the kind of always-on monitoring a
 * background worker should be doing. The LLM (when loaded) stays reserved
 * for when you're actively talking to it.
 *
 * Each rule posts to a fixed notification ID so a still-true condition
 * replaces its own notification on the next run rather than stacking —
 * but it will resurface each run until resolved or the setting is turned
 * off, since this worker has no per-suggestion "already told you" memory.
 */
class ProactiveAgentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MiniJarvisApp).container
        if (!container.assistantSettingsStore.isProactiveEnabled.first()) return Result.success()

        checkFlashlightLeftOnWithLowBattery(container)
        checkHabitsNotLoggedToday(container)
        checkMedicinesPossiblyMissed(container)
        checkExpensePace(container)

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

    private suspend fun checkHabitsNotLoggedToday(container: AppContainer) {
        val hour = Instant.ofEpochMilli(TimeUtils.nowMillis()).atZone(TimeUtils.zone).hour
        if (hour < 18) return // give the day a chance before nudging

        val habits = container.habitRepository.observeActive().first()
        val notDone = mutableListOf<String>()
        for (habit in habits) {
            if (!container.habitRepository.isCompletedForDay(habit.id)) notDone.add(habit.name)
        }
        if (notDone.isEmpty()) return
        notify(NOTIFICATION_ID_HABITS, "Habits not logged today", "Still open: ${notDone.joinToString()}")
    }

    private suspend fun checkMedicinesPossiblyMissed(container: AppContainer) {
        val hour = Instant.ofEpochMilli(TimeUtils.nowMillis()).atZone(TimeUtils.zone).hour
        if (hour < 12) return

        val now = TimeUtils.nowMillis()
        val startOfDay = TimeUtils.startOfDayMillis(now)
        val weekAgo = now - 7 * TimeUtils.DAY_MILLIS
        val recentLogs = container.medicineRepository.doseLogsBetween(weekAgo, now)

        val medicines = container.medicineRepository.observeActive().first()
        val missed = medicines.filter { medicine ->
            val logsForThis = recentLogs.filter { it.medicineId == medicine.id }
            val loggedToday = logsForThis.any { it.takenAtMillis >= startOfDay }
            val isRoutine = logsForThis.size >= 3 // taken on most days recently — likely a daily routine
            isRoutine && !loggedToday
        }
        if (missed.isEmpty()) return
        notify(NOTIFICATION_ID_MEDICINE, "Possibly missed medicine today", "Not logged yet: ${missed.joinToString { it.name }}")
    }

    private suspend fun checkExpensePace(container: AppContainer) {
        val today = Instant.ofEpochMilli(TimeUtils.nowMillis()).atZone(TimeUtils.zone).toLocalDate()
        if (today.dayOfMonth < 5) return // too early in the month for a fair projection

        val monthToDate = container.expenseRepository.totalBetween(TimeUtils.startOfMonthMillis(), TimeUtils.nowMillis())
        val prevMonthDate = today.minusMonths(1)
        val prevMonthStart = prevMonthDate.withDayOfMonth(1).atStartOfDay(TimeUtils.zone).toInstant().toEpochMilli()
        val prevMonthEnd = TimeUtils.endOfMonthMillis(prevMonthStart)
        val prevMonthTotal = container.expenseRepository.totalBetween(prevMonthStart, prevMonthEnd)
        if (prevMonthTotal <= 0.0) return

        val projected = monthToDate / today.dayOfMonth * today.lengthOfMonth()
        if (projected > prevMonthTotal * 1.2) {
            notify(
                NOTIFICATION_ID_EXPENSE,
                "On pace to spend more than last month",
                "Projected ~₹${"%.0f".format(projected)} this month vs ₹${"%.0f".format(prevMonthTotal)} last month."
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
        val notification = NotificationCompat.Builder(applicationContext, MiniJarvisApp.PROACTIVE_CHANNEL_ID)
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
        private const val NOTIFICATION_ID_HABITS = 5002
        private const val NOTIFICATION_ID_MEDICINE = 5003
        private const val NOTIFICATION_ID_EXPENSE = 5004

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProactiveAgentWorker>(60, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
