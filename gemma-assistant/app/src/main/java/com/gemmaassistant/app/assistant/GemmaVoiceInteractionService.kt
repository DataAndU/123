package com.gemmaassistant.app.assistant

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.voice.VoiceInteractionService

/**
 * Registers Gemma Assistant as eligible for Settings → Apps → Default apps →
 * Digital assistant app, so the system's assist gesture (long-press home,
 * or a corner swipe on gesture nav) opens it, the same way Google
 * Assistant/Gemini would. This only wires up that entry point — see
 * [GemmaVoiceInteractionSession] for what actually happens when it fires.
 * There is no hotword/recognition replacement here; "Gemma, ..." wake-word
 * listening is the separate, independent [WakeWordService].
 */
class GemmaVoiceInteractionService : VoiceInteractionService() {

    companion object {
        /** Whether the user has picked Gemma Assistant as their system-wide digital assistant. */
        fun isDefaultAssistant(context: Context): Boolean {
            val current = Settings.Secure.getString(context.contentResolver, "voice_interaction_service") ?: return false
            val expected = ComponentName(context, GemmaVoiceInteractionService::class.java).flattenToString()
            return current == expected
        }
    }
}
