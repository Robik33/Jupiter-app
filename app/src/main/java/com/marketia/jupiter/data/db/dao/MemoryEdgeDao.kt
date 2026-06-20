package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.MemoryEdgeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryEdgeDao {
    @Query("SELECT * FROM memory_edges ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MemoryEdgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(edge: MemoryEdgeEntity): Long

    @Delete
    suspend fun delete(edge: MemoryEdgeEntity)
}
