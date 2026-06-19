package com.marketia.jupiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val level: Int,
    val currentExp: Int,
    val maxExp: Int
)
