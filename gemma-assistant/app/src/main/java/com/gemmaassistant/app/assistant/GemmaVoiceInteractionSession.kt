package com.gemmaassistant.app.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.gemmaassistant.app.MainActivity

/**
 * Fires when the user triggers the system assist gesture (long-press home,
 * or a corner swipe on gesture nav) while Gemma Assistant is set as the
 * default digital assistant. It just hands off to the normal chat UI —
 * there's no separate overlay to maintain, so the session closes immediately
 * after launching it.
 */
class GemmaVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startAssistantActivity(intent)
        finish()
    }
}
