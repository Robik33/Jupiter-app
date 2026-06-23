package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.HermesDecisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HermesDecisionDao {
    @Query("SELECT * FROM hermes_decisions ORDER BY createdAt DESC LIMIT 100")
    fun getAll(): Flow<List<HermesDecisionEntity>>

    @Query("SELECT * FROM hermes_decisions ORDER BY createdAt DESC LIMIT 50")
    suspend fun getRecent(): List<HermesDecisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: HermesDecisionEntity): Long

    @Query("DELETE FROM hermes_decisions WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM hermes_decisions WHERE isFree = 1")
    suspend fun countFreeDecisions(): Int

    @Query("SELECT COUNT(*) FROM hermes_decisions")
    suspend fun countAll(): Int
}
