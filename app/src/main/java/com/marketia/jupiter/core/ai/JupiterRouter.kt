package com.marketia.jupiter.core.ai

import com.marketia.jupiter.core.JupiterBrain
import com.marketia.jupiter.core.JupiterResponse
import com.marketia.jupiter.core.registry.ToolRegistry
import com.marketia.jupiter.data.repository.JupiterRepository
import com.marketia.jupiter.data.settings.SettingsRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JupiterRouter @Inject constructor(
    private val aiClient: JupiterAIClient,
    private val toolRegistry: ToolRegistry,
    private val settingsRepository: SettingsRepository,
    private val repository: JupiterRepository
) {
    suspend fun route(userInput: String): JupiterResponse {
        val settings = settingsRepository.getCurrentSettings()
        val useAI = (settings.provider != AIProvider.LOCAL && settings.apiKey.isNotBlank()) ||
                    settings.provider == AIProvider.OLLAMA ||
                    settings.provider == AIProvider.HERMES

        val result: RouterResult = if (useAI) {
            val raw = runCatching { aiClient.call(userInput) }.getOrNull()
            if (raw != null) parseAI(raw) else localRoute(userInput)
        } else {
            localRoute(userInput)
        }

        // Side-effect: execute tool if action maps to one
        handleSideEffect(result, userInput)

        return JupiterResponse(
            orderReceived = userInput,
            typeDetected  = result.intent,
            nextAction    = result.response,
            status        = when {
                result.intent == "UNKNOWN" -> "ESPERANDO"
                result.action != null      -> "EJECUTANDO"
                else                       -> "COMPLETADO"
            },
            action = result.action,
            params = result.params
        )
    }

    private fun parseAI(json: String): RouterResult {
        return runCatching {
            val clean = extractJson(json)
            val obj = JSONObject(clean)
            RouterResult(
                intent   = obj.optString("intent", "UNKNOWN").uppercase(),
                skill    = obj.optString("skill").takeIf { it.isNotBlank() && it != "null" },
                response = obj.optString("response", "Procesado."),
                action   = obj.optString("action").takeIf { it.isNotBlank() && it != "null" },
                params   = buildParamsMap(obj.optJSONObject("params"))
            )
        }.getOrElse { unknownResult() }
    }

    private fun localRoute(input: String): RouterResult {
        val b = JupiterBrain.process(input)
        return RouterResult(
            intent   = b.typeDetected,
            skill    = null,
            response = b.nextAction,
            action   = b.action,
            params   = b.params
        )
    }

    private suspend fun handleSideEffect(result: RouterResult, userInput: String) {
        when (result.action) {
            "SAVE_PROJECT" -> {
                val name = result.params["name"] ?: userInput
                repository.addProject(name, result.params["type"] ?: "proyecto", result.params["description"] ?: "")
            }
            "MEMORY_SAVE" -> {
                repository.addProject(result.params["content"] ?: userInput, "nota", "Guardado por voz")
            }
            "SEARCH" -> {
                val res = runCatching { toolRegistry.webSearch.execute(result.params) }.getOrDefault("")
                if (res.isNotBlank()) result.copy(response = res)
            }
        }
    }

    private fun extractJson(text: String): String {
        val s = text.indexOf('{'); val e = text.lastIndexOf('}')
        return if (s >= 0 && e > s) text.substring(s, e + 1) else text
    }

    private fun buildParamsMap(obj: JSONObject?): Map<String, String> {
        obj ?: return emptyMap()
        return obj.keys().asSequence().associateWith { obj.optString(it) }
    }

    private fun unknownResult() = RouterResult(
        intent = "UNKNOWN", skill = null,
        response = "¿Quieres que lo convierta en un skill, ajuste de voz, proyecto o búsqueda web?",
        action = "CLARIFY", params = emptyMap()
    )

    data class RouterResult(
        val intent: String, val skill: String?,
        val response: String, val action: String?,
        val params: Map<String, String>
    )
}
