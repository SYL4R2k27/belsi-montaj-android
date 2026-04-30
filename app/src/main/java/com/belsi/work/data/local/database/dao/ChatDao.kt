package com.belsi.work.data.local.database.dao

import androidx.room.*
import com.belsi.work.data.local.database.entities.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chat_messages WHERE ticketId = :ticketId ORDER BY createdAt ASC")
    fun getMessagesFlow(ticketId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE ticketId = :ticketId ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getMessages(ticketId: String, limit: Int = 100): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getAllMessages(limit: Int = 100): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE ticketId = :ticketId")
    suspend fun deleteMessagesForTicket(ticketId: String)

    @Query("DELETE FROM chat_messages WHERE cachedAt < :timestampMillis")
    suspend fun deleteOldMessages(timestampMillis: Long)

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getMessageCount(): Int

    @Query("DELETE FROM chat_messages WHERE id IN (SELECT id FROM chat_messages ORDER BY cachedAt ASC LIMIT :count)")
    suspend fun deleteOldestMessages(count: Int)

    @Query("SELECT * FROM chat_messages WHERE syncStatus = 'pending' ORDER BY createdAt ASC")
    suspend fun getPendingMessages(): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET syncStatus = :syncStatus WHERE id = :messageId")
    suspend fun updateSyncStatus(messageId: String, syncStatus: String)
}
