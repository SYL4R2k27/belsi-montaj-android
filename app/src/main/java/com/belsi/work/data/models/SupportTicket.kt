package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Тикет поддержки (чат с куратором)
 */
@Serializable
data class SupportTicketDto(
    @SerialName("ticket_id")
    val ticketId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_phone")
    val userPhone: String,
    @SerialName("user_role")
    val userRole: String? = null,
    @SerialName("last_message_at")
    val lastMessageAt: String? = null,
    @SerialName("last_message_text")
    val lastMessageText: String? = null,
    @SerialName("unread_count")
    val unreadCount: Int = 0
)

/**
 * Категории тикетов
 */
object TicketCategory {
    const val CHAT = "chat"                 // Общий чат
    const val PAYMENT = "payment"           // Оплата
    const val TOOLS = "tools"               // Инструменты
    const val SHIFT = "shift"               // Смена
    const val PHOTO = "photo"               // Фото
    const val TECHNICAL = "technical"       // Техническая проблема
    const val OTHER = "other"               // Другое

    val ALL_CATEGORIES = listOf(
        CHAT, PAYMENT, TOOLS, SHIFT, PHOTO, TECHNICAL, OTHER
    )

    fun getDisplayName(category: String): String {
        return when (category) {
            CHAT -> "Общий чат"
            PAYMENT -> "Оплата"
            TOOLS -> "Инструменты"
            SHIFT -> "Смена"
            PHOTO -> "Фото"
            TECHNICAL -> "Тех. проблема"
            OTHER -> "Другое"
            else -> category
        }
    }

    fun getEmoji(category: String): String {
        return when (category) {
            CHAT -> "💬"
            PAYMENT -> "💰"
            TOOLS -> "🔧"
            SHIFT -> "⏰"
            PHOTO -> "📷"
            TECHNICAL -> "🔌"
            OTHER -> "📋"
            else -> "💬"
        }
    }
}

/**
 * Статусы тикетов
 */
object TicketStatus {
    const val OPEN = "open"
    const val IN_PROGRESS = "in_progress"
    const val RESOLVED = "resolved"
    const val CLOSED = "closed"
}
