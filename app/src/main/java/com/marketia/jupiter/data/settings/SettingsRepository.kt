package com.marketia.jupiter.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.marketia.jupiter.core.ai.AIProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val provider: AIProvider   = AIProvider.LOCAL,
    val apiKey: String         = "",
    val model: String          = "",
    val ollamaUrl: String      = "http://localhost:11434",
    val voiceSpeed: Float      = 1.0f,
    val voicePitch: Float      = 1.0f,
    val claudeKey: String      = "",
    val openrouterKey: String  = "",
    val geminiKey: String      = "",
    val deepseekKey: String    = "",
    val githubPat: String      = ""
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val providerName = prefs[JupiterSettings.PROVIDER] ?: AIProvider.OPENROUTER.name
        AppSettings(
            provider      = runCatching { AIProvider.valueOf(providerName) }.getOrDefault(AIProvider.LOCAL),
            apiKey        = prefs[JupiterSettings.API_KEY]        ?: "",
            model         = prefs[JupiterSettings.MODEL]          ?: "",
            ollamaUrl     = prefs[JupiterSettings.OLLAMA_URL]     ?: "http://localhost:11434",
            voiceSpeed    = prefs[JupiterSettings.VOICE_SPEED]    ?: 1.0f,
            voicePitch    = prefs[JupiterSettings.VOICE_PITCH]    ?: 1.0f,
            claudeKey     = prefs[JupiterSettings.CLAUDE_KEY]     ?: "",
            openrouterKey = prefs[JupiterSettings.OPENROUTER_KEY] ?: "",
            geminiKey     = prefs[JupiterSettings.GEMINI_KEY]     ?: "",
            deepseekKey   = prefs[JupiterSettings.DEEPSEEK_KEY]   ?: "",
            githubPat     = prefs[JupiterSettings.GITHUB_PAT]     ?: ""
        )
    }

    suspend fun getCurrentSettings(): AppSettings = settings.first()

    suspend fun setProvider(p: AIProvider) {
        val current = getCurrentSettings()
        val storedKey = when (p) {
            AIProvider.CLAUDE      -> current.claudeKey
            AIProvider.OPENROUTER  -> current.openrouterKey
            AIProvider.GEMINI      -> current.geminiKey
            AIProvider.DEEPSEEK    -> current.deepseekKey
            else                   -> ""
        }
        edit {
            this[JupiterSettings.PROVIDER] = p.name
            this[JupiterSettings.API_KEY]  = storedKey
        }
    }

    suspend fun setApiKey(k: String)     = edit { this[JupiterSettings.API_KEY]    = k }
    suspend fun setModel(m: String)      = edit { this[JupiterSettings.MODEL]      = m }
    suspend fun setOllamaUrl(u: String)  = edit { this[JupiterSettings.OLLAMA_URL] = u }
    suspend fun setVoiceSpeed(s: Float)  = edit { this[JupiterSettings.VOICE_SPEED] = s }
    suspend fun setVoicePitch(p: Float)  = edit { this[JupiterSettings.VOICE_PITCH] = p }

    suspend fun setClaudeKey(k: String)     = edit { this[JupiterSettings.CLAUDE_KEY]     = k; this[JupiterSettings.API_KEY] = k }
    suspend fun setOpenrouterKey(k: String) = edit { this[JupiterSettings.OPENROUTER_KEY] = k }
    suspend fun setGeminiKey(k: String)     = edit { this[JupiterSettings.GEMINI_KEY]     = k }
    suspend fun setDeepseekKey(k: String)   = edit { this[JupiterSettings.DEEPSEEK_KEY]   = k }
    suspend fun setGithubPat(k: String)     = edit { this[JupiterSettings.GITHUB_PAT]     = k }

    private suspend fun edit(block: MutablePreferences.() -> Unit) {
        dataStore.edit { it.block() }
    }
}
