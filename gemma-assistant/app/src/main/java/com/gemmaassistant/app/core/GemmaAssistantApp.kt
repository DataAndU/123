package com.gemmaassistant.app.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class GemmaAssistantApp : Application() {

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
                WAKE_WORD_CHANNEL_ID,
                "Wake word listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shown while Gemma Assistant is listening in the background for its wake word" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PROACTIVE_CHANNEL_ID,
                "Proactive suggestions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Things Gemma Assistant notices on its own — low battery with the flashlight on, storage running low" }
        )
    }

    companion object {
        const val WAKE_WORD_CHANNEL_ID = "gemma_assistant_wake_word"
        const val PROACTIVE_CHANNEL_ID = "gemma_assistant_proactive"
    }
}
