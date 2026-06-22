package com.marketia.jupiter.core.oracle

import com.marketia.jupiter.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class McpProcess(
    val name: String,
    val priority: Int,
    val alive: Boolean,
    val pid: Int = 0
)

data class McpTopology(
    val ts: String = "",
    val processes: List<McpProcess> = emptyList(),
    val diskFreeGb: Double = 0.0,
    val internet: String = "UNKNOWN",
    val cycle: Int = 0,
    val error: String = ""
)

@Singleton
class McpConnector @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchTopology(): McpTopology = withContext(Dispatchers.IO) {
        runCatching {
            val s = settingsRepository.getCurrentSettings()
            val base = s.ollamaUrl.trimEnd('/')
            val req = Request.Builder().url("$base/status").get().build()
            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching McpTopology(error = "HTTP ${resp.code}")
            val j = JSONObject(resp.body?.string() ?: "{}")
            val procs = j.optJSONObject("processes") ?: JSONObject()
            val processList = mutableListOf<McpProcess>()
            procs.keys().forEach { key ->
                val val_ = procs.optString(key, "DEAD")
                val alive = val_.startsWith("ALIVE")
                val pid = Regex("pid=(\\d+)").find(val_)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                processList.add(McpProcess(name = key, priority = 0, alive = alive, pid = pid))
            }
            McpTopology(
                ts = j.optString("ts", ""),
                processes = processList,
                cycle = j.optInt("cycle", 0)
            )
        }.getOrElse { McpTopology(error = it.message ?: "failed") }
    }
}
