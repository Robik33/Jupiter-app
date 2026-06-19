package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.LinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY savedAt DESC") fun getAll(): Flow<List<LinkEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(link: LinkEntity)
    @Delete suspend fun delete(link: LinkEntity)
    @Query("SELECT COUNT(*) FROM links") suspend fun count(): Int
}
