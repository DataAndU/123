package com.gemmaassistant.app.assistant

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gemmaassistant.app.core.AppContainer
import com.gemmaassistant.app.core.GemmaAssistantApp
import com.gemmaassistant.app.llm.ConfirmationReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps [WakeWordListener] running so "Gemma, ..."
 * works without touching the app. The persistent notification this requires
 * is intentional, not an oversight — a background microphone should always
 * be visible to the user, with a one-tap way to turn it off.
 */
class WakeWordService : Service() {

    private lateinit var container: AppContainer
    private lateinit var wakeWordListener: WakeWordListener
    private var commandListener: VoiceInputManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        container = (application as GemmaAssistantApp).container
        wakeWordListener = WakeWordListener(
            context = this,
            wakeWord = WAKE_WORD,
            onWakeWordDetected = { remainder -> handleWake(remainder) }
        )
        scope.launch {
            container.confirmationGate.pending.collect { request ->
                if (request != null) postConfirmationNotification(request.id, request.title, request.description)
                else NotificationManagerCompat.from(this@WakeWordService).cancel(ConfirmationReceiver.NOTIFICATION_ID)
            }
        }
    }

    private fun postConfirmationNotification(requestId: String, title: String, description: String) {
        fun responseIntent(approved: Boolean) = PendingIntent.getBroadcast(
            this,
            if (approved) 1 else 2,
            Intent(this, ConfirmationReceiver::class.java).apply {
                action = ConfirmationReceiver.ACTION_RESPOND
                putExtra(ConfirmationReceiver.EXTRA_REQUEST_ID, requestId)
                putExtra(ConfirmationReceiver.EXTRA_APPROVED, approved)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, GemmaAssistantApp.WAKE_WORD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Allow", responseIntent(true))
            .addAction(0, "Deny", responseIntent(false))
            .build()
        NotificationManagerCompat.from(this).notify(ConfirmationReceiver.NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification("Listening for \"$WAKE_WORD\"…"))
        wakeWordListener.start()
        return START_STICKY
    }

    private fun handleWake(remainder: String) {
        if (remainder.isBlank()) {
            container.voiceOutputManager.speak("Yes?")
            updateNotification("Listening for your command…")
            commandListener = VoiceInputManager(
                context = this,
                onResult = { text -> processCommand(text) },
                onError = { resumeWakeListening() }
            ).also { it.startListening() }
        } else {
            processCommand(remainder)
        }
    }

    private fun processCommand(text: String) {
        updateNotification("Thinking…")
        scope.launch {
            val reply = container.assistantEngine.handle(text, source = "voice")
            container.voiceOutputManager.speak(reply)
            resumeWakeListening()
        }
    }

    private fun resumeWakeListening() {
        commandListener?.destroy()
        commandListener = null
        updateNotification("Listening for \"$WAKE_WORD\"…")
        wakeWordListener.start()
    }

    override fun onDestroy() {
        wakeWordListener.stop()
        commandListener?.destroy()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, GemmaAssistantApp.WAKE_WORD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Gemma Assistant")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                "Stop listening",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(text: String) {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val WAKE_WORD = "gemma"
        private const val NOTIFICATION_ID = 4200
        private const val ACTION_STOP = "com.gemmaassistant.app.action.STOP_WAKE_WORD"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WakeWordService::class.java).setAction(ACTION_STOP))
        }
    }
}
