package com.marketia.jupiter.core.oracle

data class HermesInboxMessage(
    val id: String,
    val prompt: String,
    val source: String = "oracle_apk",
    val ts: Long = System.currentTimeMillis()
)

data class HermesOutboxMessage(
    val id: String,
    val response: String,
    val model: String = "oracle-hermes",
    val ts: Long = System.currentTimeMillis(),
    val error: String = ""
)

enum class HermesChannel {
    PROMPT,      // POST /prompt (async, queued)
    CHAT,        // POST /v1/chat/completions (sync)
    TASK,        // POST /jupiter/task (ClaudeCodeBridge)
    STATUS       // GET /oracle (state poll)
}
