package com.minijarvis.app.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * "Always listening" without any cloud wake-word SDK: repeatedly restarts
 * Android's on-device [SpeechRecognizer] (same one used for on-demand voice
 * chat, with [RecognizerIntent.EXTRA_PREFER_OFFLINE] set) and checks each
 * short utterance for the wake word. This keeps Mini JARVIS's zero-network
 * guarantee intact — a dedicated wake-word engine (e.g. Picovoice Porcupine)
 * would need an INTERNET permission for periodic license checks, which this
 * app deliberately never declares.
 *
 * Trade-offs versus a real wake-word engine, stated plainly: there's a brief
 * gap between listening sessions (restart isn't instantaneous), each cycle
 * does full speech-to-text rather than lightweight audio classification (so
 * it costs more battery), and — exactly as with on-demand voice chat — if a
 * given device's platform recognizer has no offline model installed, a
 * session may fail rather than silently phoning home; this class treats
 * that the same as any other recognition error and just retries.
 */
class WakeWordListener(
    private val context: Context,
    private val wakeWord: String,
    private val onWakeWordDetected: (remainder: String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        if (running) return
        running = true
        listenOnce()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        recognizer = null
        onListeningStateChanged(false)
    }

    private fun listenOnce() {
        if (!running) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        recognizer?.destroy()
        val instance = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = instance
        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListeningStateChanged(true)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onListeningStateChanged(false)

            override fun onError(error: Int) {
                onListeningStateChanged(false)
                scheduleRestart()
            }

            override fun onResults(results: Bundle?) {
                onListeningStateChanged(false)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                handleRecognized(text)
                scheduleRestart()
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        instance.startListening(intent)
    }

    private fun handleRecognized(text: String) {
        if (text.isBlank()) return
        val lower = text.lowercase()
        val idx = lower.indexOf(wakeWord)
        if (idx == -1) return
        val remainder = text.substring(idx + wakeWord.length).trim().trimStart(',', '.', '!', ' ')
        running = false // pause the wake loop while the caller handles the command
        onWakeWordDetected(remainder)
    }

    private fun scheduleRestart() {
        if (!running) return
        handler.postDelayed({ listenOnce() }, RESTART_DELAY_MS)
    }

    companion object {
        private const val RESTART_DELAY_MS = 350L
    }
}
