package com.marketia.jupiter.core.bridge

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

data class BridgeResult(
    val success: Boolean,
    val channel: BridgeChannel,
    val message: String,
    val issueUrl: String? = null,
    val updateUrl: String? = null
)

@Singleton
class ClaudeCodeBridge @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    suspend fun send(task: ClaudeCodeTask): BridgeResult = when (task.channel) {
        BridgeChannel.GITHUB_ISSUE -> sendViaGitHubIssue(task)
        BridgeChannel.HTTP_LOCAL   -> sendViaHttpLocal(task)
        BridgeChannel.TELEGRAM     -> sendViaTelegram(task)
    }

    private suspend fun sendViaGitHubIssue(task: ClaudeCodeTask): BridgeResult =
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.getCurrentSettings()
            if (settings.apiKey.isBlank()) {
                return@withContext BridgeResult(false, BridgeChannel.GITHUB_ISSUE,
                    "GitHub PAT no configurado. Configura en Config > API Key.")
            }

            runCatching {
                val body = JSONObject().apply {
                    put("title", "[JUPITER-TASK] ${task.goal.take(80)}")
                    put("body", buildIssueBody(task))
                    put("labels", org.json.JSONArray().apply {
                        put("jupiter-task")
                        put(task.priority.name.lowercase())
                    })
                }

                val req = Request.Builder()
                    .url("https://api.github.com/repos/Robik33/Jupiter-app/issues")
                    .post(body.toString().toRequestBody(JSON_MT))
                    .header("Authorization", "token ${settings.apiKey}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val resp = http.newCall(req).execute()
                val respBody = resp.body?.string() ?: ""
                val json = JSONObject(respBody)
                val url = json.optString("html_url", "")

                if (resp.isSuccessful && url.isNotBlank()) {
                    BridgeResult(true, BridgeChannel.GITHUB_ISSUE,
                        "Issue creado. Claude Code puede leer y ejecutar.", issueUrl = url)
                } else {
                    BridgeResult(false, BridgeChannel.GITHUB_ISSUE,
                        "Error GitHub ${resp.code}: ${json.optString("message")}")
                }
            }.getOrElse {
                BridgeResult(false, BridgeChannel.GITHUB_ISSUE, "Error de red: ${it.message}")
            }
        }

    private suspend fun sendViaHttpLocal(task: ClaudeCodeTask): BridgeResult =
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.getCurrentSettings()
            val url = "${settings.ollamaUrl.trimEnd('/')}/jupiter/task"
            runCatching {
                val req = Request.Builder()
                    .url(url)
                    .post(task.toJson().toRequestBody(JSON_MT))
                    .header("Content-Type", "application/json")
                    .build()
                val resp = http.newCall(req).execute()
                BridgeResult(resp.isSuccessful, BridgeChannel.HTTP_LOCAL,
                    if (resp.isSuccessful) "Tarea enviada al servidor local." else "Error HTTP ${resp.code}")
            }.getOrElse {
                BridgeResult(false, BridgeChannel.HTTP_LOCAL,
                    "No se pudo conectar a $url. Claude Code debe estar escuchando.")
            }
        }

    private suspend fun sendViaTelegram(task: ClaudeCodeTask): BridgeResult =
        BridgeResult(false, BridgeChannel.TELEGRAM,
            "Telegram bridge: configura BOT_TOKEN y CHAT_ID.")

    private fun buildIssueBody(task: ClaudeCodeTask): String {
        val filesSection = if (task.filesToChange.isNotEmpty())
            task.filesToChange.joinToString("\n") { "- `$it`" }
        else "Sin archivos especificados"

        val stepsSection = if (task.steps.isNotEmpty())
            task.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        else "Sin pasos especificados"

        return """
## [JUPITER_TASK] — ${task.priority.name}

**Versión**: ${task.version}
**Objetivo**: ${task.objective.ifBlank { task.goal }}

### Problema
${task.problem}

### Evidencia
${task.evidence}

### Cambio solicitado
${task.requestedChange}

### Archivos a modificar
$filesSection

### Pasos de implementación
$stepsSection

### Validación
${task.validation.ifBlank { "Verificar que el build compila y la funcionalidad es correcta." }}

### Resultado esperado
${task.expectedResult.ifBlank { task.goal }}

### JSON completo
```json
${task.toJson()}
```

---
*Generado automáticamente por JÚPITER Android v${task.version}*
*Label: `jupiter-task` — Claude Code PC puede leer y ejecutar este issue*
        """.trimIndent()
    }
}
