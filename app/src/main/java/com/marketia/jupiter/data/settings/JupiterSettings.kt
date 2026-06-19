package com.marketia.jupiter.data.settings

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object JupiterSettings {
    val PROVIDER        = stringPreferencesKey("provider")
    val API_KEY         = stringPreferencesKey("api_key")
    val MODEL           = stringPreferencesKey("model")
    val OLLAMA_URL      = stringPreferencesKey("ollama_url")
    val VOICE_SPEED     = floatPreferencesKey("voice_speed")
    val VOICE_PITCH     = floatPreferencesKey("voice_pitch")
    // Per-provider keys (stored independently so switching doesn't erase them)
    val CLAUDE_KEY      = stringPreferencesKey("claude_key")
    val OPENROUTER_KEY  = stringPreferencesKey("openrouter_key")
    val GEMINI_KEY      = stringPreferencesKey("gemini_key")
    val DEEPSEEK_KEY    = stringPreferencesKey("deepseek_key")
}
