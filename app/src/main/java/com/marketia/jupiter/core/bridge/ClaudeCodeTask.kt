package com.marketia.jupiter.core.bridge

data class ClaudeCodeTask(
    val source: String                = "JUPITER_ANDROID",
    val version: String,
    val goal: String,
    val problem: String,
    val evidence: String,
    val requestedChange: String,
    val priority: TaskPriority        = TaskPriority.MEDIUM,
    val requiresUserApproval: Boolean = true,
    val channel: BridgeChannel        = BridgeChannel.GITHUB_ISSUE,
    val status: TaskStatus            = TaskStatus.PENDING_USER_APPROVAL,
    // V0.8: executable task format for Claude Code PC
    val objective: String             = "",
    val filesToChange: List<String>   = emptyList(),
    val steps: List<String>           = emptyList(),
    val validation: String            = "",
    val expectedResult: String        = ""
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

fun ClaudeCodeTask.toJson(): String {
    val filesJson = filesToChange.joinToString(",") { "\"${it.jsonEscape()}\"" }
    val stepsJson = steps.joinToString(",") { "\"${it.jsonEscape()}\"" }
    return """
{
  "source": "$source",
  "version": "$version",
  "goal": ${goal.jsonQuote()},
  "problem": ${problem.jsonQuote()},
  "evidence": ${evidence.jsonQuote()},
  "requested_change": ${requestedChange.jsonQuote()},
  "priority": "${priority.name}",
  "requires_user_approval": $requiresUserApproval,
  "channel": "${channel.name}",
  "status": "${status.name}",
  "objective": ${objective.jsonQuote()},
  "files_to_change": [$filesJson],
  "steps": [$stepsJson],
  "validation": ${validation.jsonQuote()},
  "expected_result": ${expectedResult.jsonQuote()}
}""".trimIndent()
}

private fun String.jsonEscape(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

private fun String.jsonQuote(): String = "\"${jsonEscape()}\""
