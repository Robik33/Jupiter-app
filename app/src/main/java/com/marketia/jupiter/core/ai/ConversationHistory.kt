package com.marketia.jupiter.core.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationHistory @Inject constructor() {
    private val buffer = ArrayDeque<Pair<String, String>>()
    private val maxPairs = 8
    private var lastActivityMs = System.currentTimeMillis()
    private val idleMs = 30 * 60 * 1000L

    fun add(user: String, assistant: String) {
        lastActivityMs = System.currentTimeMillis()
        if (buffer.size >= maxPairs) buffer.removeFirst()
        buffer.addLast(user to assistant)
    }

    fun get(): List<Pair<String, String>> {
        if (System.currentTimeMillis() - lastActivityMs > idleMs) buffer.clear()
        return buffer.toList()
    }

    fun clear() = buffer.clear()
}
