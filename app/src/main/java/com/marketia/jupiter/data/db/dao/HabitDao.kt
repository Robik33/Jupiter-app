package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY streak DESC") fun getAll(): Flow<List<HabitEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(habits: List<HabitEntity>)
    @Update suspend fun update(habit: HabitEntity)
    @Query("SELECT COUNT(*) FROM habits") suspend fun count(): Int
}
