package com.belsi.work.data.repositories

import android.util.Log
import com.belsi.work.data.local.database.dao.MessengerMessageDao
import com.belsi.work.data.local.database.dao.MessengerThreadDao
import com.belsi.work.data.local.database.entities.MessengerMessageEntity
import com.belsi.work.data.local.database.entities.MessengerThreadEntity
import com.belsi.work.data.models.*
import com.belsi.work.data.remote.api.ChatApi
import com.belsi.work.data.remote.api.MessengerApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface MessengerRepository {
    /** Get all threads for current user */
    suspend fun getThreads(): Result<List<ThreadDTO>>

    /** Create a new thread */
    suspend fun createThread(type: String, name: String? = null, participantIds: List<String>): Result<ThreadDTO>

    /** Get messages in a thread */
    suspend fun getMessages(threadId: String, limit: Int = 50, before: String? = null): Result<Pair<List<MessengerMessageDTO>, Boolean>>

    /** Send a text/photo/voice/file message */
    suspend fun sendMessage(
        threadId: String,
        text: String? = null,
        photoUrl: String? = null,
        voiceUrl: String? = null,
        voiceDurationSeconds: Float? = null,
        fileUrl: String? = null,
        fileName: String? = null,
        fileSize: Long? = null,
        messageType: String = "text",
        replyToId: String? = null,
        forwardedFromId: String? = null
    ): Result<MessengerMessageDTO>

    /** Mark thread as read */
    suspend fun markRead(threadId: String): Result<Unit>

    /** Get available contacts */
    suspend fun getContacts(): Result<List<ContactDTO>>

    /** Upload photo for messenger (reuses support chat upload endpoint) */
    suspend fun uploadPhoto(file: File): Result<String>

    /** Upload voice for messenger (reuses support chat upload endpoint) */
    suspend fun uploadVoice(file: File, duration: Float): Result<Pair<String, Float>>

    /** Add members to group */
    suspend fun addMembers(threadId: String, userIds: List<String>): Result<ThreadDTO>

    /** Remove member from group */
    suspend fun removeMember(threadId: String, userId: String): Result<Unit>

    /** Update thread name */
    suspend fun updateThread(threadId: String, name: String): Result<ThreadDTO>

    /** Upload file for messenger */
    suspend fun uploadFile(file: File): Result<FileUploadResponse>

    /** Delete a message (soft delete, sender only) */
    suspend fun deleteMessage(threadId: String, messageId: String): Result<MessengerMessageDTO>

    /** Search messages across threads */
    suspend fun searchMessages(query: String): Result<List<MessengerMessageDTO>>
}

@Singleton
class MessengerRepositoryImpl @Inject constructor(
    private val messengerApi: MessengerApi,
    private val chatApi: ChatApi, // reuse upload endpoints
    private val messengerMessageDao: MessengerMessageDao,
    private val messengerThreadDao: MessengerThreadDao,
    private val json: Json
) : MessengerRepository {

    companion object {
        private const val TAG = "MessengerRepo"
    }

    override suspend fun getThreads(): Result<List<ThreadDTO>> {
        return try {
            val response = messengerApi.getThreads()
            if (response.isSuccessful && response.body() != null) {
                val threads = response.body()!!.threads
                // Кэшируем в Room
                try {
                    messengerThreadDao.insertAll(threads.map { it.toEntity() })
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cache threads: ${e.message}")
                }
                Result.success(threads)
            } else {
                // Fallback: возвращаем кэш при ошибке API
                val cached = getCachedThreads()
                if (cached.isNotEmpty()) {
                    Result.success(cached)
                } else {
                    Result.failure(Exception("Ошибка загрузки чатов: ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            // Offline: возвращаем кэш
            val cached = getCachedThreads()
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun getCachedThreads(): List<ThreadDTO> {
        return try {
            val entities = messengerThreadDao.getAllThreads().firstOrNull()
            entities?.map { it.toDTO(json) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun createThread(
        type: String,
        name: String?,
        participantIds: List<String>
    ): Result<ThreadDTO> {
        return try {
            val request = CreateThreadRequest(
                type = type,
                name = name,
                participantIds = participantIds
            )
            val response = messengerApi.createThread(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Ошибка создания чата: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMessages(
        threadId: String,
        limit: Int,
        before: String?
    ): Result<Pair<List<MessengerMessageDTO>, Boolean>> {
        return try {
            val response = messengerApi.getMessages(threadId, limit, before)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Кэшируем сообщения в Room
                try {
                    messengerMessageDao.insertAll(body.messages.map { it.toEntity() })
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cache messages: ${e.message}")
                }
                Result.success(Pair(body.messages, body.hasMore))
            } else {
                // Fallback: кэш при ошибке API
                val cached = getCachedMessages(threadId)
                if (cached.isNotEmpty()) {
                    Result.success(Pair(cached, false))
                } else {
                    Result.failure(Exception("Ошибка загрузки сообщений: ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            // Offline: возвращаем кэш
            val cached = getCachedMessages(threadId)
            if (cached.isNotEmpty()) {
                Result.success(Pair(cached, false))
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun getCachedMessages(threadId: String): List<MessengerMessageDTO> {
        return try {
            // getRecentMessages возвращает suspend List (не Flow) — подходит для fallback
            val entities = messengerMessageDao.getRecentMessages(threadId, 100)
            entities.reversed().map { it.toDTO() } // reversed: DAO returns DESC, we need ASC
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun sendMessage(
        threadId: String,
        text: String?,
        photoUrl: String?,
        voiceUrl: String?,
        voiceDurationSeconds: Float?,
        fileUrl: String?,
        fileName: String?,
        fileSize: Long?,
        messageType: String,
        replyToId: String?,
        forwardedFromId: String?
    ): Result<MessengerMessageDTO> {
        val localId = "local-${System.currentTimeMillis()}"

        // 1) Optimistic: сохраняем сообщение локально сразу
        val pendingEntity = MessengerMessageEntity(
            id = localId,
            threadId = threadId,
            senderId = "", // will be filled by server
            senderName = "",
            messageType = messageType,
            text = text,
            photoUrl = photoUrl,
            voiceUrl = voiceUrl,
            voiceDurationSeconds = voiceDurationSeconds,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize,
            replyToId = replyToId,
            forwardedFrom = forwardedFromId,
            createdAt = java.time.OffsetDateTime.now().toString(),
            syncStatus = "sending",
            localId = localId
        )

        return try {
            val request = SendMessengerMessageRequest(
                text = text,
                photoUrl = photoUrl,
                voiceUrl = voiceUrl,
                voiceDurationSeconds = voiceDurationSeconds,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize,
                messageType = messageType,
                replyToId = replyToId,
                forwardedFromId = forwardedFromId
            )
            val response = messengerApi.sendMessage(threadId, request)
            if (response.isSuccessful && response.body() != null) {
                val msg = response.body()!!
                // Кэшируем отправленное сообщение (с серверным id)
                try {
                    messengerMessageDao.delete(localId) // remove optimistic
                    messengerMessageDao.insert(msg.toEntity())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cache sent message: ${e.message}")
                }
                Result.success(msg)
            } else {
                // Сервер вернул ошибку — сохраняем как failed
                try {
                    pendingEntity.copy(syncStatus = "failed").let {
                        messengerMessageDao.insert(it)
                    }
                } catch (_: Exception) {}
                Result.failure(Exception("Ошибка отправки: ${response.code()}"))
            }
        } catch (e: java.net.UnknownHostException) {
            // Нет сети — сохраняем для повторной отправки
            Log.d(TAG, "Offline: queuing message for thread $threadId")
            messengerMessageDao.insert(pendingEntity)
            // Возвращаем optimistic DTO чтобы UI показал сообщение сразу
            Result.success(pendingEntity.toDTO())
        } catch (e: java.net.ConnectException) {
            Log.d(TAG, "Offline: queuing message for thread $threadId")
            messengerMessageDao.insert(pendingEntity)
            Result.success(pendingEntity.toDTO())
        } catch (e: java.io.IOException) {
            Log.d(TAG, "Network error: queuing message for thread $threadId")
            messengerMessageDao.insert(pendingEntity)
            Result.success(pendingEntity.toDTO())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Повторная отправка сообщений из офлайн-очереди.
     * Вызывается SyncWorker при появлении сети.
     */
    suspend fun syncPendingMessages() {
        val pending = messengerMessageDao.getPendingMessages()
        if (pending.isEmpty()) return

        Log.d(TAG, "Syncing ${pending.size} pending messenger messages")

        for (entity in pending) {
            try {
                messengerMessageDao.updateSyncStatus(entity.id, "sending")

                val request = SendMessengerMessageRequest(
                    text = entity.text,
                    photoUrl = entity.photoUrl,
                    voiceUrl = entity.voiceUrl,
                    voiceDurationSeconds = entity.voiceDurationSeconds,
                    fileUrl = entity.fileUrl,
                    fileName = entity.fileName,
                    fileSize = entity.fileSize,
                    messageType = entity.messageType,
                    replyToId = entity.replyToId,
                    forwardedFromId = entity.forwardedFrom
                )

                val response = messengerApi.sendMessage(entity.threadId, request)
                if (response.isSuccessful && response.body() != null) {
                    val msg = response.body()!!
                    messengerMessageDao.delete(entity.id) // remove local
                    messengerMessageDao.insert(msg.toEntity()) // insert server version
                    Log.d(TAG, "Pending message ${entity.id} synced → ${msg.id}")
                } else {
                    messengerMessageDao.updateSyncStatus(entity.id, "failed")
                    Log.w(TAG, "Pending message ${entity.id} failed: ${response.code()}")
                }
            } catch (e: Exception) {
                messengerMessageDao.updateSyncStatus(entity.id, "failed")
                Log.e(TAG, "Pending message ${entity.id} error: ${e.message}")
            }
        }
    }

    override suspend fun markRead(threadId: String): Result<Unit> {
        return try {
            messengerApi.markRead(threadId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getContacts(): Result<List<ContactDTO>> {
        return try {
            val response = messengerApi.getContacts()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Ошибка загрузки контактов: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadPhoto(file: File): Result<String> {
        return try {
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("photo", file.name, requestFile)
            val response = chatApi.uploadChatPhoto(part)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.photoUrl)
            } else {
                Result.failure(Exception("Ошибка загрузки фото: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadVoice(file: File, duration: Float): Result<Pair<String, Float>> {
        return try {
            val requestFile = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("voice", file.name, requestFile)
            val response = chatApi.uploadChatVoice(part, duration)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(Pair(body.voiceUrl, body.durationSeconds))
            } else {
                Result.failure(Exception("Ошибка загрузки голосового: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addMembers(threadId: String, userIds: List<String>): Result<ThreadDTO> {
        return try {
            val response = messengerApi.addMembers(threadId, AddMembersRequest(userIds))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Ошибка добавления: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeMember(threadId: String, userId: String): Result<Unit> {
        return try {
            messengerApi.removeMember(threadId, userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateThread(threadId: String, name: String): Result<ThreadDTO> {
        return try {
            val response = messengerApi.updateThread(threadId, UpdateThreadRequest(name))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Ошибка обновления: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(file: File): Result<FileUploadResponse> {
        return try {
            val mediaType = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "doc", "docx" -> "application/msword"
                "xls", "xlsx" -> "application/vnd.ms-excel"
                "zip" -> "application/zip"
                "rar" -> "application/x-rar-compressed"
                "dwg" -> "application/acad"
                "dxf" -> "application/dxf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "application/octet-stream"
            }.toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val response = messengerApi.uploadFile(part)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Ошибка загрузки файла: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessage(threadId: String, messageId: String): Result<MessengerMessageDTO> {
        return try {
            val response = messengerApi.deleteMessage(threadId, messageId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Ошибка удаления: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMessages(query: String): Result<List<MessengerMessageDTO>> {
        return try {
            val response = messengerApi.searchMessages(query)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.messages)
            } else {
                Result.failure(Exception("Ошибка поиска: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ---- Entity ↔ DTO маппинг ----

fun MessengerMessageDTO.toEntity(syncStatus: String = "synced"): MessengerMessageEntity {
    return MessengerMessageEntity(
        id = id,
        threadId = threadId,
        senderId = senderId,
        senderName = senderName,
        senderRole = senderRole,
        messageType = messageType,
        text = text,
        photoUrl = photoUrl,
        voiceUrl = voiceUrl,
        voiceDurationSeconds = voiceDurationSeconds,
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize,
        replyToId = replyToId,
        replyToSenderName = replyTo?.senderName,
        replyToText = replyTo?.text,
        replyToMessageType = replyTo?.messageType,
        isRead = isRead,
        forwardedFrom = forwardedFrom,
        createdAt = createdAt,
        syncStatus = syncStatus
    )
}

fun MessengerMessageEntity.toDTO(): MessengerMessageDTO {
    return MessengerMessageDTO(
        id = id,
        threadId = threadId,
        senderId = senderId,
        senderName = senderName,
        senderRole = senderRole,
        messageType = messageType,
        text = text,
        photoUrl = photoUrl,
        voiceUrl = voiceUrl,
        voiceDurationSeconds = voiceDurationSeconds,
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize,
        replyToId = replyToId,
        replyTo = if (replyToId != null) ReplyMessageDTO(
            id = replyToId,
            senderName = replyToSenderName ?: "",
            text = replyToText,
            messageType = replyToMessageType ?: "text"
        ) else null,
        isRead = isRead,
        forwardedFrom = forwardedFrom,
        createdAt = createdAt
    )
}

fun ThreadDTO.toEntity(): MessengerThreadEntity {
    return MessengerThreadEntity(
        id = id,
        type = type,
        name = name,
        avatarUrl = avatarUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        unreadCount = unreadCount,
        lastMessageId = lastMessage?.id,
        lastMessageText = lastMessage?.preview,
        lastMessageType = lastMessage?.messageType,
        lastMessageSenderName = lastMessage?.senderName,
        lastMessageCreatedAt = lastMessage?.createdAt,
        participantsJson = try {
            kotlinx.serialization.json.Json.encodeToString(participants)
        } catch (_: Exception) { null }
    )
}

fun MessengerThreadEntity.toDTO(json: Json = kotlinx.serialization.json.Json): ThreadDTO {
    val parsedParticipants = try {
        participantsJson?.let { json.decodeFromString<List<ParticipantDTO>>(it) }
    } catch (_: Exception) { null } ?: emptyList()

    return ThreadDTO(
        id = id,
        type = type,
        name = name,
        avatarUrl = avatarUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastMessage = if (lastMessageId != null) MessengerMessageDTO(
            id = lastMessageId,
            threadId = id,
            senderId = "",
            senderName = lastMessageSenderName ?: "",
            messageType = lastMessageType ?: "text",
            text = lastMessageText,
            createdAt = lastMessageCreatedAt ?: ""
        ) else null,
        unreadCount = unreadCount,
        participants = parsedParticipants
    )
}
