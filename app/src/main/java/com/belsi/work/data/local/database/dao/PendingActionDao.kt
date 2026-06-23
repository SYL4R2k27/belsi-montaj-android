package com.belsi.work.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.belsi.work.data.local.database.entities.PendingActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingActionDao {

    /** Подписка на счётчик pending — для бейджа в шапке. */
    @Query("SELECT COUNT(*) FROM pending_actions WHERE status != 'failed'")
    fun pendingCountFlow(): Flow<Int>

    /** Все pending для UI отображения. */
    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingActionEntity>>

    /** Готовые к отправке (status=pending), отсортированы FIFO. */
    @Query("""
        SELECT * FROM pending_actions
        WHERE status = 'pending'
          AND retries < 10
        ORDER BY createdAt ASC
        LIMIT 50
    """)
    suspend fun getReadyToSend(): List<PendingActionEntity>

    @Insert
    suspend fun insert(action: PendingActionEntity): Long

    @Update
    suspend fun update(action: PendingActionEntity)

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_actions SET status = 'pending', retries = 0, lastError = NULL WHERE id = :id")
    suspend fun retry(id: Long)

    @Query("DELETE FROM pending_actions WHERE status = 'failed' AND lastAttemptAt < :before")
    suspend fun cleanOldFailed(before: Long)
}
