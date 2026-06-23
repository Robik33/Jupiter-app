package com.marketia.jupiter.ui.screens.nucleus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.core.JupiterResponse
import com.marketia.jupiter.core.VoiceEngine
import com.marketia.jupiter.core.ai.JupiterRouter
import com.marketia.jupiter.core.bridge.PromptBridgeService
import com.marketia.jupiter.core.orchestrator.JupiterOrchestrator
import com.marketia.jupiter.core.skills.SkillCreatorEngine
import com.marketia.jupiter.core.oracle.OracleHermesClient
import com.marketia.jupiter.core.oracle.OracleState
import com.marketia.jupiter.data.entity.PromptInboxEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import com.marketia.jupiter.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val repository: JupiterRepository,
    private val oracleClient: OracleHermesClient,
    private val promptBridgeService: PromptBridgeService,
    private val orchestrator: JupiterOrchestrator,
    private val skillCreator: SkillCreatorEngine
) : ViewModel() {

    private val _state        = MutableStateFlow<NucleusState>(NucleusState.Idle)
    val state: StateFlow<NucleusState> = _state.asStateFlow()

    private val _inputText    = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _response     = MutableStateFlow<JupiterResponse?>(null)
    val response: StateFlow<JupiterResponse?> = _response.asStateFlow()

    private val _partialVoice = MutableStateFlow("")
    val partialVoice: StateFlow<String> = _partialVoice.asStateFlow()

    private val _oracleState  = MutableStateFlow(OracleState())
    val oracleState: StateFlow<OracleState> = _oracleState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
            val s = settingsRepository.getCurrentSettings()
            voiceEngine.setSpeed(s.voiceSpeed)
            voiceEngine.setPitch(s.voicePitch)
        }
        // Poll ORACLE state every 30 seconds
        viewModelScope.launch {
            while (true) {
                _oracleState.value = oracleClient.fetchOracleState()
                delay(30_000L)
            }
        }
    }

    fun onTextChange(text: String) { _inputText.value = text }

    fun processInput(text: String, inputSource: String = "text") {
        if (text.isBlank()) return
        _state.value = NucleusState.Processing
        _inputText.value = ""
        viewModelScope.launch {
            // Track every prompt in the inbox (DRAFT = received, not dispatched to bridge yet)
            runCatching {
                repository.insertPromptInbox(
                    PromptInboxEntity(source = inputSource, rawPrompt = text, status = "DRAFT")
                )
            }

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

            // CREATE_SKILL: create real SkillEntity (not just a ProjectEntity)
            if (result.typeDetected == "CREATE_SKILL" || result.action == "CREATE_SKILL_ENTITY") {
                runCatching {
                    skillCreator.createFromText(
                        name     = result.params["name"] ?: text.take(60),
                        content  = text,
                        category = result.params["type"] ?: "general",
                        source   = inputSource
                    )
                }
            }

            // DISPATCH_BRIDGE: CODE_TASK, INGEST_LINK, GITHUB_ACTION → send to daemon via bridge
            if (result.action == "DISPATCH_BRIDGE" ||
                result.typeDetected in listOf("CODE_TASK", "INGEST_LINK")) {
                runCatching {
                    val queueId = promptBridgeService.createAndQueue(text, inputSource)
                    promptBridgeService.dispatch(queueId)
                }
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
                onFinalResult   = { _partialVoice.value = ""; processInput(it, "voice") },
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
