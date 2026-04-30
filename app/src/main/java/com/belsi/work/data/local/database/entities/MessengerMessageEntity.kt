package com.belsi.work.data.local.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Кэш сообщений мессенджера для offline-first.
 * Индекс по threadId для быстрой выборки по диалогу.
 */
@Entity(
    tableName = "messenger_messages",
    indices = [Index(value = ["threadId", "createdAt"])]
)
data class MessengerMessageEntity(
    @PrimaryKey
    val id: String,
    val threadId: String,
    val senderId: String,
    val senderName: String = "",
    val senderRole: String = "",
    val messageType: String = "text", // "text" | "photo" | "voice" | "file" | "system"
    val text: String? = null,
    val photoUrl: String? = null,
    val voiceUrl: String? = null,
    val voiceDurationSeconds: Float? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val replyToId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val replyToMessageType: String? = null,
    val isRead: Boolean = false,
    val forwardedFrom: String? = null,
    val createdAt: String = "",
    // Для optimistic sending
    val syncStatus: String = "synced", // "synced" | "sending" | "failed"
    val localId: String? = null, // temporary client-side ID
    val cachedAt: Long = System.currentTimeMillis()
)
