package com.marketia.jupiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hermes_decisions")
data class HermesDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskType: String,
    val providerChosen: String,
    val promptPreview: String,
    val responsePreview: String,
    val tokensEstimate: Int,
    val durationMs: Long,
    val success: Boolean,
    val isFree: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
