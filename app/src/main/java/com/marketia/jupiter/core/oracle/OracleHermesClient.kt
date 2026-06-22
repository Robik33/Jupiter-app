package com.marketia.jupiter.core.oracle

import com.marketia.jupiter.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class OracleState(
    val connected: Boolean    = false,
    val btcPrice: Double      = 0.0,
    val entropy: Double       = 0.0,
    val tradeAllowed: Boolean = false,
    val blocker: String       = "UNKNOWN",
    val hermesCycle: Int      = 0,
    val wisdomSummary: String = "",
    val autopsySummary: String= "",
    val error: String         = ""
)

@Singleton
class OracleHermesClient @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private suspend fun hermesBase(): String {
        val s = settingsRepository.getCurrentSettings()
        return s.ollamaUrl.trimEnd('/')
    }

    suspend fun fetchOracleState(): OracleState = withContext(Dispatchers.IO) {
        runCatching {
            val base = hermesBase()
            val req = Request.Builder().url("$base/oracle").get().build()
            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching OracleState(error = "HTTP ${resp.code}")
            val j = JSONObject(resp.body?.string() ?: "{}")
            OracleState(
                connected     = true,
                btcPrice      = j.optDouble("btc", 0.0),
                entropy       = j.optDouble("entropy", 0.0),
                tradeAllowed  = j.optBoolean("trade_allowed", false),
                blocker       = j.optString("blocker", "NONE"),
                hermesCycle   = j.optInt("hermes_cycle", 0),
                wisdomSummary = formatWisdom(j.optJSONObject("wisdom")),
                autopsySummary= formatAutopsy(j.optJSONObject("autopsy"))
            )
        }.getOrElse { OracleState(error = it.message ?: "connect failed") }
    }

    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    suspend fun sendPromptSync(message: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val base = hermesBase()
            val bodyJson = JSONObject().apply {
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().put("role", "user").put("content", message))
                })
                put("model", "oracle-hermes")
            }.toString()
            val req = Request.Builder()
                .url("$base/v1/chat/completions")
                .post(bodyJson.toRequestBody(JSON_MT))
                .build()
            val resp = http.newCall(req).execute()
            val j = JSONObject(resp.body?.string() ?: "{}")
            j.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?: "[HERMES] Sin respuesta"
        }.getOrElse { "[HERMES ERROR] ${it.message}" }
    }

    private fun formatWisdom(w: JSONObject?): String {
        w ?: return ""
        val n   = w.optInt("total_trades", 0)
        val wr  = w.optDouble("collective_wr", 0.0)
        val pf  = w.optDouble("collective_pf", 0.0)
        return "N=$n WR=${"%.1f".format(wr * 100)}% PF=${"%.3f".format(pf)}"
    }

    private fun formatAutopsy(a: JSONObject?): String {
        a ?: return ""
        val rec = a.optString("recommendation", "")
        return rec.take(80)
    }
}
