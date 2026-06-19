package com.marketia.jupiter.core.orchestrator

interface AIOrchestrator {
    val providerName: String
    val isAvailable: Boolean
    suspend fun complete(systemPrompt: String, userMessage: String): String?
    suspend fun analyze(content: String, task: String): String?
}
