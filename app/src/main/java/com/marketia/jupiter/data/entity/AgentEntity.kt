package com.marketia.jupiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val capability: String,
    val model: String,
    val status: String = "INACTIVO",
    val createdAt: Long = System.currentTimeMillis()
)
