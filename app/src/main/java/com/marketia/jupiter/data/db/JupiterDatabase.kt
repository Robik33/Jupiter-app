package com.marketia.jupiter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.marketia.jupiter.data.db.dao.*
import com.marketia.jupiter.data.entity.*

@Database(
    entities = [
        SkillEntity::class,
        LinkEntity::class,
        ProjectEntity::class,
        SystemEntity::class,
        AgentEntity::class,
        TaskEntity::class,
        MemoryNodeEntity::class,
        MemoryEdgeEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class JupiterDatabase : RoomDatabase() {
    abstract fun skillDao(): SkillDao
    abstract fun linkDao(): LinkDao
    abstract fun projectDao(): ProjectDao
    abstract fun systemDao(): SystemDao
    abstract fun agentDao(): AgentDao
    abstract fun taskDao(): TaskDao
    abstract fun memoryNodeDao(): MemoryNodeDao
    abstract fun memoryEdgeDao(): MemoryEdgeDao
}
