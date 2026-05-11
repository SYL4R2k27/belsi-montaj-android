package com.belsi.work.data.repositories

import android.content.Context
import com.belsi.work.data.local.database.dao.ChatDao
import com.belsi.work.data.local.database.entities.ChatMessageEntity
import com.belsi.work.data.models.ChatMessageCreateRequest
import com.belsi.work.data.models.ChatMessageDTO
import com.belsi.work.data.remote.api.ChatApi
import com.belsi.work.data.remote.error.parseApiError
import com.belsi.work.utils.ImageCompressor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface ChatRepository {
    /**
     * Получить сообщения чата
     * @param ticketId ID тикета (для куратора обязательно, для пользователя null)
     * @param limit Количество сообщений (null = все сообщения)
     */
    suspend fun getMessages(
        ticketId: String? = null,
        limit: Int? = null,
        before: String? = null,
        after: String? = null,
        order: String? = "asc"
    ): Result<List<ChatMessageDTO>>

    /**
     * Отправить сообщение
     * @param text Текст сообщения (может быть null если только фото/голосовое)
     * @param ticketId ID тикета (для куратора - указать в какой чат отправить)
     * @param photoUrl URL фотографии (если уже загружена)
     * @param voiceUrl URL голосового сообщения
     * @param voiceDuration Длительность голосового (секунды)
     * @param messageType Тип сообщения: "text", "photo", "voice"
     */
    suspend fun sendMessage(
        text: String?,
        ticketId: String? = null,
        photoUrl: String? = null,
        voiceUrl: String? = null,
        voiceDuration: Float? = null,
        messageType: String = "text"
    ): Result<ChatMessageDTO>

    /**
     * Загрузить фотографию для чата
     * @param file Файл фотографии
     * @return URL загруженной фотографии
     */
    suspend fun uploadChatPhoto(file: File): Result<String>

    /**
     * Загрузить голосовое сообщение
     * @param file Аудио файл
     * @param duration Длительность в секундах
     * @return Пара (voice_url, duration_seconds)
     */
    suspend fun uploadChatVoice(file: File, duration: Float): Result<Pair<String, Float>>

    /**
     * Получить список чатов (inbox) для куратора
     */
    suspend fun getInbox(
        limit: Int? = 20,
        offset: Int? = 0,
        searchQuery: String? = null
    ): Result<List<com.belsi.work.data.models.SupportTicketDto>>

    /**
     * Отметить сообщения как прочитанные
     */
    suspend fun markAsRead(ticketId: String, lastMessageId: String? = null): Result<Unit>

    /**
     * Наблюдать за сообщениями в реальном времени через Flow
     */
    fun observeMessages(ticketId: String): Flow<List<ChatMessageDTO>>

    /**
     * Синхронизировать отложенные сообщения с сервером
     */
    suspend fun syncPendingMessages(): Result<Unit>
}

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatApi: ChatApi,
    private val chatDao: ChatDao,
    private val json: Json
) : ChatRepository {

    companion object {
        private const val CACHE_VALIDITY_MS = 60_000L // 1 minute
    }

    /**
     * Получить сообщения чата
     * Использует offline-first подход: проверяет кеш, затем загружает с API
     */
    override suspend fun getMessages(
        ticketId: String?,
        limit: Int?,
        before: String?,
        after: String?,
        order: String?
    ): Result<List<ChatMessageDTO>> {
        return try {
            android.util.Log.d("ChatRepository", "Loading chat messages, ticketId: $ticketId")

            // Проверяем локальный кеш если есть ticketId
            if (ticketId != null) {
                val cachedMessages = chatDao.getMessages(ticketId)
                val now = System.currentTimeMillis()

                if (cachedMessages.isNotEmpty()) {
                    val isRecent = cachedMessages.all { (now - it.cachedAt) < CACHE_VALIDITY_MS }
                    if (isRecent) {
                        android.util.Log.d("ChatRepository", "✅ Using cached messages: ${cachedMessages.size}")
                        return Result.success(cachedMessages.map { entityToDTO(it) })
                    }
                }
            }

            // Загружаем с API
            val response = chatApi.getChatMessages(ticketId, limit, before, after, order)

            android.util.Log.d("ChatRepository", "Get messages response code: ${response.code()}")

            if (response.isSuccessful) {
                val messages = response.body() ?: return Result.failure(Exception("Empty response body"))

                android.util.Log.d("ChatRepository", "Received ${messages.size} messages")

                // Фильтруем is_internal = true
                val filtered = messages.filterNot { it.isInternal }

                // Сортируем по дате
                val sorted = filtered.sortedBy { it.parsedDate }
                android.util.Log.d("ChatRepository", "Total messages: ${sorted.size}")

                // Кешируем сообщения
                if (ticketId != null && sorted.isNotEmpty()) {
                    val entities = sorted.map { dtoToEntity(it, ticketId) }
                    entities.forEach { chatDao.insertMessage(it) }
                    android.util.Log.d("ChatRepository", "✅ Cached ${entities.size} messages")

                    // Автоочистка старых сообщений (> 100)
                    chatDao.deleteOldestMessages(100)
                }

                Result.success(sorted)
            } else {
                val error = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("ChatRepository", "API error: $error")

                // При ошибке API возвращаем кеш если он есть
                if (ticketId != null) {
                    val cachedMessages = chatDao.getMessages(ticketId)
                    if (cachedMessages.isNotEmpty()) {
                        android.util.Log.d("ChatRepository", "⚠️ Using stale cache due to API error")
                        return Result.success(cachedMessages.map { entityToDTO(it) })
                    }
                }

                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "getMessages exception", e)

            // При сетевой ошибке возвращаем кеш
            if (ticketId != null) {
                val cachedMessages = chatDao.getMessages(ticketId)
                if (cachedMessages.isNotEmpty()) {
                    android.util.Log.d("ChatRepository", "📱 Offline mode: using cached messages")
                    return Result.success(cachedMessages.map { entityToDTO(it) })
                }
            }

            Result.failure(e)
        }
    }

    /**
     * Отправить сообщение в чат
     */
    override suspend fun sendMessage(
        text: String?,
        ticketId: String?,
        photoUrl: String?,
        voiceUrl: String?,
        voiceDuration: Float?,
        messageType: String
    ): Result<ChatMessageDTO> {
        return try {
            android.util.Log.d("ChatRepository", "Sending message, ticketId: $ticketId, text: $text, photoUrl: $photoUrl, voiceUrl: $voiceUrl, type: $messageType")

            // Определяем текст сообщения
            val messageText = when {
                messageType == "voice" && text.isNullOrBlank() -> "🎤 Голосовое сообщение"
                text.isNullOrBlank() && photoUrl != null -> "📷 Фото"
                else -> text ?: ""
            }

            val request = ChatMessageCreateRequest(
                text = messageText,
                photoUrl = photoUrl,
                voiceUrl = voiceUrl,
                voiceDuration = voiceDuration,
                messageType = messageType,
                ticketId = ticketId
            )
            val response = chatApi.sendChatMessage(request)

            android.util.Log.d("ChatRepository", "Send message response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let {
                    android.util.Log.d("ChatRepository", "✅ Message sent: ${it.id}")
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val error = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("ChatRepository", "❌ Send message error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.d("ChatRepository", "Offline: queuing chat message")
            val pendingEntity = ChatMessageEntity(
                id = "local-${System.currentTimeMillis()}",
                ticketId = ticketId ?: "",
                senderRole = "user",
                text = text ?: "",
                voiceUrl = voiceUrl,
                voiceDuration = voiceDuration,
                messageType = messageType,
                createdAt = java.time.OffsetDateTime.now().toString(),
                syncStatus = "pending"
            )
            chatDao.insertMessage(pendingEntity)
            Result.success(entityToDTO(pendingEntity))
        } catch (e: java.io.IOException) {
            android.util.Log.d("ChatRepository", "Network error: queuing chat message")
            val pendingEntity = ChatMessageEntity(
                id = "local-${System.currentTimeMillis()}",
                ticketId = ticketId ?: "",
                senderRole = "user",
                text = text ?: "",
                voiceUrl = voiceUrl,
                voiceDuration = voiceDuration,
                messageType = messageType,
                createdAt = java.time.OffsetDateTime.now().toString(),
                syncStatus = "pending"
            )
            chatDao.insertMessage(pendingEntity)
            Result.success(entityToDTO(pendingEntity))
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "sendMessage exception", e)
            Result.failure(e)
        }
    }

    /**
     * Загрузить фотографию для чата
     */
    override suspend fun uploadChatPhoto(file: File): Result<String> {
        return try {
            android.util.Log.d("ChatRepository", "Uploading chat photo: ${file.absolutePath}")
            android.util.Log.d("ChatRepository", "Original file - exists: ${file.exists()}, size: ${file.length()} bytes (${file.length() / 1024}KB)")

            // Сжимаем изображение перед загрузкой
            val compressedFileResult = ImageCompressor.compressImage(
                context = context,
                sourceFile = file
            )

            val fileToUpload = compressedFileResult.getOrNull()
            if (fileToUpload == null) {
                val errorMsg = "Не удалось сжать изображение"
                android.util.Log.e("ChatRepository", errorMsg)
                return Result.failure(Exception(errorMsg))
            }

            android.util.Log.d("ChatRepository", "Compressed file - size: ${fileToUpload.length()} bytes (${fileToUpload.length() / 1024}KB)")

            // Prepare multipart request
            val requestFile = fileToUpload.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", fileToUpload.name, requestFile)

            val response = chatApi.uploadChatPhoto(photoPart)

            android.util.Log.d("ChatRepository", "Upload response code: ${response.code()}")

            // Удаляем временный сжатый файл если он отличается от оригинального
            if (fileToUpload != file && fileToUpload.exists()) {
                fileToUpload.delete()
            }

            if (response.isSuccessful && response.body() != null) {
                val photoUrl = response.body()!!.photoUrl
                android.util.Log.d("ChatRepository", "✅ Photo uploaded successfully: $photoUrl")
                Result.success(photoUrl)
            } else {
                val error = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("ChatRepository", "❌ Upload failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "uploadChatPhoto exception", e)
            Result.failure(e)
        }
    }

    /**
     * Загрузить голосовое сообщение
     */
    override suspend fun uploadChatVoice(file: File, duration: Float): Result<Pair<String, Float>> {
        return try {
            android.util.Log.d("ChatRepository", "Uploading voice: ${file.absolutePath}, duration: $duration")

            val requestFile = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val voicePart = MultipartBody.Part.createFormData("voice", file.name, requestFile)

            val response = chatApi.uploadChatVoice(voicePart, duration)

            android.util.Log.d("ChatRepository", "Upload voice response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val voiceUrl = body.voiceUrl
                val dur = body.durationSeconds
                android.util.Log.d("ChatRepository", "Voice uploaded: $voiceUrl, duration: $dur")
                Result.success(Pair(voiceUrl, dur))
            } else {
                val error = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("ChatRepository", "Upload voice failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "uploadChatVoice exception", e)
            Result.failure(e)
        }
    }

    /**
     * Получить список чатов для куратора
     */
    override suspend fun getInbox(
        limit: Int?,
        offset: Int?,
        searchQuery: String?
    ): Result<List<com.belsi.work.data.models.SupportTicketDto>> {
        return try {
            android.util.Log.d("ChatRepository", "Loading inbox, limit: $limit, offset: $offset")

            val response = chatApi.getChatInbox(limit, offset, searchQuery)

            android.util.Log.d("ChatRepository", "Get inbox response code: ${response.code()}")

            if (response.isSuccessful) {
                val tickets = response.body() ?: emptyList()
                android.util.Log.d("ChatRepository", "Loaded ${tickets.size} tickets")
                Result.success(tickets)
            } else {
                val error = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("ChatRepository", "Get inbox error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "getInbox exception", e)
            Result.failure(e)
        }
    }

    /**
     * Отметить сообщения как прочитанные
     */
    override suspend fun markAsRead(ticketId: String, lastMessageId: String?): Result<Unit> {
        return try {
            android.util.Log.d("ChatRepository", "Marking as read, ticketId: $ticketId")

            val request = com.belsi.work.data.remote.api.MarkAsReadRequest(
                ticketId = ticketId,
                lastReadMessageId = lastMessageId
            )
            val response = chatApi.markAsRead(request)

            android.util.Log.d("ChatRepository", "Mark as read response code: ${response.code()}")

            if (response.isSuccessful) {
                android.util.Log.d("ChatRepository", "✅ Marked as read")
                Result.success(Unit)
            } else {
                val error = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("ChatRepository", "Mark as read error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "markAsRead exception", e)
            Result.failure(e)
        }
    }

    /**
     * Наблюдать за сообщениями в реальном времени через Flow
     */
    override fun observeMessages(ticketId: String): Flow<List<ChatMessageDTO>> {
        return chatDao.getMessagesFlow(ticketId).map { entities ->
            entities.map { entityToDTO(it) }
        }
    }

    /**
     * Синхронизировать отложенные сообщения с сервером
     */
    override suspend fun syncPendingMessages(): Result<Unit> {
        return try {
            android.util.Log.d("ChatRepository", "=== SYNCING PENDING MESSAGES ===")
            val pendingMessages = chatDao.getPendingMessages()

            if (pendingMessages.isEmpty()) {
                android.util.Log.d("ChatRepository", "No pending messages to sync")
                return Result.success(Unit)
            }

            android.util.Log.d("ChatRepository", "Found ${pendingMessages.size} pending messages")

            var successCount = 0
            var errorCount = 0

            pendingMessages.forEach { message ->
                try {
                    val request = ChatMessageCreateRequest(
                        text = message.text,
                        photoUrl = null, // Pending messages don't have photos yet
                        ticketId = message.ticketId
                    )
                    val response = chatApi.sendChatMessage(request)

                    if (response.isSuccessful && response.body() != null) {
                        // Помечаем как синхронизированное
                        chatDao.updateSyncStatus(message.id, "synced")
                        successCount++
                        android.util.Log.d("ChatRepository", "✅ Synced message: ${message.id}")
                    } else {
                        // Помечаем как ошибку
                        chatDao.updateSyncStatus(message.id, "error")
                        errorCount++
                        android.util.Log.e("ChatRepository", "❌ Failed to sync message: ${message.id}")
                    }
                } catch (e: Exception) {
                    chatDao.updateSyncStatus(message.id, "error")
                    errorCount++
                    android.util.Log.e("ChatRepository", "❌ Exception syncing message: ${message.id}", e)
                }
            }

            android.util.Log.d("ChatRepository", "Sync complete: success=$successCount, errors=$errorCount")

            if (errorCount > 0) {
                Result.failure(Exception("Синхронизировано $successCount из ${pendingMessages.size} сообщений"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "syncPendingMessages failed", e)
            Result.failure(e)
        }
    }

    /**
     * Конвертировать DTO в Entity для сохранения в БД
     */
    private fun dtoToEntity(dto: ChatMessageDTO, ticketId: String): ChatMessageEntity {
        return ChatMessageEntity(
            id = dto.id,
            ticketId = ticketId,
            senderRole = dto.senderRole,
            senderUserId = dto.senderUserId ?: "",
            text = dto.text,
            voiceUrl = dto.voiceUrl,
            voiceDuration = dto.voiceDuration,
            messageType = dto.messageType,
            createdAt = dto.createdAt,
            syncStatus = "synced",
            cachedAt = System.currentTimeMillis()
        )
    }

    /**
     * Конвертировать Entity в DTO для использования в приложении
     */
    private fun entityToDTO(entity: ChatMessageEntity): ChatMessageDTO {
        return ChatMessageDTO(
            id = entity.id,
            ticketId = entity.ticketId,
            senderRole = entity.senderRole,
            senderUserId = entity.senderUserId,
            text = entity.text,
            voiceUrl = entity.voiceUrl,
            voiceDuration = entity.voiceDuration,
            messageType = entity.messageType,
            createdAt = entity.createdAt,
            isInternal = false
        )
    }
}
