package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.SystemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemDao {
    @Query("SELECT * FROM systems ORDER BY createdAt DESC") fun getAll(): Flow<List<SystemEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(system: SystemEntity)
    @Delete suspend fun delete(system: SystemEntity)
    @Query("SELECT COUNT(*) FROM systems") suspend fun count(): Int
}
