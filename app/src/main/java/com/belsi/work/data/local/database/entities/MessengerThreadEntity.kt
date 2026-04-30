package com.belsi.work.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кэш тредов мессенджера для offline-first
 */
@Entity(tableName = "messenger_threads")
data class MessengerThreadEntity(
    @PrimaryKey
    val id: String,
    val type: String, // "direct" | "group"
    val name: String? = null,
    val avatarUrl: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val unreadCount: Int = 0,
    // Denormalized last message fields for fast list rendering
    val lastMessageId: String? = null,
    val lastMessageText: String? = null,
    val lastMessageType: String? = null,
    val lastMessageSenderName: String? = null,
    val lastMessageCreatedAt: String? = null,
    // Denormalized participants (JSON for simplicity)
    val participantsJson: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
