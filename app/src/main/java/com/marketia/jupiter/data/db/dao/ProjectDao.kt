package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC") fun getAll(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE name LIKE '%' || :q || '%' OR type LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun search(q: String): Flow<List<ProjectEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(project: ProjectEntity)
    @Delete suspend fun delete(project: ProjectEntity)
    @Query("SELECT COUNT(*) FROM projects") suspend fun count(): Int
}
