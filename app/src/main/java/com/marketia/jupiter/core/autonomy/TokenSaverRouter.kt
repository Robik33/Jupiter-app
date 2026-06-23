package com.marketia.jupiter.core.autonomy

import com.marketia.jupiter.core.orchestrator.ClaudeOrchestrator
import com.marketia.jupiter.core.orchestrator.DeepSeekOrchestrator
import com.marketia.jupiter.core.orchestrator.OpenRouterOrchestrator
import com.marketia.jupiter.data.db.dao.HermesDecisionDao
import com.marketia.jupiter.data.entity.HermesDecisionEntity
import com.marketia.jupiter.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class TaskType {
    CODE_GENERATION,
    ANALYSIS,
    ARCHITECTURE_REVIEW,
    INGESTION,
    GENERAL
}

data class RouterResult(
    val content: String?,
    val providerUsed: String,
    val isFree: Boolean,
    val tokensEstimate: Int = 0
)

@Singleton
class TokenSaverRouter @Inject constructor(
    private val ollamaRouter: OllamaRouter,
    private val openRouter: OpenRouterOrchestrator,
    private val claude: ClaudeOrchestrator,
    private val deepSeek: DeepSeekOrchestrator,
    private val settingsRepository: SettingsRepository,
    private val hermesDecisionDao: HermesDecisionDao
) {
    private val SYSTEM_JUPITER = "Eres JUPITER. Sistema de construcción digital. Responde en español, preciso y orientado a sistemas."

    suspend fun route(
        prompt: String,
        taskType: TaskType,
        ollamaUrl: String = "http://localhost:11434"
    ): RouterResult {
        val settings  = settingsRepository.getCurrentSettings()
        val startTime = System.currentTimeMillis()

        val result = when (taskType) {
            TaskType.CODE_GENERATION ->
                tryOllama(prompt, ollamaUrl)
                    ?: tryDeepSeek(prompt, settings.deepseekKey)
                    ?: tryOpenRouter(prompt, settings.openrouterKey.ifBlank { settings.apiKey })
                    ?: RouterResult(null, "NONE", false)

            TaskType.ANALYSIS, TaskType.INGESTION ->
                tryOpenRouter(prompt, settings.openrouterKey.ifBlank { settings.apiKey })
                    ?: tryOllama(prompt, ollamaUrl)
                    ?: RouterResult(null, "NONE", false)

            TaskType.ARCHITECTURE_REVIEW ->
                tryClaude(prompt, settings.claudeKey.ifBlank { settings.apiKey })
                    ?: tryOpenRouter(prompt, settings.openrouterKey.ifBlank { settings.apiKey })
                    ?: RouterResult(null, "NONE", false)

            TaskType.GENERAL ->
                tryOllama(prompt, ollamaUrl)
                    ?: tryOpenRouter(prompt, settings.openrouterKey.ifBlank { settings.apiKey })
                    ?: tryClaude(prompt, settings.claudeKey.ifBlank { settings.apiKey })
                    ?: RouterResult(null, "NONE", false)
        }

        // Log decision to DB (non-blocking, errors silently swallowed)
        runCatching {
            hermesDecisionDao.insert(
                HermesDecisionEntity(
                    taskType        = taskType.name,
                    providerChosen  = result.providerUsed,
                    promptPreview   = prompt.take(300),
                    responsePreview = result.content?.take(200) ?: "",
                    tokensEstimate  = result.tokensEstimate,
                    durationMs      = System.currentTimeMillis() - startTime,
                    success         = result.content != null,
                    isFree          = result.isFree
                )
            )
        }

        return result
    }

    private suspend fun tryOllama(prompt: String, url: String): RouterResult? {
        val status = ollamaRouter.checkStatus(url)
        if (!status.available || status.activeModel.isBlank()) return null
        val result = ollamaRouter.complete(prompt, baseUrl = url, systemPrompt = SYSTEM_JUPITER)
        return if (!result.isNullOrBlank()) RouterResult(result, "Ollama/${status.activeModel}", true, 0)
        else null
    }

    private suspend fun tryOpenRouter(prompt: String, key: String): RouterResult? {
        if (key.isBlank()) return null
        val result = openRouter.complete(SYSTEM_JUPITER, prompt)
        return if (!result.isNullOrBlank()) RouterResult(result, "OpenRouter", false, estimateTokens(prompt))
        else null
    }

    private suspend fun tryClaude(prompt: String, key: String): RouterResult? {
        if (key.isBlank()) return null
        val result = claude.complete(SYSTEM_JUPITER, prompt)
        return if (!result.isNullOrBlank()) RouterResult(result, "Claude", false, estimateTokens(prompt) * 3)
        else null
    }

    private suspend fun tryDeepSeek(prompt: String, key: String): RouterResult? {
        if (key.isBlank()) return null
        val result = deepSeek.complete(SYSTEM_JUPITER, prompt)
        return if (!result.isNullOrBlank()) RouterResult(result, "DeepSeek", false, estimateTokens(prompt))
        else null
    }

    private fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}
