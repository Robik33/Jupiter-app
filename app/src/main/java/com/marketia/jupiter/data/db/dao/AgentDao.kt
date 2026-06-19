package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.AgentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY createdAt DESC") fun getAll(): Flow<List<AgentEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(agent: AgentEntity)
    @Delete suspend fun delete(agent: AgentEntity)
    @Query("SELECT COUNT(*) FROM agents") suspend fun count(): Int
}
