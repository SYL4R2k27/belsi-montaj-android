package com.belsi.work.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кэш сообщений чата для offline режима
 * Хранит последние 100 сообщений для быстрого доступа
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val ticketId: String,
    val senderRole: String,
    val senderUserId: String? = null,
    val text: String,
    val voiceUrl: String? = null,
    val voiceDuration: Float? = null,
    val messageType: String = "text", // "text" | "photo" | "voice"
    val isInternal: Boolean = false,
    val createdAt: String,
    val syncStatus: String = "synced", // "synced", "pending"
    val cachedAt: Long = System.currentTimeMillis()
)
