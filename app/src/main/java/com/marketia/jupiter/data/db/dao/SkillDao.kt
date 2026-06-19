package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.SkillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY createdAt DESC") fun getAll(): Flow<List<SkillEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(skills: List<SkillEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(skill: SkillEntity)
    @Delete suspend fun delete(skill: SkillEntity)
    @Query("SELECT COUNT(*) FROM skills") suspend fun count(): Int
}
