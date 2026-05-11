package com.belsi.work.data.remote.api

import com.belsi.work.data.models.ChatMessageCreateRequest
import com.belsi.work.data.models.ChatMessageDTO
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * API для работы с чатом поддержки
 *
 * Backend может вернуть ответ в двух форматах:
 * 1. Прямой массив: [ChatMessageDTO, ...]
 * 2. Обёртка: {items: [ChatMessageDTO, ...]}
 *
 * Парсинг обоих форматов обрабатывается в Repository слое
 */
interface ChatApi {

    /**
     * Получить все сообщения чата
     *
     * Для куратора: возвращает сообщения конкретного тикета (с ticket_id)
     * Для монтажника/бригадира: backend автоматически создает/использует их тикет
     *
     * @param ticketId ID тикета (для куратора обязательно)
     * @param limit Ограничение кол-ва сообщений (null = все сообщения)
     * @param before Получить сообщения до указанной даты (ISO8601)
     * @param after Получить сообщения после указанной даты (ISO8601)
     * @param order Порядок сортировки: "asc" | "desc" (по created_at)
     */
    @GET("support/chat/messages")
    suspend fun getChatMessages(
        @Query("ticket_id") ticketId: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("before") before: String? = null,
        @Query("after") after: String? = null,
        @Query("order") order: String? = "asc"
    ): Response<List<ChatMessageDTO>>

    /**
     * Отправить сообщение в чат
     *
     * Backend автоматически определяет:
     * - sender_role из Bearer token
     * - sender_user_id из Bearer token
     * - ticket_id создаётся автоматически или используется существующий
     *
     * @param request Текст сообщения и опционально ticket_id (для куратора)
     */
    @POST("support/chat/messages")
    suspend fun sendChatMessage(
        @Body request: ChatMessageCreateRequest
    ): Response<ChatMessageDTO>

    /**
     * Получить список чатов (inbox) для куратора
     *
     * GET /support/chat/inbox
     * Доступно только для куратора
     *
     * @param limit Количество тикетов на странице
     * @param offset Смещение для пагинации
     * @param q Поиск по номеру телефона
     * @param order Сортировка: "asc" | "desc"
     */
    @GET("support/chat/inbox")
    suspend fun getChatInbox(
        @Query("limit") limit: Int? = 20,
        @Query("offset") offset: Int? = 0,
        @Query("q") searchQuery: String? = null,
        @Query("order") order: String? = "desc"
    ): Response<List<com.belsi.work.data.models.SupportTicketDto>>

    /**
     * Отметить сообщения как прочитанные
     *
     * POST /support/chat/read
     *
     * @param request ticket_id и последнее прочитанное сообщение
     */
    @POST("support/chat/read")
    suspend fun markAsRead(
        @Body request: MarkAsReadRequest
    ): Response<Unit>

    /**
     * Загрузить фотографию для чата
     *
     * POST /support/chat/upload-photo
     *
     * @param photo Файл фотографии (multipart)
     * @return URL загруженной фотографии
     */
    @Multipart
    @POST("support/chat/upload-photo")
    suspend fun uploadChatPhoto(
        @Part photo: MultipartBody.Part
    ): Response<ChatPhotoUploadResponse>

    /**
     * Загрузить голосовое сообщение для чата
     *
     * POST /support/chat/upload-voice
     *
     * @param voice Аудио файл (multipart)
     * @param duration Длительность в секундах
     * @return URL голосового и длительность
     */
    @Multipart
    @POST("support/chat/upload-voice")
    suspend fun uploadChatVoice(
        @Part voice: MultipartBody.Part,
        @Query("duration") duration: Float
    ): Response<ChatVoiceUploadResponse>
}

/**
 * Запрос на отметку сообщений как прочитанных
 */
@kotlinx.serialization.Serializable
data class MarkAsReadRequest(
    @kotlinx.serialization.SerialName("ticket_id")
    val ticketId: String,
    @kotlinx.serialization.SerialName("last_read_message_id")
    val lastReadMessageId: String? = null
)

/**
 * Ответ на загрузку фотографии для чата
 */
@kotlinx.serialization.Serializable
data class ChatPhotoUploadResponse(
    @kotlinx.serialization.SerialName("photo_url")
    val photoUrl: String
)

/**
 * Ответ на загрузку голосового сообщения
 */
@kotlinx.serialization.Serializable
data class ChatVoiceUploadResponse(
    @kotlinx.serialization.SerialName("voice_url")
    val voiceUrl: String,
    @kotlinx.serialization.SerialName("duration_seconds")
    val durationSeconds: Float
)
