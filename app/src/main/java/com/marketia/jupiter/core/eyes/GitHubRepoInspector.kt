package com.marketia.jupiter.core.eyes

import com.marketia.jupiter.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class RepoSnapshot(
    val name: String,
    val latestTag: String,
    val openIssues: Int,
    val lastCommit: String,
    val jupiterTasks: List<String>,
    val updateAvailable: Boolean,
    val latestReleaseUrl: String
)

@Singleton
class GitHubRepoInspector @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun inspect(repo: String = "Robik33/Jupiter-app"): RepoSnapshot =
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.getCurrentSettings()
            val authHeader = if (settings.apiKey.isNotBlank()) "token ${settings.apiKey}" else ""

            fun get(url: String): JSONObject? {
                return runCatching {
                    val req = Request.Builder().url(url)
                        .apply { if (authHeader.isNotBlank()) header("Authorization", authHeader) }
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val resp = http.newCall(req).execute()
                    if (resp.isSuccessful) JSONObject(resp.body?.string() ?: return@runCatching null)
                    else null
                }.getOrNull()
            }

            fun getArray(url: String): org.json.JSONArray? {
                return runCatching {
                    val req = Request.Builder().url(url)
                        .apply { if (authHeader.isNotBlank()) header("Authorization", authHeader) }
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val resp = http.newCall(req).execute()
                    if (resp.isSuccessful) org.json.JSONArray(resp.body?.string() ?: return@runCatching null)
                    else null
                }.getOrNull()
            }

            val base = "https://api.github.com/repos/$repo"

            // Latest release
            val release = get("$base/releases/latest")
            val latestTag = release?.optString("tag_name", "desconocido") ?: "desconocido"
            val releaseUrl = release?.optString("html_url", "") ?: ""

            // Repo info
            val repoInfo = get(base)
            val openIssues = repoInfo?.optInt("open_issues_count", 0) ?: 0

            // Last commit
            val commits = getArray("$base/commits?per_page=1")
            val lastCommit = commits?.optJSONObject(0)
                ?.optJSONObject("commit")?.optString("message", "")
                ?.lines()?.firstOrNull() ?: "Sin datos"

            // Jupiter tasks (issues with label jupiter-task)
            val issues = getArray("$base/issues?labels=jupiter-task&state=open&per_page=5")
            val jupiterTasks = (0 until (issues?.length() ?: 0))
                .mapNotNull { issues?.optJSONObject(it)?.optString("title") }

            RepoSnapshot(
                name = repo,
                latestTag = latestTag,
                openIssues = openIssues,
                lastCommit = lastCommit,
                jupiterTasks = jupiterTasks,
                updateAvailable = latestTag != "desconocido" && latestTag != "v0.5.0",
                latestReleaseUrl = releaseUrl
            )
        }
}
