package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

enum class SupportTicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    val displayName: String
        get() = when (this) {
            OPEN -> "Открыт"
            IN_PROGRESS -> "В работе"
            RESOLVED -> "Решен"
            CLOSED -> "Закрыт"
        }

    companion object {
        fun fromString(value: String): SupportTicketStatus {
            return when (value.lowercase()) {
                "open" -> OPEN
                "in_progress" -> IN_PROGRESS
                "resolved" -> RESOLVED
                "closed" -> CLOSED
                else -> OPEN
            }
        }
    }
}

enum class TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT;

    val displayName: String
        get() = when (this) {
            LOW -> "Низкий"
            MEDIUM -> "Средний"
            HIGH -> "Высокий"
            URGENT -> "Срочный"
        }
}

/**
 * Тикет поддержки (согласно TicketOut на бэкенде)
 */
@Serializable
data class SupportTicket(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("category") val category: String = "general",
    @SerialName("status") val statusStr: String = "open",
    @SerialName("created_at") val createdAtStr: String = "",
    @SerialName("updated_at") val updatedAtStr: String = ""
) {
    // Для совместимости со старым кодом
    val subject: String get() = title
    val description: String get() = title
    val status: SupportTicketStatus get() = SupportTicketStatus.fromString(statusStr)
    val priority: TicketPriority get() = TicketPriority.MEDIUM
    val messages: List<TicketMessage> get() = emptyList()

    // Парсинг даты из ISO строки в Long
    val createdAt: Long get() = parseIsoDateTime(createdAtStr)
    val updatedAt: Long get() = parseIsoDateTime(updatedAtStr)
}

/**
 * Сообщение в тикете (согласно MessageOut на бэкенде)
 */
@Serializable
data class TicketMessage(
    @SerialName("id") val id: String,
    @SerialName("ticket_id") val ticketId: String,
    @SerialName("sender_role") val senderRole: String = "USER",
    @SerialName("sender_user_id") val senderUserId: String? = null,
    @SerialName("text") val text: String,
    @SerialName("is_internal") val isInternal: Boolean = false,
    @SerialName("created_at") val createdAtStr: String = ""
) {
    // Для совместимости со старым кодом
    val message: String get() = text
    val senderName: String get() = when (senderRole.uppercase()) {
        "CURATOR", "curator" -> "Куратор"
        "FOREMAN", "foreman" -> "Бригадир"
        "SYSTEM", "system" -> "Система"
        else -> "Пользователь"
    }
    val isFromSupport: Boolean get() = senderRole.uppercase() in listOf("CURATOR", "SYSTEM")
    val createdAt: Long get() = parseIsoDateTime(createdAtStr)
    val attachments: List<String> get() = emptyList()
}

@Serializable
data class FAQ(
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    @SerialName("question") val question: String,
    @SerialName("answer") val answer: String,
    @SerialName("category") val category: String = "",
    @SerialName("order") val order: Int = 0
)

/**
 * Парсит ISO datetime строку в Unix timestamp (миллисекунды)
 */
private fun parseIsoDateTime(isoString: String): Long {
    if (isoString.isBlank()) return System.currentTimeMillis()
    return try {
        // Пробуем разные форматы
        val formats = listOf(
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", java.util.Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        )
        for (fmt in formats) {
            try {
                fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return fmt.parse(isoString.replace("Z", ""))?.time ?: continue
            } catch (e: Exception) {
                continue
            }
        }
        System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}
