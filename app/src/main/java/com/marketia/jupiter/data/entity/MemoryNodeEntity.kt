package com.marketia.jupiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_nodes")
data class MemoryNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,           // SKILL, LINK, PROJECT, SYSTEM, AGENT, PROMPT, API, TASK
    val refId: Long = 0,        // ID in native table (0 = standalone)
    val label: String,
    val summary: String = "",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
