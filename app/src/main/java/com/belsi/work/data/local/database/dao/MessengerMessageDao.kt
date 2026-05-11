package com.belsi.work.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.belsi.work.data.local.database.entities.MessengerMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessengerMessageDao {

    @Query("SELECT * FROM messenger_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun getMessages(threadId: String): Flow<List<MessengerMessageEntity>>

    @Query("SELECT * FROM messenger_messages WHERE threadId = :threadId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMessages(threadId: String, limit: Int = 50): List<MessengerMessageEntity>

    @Query("SELECT * FROM messenger_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessage(messageId: String): MessengerMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessengerMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessengerMessageEntity)

    /**
     * Обновить статус синхронизации (для optimistic sending)
     */
    @Query("UPDATE messenger_messages SET syncStatus = :status WHERE id = :messageId")
    suspend fun updateSyncStatus(messageId: String, status: String)

    /**
     * Заменить localId на серверный id после успешной отправки
     */
    @Query("UPDATE messenger_messages SET id = :serverId, syncStatus = 'synced' WHERE localId = :localId")
    suspend fun confirmSent(localId: String, serverId: String)

    @Query("SELECT * FROM messenger_messages WHERE syncStatus = 'sending' OR syncStatus = 'failed'")
    suspend fun getPendingMessages(): List<MessengerMessageEntity>

    @Query("DELETE FROM messenger_messages WHERE id = :messageId")
    suspend fun delete(messageId: String)

    @Query("DELETE FROM messenger_messages WHERE threadId = :threadId")
    suspend fun deleteByThread(threadId: String)

    @Query("DELETE FROM messenger_messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM messenger_messages WHERE threadId = :threadId")
    suspend fun getMessageCount(threadId: String): Int
}
