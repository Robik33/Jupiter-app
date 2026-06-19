package com.marketia.jupiter.core.orchestrator

import com.marketia.jupiter.core.ai.AIProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrchestratorFactory @Inject constructor(
    private val claude: ClaudeOrchestrator,
    private val openRouter: OpenRouterOrchestrator,
    private val gemini: GeminiOrchestrator,
    private val deepSeek: DeepSeekOrchestrator
) {
    fun get(provider: AIProvider): AIOrchestrator? = when (provider) {
        AIProvider.CLAUDE      -> claude
        AIProvider.OPENROUTER  -> openRouter
        AIProvider.GEMINI      -> gemini
        AIProvider.DEEPSEEK    -> deepSeek
        else                   -> null
    }

    fun all(): List<AIOrchestrator> = listOf(claude, openRouter, gemini, deepSeek)
}
