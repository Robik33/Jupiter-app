package com.marketia.jupiter.core.engine

import com.marketia.jupiter.core.JupiterResponse
import com.marketia.jupiter.core.ai.ConversationHistory
import com.marketia.jupiter.core.ai.JupiterRouter
import com.marketia.jupiter.data.entity.MemoryNodeEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedCoreEngine @Inject constructor(
    private val router: JupiterRouter,
    private val repository: JupiterRepository,
    private val conversationHistory: ConversationHistory,
    val toolSelector: ToolSelector
) {
    // Step 2+3: Build context — skills + recent projects + memory nodes
    private suspend fun buildContext(): String {
        val skills = runCatching {
            repository.skills.first().take(6).map { it.name }
        }.getOrDefault(emptyList())

        val projects = runCatching {
            repository.projects.first().take(3).map { "${it.name} (${it.type})" }
        }.getOrDefault(emptyList())

        val memory = runCatching {
            repository.nodes.first().takeLast(4).map { it.label }
        }.getOrDefault(emptyList())

        return buildString {
            if (skills.isNotEmpty()) appendLine("[Skills disponibles: ${skills.joinToString(", ")}]")
            if (projects.isNotEmpty()) appendLine("[Proyectos: ${projects.joinToString(", ")}]")
            if (memory.isNotEmpty()) appendLine("[Memoria reciente: ${memory.joinToString(", ")}]")
        }.trimEnd()
    }

    // Main pipeline — 12 steps condensed
    suspend fun process(input: String): JupiterResponse {
        // 2+3. Build context with memory
        val ctx = buildContext()
        val enriched = if (ctx.isNotBlank()) "$ctx\n$input" else input

        // 4-8. Route through ProviderRouter → AI → parse → tool side effects
        val result = router.route(enriched, conversationHistory.get())

        // 9+10. Verify + Reflect — catch dead-end responses and escalate
        val finalResult = if (isDeadEnd(result)) resolveDeadEnd(input, result) else result

        // 11. Save learning for meaningful interactions
        saveLearning(input, finalResult)

        // 12. Store in conversation history
        if (finalResult.typeDetected in listOf(
                "GREETING", "WEB_SEARCH", "SKILL_INFO", "AI_CHAT", "SELF_EVAL", "MEMORY_RECALL"
            )) {
            conversationHistory.add(input, finalResult.nextAction)
        }

        return finalResult
    }

    // 9. Dead-end detection
    private fun isDeadEnd(result: JupiterResponse): Boolean {
        val text = result.nextAction.lowercase()
        val deadPhrases = listOf("sin conexion", "no entiendo", "activa api", "prueba crear")
        return deadPhrases.any { text.contains(it) } && result.action == null
    }

    // 10. Resolve dead-ends by escalating to bridge
    private fun resolveDeadEnd(input: String, result: JupiterResponse): JupiterResponse {
        return result.copy(
            nextAction = "Recibido. Enviando al daemon para procesar cuando haya conexión.",
            action = "DISPATCH_BRIDGE",
            params = mapOf("query" to input, "category" to "auto_escalate"),
            status = "EJECUTANDO"
        )
    }

    // 11. Save learning to memory graph
    private suspend fun saveLearning(input: String, response: JupiterResponse) {
        val learnableTypes = setOf(
            "CREATE_SKILL", "INGEST_LINK", "MEMORY_SAVE", "MEMORY_RECALL",
            "WEB_SEARCH", "CODE_TASK", "CREATE_APP", "CREATE_SYSTEM"
        )
        if (response.typeDetected in learnableTypes) {
            runCatching {
                repository.saveMemoryNode(
                    MemoryNodeEntity(
                        type = response.typeDetected,
                        label = input.take(80),
                        summary = response.nextAction.take(200),
                        tags = response.typeDetected.lowercase(),
                        refId = 0
                    )
                )
            }
        }
    }
}
