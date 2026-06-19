package com.marketia.jupiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "systems")
data class SystemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val architecture: String,
    val status: String = "DISEÑANDO",
    val createdAt: Long = System.currentTimeMillis()
)
