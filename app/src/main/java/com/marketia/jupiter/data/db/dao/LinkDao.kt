package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.LinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY savedAt DESC") fun getAll(): Flow<List<LinkEntity>>
    @Query("SELECT * FROM links WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%' ORDER BY savedAt DESC")
    fun search(q: String): Flow<List<LinkEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(link: LinkEntity)
    @Update suspend fun update(link: LinkEntity)
    @Delete suspend fun delete(link: LinkEntity)
    @Query("SELECT COUNT(*) FROM links") suspend fun count(): Int
}
