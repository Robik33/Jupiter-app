package com.marketia.jupiter.core.bridge

data class ClaudeCodeTask(
    val source: String              = "JUPITER_ANDROID",
    val version: String,
    val goal: String,
    val problem: String,
    val evidence: String,
    val requestedChange: String,
    val priority: TaskPriority     = TaskPriority.MEDIUM,
    val requiresUserApproval: Boolean = true,
    val channel: BridgeChannel     = BridgeChannel.GITHUB_ISSUE,
    val status: TaskStatus         = TaskStatus.PENDING_USER_APPROVAL
)

enum class TaskPriority { LOW, MEDIUM, HIGH, CRITICAL }

enum class TaskStatus {
    PENDING_USER_APPROVAL,
    APPROVED,
    SENT,
    IN_PROGRESS,
    COMPLETED,
    REJECTED
}

enum class BridgeChannel {
    GITHUB_ISSUE,
    HTTP_LOCAL,
    TELEGRAM
}

fun ClaudeCodeTask.toJson(): String = """
{
  "source": "$source",
  "version": "$version",
  "goal": ${goal.jsonEscape()},
  "problem": ${problem.jsonEscape()},
  "evidence": ${evidence.jsonEscape()},
  "requested_change": ${requestedChange.jsonEscape()},
  "priority": "${priority.name}",
  "requires_user_approval": $requiresUserApproval,
  "channel": "${channel.name}",
  "status": "${status.name}"
}
""".trimIndent()

private fun String.jsonEscape(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
