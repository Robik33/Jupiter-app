package com.marketia.jupiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_edges")
data class MemoryEdgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromType: String,
    val fromId: Long,
    val toType: String,
    val toId: Long,
    val relation: String,       // CREATED_FROM, RELATED_TO, DEPENDS_ON, USES, IMPROVES, PART_OF
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
