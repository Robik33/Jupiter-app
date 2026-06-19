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
class ClaudeOrchestrator @Inject constructor(
    private val settingsRepository: SettingsRepository
) : AIOrchestrator {

    override val providerName = "Claude (Anthropic)"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override val isAvailable: Boolean get() = true

    override suspend fun complete(systemPrompt: String, userMessage: String): String? =
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.getCurrentSettings()
            val key = settings.claudeKey.ifBlank { settings.apiKey }
            if (key.isBlank()) return@withContext null
            runCatching {
                val body = JSONObject().apply {
                    put("model", "claude-haiku-4-5-20251001")
                    put("max_tokens", 1024)
                    put("system", systemPrompt)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role","user"); put("content", userMessage) })
                    })
                }
                val req = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                    .build()
                val resp = http.newCall(req).execute()
                val json = JSONObject(resp.body?.string() ?: return@runCatching null)
                json.getJSONArray("content").getJSONObject(0).getString("text")
            }.getOrNull()
        }

    override suspend fun analyze(content: String, task: String): String? =
        complete("Eres JUPITER, sistema de análisis. Responde en español, formato JSON cuando se pida.", "$task\n\nContenido:\n$content")
}
