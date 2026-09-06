package com.minijarvis.app.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MiniJarvisApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Task and medicine reminders" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WAKE_WORD_CHANNEL_ID,
                "Wake word listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shown while Mini JARVIS is listening in the background for its wake word" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PROACTIVE_CHANNEL_ID,
                "Proactive suggestions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Things Mini JARVIS notices on its own — a habit not logged, a possible missed dose, high spending pace" }
        )
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "minijarvis_reminders"
        const val WAKE_WORD_CHANNEL_ID = "minijarvis_wake_word"
        const val PROACTIVE_CHANNEL_ID = "minijarvis_proactive"
    }
}
