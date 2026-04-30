package com.belsi.work.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.belsi.work.data.local.database.entities.MessengerThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessengerThreadDao {

    @Query("SELECT * FROM messenger_threads ORDER BY updatedAt DESC")
    fun getAllThreads(): Flow<List<MessengerThreadEntity>>

    @Query("SELECT * FROM messenger_threads WHERE id = :threadId LIMIT 1")
    suspend fun getThread(threadId: String): MessengerThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(threads: List<MessengerThreadEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: MessengerThreadEntity)

    @Query("UPDATE messenger_threads SET unreadCount = 0 WHERE id = :threadId")
    suspend fun markRead(threadId: String)

    @Query("UPDATE messenger_threads SET lastMessageText = :text, lastMessageType = :type, lastMessageSenderName = :senderName, lastMessageCreatedAt = :createdAt, updatedAt = :createdAt WHERE id = :threadId")
    suspend fun updateLastMessage(threadId: String, text: String?, type: String, senderName: String, createdAt: String)

    @Query("DELETE FROM messenger_threads WHERE id = :threadId")
    suspend fun delete(threadId: String)

    @Query("DELETE FROM messenger_threads")
    suspend fun deleteAll()
}
