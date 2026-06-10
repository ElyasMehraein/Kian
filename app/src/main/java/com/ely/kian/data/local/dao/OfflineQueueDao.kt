package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.OfflineQueue
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: OfflineQueue)

    @Query("SELECT * FROM offline_queue ORDER BY createdAt ASC")
    fun getAll(): Flow<List<OfflineQueue>>

    @Query("DELETE FROM offline_queue WHERE eventId = :eventId")
    suspend fun delete(eventId: String)

    @Query("SELECT * FROM offline_queue WHERE queueScope = :scope")
    suspend fun getByScope(scope: String): List<OfflineQueue>

    @Query("UPDATE offline_queue SET retryCount = retryCount + 1 WHERE eventId = :eventId")
    suspend fun incrementRetryCount(eventId: String)
}
