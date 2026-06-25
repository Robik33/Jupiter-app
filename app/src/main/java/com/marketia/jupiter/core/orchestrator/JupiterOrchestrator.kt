package com.marketia.jupiter.core.orchestrator

import com.marketia.jupiter.core.autonomy.TaskType
import com.marketia.jupiter.core.autonomy.TokenSaverRouter
import com.marketia.jupiter.core.skills.SkillCreatorEngine
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

data class OrchestratorStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val requiresBridge: Boolean,
    val category: String = "code"
)

@Singleton
class JupiterOrchestrator @Inject constructor(
    private val repository: JupiterRepository,
    private val tokenSaver: TokenSaverRouter,
    private val settingsRepository: SettingsRepository,
    private val skillCreator: SkillCreatorEngine
) {
    suspend fun process(command: String): OrchestratorResult {
        val intent = detectIntent(command)
        val settings = settingsRepository.getCurrentSettings()

        // CREATE_SKILL is always local — no AI needed, creates real SkillEntity
        if (intent == UserIntent.CREATE_SKILL) {
            val skillId = runCatching {
                skillCreator.createFromText(
                    name     = command.take(60),
                    content  = command,
                    category = "general",
                    source   = "voz"
                )
            }.getOrDefault(-1L)
            val taskId = runCatching { repository.submitTask("CREAR SKILL: ${command.take(50)}", command, "MEDIUM") }.getOrDefault(0L)
            return OrchestratorResult(
                intent     = intent,
                plan       = "1. Crear SkillEntity\n2. Guardar en Room DB\n3. Disponible en SKILLS",
                provider   = "LOCAL",
                nextAction = if (skillId > 0) "Skill creado (id=$skillId). Ver en pantalla SKILLS." else "Error creando skill.",
                status     = if (skillId > 0) "QUEUED" else "ERROR",
                taskId     = taskId
            )
        }

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

    fun isMultiStep(command: String): Boolean {
        val lower = command.lowercase()
        val complexVerbs = listOf(
            "hazte", "se mas", "vuelvete", "mejorate", "transforma",
            "convierte", "evoluciona", "conviertete", "hazme", "hazlo"
        )
        val openTargets = listOf(
            "jarvis", "mejor asistente", "mas capaz", "mas inteligente",
            "autonomo", "autonoma", "completo", "completa", "real", "verdadero"
        )
        return complexVerbs.any { lower.contains(it) } ||
               openTargets.any { lower.contains(it) }
    }

    fun planMultiStep(command: String): List<OrchestratorStep> {
        val lower = command.lowercase()
        return when {
            lower.contains("jarvis") || lower.contains("asistente") ||
            lower.contains("completo") || lower.contains("autonomo") ||
            lower.contains("autonoma") || lower.contains("mejor asistente") ->
                listOf(
                    OrchestratorStep(1, "Mejorar voz",
                        "Modificar TTS para sonar mas natural, expresivo y menos robotico",
                        true, "voice"),
                    OrchestratorStep(2, "Mejorar interfaz",
                        "Redisenar cards con animaciones premium y feedback visual claro",
                        true, "ui"),
                    OrchestratorStep(3, "Mejorar interpretacion",
                        "Expandir contexto multi-turno y memoria entre sesiones",
                        true, "nlp"),
                    OrchestratorStep(4, "Activar planeacion",
                        "Conectar JupiterOrchestrator al ciclo de decision principal",
                        false, "code"),
                    OrchestratorStep(5, "Autodiagnostico",
                        "Ejecutar SelfEvaluationEngine y reportar metricas reales",
                        false, "eval")
                )
            lower.contains("rapido") || lower.contains("veloc") || lower.contains("lento") ->
                listOf(
                    OrchestratorStep(1, "Optimizar Room",
                        "Anadir indices y queries paginadas en PromptInboxDao", true, "code"),
                    OrchestratorStep(2, "Cache de respuestas",
                        "Implementar cache LRU para respuestas frecuentes de IA", true, "code"),
                    OrchestratorStep(3, "Startup lazy",
                        "Diferir init de componentes no criticos al primer uso", true, "code")
                )
            lower.contains("memoria") || lower.contains("recuerd") || lower.contains("aprend") ->
                listOf(
                    OrchestratorStep(1, "Expandir MemoryEntity",
                        "Anadir campos de contexto, timestamp y tipo de memoria", true, "code"),
                    OrchestratorStep(2, "Memoria entre sesiones",
                        "Cargar ultimos 10 intercambios relevantes en el prompt de sistema", true, "code"),
                    OrchestratorStep(3, "Indexado semantico",
                        "Skills + Memory con busqueda por similitud de palabras clave", true, "code")
                )
            else ->
                listOf(
                    OrchestratorStep(1, "Analizar objetivo",
                        "Interpretar comando y descomponer en tareas atomicas", false, "eval"),
                    OrchestratorStep(2, "Planificar cambios",
                        "Generar plan de ejecucion especifico para: $command", true, "code"),
                    OrchestratorStep(3, "Ejecutar y verificar",
                        "Ejecutar pasos y validar resultado con build", true, "code")
                )
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
