package com.marketia.jupiter.ui.screens.autonomy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.core.autonomy.AutonomyEngine
import com.marketia.jupiter.core.autonomy.EngineState
import com.marketia.jupiter.core.autonomy.OllamaRouter
import com.marketia.jupiter.core.autonomy.OllamaStatus
import com.marketia.jupiter.core.ingestion.IngestionResult
import com.marketia.jupiter.core.ingestion.KnowledgeIngestionEngine
import com.marketia.jupiter.core.orchestrator.JupiterOrchestrator
import com.marketia.jupiter.core.orchestrator.OrchestratorResult
import com.marketia.jupiter.data.entity.TaskEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import com.marketia.jupiter.data.settings.AppSettings
import com.marketia.jupiter.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class AutonomyViewModel @Inject constructor(
    private val engine: AutonomyEngine,
    private val repository: JupiterRepository,
    private val ollamaRouter: OllamaRouter,
    private val settingsRepository: SettingsRepository,
    private val orchestrator: JupiterOrchestrator,
    private val ingestionEngine: KnowledgeIngestionEngine
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

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
        refreshOllama()
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

    fun pendingCount() = tasks.value.count { it.status == "PENDING" }
    fun runningCount() = tasks.value.count { it.status == "RUNNING" || it.status == "FIXING" }
    fun doneCount()    = tasks.value.count { it.status == "DONE" }
    fun failedCount()  = tasks.value.count { it.status == "FAILED" || it.status == "BLOCKED" }
}
