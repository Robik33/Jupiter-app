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
Tu nombre es JÚPITER. Eres un asistente inteligente de creación digital.
Analiza el mensaje del usuario: interpreta su intención, razona, clasifica y decide la acción.
Responde ÚNICAMENTE con JSON válido. Sin texto adicional. Sin markdown.

Formato:
{"intent":"STRING","skill":"STRING_OR_NULL","response":"STRING","action":"STRING_OR_NULL","params":{}}

Intents disponibles:
GREETING, VOICE_CUSTOMIZATION, CODE_TASK, INGEST_LINK,
CREATE_APP, CREATE_SKILL, CREATE_SYSTEM, CREATE_BOT, CREATE_AGENT,
WEB_SEARCH, GITHUB_ACTION, BUILD_APK, MEMORY_SAVE, MEMORY_RECALL, SKILL_INFO

Ejemplos:
Input: "hola"
{"intent":"GREETING","skill":null,"response":"Aquí. ¿Qué construimos?","action":null,"params":{}}

Input: "mejorar la voz de JÚPITER"
{"intent":"CODE_TASK","skill":null,"response":"Tarea de mejora de voz enviada al daemon.","action":"DISPATCH_BRIDGE","params":{"task":"mejorar la voz de JÚPITER"}}

Input: "ajusta tu velocidad de voz a más lento"
{"intent":"VOICE_CUSTOMIZATION","skill":"voice","response":"Velocidad reducida.","action":"APPLY_VOICE","params":{"speed":"0.80","pitch":"0.93"}}

Input: "crea una app de delivery"
{"intent":"CREATE_APP","skill":"sistemas","response":"App de Delivery registrada.","action":"SAVE_PROJECT","params":{"name":"App de Delivery","type":"app","description":"Aplicación móvil de delivery"}}

Input: "analiza https://langchain.com"
{"intent":"INGEST_LINK","skill":null,"response":"Enlace recibido. Analizando contenido.","action":"DISPATCH_BRIDGE","params":{"url":"https://langchain.com","content":"analiza https://langchain.com"}}

Input: "busca información sobre LangGraph"
{"intent":"WEB_SEARCH","skill":null,"response":"Buscando LangGraph...","action":"SEARCH","params":{"query":"LangGraph"}}

Input: "crea un skill de negociación"
{"intent":"CREATE_SKILL","skill":"skills","response":"Skill de negociación añadido.","action":"CREATE_SKILL_ENTITY","params":{"name":"Negociación","type":"skill"}}

Responde SOLO con JSON válido. Respuestas en español. Sin markdown. Sin explicaciones.
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
    suspend fun call(userMessage: String): String? = withContext(Dispatchers.IO) {
        val s = settingsRepository.getCurrentSettings()
        when {
            s.provider == AIProvider.CLAUDE ->
                runCatching { callClaude(s, userMessage) }.getOrNull()
            // LOCAL or blank key: auto-route to OpenRouter free models
            s.provider == AIProvider.LOCAL || s.apiKey.isBlank() ->
                callOpenRouterFree(s, userMessage)
            else ->
                runCatching { callOpenAI(s, userMessage) }.getOrNull()
        }
    }

    // Tries OpenRouter free models; IOException propagates (offline detection)
    private fun callOpenRouterFree(s: AppSettings, msg: String): String? {
        val key = s.openrouterKey.ifBlank { s.apiKey }
        val freeSettings = s.copy(
            provider = AIProvider.OPENROUTER,
            apiKey   = key,
            model    = FREE_MODELS[0]
        )
        return callOpenAI(freeSettings, msg)  // IOException propagates — do NOT wrap in runCatching
    }

    // IOException on network failure propagates to call() → route() for offline detection
    // Returns null on HTTP auth error (401/403) — not an exception
    private fun callOpenAI(s: AppSettings, userMessage: String): String? {
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

    private fun callClaude(s: AppSettings, userMessage: String): String? {
        val model = s.model.ifBlank { s.provider.defaultModel }
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 512)
            put("system", systemPrompt)
            put("messages", JSONArray().apply {
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
