package com.marketia.jupiter.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.core.VoiceEngine
import com.marketia.jupiter.core.ai.AIProvider
import com.marketia.jupiter.data.settings.AppSettings
import com.marketia.jupiter.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val voiceEngine: VoiceEngine
) : ViewModel() {

    val settings = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings()
    )

    fun setProvider(p: AIProvider) = launch { settingsRepository.setProvider(p) }
    fun setApiKey(k: String)       = launch { settingsRepository.setApiKey(k) }
    fun setModel(m: String)        = launch { settingsRepository.setModel(m) }
    fun setOllamaUrl(u: String)   = launch { settingsRepository.setOllamaUrl(u) }

    fun setVoiceSpeed(s: Float) = launch {
        settingsRepository.setVoiceSpeed(s)
        voiceEngine.setSpeed(s)
    }

    fun setVoicePitch(p: Float) = launch {
        settingsRepository.setVoicePitch(p)
        voiceEngine.setPitch(p)
    }

    fun testVoice() {
        voiceEngine.speak("Hola. Soy JÚPITER. Esta es mi configuración de voz actual.")
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
