package com.marketia.jupiter.data.db.dao

import androidx.room.*
import com.marketia.jupiter.data.entity.PromptInboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptInboxDao {
    @Query("SELECT * FROM prompt_inbox ORDER BY createdAt DESC LIMIT 100")
    fun getAll(): Flow<List<PromptInboxEntity>>

    @Query("SELECT * FROM prompt_inbox WHERE status IN ('QUEUED','SENT','RUNNING') ORDER BY createdAt ASC")
    suspend fun getActive(): List<PromptInboxEntity>

    @Query("SELECT * FROM prompt_inbox WHERE id = :id")
    suspend fun getById(id: Long): PromptInboxEntity?

    @Query("SELECT * FROM prompt_inbox WHERE issueUrl = :url LIMIT 1")
    suspend fun getByIssueUrl(url: String): PromptInboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: PromptInboxEntity): Long

    @Update
    suspend fun update(prompt: PromptInboxEntity)

    @Delete
    suspend fun delete(prompt: PromptInboxEntity)

    @Query("SELECT COUNT(*) FROM prompt_inbox WHERE status IN ('QUEUED','SENT','RUNNING')")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM prompt_inbox WHERE status = 'DONE'")
    suspend fun countDone(): Int

    @Query("DELETE FROM prompt_inbox WHERE status = 'DONE' AND resolvedAt < :before")
    suspend fun purgeOldDone(before: Long)
}
