package com.marketia.jupiter.core.autonomy

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

data class OllamaStatus(
    val available: Boolean,
    val models: List<String>,
    val activeModel: String,
    val url: String,
    val error: String = ""
)

@Singleton
class OllamaRouter @Inject constructor() {

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val preferredModels = listOf(
        "qwen2.5-coder", "deepseek-coder", "deepseek-r1",
        "llama3.1", "llama3.2", "mistral", "phi3", "gemma2"
    )

    suspend fun checkStatus(baseUrl: String = "http://localhost:11434"): OllamaStatus =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url("$baseUrl/api/tags").build()
                val resp = http.newCall(req).execute()
                if (!resp.isSuccessful) return@runCatching OllamaStatus(
                    available = false, models = emptyList(), activeModel = "", url = baseUrl,
                    error = "HTTP ${resp.code}"
                )
                val body = resp.body?.string() ?: return@runCatching OllamaStatus(
                    available = false, models = emptyList(), activeModel = "", url = baseUrl,
                    error = "Empty response"
                )
                val modelsArr: JSONArray = JSONObject(body).optJSONArray("models") ?: JSONArray()
                val models = (0 until modelsArr.length())
                    .map { modelsArr.getJSONObject(it).optString("name", "") }
                    .filter { it.isNotBlank() }
                val best = selectBestModel(models)
                OllamaStatus(available = true, models = models, activeModel = best, url = baseUrl)
            }.getOrElse {
                OllamaStatus(
                    available = false, models = emptyList(), activeModel = "",
                    url = baseUrl, error = it.message ?: "Ollama no disponible"
                )
            }
        }

    suspend fun complete(
        prompt: String,
        model: String? = null,
        baseUrl: String = "http://localhost:11434",
        systemPrompt: String = "Eres JUPITER. Responde en español de forma precisa y concisa."
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val status = checkStatus(baseUrl)
            if (!status.available) return@runCatching null
            val selectedModel = model ?: status.activeModel
            if (selectedModel.isBlank()) return@runCatching null

            val body = JSONObject().apply {
                put("model", selectedModel)
                put("stream", false)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                })
            }
            val req = Request.Builder()
                .url("$baseUrl/api/chat")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching null
            val json = JSONObject(resp.body?.string() ?: return@runCatching null)
            json.optJSONObject("message")?.optString("content")
        }.getOrNull()
    }

    fun selectBestModel(availableModels: List<String>): String {
        for (preferred in preferredModels) {
            val match = availableModels.firstOrNull { it.contains(preferred, ignoreCase = true) }
            if (match != null) return match
        }
        return availableModels.firstOrNull() ?: ""
    }

    fun estimatedCostLabel(): String = "GRATIS (local)"
}
