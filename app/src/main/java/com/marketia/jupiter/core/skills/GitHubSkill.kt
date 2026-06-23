package com.marketia.jupiter.core.skills

import com.marketia.jupiter.core.bridge.BridgeChannel
import com.marketia.jupiter.core.bridge.ClaudeCodeBridge
import com.marketia.jupiter.core.bridge.ClaudeCodeTask
import com.marketia.jupiter.core.bridge.TaskPriority
import com.marketia.jupiter.core.eyes.GitHubRepoInspector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubSkill @Inject constructor(
    private val inspector: GitHubRepoInspector,
    private val bridge: ClaudeCodeBridge
) : JupiterSkill {
    override val id   = "github"
    override val name = "GitHub"

    override suspend fun execute(params: Map<String, String>): String {
        val action = params["action"] ?: "inspect"
        val repo   = params["repo"]   ?: "Robik33/Jupiter-app"

        return when (action) {
            "inspect" -> {
                val snap = inspector.inspect(repo)
                buildString {
                    appendLine("GitHub: ${snap.name}")
                    appendLine("Tag actual: ${snap.latestTag}")
                    appendLine("Issues abiertos: ${snap.openIssues}")
                    appendLine("Último commit: ${snap.lastCommit}")
                    if (snap.jupiterTasks.isNotEmpty()) {
                        appendLine("Tareas JUPITER (${snap.jupiterTasks.size}):")
                        snap.jupiterTasks.forEach { appendLine("  • $it") }
                    }
                    if (snap.latestReleaseUrl.isNotBlank()) appendLine("Release: ${snap.latestReleaseUrl}")
                }.trim()
            }

            "release" -> {
                val snap = inspector.inspect(repo)
                "Release más reciente: ${snap.latestTag}\n${snap.latestReleaseUrl}"
            }

            "issue" -> {
                val title = params["title"] ?: "Tarea desde JÚPITER"
                val body  = params["body"]  ?: title
                val task  = ClaudeCodeTask(
                    version         = "manual",
                    goal            = title,
                    objective       = title,
                    problem         = body,
                    evidence        = "Solicitado desde JÚPITER GitHubSkill",
                    requestedChange = body,
                    filesToChange   = emptyList(),
                    steps           = listOf("Analizar", "Implementar", "Verificar"),
                    validation      = "Issue procesado y cerrado",
                    expectedResult  = title,
                    priority        = TaskPriority.MEDIUM,
                    channel         = BridgeChannel.GITHUB_ISSUE
                )
                val result = bridge.send(task)
                if (result.success) "Issue creado: ${result.issueUrl}" else "Error: ${result.message}"
            }

            else -> {
                val snap = inspector.inspect(repo)
                "GitHub $action — ${snap.name} @ ${snap.latestTag}"
            }
        }
    }
}
