package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.UserProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM user_progress WHERE id = 1") fun get(): Flow<UserProgressEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(progress: UserProgressEntity)
    @Update suspend fun update(progress: UserProgressEntity)
    @Query("SELECT COUNT(*) FROM user_progress") suspend fun count(): Int
}
