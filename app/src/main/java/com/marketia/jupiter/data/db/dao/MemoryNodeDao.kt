package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.MemoryNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryNodeDao {
    @Query("SELECT * FROM memory_nodes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MemoryNodeEntity>>

    @Query("SELECT * FROM memory_nodes WHERE type = :type ORDER BY createdAt DESC")
    fun getByType(type: String): Flow<List<MemoryNodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: MemoryNodeEntity): Long

    @Delete
    suspend fun delete(node: MemoryNodeEntity)

    @Query("SELECT COUNT(*) FROM memory_nodes")
    suspend fun count(): Int
}
