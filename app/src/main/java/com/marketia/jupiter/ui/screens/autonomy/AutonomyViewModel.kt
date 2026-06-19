package com.marketia.jupiter.ui.screens.autonomy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.core.autonomy.AutonomyEngine
import com.marketia.jupiter.core.autonomy.EngineState
import com.marketia.jupiter.core.autonomy.OllamaRouter
import com.marketia.jupiter.core.autonomy.OllamaStatus
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

@HiltViewModel
class AutonomyViewModel @Inject constructor(
    private val engine: AutonomyEngine,
    private val repository: JupiterRepository,
    private val ollamaRouter: OllamaRouter,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val engineState: StateFlow<EngineState>  = engine.engineState
    val tokensSaved: StateFlow<Int>          = engine.tokensSaved
    val currentStep                          = engine.currentStep

    val tasks = repository.tasks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    private val _ollamaStatus = MutableStateFlow<OllamaStatus?>(null)
    val ollamaStatus: StateFlow<OllamaStatus?> = _ollamaStatus.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

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

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { repository.deleteTask(task) }
    }

    fun clearDone() {
        viewModelScope.launch { repository.clearDoneTasks() }
    }

    fun refreshOllama() {
        viewModelScope.launch {
            val settings = settingsRepository.getCurrentSettings()
            _ollamaStatus.value = ollamaRouter.checkStatus(settings.ollamaUrl)
        }
    }

    fun pendingCount()    = tasks.value.count { it.status == "PENDING" }
    fun runningCount()    = tasks.value.count { it.status == "RUNNING" || it.status == "FIXING" }
    fun doneCount()       = tasks.value.count { it.status == "DONE" }
    fun failedCount()     = tasks.value.count { it.status == "FAILED" || it.status == "BLOCKED" }
}
