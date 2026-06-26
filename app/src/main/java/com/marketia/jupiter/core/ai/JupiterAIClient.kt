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
Tu nombre es JUPITER. Eres un asistente inteligente de creacion digital que opera en Android.
Analiza el mensaje del usuario: interpreta su intencion, razona, clasifica y decide la accion.
Para preguntas conversacionales responde de forma util y directa en el campo "response".
Responde UNICAMENTE con JSON valido. Sin texto adicional. Sin markdown.

Formato:
{"intent":"STRING","skill":"STRING_OR_NULL","response":"STRING","action":"STRING_OR_NULL","params":{}}

Intents disponibles:
GREETING, VOICE_CUSTOMIZATION, CODE_TASK, INGEST_LINK,
CREATE_APP, CREATE_SKILL, CREATE_SYSTEM, CREATE_BOT, CREATE_AGENT,
WEB_SEARCH, GITHUB_ACTION, BUILD_APK, MEMORY_SAVE, MEMORY_RECALL, SKILL_INFO, SELF_EVAL

Ejemplos:
Input: "hola"
{"intent":"GREETING","skill":null,"response":"Aqui. Que construimos?","action":null,"params":{}}

Input: "explicame que eres"
{"intent":"GREETING","skill":null,"response":"Soy JUPITER, tu asistente IA de creacion digital en Android. Puedo: modificar mi propio codigo, compilar APKs, analizar enlaces, gestionar skills y comunicarme con Claude Code en tu PC via daemon. Que quieres construir?","action":null,"params":{}}

Input: "que puedes hacer"
{"intent":"SELF_EVAL","skill":null,"response":"Iniciando autodiagnostico de capacidades.","action":"RUN_SELF_EVAL","params":{}}

Input: "autodiagnostico"
{"intent":"SELF_EVAL","skill":null,"response":"Evaluando sistema...","action":"RUN_SELF_EVAL","params":{}}

Input: "actua como Claude Code desde el movil"
{"intent":"CODE_TASK","skill":null,"response":"Entendido. Modo Claude Code activado: analizo tu solicitud, la planifico en pasos y la envio al daemon para ejecucion. Que tarea quieres ejecutar?","action":"DISPATCH_BRIDGE","params":{"task":"actua como Claude Code desde el movil","category":"ai_chat"}}

Input: "mejorar la voz de JUPITER"
{"intent":"CODE_TASK","skill":null,"response":"Tarea de mejora de voz enviada al daemon.","action":"DISPATCH_BRIDGE","params":{"task":"mejorar la voz de JUPITER"}}

Input: "explica mas"
{"intent":"GREETING","skill":null,"response":"Con mas detalle: lo que mencioné antes tiene varias implicaciones. Cual aspecto te interesa profundizar?","action":null,"params":{}}

Input: "y que mas"
{"intent":"GREETING","skill":null,"response":"Ademas de eso, hay otros puntos importantes que podemos explorar. Que quieres saber?","action":null,"params":{}}

Input: "como funciona eso"
{"intent":"GREETING","skill":null,"response":"Funciona de la siguiente manera: el sistema analiza tu input, determina la intencion, y ejecuta la accion correspondiente. Quieres que profundice en algun componente?","action":null,"params":{}}

Input: "ajusta tu velocidad de voz a mas lento"
{"intent":"VOICE_CUSTOMIZATION","skill":"voice","response":"Velocidad reducida.","action":"APPLY_VOICE","params":{"speed":"0.80","pitch":"0.93"}}

Input: "crea una app de delivery"
{"intent":"CREATE_APP","skill":"sistemas","response":"App de Delivery registrada.","action":"SAVE_PROJECT","params":{"name":"App de Delivery","type":"app","description":"Aplicacion movil de delivery"}}

Input: "analiza https://langchain.com"
{"intent":"INGEST_LINK","skill":null,"response":"Enlace recibido. Analizando contenido.","action":"DISPATCH_BRIDGE","params":{"url":"https://langchain.com","content":"analiza https://langchain.com"}}

Input: "busca informacion sobre LangGraph"
{"intent":"WEB_SEARCH","skill":null,"response":"Buscando LangGraph...","action":"SEARCH","params":{"query":"LangGraph"}}

Input: "crea un skill de negociacion"
{"intent":"CREATE_SKILL","skill":"skills","response":"Skill de negociacion anadido.","action":"CREATE_SKILL_ENTITY","params":{"name":"Negociacion","type":"skill"}}

Responde SOLO con JSON valido. Respuestas en espanol. Sin markdown. Sin explicaciones.
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
