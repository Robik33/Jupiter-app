package com.marketia.jupiter.core.engine

import com.marketia.jupiter.core.ai.AIProvider
import com.marketia.jupiter.core.ai.JupiterAIClient
import com.marketia.jupiter.core.orchestrator.OrchestratorFactory
import com.marketia.jupiter.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ProviderResult(
    val text: String,
    val provider: String,
    val isLocal: Boolean = false
)

@Singleton
class ProviderRouter @Inject constructor(
    private val aiClient: JupiterAIClient,
    private val factory: OrchestratorFactory,
    private val settingsRepository: SettingsRepository
) {
    suspend fun complete(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList()
    ): ProviderResult = withContext(Dispatchers.IO) {
        // 1. JupiterAIClient: configured provider + OpenRouter free 4-model fallback
        val aiResult = runCatching { aiClient.call(userMessage, history) }.getOrNull()
        if (aiResult != null) {
            val s = settingsRepository.getCurrentSettings()
            val label = if (s.provider == AIProvider.LOCAL || s.apiKey.isBlank()) "OpenRouter/free"
                        else s.provider.label
            return@withContext ProviderResult(aiResult, label)
        }

        // 2-4. Fallback chain: Claude → Gemini → DeepSeek (with available keys)
        val s = settingsRepository.getCurrentSettings()
        val chain = listOfNotNull(
            if (s.claudeKey.isNotBlank() && s.provider != AIProvider.CLAUDE)
                AIProvider.CLAUDE else null,
            if (s.geminiKey.isNotBlank() && s.provider != AIProvider.GEMINI)
                AIProvider.GEMINI else null,
            if (s.deepseekKey.isNotBlank() && s.provider != AIProvider.DEEPSEEK)
                AIProvider.DEEPSEEK else null
        )
        for (provider in chain) {
            val orch = factory.get(provider) ?: continue
            val r = runCatching { orch.complete(aiClient.systemPrompt, userMessage) }.getOrNull()
            if (r != null) return@withContext ProviderResult(r, provider.label)
        }

        // 5. Local fallback — always returns valid JSON, dispatches to bridge
        ProviderResult(buildLocalFallback(userMessage), "LOCAL", isLocal = true)
    }

    private fun buildLocalFallback(input: String): String {
        val q = input.take(100).replace("\"", "'")
        return """{"intent":"AI_CHAT","skill":null,"response":"Sin acceso a IA en este momento. Tu consulta fue recibida — el daemon la procesará cuando haya conexión. ¿Puedo ayudarte con algo de mi base de conocimiento local?","action":"DISPATCH_BRIDGE","params":{"query":"$q","category":"offline_queue"}}"""
    }
}
