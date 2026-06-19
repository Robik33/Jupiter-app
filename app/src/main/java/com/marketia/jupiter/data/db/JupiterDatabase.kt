package com.marketia.jupiter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.marketia.jupiter.data.db.dao.*
import com.marketia.jupiter.data.entity.*

@Database(
    entities = [StatEntity::class, HabitEntity::class, MissionEntity::class, UserProgressEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JupiterDatabase : RoomDatabase() {
    abstract fun statDao(): StatDao
    abstract fun habitDao(): HabitDao
    abstract fun missionDao(): MissionDao
    abstract fun progressDao(): ProgressDao
}
