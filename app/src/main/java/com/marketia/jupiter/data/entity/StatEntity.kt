package com.marketia.jupiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stats")
data class StatEntity(
    @PrimaryKey val name: String,
    val label: String,
    val value: Int
)
