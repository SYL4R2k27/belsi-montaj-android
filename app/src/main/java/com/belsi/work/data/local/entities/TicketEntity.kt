package com.belsi.work.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val status: String,
    val priority: String,
    val createdAt: Long,
    val updatedAt: Long,
    val assignedTo: String?
)

@Entity(tableName = "ticket_messages")
data class TicketMessageEntity(
    @PrimaryKey
    val id: String,
    val ticketId: String,
    val senderId: String,
    val senderName: String,
    val message: String,
    val isFromSupport: Boolean,
    val createdAt: Long,
    val attachments: String? // JSON string
)
