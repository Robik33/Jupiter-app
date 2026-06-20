package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.SkillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY createdAt DESC") fun getAll(): Flow<List<SkillEntity>>
    @Query("SELECT * FROM skills WHERE name LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' OR resumen LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun search(q: String): Flow<List<SkillEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(skills: List<SkillEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(skill: SkillEntity): Long
    @Delete suspend fun delete(skill: SkillEntity)
    @Query("SELECT COUNT(*) FROM skills") suspend fun count(): Int
}
