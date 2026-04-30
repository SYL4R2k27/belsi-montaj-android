package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Модель сообщения чата поддержки
 * Поддерживает парсинг дат с/без fractional seconds
 *
 * ВАЖНО: API возвращает sender_role в lowercase, хотя в БД хранится UPPER.
 * Android работает по API формату.
 */
@Serializable
data class ChatMessageDTO(
    val id: String,
    @SerialName("ticket_id") val ticketId: String,
    @SerialName("sender_role") val senderRole: String, // "user" | "foreman" | "curator" | "system" (lowercase!)
    @SerialName("sender_user_id") val senderUserId: String? = null,
    val text: String,
    @SerialName("photo_url") val photoUrl: String? = null, // URL фотографии, приложенной к сообщению
    @SerialName("voice_url") val voiceUrl: String? = null, // URL голосового сообщения
    @SerialName("voice_duration_seconds") val voiceDuration: Float? = null, // Длительность голосового (секунды)
    @SerialName("message_type") val messageType: String = "text", // "text" | "photo" | "voice"
    @SerialName("is_internal") val isInternal: Boolean = false,
    @SerialName("created_at") val createdAt: String
) {
    /**
     * Определяет, является ли сообщение от пользователя (монтажник/бригадир)
     */
    val isFromUser: Boolean
        get() = senderRole.lowercase() in listOf("user", "foreman", "installer")

    /**
     * Определяет, является ли сообщение от системы/поддержки
     */
    val isFromSupport: Boolean
        get() = senderRole.lowercase() in listOf("curator", "system", "support")
    /**
     * Парсинг даты с fallback для дат без миллисекунд
     * Поддерживаемые форматы:
     * - 2025-12-23T15:06:23.165356+03:00 (с fractional seconds)
     * - 2025-12-23T15:06:23+03:00 (без fractional seconds)
     */
    val parsedDate: OffsetDateTime?
        get() = parseISO8601WithFallback(createdAt)

    companion object {
        fun parseISO8601WithFallback(dateString: String): OffsetDateTime? {
            return try {
                // Попытка 1: ISO_OFFSET_DATE_TIME (поддерживает fractional seconds)
                OffsetDateTime.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            } catch (e1: DateTimeParseException) {
                try {
                    // Попытка 2: Добавить .0 если миллисекунды отсутствуют
                    val withMillis = if (!dateString.contains('.')) {
                        // Найти позицию '+' или '-' для timezone
                        val tzIndex = dateString.indexOfAny(charArrayOf('+', '-'), dateString.lastIndexOf('T'))
                        if (tzIndex > 0) {
                            dateString.substring(0, tzIndex) + ".0" + dateString.substring(tzIndex)
                        } else {
                            dateString
                        }
                    } else {
                        dateString
                    }
                    OffsetDateTime.parse(withMillis, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                } catch (e2: DateTimeParseException) {
                    // Fallback: вернуть null
                    null
                }
            }
        }
    }
}

/**
 * Запрос на создание сообщения в чате
 */
@Serializable
data class ChatMessageCreateRequest(
    val text: String,
    @SerialName("photo_url")
    val photoUrl: String? = null, // URL фотографии, приложенной к сообщению
    @SerialName("voice_url")
    val voiceUrl: String? = null, // URL голосового сообщения
    @SerialName("voice_duration_seconds")
    val voiceDuration: Float? = null, // Длительность голосового
    @SerialName("message_type")
    val messageType: String = "text", // "text" | "photo" | "voice"
    @SerialName("ticket_id")
    val ticketId: String? = null
)

/**
 * Обёртка для поддержки формата {items: [...]}
 * Backend может вернуть либо [...], либо {items: [...]}
 */
@Serializable
data class ChatMessagesWrapper(
    val items: List<ChatMessageDTO>
)

/**
 * Сводка по чату (для списка чатов куратора)
 */
data class ChatSummary(
    val ticketId: String,
    val lastMessage: String,
    val lastMessageDate: OffsetDateTime?,
    val unreadCount: Int, // Кол-во сообщений с sender_role == "USER"
    val messageCount: Int,
    val userName: String? = null,
    val userPhone: String? = null
)
