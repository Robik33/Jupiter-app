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
Tu nombre es JÚPITER. Eres un asistente de creación digital avanzado.
Analiza el mensaje del usuario y responde ÚNICAMENTE con JSON válido. Sin texto adicional.

Formato obligatorio:
{"intent":"STRING","skill":"STRING_OR_NULL","response":"STRING","action":"STRING_OR_NULL","params":{}}

Intents disponibles:
VOICE_CUSTOMIZATION, CREATE_APP, CREATE_SKILL, CREATE_SYSTEM, CREATE_BOT, CREATE_AGENT,
WEB_SEARCH, GITHUB_ACTION, BUILD_APK, MEMORY_SAVE, MEMORY_RECALL, SKILL_INFO, GREETING, UNKNOWN

Ejemplos:
Input: "quiero que te crees una voz más humana"
{"intent":"VOICE_CUSTOMIZATION","skill":"voice","response":"Ajustando mi voz para sonar más natural. Reduciré la velocidad y suavizaré el tono.","action":"APPLY_VOICE","params":{"speed":"0.82","pitch":"0.90"}}

Input: "busca información sobre LangGraph"
{"intent":"WEB_SEARCH","skill":null,"response":"Buscando LangGraph en la web...","action":"SEARCH","params":{"query":"LangGraph"}}

Input: "crea una app de delivery"
{"intent":"CREATE_APP","skill":"sistemas","response":"Iniciando proyecto: App de Delivery. Registrando en memoria...","action":"SAVE_PROJECT","params":{"name":"App de Delivery","type":"app","description":"Aplicación móvil de delivery"}}

Input: "quiero crear un bot de trading"
{"intent":"CREATE_BOT","skill":"finanzas","response":"Iniciando arquitectura del bot de trading. Registrando en proyectos...","action":"SAVE_PROJECT","params":{"name":"Bot de Trading","type":"bot","description":"Bot automático de trading"}}

Para UNKNOWN usa exactamente:
{"intent":"UNKNOWN","skill":null,"response":"¿Quieres que lo convierta en un skill, ajuste de voz, proyecto o búsqueda web?","action":"CLARIFY","params":{}}

Responde SOLO con JSON válido. Respuestas en español. Sin markdown. Sin explicaciones.
    """.trimIndent()

    suspend fun call(userMessage: String): String? = withContext(Dispatchers.IO) {
        val s = settingsRepository.getCurrentSettings()
        runCatching {
            when {
                s.provider == AIProvider.LOCAL || s.apiKey.isBlank() && !s.provider.usesOpenAIFormat && s.provider != AIProvider.OLLAMA -> null
                s.provider == AIProvider.CLAUDE -> callClaude(s, userMessage)
                else -> callOpenAI(s, userMessage)
            }
        }.getOrNull()
    }

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
            put("response_format", JSONObject().put("type", "json_object"))
        }

        val req = Request.Builder().url(baseUrl)
            .post(body.toString().toRequestBody(JSON_MT))
            .header("Authorization", "Bearer ${s.apiKey}")
            .header("Content-Type", "application/json")
            .apply {
                if (s.provider == AIProvider.OPENROUTER) {
                    header("HTTP-Referer", "com.marketia.jupiter")
                    header("X-Title", "JÚPITER")
                }
            }
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) return null
        val json = JSONObject(resp.body?.string() ?: return null)
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
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
        val json = JSONObject(resp.body?.string() ?: return null)
        return json.getJSONArray("content").getJSONObject(0).getString("text")
    }
}
