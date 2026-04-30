package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Thread (chat room) from messenger API
 */
@Serializable
data class ThreadDTO(
    val id: String,
    val type: String, // "direct" | "group"
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("last_message") val lastMessage: MessengerMessageDTO? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    val participants: List<ParticipantDTO> = emptyList()
) {
    val parsedUpdatedAt: OffsetDateTime?
        get() = ChatMessageDTO.parseISO8601WithFallback(updatedAt)

    /** Display name: for direct chats use other participant name, for groups use thread name */
    val displayName: String
        get() = name ?: "Чат"
}

/**
 * Participant in a thread
 */
@Serializable
data class ParticipantDTO(
    @SerialName("user_id") val userId: String,
    val role: String = "member", // "admin" | "member"
    @SerialName("full_name") val fullName: String = "",
    val phone: String = "",
    @SerialName("user_role") val userRole: String = "", // "installer" | "foreman" | "curator"
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false
)

/**
 * Message from messenger API (chat_messages_v2)
 */
@Serializable
data class MessengerMessageDTO(
    val id: String,
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String = "",
    @SerialName("sender_role") val senderRole: String = "", // "installer" | "foreman" | "curator"
    @SerialName("message_type") val messageType: String = "text", // "text" | "photo" | "voice" | "file" | "system"
    val text: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("voice_url") val voiceUrl: String? = null,
    @SerialName("voice_duration_seconds") val voiceDurationSeconds: Float? = null,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_to") val replyTo: ReplyMessageDTO? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("forwarded_from") val forwardedFrom: String? = null, // original sender name
    @SerialName("created_at") val createdAt: String = ""
) {
    val parsedDate: OffsetDateTime?
        get() = ChatMessageDTO.parseISO8601WithFallback(createdAt)

    /** Short preview for thread list */
    val preview: String
        get() = when (messageType) {
            "photo" -> "📷 Фото"
            "voice" -> "🎤 Голосовое"
            "file" -> "📎 ${fileName ?: "Файл"}"
            "system" -> text ?: ""
            else -> text ?: ""
        }
}

/**
 * Краткая инфо о сообщении, на которое ответили
 */
@Serializable
data class ReplyMessageDTO(
    val id: String,
    @SerialName("sender_name") val senderName: String = "",
    val text: String? = null,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("photo_url") val photoUrl: String? = null
) {
    val preview: String
        get() = when (messageType) {
            "photo" -> "📷 Фото"
            "voice" -> "🎤 Голосовое"
            "file" -> "📎 Файл"
            else -> text ?: ""
        }
}

/**
 * Contact available for creating chats
 */
@Serializable
data class ContactDTO(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val phone: String = "",
    val role: String = "" // "installer" | "foreman" | "curator"
) {
    val roleDisplayName: String
        get() = when (role) {
            "installer" -> "Монтажник"
            "foreman" -> "Бригадир"
            "coordinator" -> "Координатор"
            "curator" -> "Куратор"
            else -> role
        }
}

// Request DTOs

@Serializable
data class CreateThreadRequest(
    val type: String, // "direct" | "group"
    val name: String? = null,
    @SerialName("participant_ids") val participantIds: List<String>
)

@Serializable
data class SendMessengerMessageRequest(
    val text: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("voice_url") val voiceUrl: String? = null,
    @SerialName("voice_duration_seconds") val voiceDurationSeconds: Float? = null,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("forwarded_from_id") val forwardedFromId: String? = null
)

@Serializable
data class AddMembersRequest(
    @SerialName("user_ids") val userIds: List<String>
)

@Serializable
data class UpdateThreadRequest(
    val name: String? = null
)

// Response wrappers

@Serializable
data class ThreadListResponse(
    val threads: List<ThreadDTO>
)

@Serializable
data class MessagesResponse(
    val messages: List<MessengerMessageDTO>,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class FileUploadResponse(
    @SerialName("file_url") val fileUrl: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_size") val fileSize: Long
)

@Serializable
data class SearchMessagesResponse(
    val messages: List<MessengerMessageDTO> = emptyList(),
    val total: Int = 0
)
