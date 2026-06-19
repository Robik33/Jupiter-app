package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.StatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatDao {
    @Query("SELECT * FROM stats") fun getAll(): Flow<List<StatEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(stats: List<StatEntity>)
    @Update suspend fun update(stat: StatEntity)
    @Query("SELECT COUNT(*) FROM stats") suspend fun count(): Int
}
