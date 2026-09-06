package com.minijarvis.app.system

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.assistantSettingsDataStore by preferencesDataStore(name = "assistant_settings")

/** Local-only preference flags — no server, no sync, plain DataStore on-device. */
class AssistantSettingsStore(private val context: Context) {

    val isWakeWordEnabled: Flow<Boolean> =
        context.assistantSettingsDataStore.data.map { it[WAKE_WORD_ENABLED] ?: false }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.assistantSettingsDataStore.edit { it[WAKE_WORD_ENABLED] = enabled }
    }

    /** File name (inside the app's private models/ folder) of the model to auto-load at startup, if any. */
    val selectedModelFileName: Flow<String?> =
        context.assistantSettingsDataStore.data.map { it[SELECTED_MODEL_FILE] }

    suspend fun setSelectedModelFileName(fileName: String?) {
        context.assistantSettingsDataStore.edit {
            if (fileName == null) it.remove(SELECTED_MODEL_FILE) else it[SELECTED_MODEL_FILE] = fileName
        }
    }

    val isProactiveEnabled: Flow<Boolean> =
        context.assistantSettingsDataStore.data.map { it[PROACTIVE_ENABLED] ?: false }

    suspend fun setProactiveEnabled(enabled: Boolean) {
        context.assistantSettingsDataStore.edit { it[PROACTIVE_ENABLED] = enabled }
    }

    val isAutoApplySafeActionsEnabled: Flow<Boolean> =
        context.assistantSettingsDataStore.data.map { it[AUTO_APPLY_SAFE_ACTIONS] ?: false }

    suspend fun setAutoApplySafeActionsEnabled(enabled: Boolean) {
        context.assistantSettingsDataStore.edit { it[AUTO_APPLY_SAFE_ACTIONS] = enabled }
    }

    /**
     * Best-effort, Mini-JARVIS-only view of whether the flashlight is on —
     * updated only when this app itself toggles it, so it can drift from
     * reality if another app or the quick-settings tile changes the torch.
     * Used solely by [ProactiveAgentWorker]'s low-battery-with-torch-on check.
     */
    suspend fun isFlashlightOnAsFarAsWeKnow(): Boolean =
        context.assistantSettingsDataStore.data.first()[FLASHLIGHT_ON] ?: false

    suspend fun setFlashlightOnAsFarAsWeKnow(on: Boolean) {
        context.assistantSettingsDataStore.edit { it[FLASHLIGHT_ON] = on }
    }

    companion object {
        private val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        private val SELECTED_MODEL_FILE = stringPreferencesKey("selected_model_file")
        private val PROACTIVE_ENABLED = booleanPreferencesKey("proactive_enabled")
        private val AUTO_APPLY_SAFE_ACTIONS = booleanPreferencesKey("auto_apply_safe_actions")
        private val FLASHLIGHT_ON = booleanPreferencesKey("flashlight_on_known")
    }
}
