package com.marketia.jupiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "links")
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val category: String,
    val summary: String = "",
    val skills: String = "",
    val concepts: String = "",
    val processed: Boolean = false,
    val savedAt: Long = System.currentTimeMillis()
)
