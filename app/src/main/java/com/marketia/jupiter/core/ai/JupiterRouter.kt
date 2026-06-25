package com.marketia.jupiter.core.ai

import com.marketia.jupiter.core.JupiterBrain
import com.marketia.jupiter.core.JupiterResponse
import com.marketia.jupiter.core.registry.ToolRegistry
import com.marketia.jupiter.data.repository.JupiterRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JupiterRouter @Inject constructor(
    private val aiClient: JupiterAIClient,
    private val toolRegistry: ToolRegistry,
    private val repository: JupiterRepository
) {
    suspend fun route(userInput: String): JupiterResponse {
        // Always try AI first — local only if no internet (IOException)
        val result: RouterResult = try {
            val raw = aiClient.call(userInput)
            if (raw != null) parseAI(raw) else localRoute(userInput)
        } catch (_: java.net.UnknownHostException) {
            localRoute(userInput)   // No DNS → offline → local rules
        } catch (_: java.net.SocketException) {
            localRoute(userInput)   // Connection reset → offline
        } catch (_: java.net.SocketTimeoutException) {
            localRoute(userInput)   // Timeout → treat as offline
        } catch (_: Exception) {
            localRoute(userInput)   // Any other network failure
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
            "SAVE_SYSTEM" -> {
                val name = result.params["name"] ?: userInput
                repository.addSystem(name, result.params["type"] ?: "sistema", result.params["description"] ?: "")
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

    private fun noProviderResult() = RouterResult(
        intent   = "CONFIG",
        skill    = null,
        response = "Sin clave IA activa. Obtén tu clave gratuita en openrouter.ai y añádela en JÚPITER → Configuración.",
        action   = "OPEN_SETTINGS",
        params   = emptyMap()
    )

    private fun unknownResult() = RouterResult(
        intent = "UNKNOWN", skill = null,
        response = "Sin conexión. Comandos offline: 'crear skill', 'mejorar algo', o pega un enlace.",
        action = "CLARIFY", params = emptyMap()
    )

    data class RouterResult(
        val intent: String, val skill: String?,
        val response: String, val action: String?,
        val params: Map<String, String>
    )
}
