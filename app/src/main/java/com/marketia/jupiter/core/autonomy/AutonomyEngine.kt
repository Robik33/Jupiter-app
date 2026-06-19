package com.marketia.jupiter.core.autonomy

import com.marketia.jupiter.data.entity.TaskEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import com.marketia.jupiter.data.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class EngineState {
    object Idle       : EngineState()
    object Running    : EngineState()
    data class Processing(val taskTitle: String, val step: String) : EngineState()
    object Stopped    : EngineState()
}

data class CycleStep(val name: String, val taskId: Long)

@Singleton
class AutonomyEngine @Inject constructor(
    private val repository: JupiterRepository,
    private val tokenSaver: TokenSaverRouter,
    private val ollamaRouter: OllamaRouter,
    private val settingsRepository: SettingsRepository
) {
    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _tokensSaved = MutableStateFlow(0)
    val tokensSaved: StateFlow<Int> = _tokensSaved.asStateFlow()

    private val _currentStep = MutableStateFlow<CycleStep?>(null)
    val currentStep: StateFlow<CycleStep?> = _currentStep.asStateFlow()

    private var engineJob: Job? = null
    private val MAX_ATTEMPTS = 5

    fun start(scope: CoroutineScope) {
        if (_engineState.value is EngineState.Running) return
        _engineState.value = EngineState.Running
        engineJob = scope.launch(Dispatchers.IO) {
            runLoop()
        }
    }

    fun stop() {
        engineJob?.cancel()
        engineJob = null
        _engineState.value = EngineState.Stopped
    }

    fun isRunning(): Boolean = _engineState.value is EngineState.Running ||
                               _engineState.value is EngineState.Processing

    private suspend fun runLoop() {
        while (true) {
            val task = repository.getNextPendingTask()
            if (task == null) {
                delay(3000) // wait for new tasks
                continue
            }
            _engineState.value = EngineState.Processing(task.title, "INICIANDO")
            val success = processTask(task)
            if (success) {
                _engineState.value = EngineState.Running
            } else {
                delay(1000)
            }
        }
    }

    private suspend fun processTask(task: TaskEntity): Boolean {
        val settings = settingsRepository.getCurrentSettings()
        val ollamaUrl = settings.ollamaUrl

        // Step 1: PLAN
        updateStep(task, "PLAN")
        val plan = plan(task, ollamaUrl)

        // Step 2: EXECUTE
        updateStep(task, "EXECUTE")
        var result = execute(task, plan, ollamaUrl)
        var providerUsed = ""

        // Step 3: VERIFY + FIX loop
        var attempts = task.attempts
        while (!verify(result) && attempts < MAX_ATTEMPTS) {
            attempts++
            updateStep(task, "FIX (intento $attempts/$MAX_ATTEMPTS)")
            updateTaskInDb(task, "FIXING", attempts, result ?: "Sin respuesta", "")
            val fixed = fix(task, plan, result, ollamaUrl)
            result = fixed?.first
            providerUsed = fixed?.second ?: ""
        }

        // Step 4: REPORT
        updateStep(task, "REPORT")
        return if (verify(result)) {
            val finalResult = result ?: ""
            if (providerUsed.contains("Ollama")) {
                _tokensSaved.value = _tokensSaved.value + estimateTokenSavings(task.description)
            }
            updateTaskInDb(task.copy(provider = providerUsed), "DONE", attempts, "", finalResult)
            true
        } else {
            val blockedReason = "Fallo tras $MAX_ATTEMPTS intentos. ${task.lastError}"
            updateTaskInDb(task, "BLOCKED", attempts, blockedReason, "")
            false
        }
    }

    private suspend fun plan(task: TaskEntity, ollamaUrl: String): String {
        val prompt = "Genera un plan de ejecución paso a paso para: ${task.title}\nDescripción: ${task.description}\nResponde en formato de lista numerada."
        val result = tokenSaver.route(prompt, TaskType.GENERAL, ollamaUrl)
        return result.content ?: "1. Analizar requisitos\n2. Implementar solución\n3. Verificar resultado"
    }

    private suspend fun execute(task: TaskEntity, plan: String, ollamaUrl: String): String? {
        val taskType = detectTaskType(task)
        val prompt = """
Tarea: ${task.title}
Descripción: ${task.description}
Plan: $plan

Ejecuta la tarea. Sé preciso y orientado a sistemas. Responde en español.
        """.trimIndent()
        val result = tokenSaver.route(prompt, taskType, ollamaUrl)
        updateTaskInDb(task, "RUNNING", task.attempts + 1, "", "")
        return result.content.also {
            if (result.isFree) _tokensSaved.value += estimateTokenSavings(prompt)
        }
    }

    private fun verify(result: String?): Boolean {
        if (result.isNullOrBlank()) return false
        if (result.length < 10) return false
        val errorWords = listOf("error", "fallo", "no puedo", "cannot", "failed")
        val hasOnlyErrors = errorWords.all { result.lowercase().contains(it) } && result.length < 50
        return !hasOnlyErrors
    }

    private suspend fun fix(task: TaskEntity, originalPlan: String, failedResult: String?, ollamaUrl: String): Pair<String?, String>? {
        val taskType = detectTaskType(task)
        val prompt = """
Tarea: ${task.title}
El intento anterior falló o fue insuficiente.
Resultado anterior: ${failedResult?.take(200) ?: "Sin respuesta"}
Plan original: ${originalPlan.take(200)}

Intenta de nuevo con un enfoque diferente. Responde en español, sé preciso.
        """.trimIndent()
        val result = tokenSaver.route(prompt, taskType, ollamaUrl)
        return Pair(result.content, result.providerUsed)
    }

    private suspend fun updateStep(task: TaskEntity, step: String) {
        _engineState.value = EngineState.Processing(task.title, step)
        _currentStep.value = CycleStep(step, task.id)
    }

    private suspend fun updateTaskInDb(
        task: TaskEntity, status: String, attempts: Int, lastError: String, result: String
    ) {
        repository.updateTask(task.copy(
            status    = status,
            attempts  = attempts,
            lastError = lastError,
            result    = result,
            updatedAt = System.currentTimeMillis()
        ))
    }

    private fun detectTaskType(task: TaskEntity): TaskType {
        val lower = (task.title + " " + task.description).lowercase()
        return when {
            lower.contains("código") || lower.contains("kotlin") || lower.contains("python") ||
            lower.contains("función") || lower.contains("clase") || lower.contains("build") -> TaskType.CODE_GENERATION
            lower.contains("analiz") || lower.contains("resumir") || lower.contains("extraer") -> TaskType.ANALYSIS
            lower.contains("arquitectura") || lower.contains("diseño") || lower.contains("revisión") -> TaskType.ARCHITECTURE_REVIEW
            lower.contains("link") || lower.contains("url") || lower.contains("importar") -> TaskType.INGESTION
            else -> TaskType.GENERAL
        }
    }

    private fun estimateTokenSavings(text: String): Int = (text.length / 4).coerceIn(10, 2000)
}
