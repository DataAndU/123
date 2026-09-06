package com.gemmaassistant.app.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Wraps Android's on-device [SpeechRecognizer]. [RecognizerIntent.EXTRA_PREFER_OFFLINE]
 * asks the platform to use its offline recognizer where the device supports
 * one; Mini JARVIS makes no network call of its own either way, but exact
 * offline availability is decided by the device/OS, not this app.
 */
class VoiceInputManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition isn't available on this device.")
            return
        }
        val instance = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListeningChanged(true) }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { onListeningChanged(false) }

            override fun onError(error: Int) {
                onListeningChanged(false)
                onError(errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                onListeningChanged(false)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onResult(text)
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

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "This device's speech recognizer needs a network fallback, which Mini JARVIS does not use. Try again or use text chat."
        else -> "Voice recognition error ($code)."
    }
}
