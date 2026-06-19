package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM autonomy_tasks ORDER BY CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, createdAt ASC")
    fun getAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM autonomy_tasks WHERE status = :status ORDER BY createdAt ASC")
    fun getByStatus(status: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM autonomy_tasks WHERE status IN ('PENDING', 'FAILED', 'FIXING') ORDER BY CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, createdAt ASC LIMIT 1")
    suspend fun getNextPending(): TaskEntity?

    @Query("SELECT COUNT(*) FROM autonomy_tasks WHERE status = 'DONE'") suspend fun countDone(): Int
    @Query("SELECT COUNT(*) FROM autonomy_tasks WHERE status = 'PENDING'") suspend fun countPending(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(task: TaskEntity): Long
    @Update suspend fun update(task: TaskEntity)
    @Delete suspend fun delete(task: TaskEntity)
    @Query("DELETE FROM autonomy_tasks WHERE status = 'DONE'") suspend fun clearDone()
}
