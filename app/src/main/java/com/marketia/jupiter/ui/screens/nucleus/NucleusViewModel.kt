package com.marketia.jupiter.ui.screens.nucleus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.core.JupiterBrain
import com.marketia.jupiter.core.JupiterResponse
import com.marketia.jupiter.core.VoiceEngine
import com.marketia.jupiter.data.repository.JupiterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NucleusState {
    object Idle       : NucleusState()
    object Listening  : NucleusState()
    object Processing : NucleusState()
    data class Responding(val response: JupiterResponse) : NucleusState()
}

@HiltViewModel
class NucleusViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: JupiterRepository
) : ViewModel() {

    private val voiceEngine = VoiceEngine(appContext)

    private val _state       = MutableStateFlow<NucleusState>(NucleusState.Idle)
    val state: StateFlow<NucleusState> = _state.asStateFlow()

    private val _inputText   = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _response    = MutableStateFlow<JupiterResponse?>(null)
    val response: StateFlow<JupiterResponse?> = _response.asStateFlow()

    private val _partialVoice = MutableStateFlow("")
    val partialVoice: StateFlow<String> = _partialVoice.asStateFlow()

    val voiceAvailable: Boolean get() = voiceEngine.isAvailable()

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
    }

    fun onTextChange(text: String) { _inputText.value = text }

    fun processInput(text: String) {
        if (text.isBlank()) return
        _state.value = NucleusState.Processing
        val result = JupiterBrain.process(text)
        _response.value = result
        _state.value = NucleusState.Responding(result)
        _inputText.value = ""
        viewModelScope.launch(Dispatchers.Main) {
            voiceEngine.speak(result.toSpokenText())
        }
    }

    fun startListening() {
        viewModelScope.launch(Dispatchers.Main) {
            _state.value = NucleusState.Listening
            _partialVoice.value = ""
            voiceEngine.startListening(
                onPartialResult = { _partialVoice.value = it },
                onFinalResult = { text ->
                    _partialVoice.value = ""
                    processInput(text)
                },
                onError = {
                    _state.value = NucleusState.Idle
                    _partialVoice.value = ""
                }
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

    override fun onCleared() {
        super.onCleared()
        voiceEngine.destroy()
    }
}
