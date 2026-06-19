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
class GeminiOrchestrator @Inject constructor(
    private val settingsRepository: SettingsRepository
) : AIOrchestrator {

    override val providerName = "Gemini (Google)"
    override val isAvailable: Boolean get() = true

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(systemPrompt: String, userMessage: String): String? =
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.getCurrentSettings()
            val key = settings.geminiKey
            if (key.isBlank()) return@withContext null
            runCatching {
                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", "$systemPrompt\n\n$userMessage") })
                            })
                        })
                    })
                }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key"
                val req = Request.Builder()
                    .url(url)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val resp = http.newCall(req).execute()
                val json = JSONObject(resp.body?.string() ?: return@runCatching null)
                json.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0)
                    .getString("text")
            }.getOrNull()
        }

    override suspend fun analyze(content: String, task: String): String? =
        complete("Eres JUPITER, sistema de análisis. Responde en español.", "$task\n\nContenido:\n$content")
}
