package com.marketia.jupiter.core.autonomy

import com.marketia.jupiter.core.ai.AIProvider
import com.marketia.jupiter.core.ai.JupiterAIClient
import com.marketia.jupiter.core.orchestrator.ClaudeOrchestrator
import com.marketia.jupiter.core.orchestrator.DeepSeekOrchestrator
import com.marketia.jupiter.core.orchestrator.OpenRouterOrchestrator
import com.marketia.jupiter.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class TaskType {
    CODE_GENERATION,    // Ollama first (free)
    ANALYSIS,           // OpenRouter (cheap)
    ARCHITECTURE_REVIEW,// Claude (critical)
    INGESTION,          // Ollama → OpenRouter
    GENERAL             // Ollama → OpenRouter → Claude
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
    private val jupiterAIClient: JupiterAIClient,
    private val openRouter: OpenRouterOrchestrator,
    private val claude: ClaudeOrchestrator,
    private val deepSeek: DeepSeekOrchestrator,
    private val settingsRepository: SettingsRepository
) {
    private val SYSTEM_JUPITER = "Eres JUPITER. Sistema de construcción digital. Responde en español, preciso y orientado a sistemas."

    suspend fun route(prompt: String, taskType: TaskType, ollamaUrl: String = "http://localhost:11434"): RouterResult {
        val settings = settingsRepository.getCurrentSettings()

        return when (taskType) {
            TaskType.CODE_GENERATION -> {
                // Ollama first (free), then DeepSeek, then OpenRouter
                tryOllama(prompt, ollamaUrl)
                    ?: tryDeepSeek(prompt, settings.deepseekKey)
                    ?: tryOpenRouter(prompt, settings.openrouterKey.ifBlank { settings.apiKey })
                    ?: RouterResult(null, "NONE", false)
            }

            TaskType.ANALYSIS, TaskType.INGESTION -> {
                // OpenRouter first (cheap), fall back to Ollama
                tryOpenRouter(prompt, settings.openrouterKey.ifBlank { settings.apiKey })
                    ?: tryOllama(prompt, ollamaUrl)
                    ?: RouterResult(null, "NONE", false)
            }

            TaskType.ARCHITECTURE_REVIEW -> {
                // Claude first (best quality), fall back to OpenRouter
                tryClaude(prompt, settings.claudeKey.ifBlank { settings.apiKey })
                    ?: tryOpenRouter(prompt, settings.openrouterKey.ifBlank { settings.apiKey })
                    ?: RouterResult(null, "NONE", false)
            }

            TaskType.GENERAL -> {
                // Ollama → OpenRouter → Claude cascade
                tryOllama(prompt, ollamaUrl)
                    ?: tryOpenRouter(prompt, settings.openrouterKey.ifBlank { settings.apiKey })
                    ?: tryClaude(prompt, settings.claudeKey.ifBlank { settings.apiKey })
                    ?: RouterResult(null, "NONE", false)
            }
        }
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
