package com.marketia.jupiter.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.core.VoiceEngine
import com.marketia.jupiter.core.ai.AIProvider
import com.marketia.jupiter.core.ai.JupiterAIClient
import com.marketia.jupiter.core.update.UpdateManager
import com.marketia.jupiter.core.update.UpdateManifest
import com.marketia.jupiter.core.update.UpdateState
import com.marketia.jupiter.data.settings.AppSettings
import com.marketia.jupiter.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val voiceEngine: VoiceEngine,
    private val updateManager: UpdateManager,
    private val aiClient: JupiterAIClient
) : ViewModel() {

    val settings = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings()
    )

    val updateState: StateFlow<UpdateState> = updateManager.state

    private val _aiTestResult = MutableStateFlow<String?>(null)
    val aiTestResult: StateFlow<String?> = _aiTestResult.asStateFlow()

    fun setProvider(p: AIProvider) = launch { settingsRepository.setProvider(p) }
    fun setApiKey(k: String)       = launch { settingsRepository.setApiKey(k) }
    fun setModel(m: String)        = launch { settingsRepository.setModel(m) }
    fun setOllamaUrl(u: String)    = launch { settingsRepository.setOllamaUrl(u) }
    fun setGithubPat(k: String)    = launch { settingsRepository.setGithubPat(k) }
    fun setOpenrouterKey(k: String) = launch { settingsRepository.setOpenrouterKey(k) }
    fun clearAiTestResult()        { _aiTestResult.value = null }

    fun testAIConnection() {
        _aiTestResult.value = "Probando..."
        launch {
            val result = runCatching { aiClient.call("hola") }.getOrNull()
            _aiTestResult.value = if (result != null) "OK — IA conectada" else "Sin respuesta — verifica tu clave"
        }
    }

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

    fun checkForUpdate()                        = launch { updateManager.checkForUpdate() }
    fun downloadUpdate(manifest: UpdateManifest) = launch { updateManager.downloadAndInstall(manifest) }
    fun installDownloaded(file: File)            = updateManager.triggerInstall(file)
    fun resetUpdateState()                       = updateManager.resetState()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
