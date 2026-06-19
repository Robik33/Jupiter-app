package com.marketia.jupiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "autonomy_tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val status: String = "PENDING",
    val priority: String = "MEDIUM",
    val provider: String = "",
    val attempts: Int = 0,
    val lastError: String = "",
    val nextAction: String = "",
    val result: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
