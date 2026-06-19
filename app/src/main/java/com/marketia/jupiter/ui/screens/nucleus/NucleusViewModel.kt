package com.marketia.jupiter.ui.screens.nucleus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.core.JupiterResponse
import com.marketia.jupiter.core.VoiceEngine
import com.marketia.jupiter.core.ai.JupiterRouter
import com.marketia.jupiter.data.repository.JupiterRepository
import com.marketia.jupiter.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class NucleusState {
    object Idle       : NucleusState()
    object Listening  : NucleusState()
    object Processing : NucleusState()
    data class Responding(val response: JupiterResponse) : NucleusState()
}

@HiltViewModel
class NucleusViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val router: JupiterRouter,
    private val settingsRepository: SettingsRepository,
    private val repository: JupiterRepository
) : ViewModel() {

    private val _state        = MutableStateFlow<NucleusState>(NucleusState.Idle)
    val state: StateFlow<NucleusState> = _state.asStateFlow()

    private val _inputText    = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _response     = MutableStateFlow<JupiterResponse?>(null)
    val response: StateFlow<JupiterResponse?> = _response.asStateFlow()

    private val _partialVoice = MutableStateFlow("")
    val partialVoice: StateFlow<String> = _partialVoice.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
            val s = settingsRepository.getCurrentSettings()
            voiceEngine.setSpeed(s.voiceSpeed)
            voiceEngine.setPitch(s.voicePitch)
        }
    }

    fun onTextChange(text: String) { _inputText.value = text }

    fun processInput(text: String) {
        if (text.isBlank()) return
        _state.value = NucleusState.Processing
        _inputText.value = ""
        viewModelScope.launch {
            val result = router.route(text)

            // Handle APPLY_VOICE immediately in ViewModel
            if (result.action == "APPLY_VOICE") {
                val spd = result.params["speed"]?.toFloatOrNull() ?: 0.82f
                val pit = result.params["pitch"]?.toFloatOrNull() ?: 0.90f
                voiceEngine.setSpeed(spd)
                voiceEngine.setPitch(pit)
                settingsRepository.setVoiceSpeed(spd)
                settingsRepository.setVoicePitch(pit)
            }

            _response.value = result
            _state.value = NucleusState.Responding(result)

            withContext(Dispatchers.Main) {
                voiceEngine.speak(result.toSpokenText())
            }
        }
    }

    fun startListening() {
        viewModelScope.launch(Dispatchers.Main) {
            _state.value = NucleusState.Listening
            _partialVoice.value = ""
            voiceEngine.startListening(
                onPartialResult = { _partialVoice.value = it },
                onFinalResult   = { _partialVoice.value = ""; processInput(it) },
                onError         = { _state.value = NucleusState.Idle; _partialVoice.value = "" }
            )
        }
    }

    fun stopListening() {
        voiceEngine.stopListening()
        _state.value = NucleusState.Idle
    }

    fun clearResponse() {
        _response.value = null
        _state.value = NucleusState.Idle
        voiceEngine.stopSpeaking()
    }
}
