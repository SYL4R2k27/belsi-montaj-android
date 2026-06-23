package com.belsi.work.presentation.screens.messenger

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.TokenManager
import com.belsi.work.data.models.MessengerMessageDTO
import com.belsi.work.data.models.ParticipantDTO
import com.belsi.work.data.models.ThreadDTO
import com.belsi.work.data.repositories.MessengerRepository
import com.belsi.work.data.repositories.UserRepository
import com.belsi.work.data.remote.websocket.MessengerWebSocket
import com.belsi.work.data.remote.websocket.WsConnectionState
import com.belsi.work.data.remote.websocket.WsEvent
import com.belsi.work.presentation.screens.chat.components.VoiceRecorderHelper
import com.belsi.work.utils.ImageCompressor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import javax.inject.Inject

data class ConversationUiState(
    val thread: ThreadDTO? = null,
    val messages: List<MessengerMessageDTO> = emptyList(),
    val messageText: String = "",
    val selectedPhotoUri: Uri? = null,
    val currentUserId: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val hasMoreMessages: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val isRecordingVoice: Boolean = false,
    val voiceRecordingDurationMs: Long = 0L,
    // Reply
    val replyingTo: MessengerMessageDTO? = null,
    // Typing
    val typingUserName: String? = null,
    val error: String? = null
)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val messengerRepository: MessengerRepository,
    private val userRepository: UserRepository,
    private val tokenManager: TokenManager,
    private val messengerWebSocket: MessengerWebSocket,
    private val json: Json,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val threadId: String = savedStateHandle.get<String>("threadId") ?: ""

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var wsListenerJob: Job? = null
    private var voiceRecorder: VoiceRecorderHelper? = null
    private var voiceTimerJob: Job? = null
    private var typingJob: Job? = null
    private var typingClearJob: Job? = null

    init {
        if (threadId.isNotEmpty()) {
            viewModelScope.launch {
                var userId = tokenManager.getUserId() ?: ""
                // Fallback: если userId пустой (старая сессия), подтягиваем из /user/me
                if (userId.isBlank()) {
                    try {
                        userRepository.getProfile().onSuccess { user ->
                            userId = user.id.toString()
                        }
                    } catch (_: Exception) { /* не блокируем UI */ }
                }
                _uiState.value = _uiState.value.copy(currentUserId = userId)
            }
            loadMessages()
            // Connect WS and listen for events
            messengerWebSocket.connect()
            listenWebSocket()
            // Fallback polling (slower when WS is connected)
            startPolling()
        }
    }

    fun loadMessages() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            messengerRepository.getMessages(threadId)
                .onSuccess { (messages, hasMore) ->
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        hasMoreMessages = hasMore,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = it.message
                    )
                }

            // Load thread info
            messengerRepository.getThreads()
                .onSuccess { threads ->
                    val thread = threads.find { it.id == threadId }
                    if (thread != null) {
                        _uiState.value = _uiState.value.copy(thread = thread)
                    }
                }

            // Mark as read
            messengerRepository.markRead(threadId)
            messengerWebSocket.sendRead(threadId)
        }
    }

    fun loadOlderMessages() {
        if (_uiState.value.isLoadingOlder || !_uiState.value.hasMoreMessages) return
        val oldest = _uiState.value.messages.firstOrNull() ?: return

        _uiState.value = _uiState.value.copy(isLoadingOlder = true)
        viewModelScope.launch {
            messengerRepository.getMessages(threadId, before = oldest.id)
                .onSuccess { (olderMessages, hasMore) ->
                    _uiState.value = _uiState.value.copy(
                        messages = olderMessages + _uiState.value.messages,
                        hasMoreMessages = hasMore,
                        isLoadingOlder = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingOlder = false)
                }
        }
    }

    fun updateMessageText(text: String) {
        _uiState.value = _uiState.value.copy(messageText = text)
        // Send typing indicator (throttled)
        sendTypingThrottled()
    }

    fun selectPhoto(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedPhotoUri = uri)
    }

    fun clearPhoto() {
        _uiState.value = _uiState.value.copy(selectedPhotoUri = null)
    }

    // ---- Reply ----

    fun setReplyTo(message: MessengerMessageDTO) {
        _uiState.value = _uiState.value.copy(replyingTo = message)
    }

    fun clearReply() {
        _uiState.value = _uiState.value.copy(replyingTo = null)
    }

    // ---- File sending ----

    fun sendFile(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(isSending = true)
        viewModelScope.launch {
            try {
                val file = createTempFileFromUri(context, uri, preserveName = true)
                if (file == null) {
                    _uiState.value = _uiState.value.copy(isSending = false, error = "Ошибка чтения файла")
                    return@launch
                }

                messengerRepository.uploadFile(file)
                    .onSuccess { uploadResp ->
                        messengerRepository.sendMessage(
                            threadId = threadId,
                            text = null,
                            fileUrl = uploadResp.fileUrl,
                            fileName = uploadResp.fileName,
                            fileSize = uploadResp.fileSize,
                            messageType = "file",
                            replyToId = _uiState.value.replyingTo?.id
                        )
                            .onSuccess { newMsg ->
                                val current = _uiState.value.messages
                                val updated = if (current.any { it.id == newMsg.id }) current
                                else current + newMsg
                                _uiState.value = _uiState.value.copy(
                                    messages = updated,
                                    isSending = false,
                                    replyingTo = null,
                                    error = null
                                )
                            }
                            .onFailure {
                                _uiState.value = _uiState.value.copy(isSending = false, error = "Ошибка отправки: ${it.message}")
                            }
                    }
                    .onFailure {
                        _uiState.value = _uiState.value.copy(isSending = false, error = "Ошибка загрузки файла: ${it.message}")
                    }
                file.delete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSending = false, error = "Ошибка: ${e.message}")
            }
        }
    }

    // ---- Delete message ----

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            messengerRepository.deleteMessage(threadId, messageId)
                .onSuccess { deletedMsg ->
                    val updated = _uiState.value.messages.map { msg ->
                        if (msg.id == messageId) deletedMsg else msg
                    }
                    _uiState.value = _uiState.value.copy(messages = updated)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(error = "Ошибка удаления: ${it.message}")
                }
        }
    }

    // ---- Forward message ----

    fun forwardMessage(message: com.belsi.work.data.models.MessengerMessageDTO, toThreadId: String) {
        viewModelScope.launch {
            messengerRepository.sendMessage(
                threadId = toThreadId,
                text = message.text,
                photoUrl = message.photoUrl,
                voiceUrl = message.voiceUrl,
                voiceDurationSeconds = message.voiceDurationSeconds,
                fileUrl = message.fileUrl,
                fileName = message.fileName,
                fileSize = message.fileSize,
                messageType = message.messageType,
                forwardedFromId = message.id
            )
        }
    }

    // ---- Typing indicator ----

    private fun sendTypingThrottled() {
        if (typingJob?.isActive == true) return
        typingJob = viewModelScope.launch {
            messengerWebSocket.sendTyping(threadId)
            delay(3000) // throttle: one typing event per 3s
        }
    }

    // ---- Send message ----

    fun sendMessage(context: Context) {
        val text = _uiState.value.messageText.trim()
        val photoUri = _uiState.value.selectedPhotoUri
        val replyToId = _uiState.value.replyingTo?.id
        val replyTo = _uiState.value.replyingTo
        if (text.isEmpty() && photoUri == null) return

        // Optimistic: for text-only messages, show immediately
        val localId = UUID.randomUUID().toString()
        val isTextOnly = photoUri == null && text.isNotEmpty()

        if (isTextOnly) {
            val optimisticMsg = MessengerMessageDTO(
                id = localId,
                threadId = threadId,
                senderId = _uiState.value.currentUserId,
                senderName = "Вы",
                messageType = "text",
                text = text,
                replyToId = replyToId,
                replyTo = replyTo?.let { r ->
                    com.belsi.work.data.models.ReplyMessageDTO(
                        id = r.id,
                        senderName = r.senderName,
                        text = r.text,
                        messageType = r.messageType,
                        photoUrl = r.photoUrl
                    )
                },
                createdAt = java.time.OffsetDateTime.now().toString()
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + optimisticMsg,
                messageText = "",
                replyingTo = null
            )
        } else {
            _uiState.value = _uiState.value.copy(isSending = true)
        }

        viewModelScope.launch {
            try {
                var photoUrl: String? = null

                if (photoUri != null) {
                    _uiState.value = _uiState.value.copy(isUploadingPhoto = true)
                    val file = createTempFileFromUri(context, photoUri)
                    if (file != null) {
                        val compressedFile = File(context.cacheDir, "compressed_${file.name}")
                        ImageCompressor.compressImage(context, file, compressedFile)
                            .onSuccess { compressed ->
                                messengerRepository.uploadPhoto(compressed)
                                    .onSuccess { photoUrl = it }
                                    .onFailure {
                                        _uiState.value = _uiState.value.copy(
                                            isSending = false,
                                            isUploadingPhoto = false,
                                            error = "Ошибка загрузки фото: ${it.message}"
                                        )
                                        return@launch
                                    }
                                compressed.delete()
                            }
                            .onFailure {
                                messengerRepository.uploadPhoto(file)
                                    .onSuccess { photoUrl = it }
                                    .onFailure { err ->
                                        _uiState.value = _uiState.value.copy(
                                            isSending = false,
                                            isUploadingPhoto = false,
                                            error = "Ошибка загрузки фото: ${err.message}"
                                        )
                                        return@launch
                                    }
                            }
                        file.delete()
                    }
                    _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
                }

                val msgType = if (photoUrl != null) "photo" else "text"
                messengerRepository.sendMessage(
                    threadId = threadId,
                    text = text.ifEmpty { null },
                    photoUrl = photoUrl,
                    messageType = msgType,
                    replyToId = replyToId
                )
                    .onSuccess { newMsg ->
                        val current = _uiState.value.messages
                        // Replace optimistic message with server response
                        val updated = if (isTextOnly) {
                            current.map { if (it.id == localId) newMsg else it }
                        } else {
                            if (current.any { it.id == newMsg.id }) current
                            else current + newMsg
                        }
                        _uiState.value = _uiState.value.copy(
                            messages = updated,
                            messageText = if (!isTextOnly) "" else _uiState.value.messageText,
                            selectedPhotoUri = null,
                            replyingTo = if (!isTextOnly) null else _uiState.value.replyingTo,
                            isSending = false,
                            error = null
                        )
                    }
                    .onFailure {
                        if (isTextOnly) {
                            // Remove the optimistic message on failure
                            val current = _uiState.value.messages
                            _uiState.value = _uiState.value.copy(
                                messages = current.filter { it.id != localId },
                                messageText = text, // restore text
                                error = "Ошибка отправки: ${it.message}"
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isSending = false,
                                error = "Ошибка отправки: ${it.message}"
                            )
                        }
                    }
            } catch (e: Exception) {
                if (isTextOnly) {
                    val current = _uiState.value.messages
                    _uiState.value = _uiState.value.copy(
                        messages = current.filter { it.id != localId },
                        messageText = text,
                        error = "Ошибка: ${e.message}"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        isUploadingPhoto = false,
                        error = "Ошибка: ${e.message}"
                    )
                }
            }
        }
    }

    // ---- Voice recording ----

    fun startVoiceRecording() {
        val recorder = VoiceRecorderHelper(appContext)
        val file = recorder.startRecording()
        if (file != null) {
            voiceRecorder = recorder
            _uiState.value = _uiState.value.copy(
                isRecordingVoice = true,
                voiceRecordingDurationMs = 0L
            )
            voiceTimerJob?.cancel()
            voiceTimerJob = viewModelScope.launch {
                while (isActive) {
                    delay(100)
                    _uiState.value = _uiState.value.copy(
                        voiceRecordingDurationMs = voiceRecorder?.getElapsedMs() ?: 0L
                    )
                }
            }
        }
    }

    fun stopVoiceRecording() {
        voiceTimerJob?.cancel()
        val result = voiceRecorder?.stopRecording()
        voiceRecorder = null
        _uiState.value = _uiState.value.copy(
            isRecordingVoice = false,
            voiceRecordingDurationMs = 0L
        )
        if (result != null) {
            val (file, duration) = result
            sendVoiceMessage(file, duration)
        }
    }

    fun cancelVoiceRecording() {
        voiceTimerJob?.cancel()
        voiceRecorder?.cancelRecording()
        voiceRecorder = null
        _uiState.value = _uiState.value.copy(
            isRecordingVoice = false,
            voiceRecordingDurationMs = 0L
        )
    }

    private fun sendVoiceMessage(file: File, duration: Float) {
        _uiState.value = _uiState.value.copy(isSending = true)
        viewModelScope.launch {
            try {
                messengerRepository.uploadVoice(file, duration)
                    .onSuccess { (voiceUrl, dur) ->
                        messengerRepository.sendMessage(
                            threadId = threadId,
                            text = null,
                            voiceUrl = voiceUrl,
                            voiceDurationSeconds = dur,
                            messageType = "voice",
                            replyToId = _uiState.value.replyingTo?.id
                        )
                            .onSuccess { newMsg ->
                                val current = _uiState.value.messages
                                val updated = if (current.any { it.id == newMsg.id }) current
                                else current + newMsg
                                _uiState.value = _uiState.value.copy(
                                    messages = updated,
                                    isSending = false,
                                    replyingTo = null,
                                    error = null
                                )
                            }
                            .onFailure {
                                _uiState.value = _uiState.value.copy(
                                    isSending = false,
                                    error = "Ошибка отправки: ${it.message}"
                                )
                            }
                    }
                    .onFailure {
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            error = "Ошибка загрузки голосового: ${it.message}"
                        )
                    }
                file.delete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    error = "Ошибка: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun listenWebSocket() {
        wsListenerJob?.cancel()
        wsListenerJob = viewModelScope.launch {
            messengerWebSocket.events.collect { event ->
                when (event) {
                    is WsEvent.NewMessage -> {
                        if (event.threadId == threadId) {
                            // Parse the incoming message and add to list
                            try {
                                val msg = json.decodeFromJsonElement<MessengerMessageDTO>(event.messageJson)
                                val current = _uiState.value.messages
                                if (current.none { it.id == msg.id }) {
                                    _uiState.value = _uiState.value.copy(
                                        messages = current + msg,
                                        typingUserName = null // clear typing on new message
                                    )
                                }
                                // Mark as read since we're viewing this thread
                                messengerRepository.markRead(threadId)
                                messengerWebSocket.sendRead(threadId)
                            } catch (_: Exception) {
                                // Fallback: reload from API
                                loadMessages()
                            }
                        }
                    }
                    is WsEvent.ThreadUpdated -> {
                        if (event.threadId == threadId) {
                            messengerRepository.getThreads()
                                .onSuccess { threads ->
                                    val thread = threads.find { it.id == threadId }
                                    if (thread != null) {
                                        _uiState.value = _uiState.value.copy(thread = thread)
                                    }
                                }
                        }
                    }
                    is WsEvent.Typing -> {
                        if (event.threadId == threadId &&
                            event.userId != _uiState.value.currentUserId) {
                            _uiState.value = _uiState.value.copy(
                                typingUserName = event.userName
                            )
                            // Clear typing after 4 seconds
                            typingClearJob?.cancel()
                            typingClearJob = viewModelScope.launch {
                                delay(4000)
                                _uiState.value = _uiState.value.copy(typingUserName = null)
                            }
                        }
                    }
                    is WsEvent.Read -> {
                        if (event.threadId == threadId) {
                            // Mark all messages as read for that user
                            val updated = _uiState.value.messages.map { msg ->
                                if (msg.senderId == _uiState.value.currentUserId && !msg.isRead) {
                                    msg.copy(isRead = true)
                                } else msg
                            }
                            _uiState.value = _uiState.value.copy(messages = updated)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                // Use longer interval when WS is connected, shorter as fallback
                val interval = if (messengerWebSocket.connectionState.value == WsConnectionState.CONNECTED) {
                    15_000L // 15s — rare safety check
                } else {
                    3_000L // 3s — active polling when WS is down
                }
                delay(interval)
                messengerRepository.getMessages(threadId)
                    .onSuccess { (messages, hasMore) ->
                        val current = _uiState.value.messages
                        val hasChanges = messages.size != current.size ||
                                messages.lastOrNull()?.id != current.lastOrNull()?.id
                        if (hasChanges) {
                            _uiState.value = _uiState.value.copy(
                                messages = messages,
                                hasMoreMessages = hasMore
                            )
                            messengerRepository.markRead(threadId)
                        }
                    }
            }
        }
    }

    private fun createTempFileFromUri(context: Context, uri: Uri, preserveName: Boolean = false): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            val fileName = if (preserveName) {
                // Try to get real file name
                var name: String? = null
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(idx)
                    }
                }
                name ?: "file_${System.currentTimeMillis()}"
            } else {
                "msg_photo_${System.currentTimeMillis()}.jpg"
            }

            val tempFile = File(context.cacheDir, fileName)
            tempFile.outputStream().use { inputStream.copyTo(it) }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        wsListenerJob?.cancel()
        voiceTimerJob?.cancel()
        typingClearJob?.cancel()
        voiceRecorder?.cancelRecording()
        messengerWebSocket.disconnect()
    }
}
