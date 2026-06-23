package com.marketia.jupiter.ui.screens.autonomy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.core.autonomy.AutonomyEngine
import com.marketia.jupiter.core.autonomy.EngineState
import com.marketia.jupiter.core.autonomy.OllamaRouter
import com.marketia.jupiter.core.autonomy.OllamaStatus
import com.marketia.jupiter.BuildConfig
import com.marketia.jupiter.core.bridge.BridgeChannel
import com.marketia.jupiter.core.autonomy.TaskScheduler
import com.marketia.jupiter.core.bridge.ClaudeCodeBridge
import com.marketia.jupiter.core.bridge.ClaudeCodeTask
import com.marketia.jupiter.core.bridge.IssueStatus
import com.marketia.jupiter.core.bridge.TaskPriority
import com.marketia.jupiter.core.ingestion.IngestionResult
import com.marketia.jupiter.core.ingestion.KnowledgeIngestionEngine
import com.marketia.jupiter.core.orchestrator.JupiterOrchestrator
import com.marketia.jupiter.core.orchestrator.OrchestratorResult
import com.marketia.jupiter.data.entity.TaskEntity
import com.marketia.jupiter.data.entity.HermesDecisionEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import com.marketia.jupiter.data.settings.AppSettings
import com.marketia.jupiter.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CommandState {
    object Idle : CommandState()
    object Processing : CommandState()
    data class Done(val result: OrchestratorResult) : CommandState()
    data class Error(val message: String) : CommandState()
}

sealed class IngestionState {
    object Idle : IngestionState()
    object Processing : IngestionState()
    data class Done(val result: IngestionResult) : IngestionState()
}

sealed class SyncState {
    object Idle       : SyncState()
    object Syncing    : SyncState()
    data class Done(val updated: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

@HiltViewModel
class AutonomyViewModel @Inject constructor(
    private val engine: AutonomyEngine,
    private val repository: JupiterRepository,
    private val ollamaRouter: OllamaRouter,
    private val settingsRepository: SettingsRepository,
    private val orchestrator: JupiterOrchestrator,
    private val ingestionEngine: KnowledgeIngestionEngine,
    private val bridge: ClaudeCodeBridge,
    private val taskScheduler: TaskScheduler
) : ViewModel() {

    val engineState: StateFlow<EngineState> = engine.engineState
    val tokensSaved: StateFlow<Int>         = engine.tokensSaved
    val currentStep                         = engine.currentStep

    val tasks = repository.tasks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    private val _ollamaStatus = MutableStateFlow<OllamaStatus?>(null)
    val ollamaStatus: StateFlow<OllamaStatus?> = _ollamaStatus.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _commandState = MutableStateFlow<CommandState>(CommandState.Idle)
    val commandState: StateFlow<CommandState> = _commandState.asStateFlow()

    private val _ingestionState = MutableStateFlow<IngestionState>(IngestionState.Idle)
    val ingestionState: StateFlow<IngestionState> = _ingestionState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _bridgeState = MutableStateFlow<BridgeOpState>(BridgeOpState.Idle)
    val bridgeState: StateFlow<BridgeOpState> = _bridgeState.asStateFlow()

    val hermesDecisions = repository.hermesDecisions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
        refreshOllama()
        // Schedule background autonomy execution via WorkManager
        taskScheduler.schedulePeriodicExecution()
        // Auto-sync remote tasks on startup (REGLA 5)
        viewModelScope.launch {
            delay(2000) // let DB settle
            syncRemoteTasks()
        }
    }

    fun startEngine() = engine.start(viewModelScope)
    fun stopEngine()  = engine.stop()
    fun isRunning()   = engine.isRunning()

    fun submitTask(title: String, description: String, priority: String = "MEDIUM") {
        viewModelScope.launch {
            repository.submitTask(title, description, priority)
            if (!engine.isRunning()) engine.start(viewModelScope)
        }
    }

    fun processCommand(command: String) {
        if (command.isBlank()) return
        _commandState.value = CommandState.Processing
        viewModelScope.launch {
            runCatching {
                val result = orchestrator.process(command)
                _commandState.value = CommandState.Done(result)
                if (!engine.isRunning()) engine.start(viewModelScope)
                // Auto-bridge: CODE_TASK + HIGH/CRITICAL go to Claude Code automatically (REGLA 7)
                if (result.taskId > 0 && result.intent == com.marketia.jupiter.core.orchestrator.UserIntent.CODE_TASK) {
                    val task = tasks.value.find { it.id == result.taskId }
                    if (task != null && task.priority in listOf("HIGH", "CRITICAL")) {
                        sendTaskToBridge(task)
                    }
                }
            }.onFailure { e ->
                _commandState.value = CommandState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun ingestLink(url: String) {
        if (url.isBlank()) return
        _ingestionState.value = IngestionState.Processing
        viewModelScope.launch {
            runCatching {
                val result = ingestionEngine.ingest(url)
                _ingestionState.value = IngestionState.Done(result)
            }.onFailure {
                _ingestionState.value = IngestionState.Idle
            }
        }
    }

    fun clearCommandState()   { _commandState.value = CommandState.Idle }
    fun clearIngestionState() { _ingestionState.value = IngestionState.Idle }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { repository.deleteTask(task) }
    }

    fun clearDone() {
        viewModelScope.launch { repository.clearDoneTasks() }
    }

    fun refreshOllama() {
        viewModelScope.launch {
            val s = settingsRepository.getCurrentSettings()
            _ollamaStatus.value = ollamaRouter.checkStatus(s.ollamaUrl)
        }
    }

    fun pendingCount()  = tasks.value.count { it.status == "PENDING" }
    fun runningCount()  = tasks.value.count { it.status == "RUNNING" || it.status == "FIXING" }
    fun doneCount()     = tasks.value.count { it.status == "DONE" }
    fun failedCount()   = tasks.value.count { it.status == "FAILED" || it.status == "BLOCKED" }
    fun remoteCount()   = tasks.value.count { it.issueUrl.isNotBlank() }

    fun sendTaskToBridge(task: TaskEntity) {
        viewModelScope.launch {
            _bridgeState.value = BridgeOpState.Sending(task.id)
            val claudeTask = ClaudeCodeTask(
                version          = BuildConfig.VERSION_NAME,
                goal             = task.title,
                objective        = task.title,
                problem          = task.description,
                evidence         = "Tarea #${task.id} — prioridad: ${task.priority}",
                requestedChange  = task.description,
                filesToChange    = emptyList(),
                steps            = listOf("Analizar requisito", "Implementar solución", "Verificar y reportar"),
                validation       = "Build exitoso. Funcionalidad verificada.",
                expectedResult   = task.title,
                priority         = when (task.priority) {
                    "CRITICAL" -> TaskPriority.CRITICAL
                    "HIGH"     -> TaskPriority.HIGH
                    "LOW"      -> TaskPriority.LOW
                    else       -> TaskPriority.MEDIUM
                },
                channel = BridgeChannel.GITHUB_ISSUE
            )
            val result = bridge.send(claudeTask)
            if (result.success && result.issueUrl != null) {
                repository.updateTask(task.copy(
                    issueUrl     = result.issueUrl,
                    remoteStatus = "PENDING",
                    updatedAt    = System.currentTimeMillis()
                ))
                _bridgeState.value = BridgeOpState.Sent(task.id, result.issueUrl)
            } else {
                _bridgeState.value = BridgeOpState.Error(result.message)
            }
        }
    }

    fun syncRemoteTasks() {
        viewModelScope.launch {
            val tasksWithIssue = repository.getTasksWithIssueUrl()
            if (tasksWithIssue.isEmpty()) {
                _syncState.value = SyncState.Idle
                return@launch
            }
            _syncState.value = SyncState.Syncing
            var updated = 0
            for (task in tasksWithIssue) {
                if (task.remoteStatus == "DONE") continue  // already closed
                runCatching {
                    val poll = bridge.pollFull(task.issueUrl)
                    val newStatus = poll.status.name
                    val localStatus = when (poll.status) {
                        IssueStatus.DONE    -> "DONE"
                        IssueStatus.BLOCKED -> "BLOCKED"
                        IssueStatus.RUNNING -> "RUNNING"
                        else                -> task.status
                    }
                    if (newStatus != task.remoteStatus || poll.apkUrl != task.remoteApkUrl || localStatus != task.status) {
                        repository.updateTask(task.copy(
                            status             = localStatus,
                            remoteStatus       = newStatus,
                            remoteResult       = poll.result.ifBlank { task.remoteResult },
                            remoteApkUrl       = poll.apkUrl.ifBlank { task.remoteApkUrl },
                            remoteReleaseUrl   = poll.releaseUrl.ifBlank { task.remoteReleaseUrl },
                            lastRemoteCheckAt  = System.currentTimeMillis(),
                            updatedAt          = System.currentTimeMillis()
                        ))
                        updated++
                    }
                }
            }
            _syncState.value = SyncState.Done(updated)
        }
    }

    fun clearBridgeState() { _bridgeState.value = BridgeOpState.Idle }
    fun clearSyncState()   { _syncState.value = SyncState.Idle }
}

sealed class BridgeOpState {
    object Idle : BridgeOpState()
    data class Sending(val taskId: Long) : BridgeOpState()
    data class Sent(val taskId: Long, val issueUrl: String) : BridgeOpState()
    data class Error(val message: String) : BridgeOpState()
}
