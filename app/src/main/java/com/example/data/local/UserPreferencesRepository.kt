package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ThemePreference
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mufradati_prefs")

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val PREFERRED_ACCENT = stringPreferencesKey("preferred_accent")
        val PREFERRED_VOICE = stringPreferencesKey("preferred_voice")
        val ACTIVE_WORKSPACE_ID = stringPreferencesKey("active_workspace_id")
        val LAST_SYNC_TS = longPreferencesKey("last_sync_ts")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themePreference = ThemePreference.safeValueOf(prefs[Keys.THEME]),
            aiEnabled = prefs[Keys.AI_ENABLED] ?: false, // Disabled by default per spec
            ttsSpeed = prefs[Keys.TTS_SPEED] ?: 1.0f,
            preferredAccent = prefs[Keys.PREFERRED_ACCENT] ?: "US",
            preferredVoiceName = prefs[Keys.PREFERRED_VOICE] ?: "",
            activeWorkspaceId = prefs[Keys.ACTIVE_WORKSPACE_ID] ?: "",
            lastSyncTimestamp = prefs[Keys.LAST_SYNC_TS] ?: 0L
        )
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AI_ENABLED] = enabled }
    }

    suspend fun setTtsSettings(speed: Float, accent: String, voiceName: String) {
        context.dataStore.edit {
            it[Keys.TTS_SPEED] = speed.coerceIn(0.5f, 1.75f)
            it[Keys.PREFERRED_ACCENT] = accent
            it[Keys.PREFERRED_VOICE] = voiceName
        }
    }

    suspend fun setActiveWorkspaceId(workspaceId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_WORKSPACE_ID] = workspaceId }
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC_TS] = timestamp }
    }
}
