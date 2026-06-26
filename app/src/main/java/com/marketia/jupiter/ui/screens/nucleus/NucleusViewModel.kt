package com.marketia.jupiter.ui.screens.nucleus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.core.JupiterResponse
import com.marketia.jupiter.core.VoiceEngine
import com.marketia.jupiter.core.ai.ConversationHistory
import com.marketia.jupiter.core.ai.JupiterRouter
import com.marketia.jupiter.core.evaluation.SelfEvaluationEngine
import com.marketia.jupiter.core.bridge.PromptBridgeService
import com.marketia.jupiter.core.ingestion.LinkAnalyzer
import com.marketia.jupiter.core.orchestrator.JupiterOrchestrator
import com.marketia.jupiter.core.skills.SkillCreatorEngine
import com.marketia.jupiter.core.oracle.OracleHermesClient
import com.marketia.jupiter.core.oracle.OracleState
import com.marketia.jupiter.core.update.UpdateManager
import com.marketia.jupiter.core.update.UpdateManifest
import com.marketia.jupiter.core.update.UpdateState
import com.marketia.jupiter.data.entity.PromptInboxEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import com.marketia.jupiter.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    private val skillCreator: SkillCreatorEngine,
    private val updateManager: UpdateManager,
    private val conversationHistory: ConversationHistory,
    private val linkAnalyzer: LinkAnalyzer,
    private val selfEvalEngine: SelfEvaluationEngine
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

    private var otaShown = false

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
            val s = settingsRepository.getCurrentSettings()
            // Auto-optimize voice on first launch (defaults 1.0f → Jarvis-like 0.85f/0.93f)
            val speed = if (s.voiceSpeed == 1.0f) 0.85f else s.voiceSpeed
            val pitch = if (s.voicePitch == 1.0f) 0.93f else s.voicePitch
            if (speed != s.voiceSpeed || pitch != s.voicePitch) {
                settingsRepository.setVoiceSpeed(speed)
                settingsRepository.setVoicePitch(pitch)
            }
            voiceEngine.setSpeed(speed)
            voiceEngine.setPitch(pitch)
            syncBridgeResults()
        }
        // Poll ORACLE + bridge sync every 60 seconds
        viewModelScope.launch {
            while (true) {
                _oracleState.value = oracleClient.fetchOracleState()
                val active = runCatching { repository.countActivePrompts() }.getOrDefault(0)
                if (active > 0) {
                    runCatching { promptBridgeService.syncAll() }
                }
                delay(60_000L)
            }
        }
        // React to completed bridge tasks (OTA notification)
        viewModelScope.launch {
            repository.promptInbox.collect { entries ->
                val done = entries.firstOrNull { it.status == "DONE" && it.apkUrl.isNotBlank() }
                if (done != null && !otaShown) {
                    otaShown = true
                    _response.value = JupiterResponse(
                        orderReceived = done.rawPrompt.take(80),
                        typeDetected  = "BUILD_COMPLETE",
                        nextAction    = "Build listo. Nueva version disponible: ${done.releaseUrl.substringAfterLast("/").take(20)}",
                        status        = "COMPLETADO",
                        action        = "OTA_READY",
                        params        = mapOf("apkUrl" to done.apkUrl, "releaseUrl" to done.releaseUrl)
                    )
                }
            }
        }
        // Auto-check published release 8s after launch
        viewModelScope.launch {
            delay(8_000L)
            runCatching { updateManager.checkForUpdate() }
        }
        // React to published release being available → show OTA card on main screen
        viewModelScope.launch {
            updateManager.state.collect { s ->
                if (s is UpdateState.Available && !otaShown) {
                    otaShown = true
                    _response.value = JupiterResponse(
                        orderReceived = "update_check",
                        typeDetected  = "BUILD_COMPLETE",
                        nextAction    = "Version v${s.manifest.versionName} disponible. Toca INSTALAR AHORA — todo automatico.",
                        status        = "COMPLETADO",
                        action        = "OTA_READY",
                        params        = mapOf(
                            "apkUrl"     to s.manifest.apkUrl,
                            "sha256"     to s.manifest.sha256,
                            "releaseUrl" to s.manifest.releaseUrl
                        )
                    )
                }
            }
        }
    }

    fun syncBridgeResults() {
        viewModelScope.launch {
            runCatching { promptBridgeService.syncAll() }
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

            // Multi-step orchestration check — before single-intent routing
            if (orchestrator.isMultiStep(text)) {
                val steps = orchestrator.planMultiStep(text)
                val bridgeSteps = steps.filter { it.requiresBridge }
                var dispatched = 0

                bridgeSteps.forEach { step ->
                    runCatching {
                        val prompt = "${step.title}: ${step.description}\n\nContexto original: $text"
                        repository.submitTask(step.title, prompt, "HIGH")
                        val qid = promptBridgeService.createAndQueue(prompt, "orchestrator")
                        if (promptBridgeService.dispatch(qid)) dispatched++
                    }
                }

                if (dispatched > 0) otaShown = false

                val planText = buildString {
                    appendLine("Plan activado — ${steps.size} acciones:")
                    appendLine()
                    steps.forEach { s ->
                        val tag = if (s.requiresBridge) "bridge" else "local"
                        appendLine("${s.stepNumber}. ${s.title} [$tag]")
                    }
                    appendLine()
                    append("Bridge: $dispatched tarea(s) despachada(s) al daemon.")
                }

                val planResult = JupiterResponse(
                    orderReceived = text,
                    typeDetected  = "MULTI_PLAN",
                    nextAction    = planText.trim(),
                    status        = "EJECUTANDO",
                    action        = "PLAN_EXECUTING",
                    params        = mapOf("steps" to steps.size.toString(), "dispatched" to dispatched.toString())
                )
                _response.value = planResult
                _state.value = NucleusState.Responding(planResult)
                withContext(Dispatchers.Main) {
                    voiceEngine.speak("Plan creado con ${steps.size} acciones. ${dispatched} tareas enviadas al daemon.")
                }
                return@launch
            }

            val result = router.route(text, conversationHistory.get())

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

            // SELF_EVAL: run SelfEvaluationEngine and display formatted report
            if (result.typeDetected == "SELF_EVAL" || result.action == "RUN_SELF_EVAL") {
                viewModelScope.launch {
                    runCatching {
                        val report = selfEvalEngine.evaluate()
                        val formatted = buildString {
                            appendLine("JUPITER v${report.version} — Autodiagnostico")
                            appendLine()
                            appendLine("Modulos activos: ${report.activeModules.size}")
                            report.activeModules.forEach { appendLine("  · $it") }
                            appendLine()
                            appendLine("Skills en DB: ${report.memoryStats.totalSkills}")
                            appendLine("Proyectos: ${report.memoryStats.totalProjects}")
                            appendLine("Agentes: ${report.memoryStats.totalAgents}")
                            appendLine()
                            if (report.detectedIssues.isNotEmpty()) {
                                appendLine("Issues:")
                                report.detectedIssues.forEach { issue ->
                                    appendLine("  [${issue.severity}] ${issue.module}: ${issue.description}")
                                }
                                appendLine()
                            }
                            if (report.recommendations.isNotEmpty()) {
                                appendLine("Recomendaciones:")
                                report.recommendations.forEach { appendLine("  → $it") }
                            }
                        }
                        _response.value = result.copy(
                            nextAction = formatted.trim(),
                            status = "COMPLETADO"
                        )
                    }
                }
            }

            // DISPATCH_BRIDGE: only when explicitly flagged by action (fixes AI_CHAT over-dispatch)
            if (result.action == "DISPATCH_BRIDGE") {
                runCatching {
                    repository.submitTask(text.take(80), text, "HIGH")
                    val queueId = promptBridgeService.createAndQueue(text, inputSource)
                    val dispatched = promptBridgeService.dispatch(queueId)
                    if (dispatched) {
                        otaShown = false
                        viewModelScope.launch {
                            delay(30_000L)
                            runCatching { promptBridgeService.syncAll() }
                        }
                    }
                }
            }

            // INGEST_LINK: analyze URL inline for immediate skill creation + feedback
            if (result.typeDetected == "INGEST_LINK") {
                val url = result.params["url"] ?: result.params["content"] ?: ""
                if (url.startsWith("http")) {
                    viewModelScope.launch {
                        runCatching {
                            val analysis = linkAnalyzer.analyze(url)
                            val skillId = skillCreator.createFromText(
                                name     = analysis.title.take(60).ifBlank { "Recurso web" },
                                content  = "${analysis.metaDescription}\n${analysis.rawContent.take(600)}\nFuente: ${analysis.url}",
                                category = "general",
                                source   = "link"
                            )
                            if (skillId > 0) {
                                _response.value = result.copy(
                                    nextAction = "Analizado: '${analysis.title.take(50)}'\nSkill creado en base de conocimiento. Bridge profundizara el analisis.",
                                    status     = "COMPLETADO"
                                )
                            }
                        }
                    }
                }
            }

            // Store conversational exchanges in history for multi-turn context
            if (result.typeDetected in listOf("GREETING", "WEB_SEARCH", "SKILL_INFO", "AI_CHAT", "SELF_EVAL")) {
                conversationHistory.add(text, result.nextAction)
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

    fun installOTA(apkUrl: String, sha256: String, releaseUrl: String) {
        if (!updateManager.canInstallPackages()) {
            updateManager.openInstallPermissionSettings()
            return
        }
        viewModelScope.launch {
            otaShown = false  // allow re-show after install completes
            val manifest = UpdateManifest(
                versionCode = Int.MAX_VALUE,
                versionName = "auto",
                apkUrl      = apkUrl,
                releaseUrl  = releaseUrl,
                sha256      = sha256,
                sizeBytes   = 0L,
                mandatory   = false,
                changelog   = emptyList()
            )
            runCatching { updateManager.downloadAndInstall(manifest) }
        }
    }

    fun canInstallPackages(): Boolean = updateManager.canInstallPackages()

    fun clearResponse() {
        _response.value = null
        _state.value = NucleusState.Idle
        voiceEngine.stopSpeaking()
    }

    fun clearConversation() {
        conversationHistory.clear()
    }
}
