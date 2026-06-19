package com.marketia.jupiter.ui.screens.evaluation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.BuildConfig
import com.marketia.jupiter.core.bridge.*
import com.marketia.jupiter.core.evaluation.EvaluationReport
import com.marketia.jupiter.core.evaluation.SelfEvaluationEngine
import com.marketia.jupiter.core.eyes.GitHubRepoInspector
import com.marketia.jupiter.core.eyes.RepoSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EvalState {
    object Idle : EvalState()
    object Loading : EvalState()
    data class Done(val report: EvaluationReport) : EvalState()
    data class Error(val msg: String) : EvalState()
}

sealed class BridgeState {
    object Idle : BridgeState()
    object Sending : BridgeState()
    data class Sent(val result: BridgeResult) : BridgeState()
    object WaitingApproval : BridgeState()
}

@HiltViewModel
class SelfEvaluationViewModel @Inject constructor(
    private val engine: SelfEvaluationEngine,
    private val bridge: ClaudeCodeBridge,
    private val inspector: GitHubRepoInspector
) : ViewModel() {

    private val _evalState    = MutableStateFlow<EvalState>(EvalState.Idle)
    val evalState: StateFlow<EvalState> = _evalState.asStateFlow()

    private val _bridgeState  = MutableStateFlow<BridgeState>(BridgeState.Idle)
    val bridgeState: StateFlow<BridgeState> = _bridgeState.asStateFlow()

    private val _repoSnapshot = MutableStateFlow<RepoSnapshot?>(null)
    val repoSnapshot: StateFlow<RepoSnapshot?> = _repoSnapshot.asStateFlow()

    private val _pendingTask  = MutableStateFlow<ClaudeCodeTask?>(null)
    val pendingTask: StateFlow<ClaudeCodeTask?> = _pendingTask.asStateFlow()

    fun evaluate() {
        viewModelScope.launch {
            _evalState.value = EvalState.Loading
            _evalState.value = runCatching { EvalState.Done(engine.evaluate()) }
                .getOrElse { EvalState.Error(it.message ?: "Error desconocido") }
        }
    }

    fun inspectRepo() {
        viewModelScope.launch {
            _repoSnapshot.value = runCatching { inspector.inspect() }.getOrNull()
        }
    }

    fun prepareTask(report: EvaluationReport) {
        val issues = report.detectedIssues.joinToString("; ") { "${it.module}: ${it.description}" }
        val recs = report.recommendations.joinToString("; ")
        val task = ClaudeCodeTask(
            version = BuildConfig.VERSION_NAME,
            goal = "Mejorar JUPITER basado en autoevaluacion",
            problem = if (issues.isNotBlank()) issues else "Sin problemas criticos detectados.",
            evidence = "Modulos activos: ${report.activeModules.size}. " +
                       "Skills: ${report.memoryStats.totalSkills}. " +
                       "Proyectos: ${report.memoryStats.totalProjects}.",
            requestedChange = recs.ifBlank { "Implementar capacidades faltantes: ${report.missingCapabilities.take(3).joinToString()}" },
            priority = if (report.detectedIssues.any { it.severity == com.marketia.jupiter.core.evaluation.IssueSeverity.HIGH })
                TaskPriority.HIGH else TaskPriority.MEDIUM,
            channel = BridgeChannel.GITHUB_ISSUE,
            status = TaskStatus.PENDING_USER_APPROVAL
        )
        _pendingTask.value = task
        _bridgeState.value = BridgeState.WaitingApproval
    }

    fun approveAndSend() {
        val task = _pendingTask.value ?: return
        viewModelScope.launch {
            _bridgeState.value = BridgeState.Sending
            val result = bridge.send(task.copy(status = TaskStatus.SENT))
            _bridgeState.value = BridgeState.Sent(result)
            _pendingTask.value = null
        }
    }

    fun rejectTask() {
        _pendingTask.value = null
        _bridgeState.value = BridgeState.Idle
    }
}
