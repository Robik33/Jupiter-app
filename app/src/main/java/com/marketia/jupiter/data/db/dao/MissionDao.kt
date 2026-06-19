package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.MissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionDao {
    @Query("SELECT * FROM daily_missions") fun getAll(): Flow<List<MissionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(missions: List<MissionEntity>)
    @Update suspend fun update(mission: MissionEntity)
    @Query("SELECT COUNT(*) FROM daily_missions") suspend fun count(): Int
}
