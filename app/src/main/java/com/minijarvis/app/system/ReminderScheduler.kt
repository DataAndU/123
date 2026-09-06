package com.minijarvis.app.system

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.minijarvis.app.core.MiniJarvisApp
import java.util.concurrent.TimeUnit

/**
 * Schedules local task/medicine reminders via [WorkManager] — no exact-alarm
 * special permission is requested, trading a little timing precision for a
 * smaller permission footprint. Everything resolves to an on-device
 * notification; nothing is sent anywhere.
 */
class ReminderScheduler(private val context: Context) {

    fun scheduleTaskReminder(taskId: Long, title: String, triggerAtMillis: Long) {
        val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_NOTIFICATION_ID to taskId.toInt(),
                    ReminderWorker.KEY_TITLE to "Task reminder",
                    ReminderWorker.KEY_MESSAGE to title
                )
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueTaskWorkName(taskId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelTaskReminder(taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueTaskWorkName(taskId))
    }

    private fun uniqueTaskWorkName(taskId: Long) = "task_reminder_$taskId"
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 0)
        val title = inputData.getString(KEY_TITLE) ?: "Reminder"
        val message = inputData.getString(KEY_MESSAGE) ?: ""

        val notification = NotificationCompat.Builder(applicationContext, MiniJarvisApp.REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
            Result.success()
        } catch (e: SecurityException) {
            Log.w("ReminderWorker", "Notification permission not granted; reminder skipped", e)
            Result.success()
        }
    }

    companion object {
        const val KEY_NOTIFICATION_ID = "notification_id"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
    }
}
