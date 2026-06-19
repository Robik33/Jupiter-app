package com.marketia.jupiter.core.orchestrator

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
class DeepSeekOrchestrator @Inject constructor(
    private val settingsRepository: SettingsRepository
) : AIOrchestrator {

    override val providerName = "DeepSeek"
    override val isAvailable: Boolean get() = true

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(systemPrompt: String, userMessage: String): String? =
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.getCurrentSettings()
            val key = settings.deepseekKey
            if (key.isBlank()) return@withContext null
            runCatching {
                val body = JSONObject().apply {
                    put("model", "deepseek-chat")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role","system"); put("content", systemPrompt) })
                        put(JSONObject().apply { put("role","user"); put("content", userMessage) })
                    })
                }
                val req = Request.Builder()
                    .url("https://api.deepseek.com/v1/chat/completions")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer $key")
                    .build()
                val resp = http.newCall(req).execute()
                val json = JSONObject(resp.body?.string() ?: return@runCatching null)
                json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
            }.getOrNull()
        }

    override suspend fun analyze(content: String, task: String): String? =
        complete("Eres JUPITER, sistema de análisis. Responde en español.", "$task\n\nContenido:\n$content")
}
