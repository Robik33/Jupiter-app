package com.marketia.jupiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks every prompt sent from JÚPITER Android to Claude Code PC via GitHub Issues.
 * States: DRAFT → QUEUED → SENT → RUNNING → DONE | BLOCKED | FAILED
 */
@Entity(tableName = "prompt_inbox")
data class PromptInboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String = "text",            // "voice" | "text" | "autonomy" | "skill"
    val rawPrompt: String = "",
    val status: String = "DRAFT",           // DRAFT | QUEUED | SENT | RUNNING | DONE | BLOCKED | FAILED
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long = 0L,
    val resolvedAt: Long = 0L,
    val issueUrl: String = "",
    val issueNumber: Int = 0,
    val result: String = "",
    val apkUrl: String = "",
    val releaseUrl: String = "",
    val errorReason: String = "",
    val retryCount: Int = 0
)
