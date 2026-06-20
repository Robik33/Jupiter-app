package com.marketia.jupiter.core.orchestrator

import com.marketia.jupiter.core.autonomy.TaskType
import com.marketia.jupiter.core.autonomy.TokenSaverRouter
import com.marketia.jupiter.data.repository.JupiterRepository
import com.marketia.jupiter.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class UserIntent {
    CREATE_APP, CREATE_SKILL, INGEST_LINK, CREATE_AGENT,
    CREATE_AUTOMATION, CREATE_SYSTEM, CODE_TASK, UNKNOWN
}

data class OrchestratorResult(
    val intent: UserIntent,
    val plan: String,
    val provider: String,
    val nextAction: String,
    val status: String,     // QUEUED | ERROR
    val taskId: Long = 0
)

@Singleton
class JupiterOrchestrator @Inject constructor(
    private val repository: JupiterRepository,
    private val tokenSaver: TokenSaverRouter,
    private val settingsRepository: SettingsRepository
) {
    suspend fun process(command: String): OrchestratorResult {
        val intent = detectIntent(command)
        val settings = settingsRepository.getCurrentSettings()

        val (title, priority) = titleAndPriority(intent, command)

        val plan = runCatching {
            val p = "Plan de 3 pasos para: $command"
            tokenSaver.route(p, TaskType.GENERAL, settings.ollamaUrl).content
        }.getOrNull() ?: defaultPlan(intent)

        return runCatching {
            val taskId = repository.submitTask(title, command, priority)
            OrchestratorResult(
                intent     = intent,
                plan       = plan,
                provider   = "AutonomyEngine",
                nextAction = "Tarea #$taskId en cola. Inicia el loop para ejecutar.",
                status     = "QUEUED",
                taskId     = taskId
            )
        }.getOrElse { e ->
            OrchestratorResult(intent, plan, "", "Error al crear tarea: ${e.message}", "ERROR")
        }
    }

    private fun titleAndPriority(intent: UserIntent, command: String): Pair<String, String> = when (intent) {
        UserIntent.INGEST_LINK      -> "INGESTAR: ${extractUrl(command) ?: command.take(50)}" to "HIGH"
        UserIntent.CREATE_APP       -> "CREAR APP: ${command.take(50)}" to "HIGH"
        UserIntent.CREATE_AGENT     -> "CREAR AGENTE: ${command.take(50)}" to "HIGH"
        UserIntent.CREATE_SKILL     -> "CREAR SKILL: ${command.take(50)}" to "MEDIUM"
        UserIntent.CREATE_SYSTEM    -> "CREAR SISTEMA: ${command.take(50)}" to "HIGH"
        UserIntent.CREATE_AUTOMATION -> "AUTOMATIZAR: ${command.take(50)}" to "MEDIUM"
        UserIntent.CODE_TASK        -> "CÓDIGO: ${command.take(50)}" to "HIGH"
        UserIntent.UNKNOWN          -> command.take(60) to "MEDIUM"
    }

    fun detectIntent(command: String): UserIntent {
        val lower = command.lowercase()
        return when {
            lower.contains("http://") || lower.contains("https://") || lower.contains("www.") ->
                UserIntent.INGEST_LINK
            lower.contains("crear app") || lower.contains("build app") || lower.contains("hacer app") ||
            lower.contains("nueva app") ->
                UserIntent.CREATE_APP
            lower.contains("crear agente") || lower.contains("crear bot") ||
            lower.contains("nuevo agente") || lower.contains("nuevo bot") ->
                UserIntent.CREATE_AGENT
            lower.contains("crear skill") || lower.contains("nuevo skill") ->
                UserIntent.CREATE_SKILL
            lower.contains("crear sistema") || lower.contains("diseñar sistema") ||
            lower.contains("arquitectura") ->
                UserIntent.CREATE_SYSTEM
            lower.contains("automatizar") || lower.contains("crear automatización") ||
            lower.contains("workflow") ->
                UserIntent.CREATE_AUTOMATION
            lower.contains("código") || lower.contains("función") || lower.contains("clase") ||
            lower.contains("kotlin") || lower.contains("python") || lower.contains("corregir") ->
                UserIntent.CODE_TASK
            else -> UserIntent.UNKNOWN
        }
    }

    fun extractUrl(command: String): String? {
        val urlRegex = Regex("https?://[^\\s]+|www\\.[^\\s]+")
        return urlRegex.find(command)?.value
    }

    private fun defaultPlan(intent: UserIntent): String = when (intent) {
        UserIntent.INGEST_LINK       -> "1. Analizar URL\n2. Extraer conocimiento\n3. Guardar como Skill"
        UserIntent.CREATE_APP        -> "1. Definir requisitos\n2. Crear estructura\n3. Implementar"
        UserIntent.CREATE_SKILL      -> "1. Analizar contenido\n2. Extraer conceptos\n3. Guardar Skill"
        UserIntent.CREATE_AGENT      -> "1. Definir capacidades\n2. Diseñar prompts\n3. Registrar agente"
        UserIntent.CREATE_AUTOMATION -> "1. Identificar proceso\n2. Diseñar flujo\n3. Implementar"
        UserIntent.CREATE_SYSTEM     -> "1. Diseñar arquitectura\n2. Definir componentes\n3. Documentar"
        UserIntent.CODE_TASK         -> "1. Analizar requisitos\n2. Escribir código\n3. Verificar build"
        UserIntent.UNKNOWN           -> "1. Analizar\n2. Ejecutar\n3. Reportar"
    }
}
