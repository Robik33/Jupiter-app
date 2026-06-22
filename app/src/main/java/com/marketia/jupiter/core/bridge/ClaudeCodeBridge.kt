package com.marketia.jupiter.core.bridge

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

data class BridgeResult(
    val success: Boolean,
    val channel: BridgeChannel,
    val message: String,
    val issueUrl: String? = null,
    val updateUrl: String? = null
)

enum class IssueStatus { PENDING, RUNNING, DONE, BLOCKED, UNKNOWN }

data class RemotePollResult(
    val status: IssueStatus,
    val result: String = "",
    val apkUrl: String = "",
    val releaseUrl: String = "",
    val blockedReason: String = ""
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
            if (settings.githubPat.isBlank()) {
                return@withContext BridgeResult(false, BridgeChannel.GITHUB_ISSUE,
                    "GitHub PAT no configurado. Configura en Config > GitHub PAT.")
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
                    .header("Authorization", "token ${settings.githubPat}")
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

    suspend fun pollIssueStatus(issueUrl: String): IssueStatus = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getCurrentSettings()
        if (settings.githubPat.isBlank()) return@withContext IssueStatus.UNKNOWN
        val apiUrl = issueUrl
            .replace("https://github.com/", "https://api.github.com/repos/")
            .replace("/issues/", "/issues/")
        runCatching {
            val req = Request.Builder()
                .url(apiUrl)
                .get()
                .header("Authorization", "token ${settings.githubPat}")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val resp = http.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: return@runCatching IssueStatus.UNKNOWN)
            val state = json.optString("state", "open")
            val labels = json.optJSONArray("labels")
            val labelNames = (0 until (labels?.length() ?: 0)).map {
                labels!!.getJSONObject(it).optString("name")
            }
            when {
                "jupiter-done"    in labelNames -> IssueStatus.DONE
                "jupiter-running" in labelNames -> IssueStatus.RUNNING
                "jupiter-blocked" in labelNames -> IssueStatus.BLOCKED
                state == "closed"               -> IssueStatus.DONE
                else                            -> IssueStatus.PENDING
            }
        }.getOrDefault(IssueStatus.UNKNOWN)
    }

    suspend fun pollFull(issueUrl: String): RemotePollResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getCurrentSettings()
        if (settings.githubPat.isBlank()) return@withContext RemotePollResult(IssueStatus.UNKNOWN)
        val apiUrl = issueUrl
            .replace("https://github.com/", "https://api.github.com/repos/")

        runCatching {
            val authHeader = "token ${settings.githubPat}"
            val accept = "application/vnd.github.v3+json"

            // Fetch issue state + labels
            val issueReq = Request.Builder().url(apiUrl).get()
                .header("Authorization", authHeader).header("Accept", accept).build()
            val issueResp = http.newCall(issueReq).execute()
            val issueJson = JSONObject(issueResp.body?.string() ?: return@runCatching RemotePollResult(IssueStatus.UNKNOWN))

            val issueState = issueJson.optString("state", "open")
            val labels = issueJson.optJSONArray("labels")
            val labelNames = (0 until (labels?.length() ?: 0)).map {
                labels!!.getJSONObject(it).optString("name")
            }

            val status = when {
                "jupiter-done"    in labelNames -> IssueStatus.DONE
                "jupiter-running" in labelNames -> IssueStatus.RUNNING
                "jupiter-blocked" in labelNames -> IssueStatus.BLOCKED
                issueState == "closed"          -> IssueStatus.DONE
                else                            -> IssueStatus.PENDING
            }

            // Fetch comments to extract structured data
            val commentsReq = Request.Builder().url("$apiUrl/comments?per_page=20").get()
                .header("Authorization", authHeader).header("Accept", accept).build()
            val commentsResp = http.newCall(commentsReq).execute()
            val commentsBody = commentsResp.body?.string() ?: "[]"
            val commentsArray = runCatching { JSONArray(commentsBody) }.getOrDefault(JSONArray())

            var apkUrl = ""
            var releaseUrl = ""
            var result = ""
            var blockedReason = ""

            // Scan comments from newest to oldest
            for (i in (commentsArray.length() - 1) downTo 0) {
                val body = commentsArray.getJSONObject(i).optString("body", "")
                apkUrl       = apkUrl.ifBlank { extractTag(body, "APK_URL") }
                releaseUrl   = releaseUrl.ifBlank { extractTag(body, "RELEASE_URL") }
                result       = result.ifBlank { extractTag(body, "RESULT") }
                blockedReason = blockedReason.ifBlank { extractTag(body, "BLOCKED") }
                if (apkUrl.isNotBlank() && releaseUrl.isNotBlank() && result.isNotBlank()) break
            }

            RemotePollResult(status, result, apkUrl, releaseUrl, blockedReason)
        }.getOrDefault(RemotePollResult(IssueStatus.UNKNOWN))
    }

    private fun extractTag(body: String, tag: String): String {
        val patterns = listOf(
            Regex("\\*\\*$tag\\*\\*:?\\s*([^\\n]+)"),  // **TAG**: value
            Regex("$tag:?\\s*([^\\n]+)"),                // TAG: value
            Regex("`$tag`[:\\s]+([^\\n]+)")              // `TAG`: value
        )
        for (p in patterns) {
            val match = p.find(body) ?: continue
            val value = match.groupValues[1].trim().trimEnd('.')
            if (value.isNotBlank()) return value
        }
        return ""
    }

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
