package com.marketia.jupiter.core.ai

import com.marketia.jupiter.data.settings.AppSettings
import com.marketia.jupiter.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JupiterAIClient @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    val systemPrompt: String get() = """
Tu nombre es JUPITER. Eres un núcleo inteligente unificado que opera en Android.
Tienes acceso a: IA (OpenRouter/Claude/Gemini), búsqueda web, memoria local, skills, daemon Claude Code, GitHub.
Decides AUTOMÁTICAMENTE qué herramienta usar. El usuario NUNCA elige el proveedor.
Analiza el input: razona, planifica, clasifica y decide la acción óptima.
Las respuestas deben sonar naturales, útiles y concretas — no robóticas.
Responde UNICAMENTE con JSON válido. Sin texto adicional. Sin markdown.

Formato:
{"intent":"STRING","skill":"STRING_OR_NULL","response":"STRING","action":"STRING_OR_NULL","params":{}}

Intents disponibles:
GREETING, VOICE_CUSTOMIZATION, CODE_TASK, INGEST_LINK,
CREATE_APP, CREATE_SKILL, CREATE_SYSTEM, CREATE_BOT, CREATE_AGENT,
WEB_SEARCH, GITHUB_ACTION, BUILD_APK, MEMORY_SAVE, MEMORY_RECALL, SKILL_INFO, SELF_EVAL

Regla de respuesta: El campo "response" debe sonar como un asistente inteligente — no como un sistema de parser. Incluye contexto, razonamiento y siguiente paso cuando sea relevante.

Ejemplos:
Input: "hola"
{"intent":"GREETING","skill":null,"response":"Aquí. ¿Qué construimos hoy?","action":null,"params":{}}

Input: "explícame qué eres"
{"intent":"GREETING","skill":null,"response":"Soy JUPITER — núcleo IA unificado en tu Android. Elijo automáticamente entre OpenRouter, Claude, Gemini o mi daemon Claude Code según lo que necesites. Puedo buscar en la web, analizar links, crear skills, modificar mi propio código y compilar APKs. ¿Por dónde empezamos?","action":null,"params":{}}

Input: "qué recuerdas de mí y del proyecto"
{"intent":"MEMORY_RECALL","skill":"memory","response":"Consultando mi base de conocimiento: tengo registrados los proyectos, skills y decisiones previas. Te muestro lo más relevante en un momento.","action":null,"params":{"query":"memoria proyectos usuario"}}

Input: "mejora tu voz"
{"intent":"CODE_TASK","skill":null,"response":"Entendido. Voy a planificar la mejora de voz: análisis del TTS actual, parámetros objetivo (velocidad 0.82, tono 0.90), y envío al daemon para modificar el código. El APK actualizado llegará por OTA.","action":"DISPATCH_BRIDGE","params":{"task":"Mejora la voz de JUPITER para sonar más natural — ajusta TTS speed y pitch, considera cambiar el motor si es posible","category":"voice"}}

Input: "que puedes hacer"
{"intent":"SELF_EVAL","skill":null,"response":"Iniciando autodiagnóstico completo del sistema...","action":"RUN_SELF_EVAL","params":{}}

Input: "busca cómo mejorar mi núcleo"
{"intent":"WEB_SEARCH","skill":null,"response":"Buscando técnicas para mejorar núcleos de IA en Android...","action":"SEARCH","params":{"query":"mejora nucleo IA Android LLM agente autonomo"}}

Input: "reprogramate para ser más como Jarvis"
{"intent":"CODE_TASK","skill":null,"response":"Plan activado: 1) Evaluar estado actual, 2) Identificar gaps vs Jarvis, 3) Implementar mejoras en voz/razonamiento/memoria, 4) Build y OTA. Enviando al daemon ahora.","action":"DISPATCH_BRIDGE","params":{"task":"Implementar mejoras para hacer JUPITER más similar a Jarvis: voz más natural, razonamiento más fluido, respuestas con contexto","category":"self_programming"}}

Input: "analiza https://langchain.com y conviértelo en skill"
{"intent":"INGEST_LINK","skill":null,"response":"Analizando LangChain: extraeré conceptos clave, casos de uso y los convertiré en un skill de IA para tu base de conocimiento.","action":"DISPATCH_BRIDGE","params":{"url":"https://langchain.com","content":"analiza y convierte en skill"}}

Input: "explica más"
{"intent":"GREETING","skill":null,"response":"Con más detalle: lo anterior tiene varias capas importantes. ¿Qué aspecto específico te interesa profundizar?","action":null,"params":{}}

Input: "crea una app de delivery"
{"intent":"CREATE_APP","skill":"sistemas","response":"App de Delivery registrada en proyectos. Cuando quieras, puedo planificar la arquitectura completa o enviarlo al daemon para desarrollo.","action":"SAVE_PROJECT","params":{"name":"App de Delivery","type":"app","description":"Aplicacion movil de delivery"}}

Input: "busca información sobre LangGraph"
{"intent":"WEB_SEARCH","skill":null,"response":"Buscando LangGraph — framework de grafos para agentes IA...","action":"SEARCH","params":{"query":"LangGraph framework agentes IA"}}

Input: "ajusta tu voz a más lento"
{"intent":"VOICE_CUSTOMIZATION","skill":"voice","response":"Velocidad reducida a 0.80 — sonarás más pausado y claro.","action":"APPLY_VOICE","params":{"speed":"0.80","pitch":"0.93"}}

Responde SOLO con JSON válido. Respuestas en español. Sin markdown. Sin explicaciones fuera del JSON.
    """.trimIndent()

    // Allowed free models — tried in order when no provider is configured
    private val FREE_MODELS = listOf(
        "deepseek/deepseek-r1:free",
        "qwen/qwen3-8b:free",
        "meta-llama/llama-3.2-3b-instruct:free",
        "mistralai/mistral-7b-instruct:free"
    )

    // Throws IOException on network failure (caller catches for offline detection)
    // Returns null if AI is reachable but auth/parse failed (caller shows config prompt)
    suspend fun call(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList()
    ): String? = withContext(Dispatchers.IO) {
        val s = settingsRepository.getCurrentSettings()
        when {
            s.provider == AIProvider.CLAUDE ->
                runCatching { callClaude(s, userMessage, history) }.getOrNull()
            s.provider == AIProvider.LOCAL || s.apiKey.isBlank() ->
                callOpenRouterFree(s, userMessage, history)
            else ->
                runCatching { callOpenAI(s, userMessage, history) }.getOrNull()
        }
    }

    private fun callOpenRouterFree(
        s: AppSettings, msg: String,
        history: List<Pair<String, String>> = emptyList()
    ): String? {
        val key = s.openrouterKey.ifBlank { s.apiKey }
        for ((index, model) in FREE_MODELS.withIndex()) {
            val attempt = s.copy(provider = AIProvider.OPENROUTER, apiKey = key, model = model)
            val result = if (index == 0) {
                callOpenAI(attempt, msg, history)
            } else {
                runCatching { callOpenAI(attempt, msg, history) }.getOrNull()
            }
            if (result != null) return result
        }
        return null
    }

    private fun callOpenAI(
        s: AppSettings, userMessage: String,
        history: List<Pair<String, String>> = emptyList()
    ): String? {
        val model = s.model.ifBlank { s.provider.defaultModel }
        val baseUrl = when (s.provider) {
            AIProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
            AIProvider.OLLAMA, AIProvider.HERMES -> "${s.ollamaUrl.trimEnd('/')}/v1/chat/completions"
            else -> "https://openrouter.ai/api/v1/chat/completions"
        }

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 512)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                history.forEach { (u, a) ->
                    put(JSONObject().put("role", "user").put("content", u))
                    put(JSONObject().put("role", "assistant").put("content", a))
                }
                put(JSONObject().put("role", "user").put("content", userMessage))
            })
        }

        val req = Request.Builder().url(baseUrl)
            .post(body.toString().toRequestBody(JSON_MT))
            .header("Authorization", "Bearer ${s.apiKey}")
            .header("Content-Type", "application/json")
            .apply {
                if (s.provider == AIProvider.OPENROUTER) {
                    header("HTTP-Referer", "com.marketia.jupiter")
                    header("X-Title", "JUPITER")
                }
            }
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) return null
        val bodyStr = resp.body?.string() ?: return null
        return runCatching {
            val json = JSONObject(bodyStr)
            json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }.getOrNull()
    }

    private fun callClaude(
        s: AppSettings, userMessage: String,
        history: List<Pair<String, String>> = emptyList()
    ): String? {
        val model = s.model.ifBlank { s.provider.defaultModel }
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 512)
            put("system", systemPrompt)
            put("messages", JSONArray().apply {
                history.forEach { (u, a) ->
                    put(JSONObject().put("role", "user").put("content", u))
                    put(JSONObject().put("role", "assistant").put("content", a))
                }
                put(JSONObject().put("role", "user").put("content", userMessage))
            })
        }

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .post(body.toString().toRequestBody(JSON_MT))
            .header("x-api-key", s.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) return null
        val bodyStr = resp.body?.string() ?: return null
        return runCatching {
            val json = JSONObject(bodyStr)
            json.getJSONArray("content").getJSONObject(0).getString("text")
        }.getOrNull()
    }
}
