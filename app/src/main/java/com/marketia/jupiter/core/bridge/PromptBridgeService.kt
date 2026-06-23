package com.marketia.jupiter.core.bridge

import com.marketia.jupiter.BuildConfig
import com.marketia.jupiter.data.entity.PromptInboxEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full bridge pipeline:
 *   Android prompt → GitHub Issue → (daemon picks up) → Claude Code → build → release → JÚPITER
 *
 * Flow:
 *   1. createAndQueue()  — saves QUEUED entry in PromptInbox
 *   2. dispatch()        — creates GitHub Issue, marks SENT
 *   3. syncAll()         — polls pollFull() for each SENT/RUNNING entry, updates state
 *   4. triggerUpdate()   — called by AutonomyViewModel when APK_URL received
 */
@Singleton
class PromptBridgeService @Inject constructor(
    private val repository: JupiterRepository,
    private val bridge: ClaudeCodeBridge
) {

    suspend fun createAndQueue(rawPrompt: String, source: String = "text"): Long {
        return repository.insertPromptInbox(
            PromptInboxEntity(
                source    = source,
                rawPrompt = rawPrompt,
                status    = "QUEUED"
            )
        )
    }

    suspend fun dispatch(inboxId: Long): Boolean = withContext(Dispatchers.IO) {
        val entry = repository.getPromptInboxById(inboxId) ?: return@withContext false

        val task = ClaudeCodeTask(
            version         = BuildConfig.VERSION_NAME,
            goal            = entry.rawPrompt.take(80),
            objective       = entry.rawPrompt.take(80),
            problem         = entry.rawPrompt,
            evidence        = "PromptInbox #${entry.id} — source: ${entry.source}",
            requestedChange = entry.rawPrompt,
            filesToChange   = emptyList(),
            steps           = listOf(
                "Leer y comprender el prompt del usuario",
                "Identificar qué archivos del proyecto necesitan cambios",
                "Implementar los cambios en Kotlin/Compose",
                "Verificar que no hay errores de compilación",
                "Reportar archivos modificados y resumen del cambio"
            ),
            validation      = "Build exitoso con ./gradlew assembleRelease --no-daemon",
            expectedResult  = entry.rawPrompt,
            priority        = TaskPriority.HIGH,
            channel         = BridgeChannel.GITHUB_ISSUE
        )

        val result = bridge.send(task)
        val issueNum = result.issueUrl?.substringAfterLast("/")?.toIntOrNull() ?: 0

        repository.updatePromptInbox(
            entry.copy(
                status      = if (result.success) "SENT" else "FAILED",
                sentAt      = System.currentTimeMillis(),
                issueUrl    = result.issueUrl ?: "",
                issueNumber = issueNum,
                errorReason = if (result.success) "" else result.message
            )
        )
        result.success
    }

    suspend fun syncAll(): Int = withContext(Dispatchers.IO) {
        val active = repository.getActivePromptInbox()
        var updated = 0
        for (entry in active) {
            if (entry.issueUrl.isBlank()) continue
            runCatching {
                val poll = bridge.pollFull(entry.issueUrl)
                val newStatus = when (poll.status) {
                    IssueStatus.DONE    -> "DONE"
                    IssueStatus.BLOCKED -> "BLOCKED"
                    IssueStatus.RUNNING -> "RUNNING"
                    IssueStatus.PENDING -> "SENT"
                    else                -> entry.status
                }
                if (newStatus != entry.status || poll.apkUrl != entry.apkUrl) {
                    repository.updatePromptInbox(
                        entry.copy(
                            status      = newStatus,
                            result      = poll.result.ifBlank { entry.result },
                            apkUrl      = poll.apkUrl.ifBlank { entry.apkUrl },
                            releaseUrl  = poll.releaseUrl.ifBlank { entry.releaseUrl },
                            errorReason = poll.blockedReason.ifBlank { entry.errorReason },
                            resolvedAt  = if (newStatus in listOf("DONE","BLOCKED","FAILED"))
                                System.currentTimeMillis() else entry.resolvedAt
                        )
                    )
                    updated++
                }
            }
        }
        updated
    }

    suspend fun purgeOldDone(keepDays: Int = 14) {
        val cutoff = System.currentTimeMillis() - keepDays * 24 * 60 * 60 * 1000L
        repository.purgeOldDonePrompts(cutoff)
    }
}
