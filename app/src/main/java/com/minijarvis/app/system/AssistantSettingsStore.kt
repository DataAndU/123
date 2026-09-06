package com.minijarvis.app.system

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.assistantSettingsDataStore by preferencesDataStore(name = "assistant_settings")

/** Local-only preference flags — no server, no sync, plain DataStore on-device. */
class AssistantSettingsStore(private val context: Context) {

    val isWakeWordEnabled: Flow<Boolean> =
        context.assistantSettingsDataStore.data.map { it[WAKE_WORD_ENABLED] ?: false }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.assistantSettingsDataStore.edit { it[WAKE_WORD_ENABLED] = enabled }
    }

    companion object {
        private val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
    }
}
