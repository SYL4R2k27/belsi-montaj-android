package com.belsi.work.presentation.screens.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.ChatMessageDTO
import com.belsi.work.data.repositories.ChatRepository
import com.belsi.work.presentation.screens.chat.components.VoiceRecorderHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class InstallerChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var voiceRecorder: VoiceRecorderHelper? = null
    private var voiceTimerJob: Job? = null

    init {
        loadMessages()
        startPolling()
    }

    /**
     * Выбрать фотографию для отправки
     */
    fun selectPhoto(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedPhotoUri = uri)
    }

    /**
     * Удалить выбранную фотографию
     */
    fun clearPhoto() {
        _uiState.value = _uiState.value.copy(selectedPhotoUri = null)
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
                    val elapsed = voiceRecorder?.getElapsedMs() ?: 0L
                    _uiState.value = _uiState.value.copy(voiceRecordingDurationMs = elapsed)
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
                chatRepository.uploadChatVoice(file, duration)
                    .onSuccess { (voiceUrl, dur) ->
                        chatRepository.sendMessage(
                            text = null,
                            voiceUrl = voiceUrl,
                            voiceDuration = dur,
                            messageType = "voice"
                        )
                            .onSuccess { newMessage ->
                                val currentMessages = _uiState.value.messages
                                val messageExists = currentMessages.any { it.id == newMessage.id }
                                val updatedMessages = if (messageExists) currentMessages
                                else (currentMessages + newMessage).sortedBy { it.parsedDate }

                                _uiState.value = _uiState.value.copy(
                                    messages = updatedMessages,
                                    isSending = false,
                                    errorMessage = null
                                )
                                loadMessages(isRefresh = true)
                            }
                            .onFailure { e ->
                                _uiState.value = _uiState.value.copy(
                                    isSending = false,
                                    errorMessage = "Ошибка отправки: ${e.message}"
                                )
                            }
                    }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            errorMessage = "Ошибка загрузки голосового: ${e.message}"
                        )
                    }

                file.delete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = "Ошибка: ${e.message}"
                )
            }
        }
    }

    /**
     * Автообновление каждые 3 секунды (как в iOS)
     */
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000) // 3 seconds
                loadMessages(isRefresh = true)
            }
        }
    }

    /**
     * Загрузить сообщения из чата
     */
    private fun loadMessages(isRefresh: Boolean = false) {
        if (!isRefresh) {
            _uiState.value = _uiState.value.copy(isLoading = true)
        }

        viewModelScope.launch {
            chatRepository.getMessages(
                limit = null,
                order = "asc"
            )
                .onSuccess { messages ->
                    android.util.Log.d("InstallerChatVM", "Loaded ${messages.size} messages")

                    val currentMessages = _uiState.value.messages
                    val hasChanges = messages.size != currentMessages.size ||
                                    messages.lastOrNull()?.id != currentMessages.lastOrNull()?.id

                    if (hasChanges || !isRefresh) {
                        _uiState.value = _uiState.value.copy(
                            messages = messages,
                            isLoading = false,
                            errorMessage = null,
                            hasMoreMessages = false
                        )
                    } else if (isRefresh) {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
                .onFailure { e ->
                    android.util.Log.e("InstallerChatVM", "Failed to load messages", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Ошибка загрузки сообщений"
                    )
                }
        }
    }

    /**
     * Загрузить старые сообщения (pagination)
     */
    fun loadOlderMessages() {
        if (_uiState.value.isLoadingOlder || !_uiState.value.hasMoreMessages) {
            return
        }

        val oldestMessage = _uiState.value.messages.firstOrNull() ?: return
        _uiState.value = _uiState.value.copy(isLoadingOlder = true)

        viewModelScope.launch {
            chatRepository.getMessages(
                limit = 20,
                before = oldestMessage.id,
                order = "asc"
            )
                .onSuccess { olderMessages ->
                    if (olderMessages.isNotEmpty()) {
                        val combinedMessages = olderMessages + _uiState.value.messages
                        _uiState.value = _uiState.value.copy(
                            messages = combinedMessages,
                            isLoadingOlder = false,
                            hasMoreMessages = olderMessages.size >= 20
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoadingOlder = false,
                            hasMoreMessages = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingOlder = false,
                        errorMessage = e.message ?: "Ошибка загрузки старых сообщений"
                    )
                }
        }
    }

    /**
     * Отправить сообщение (с поддержкой фотографий)
     */
    fun sendMessage(context: Context) {
        val text = _uiState.value.messageText.trim()
        val photoUri = _uiState.value.selectedPhotoUri

        if (text.isEmpty() && photoUri == null) return

        _uiState.value = _uiState.value.copy(isSending = true)

        viewModelScope.launch {
            try {
                var photoUrl: String? = null

                if (photoUri != null) {
                    _uiState.value = _uiState.value.copy(isUploadingPhoto = true)

                    val photoFile = createTempFileFromUri(context, photoUri)
                    if (photoFile != null && photoFile.exists()) {
                        chatRepository.uploadChatPhoto(photoFile)
                            .onSuccess { url ->
                                photoUrl = url
                                android.util.Log.d("InstallerChatVM", "Photo uploaded: $url")
                            }
                            .onFailure { e ->
                                android.util.Log.e("InstallerChatVM", "Failed to upload photo", e)
                                _uiState.value = _uiState.value.copy(
                                    isSending = false,
                                    isUploadingPhoto = false,
                                    errorMessage = "Ошибка загрузки фото: ${e.message}"
                                )
                                return@launch
                            }
                        photoFile.delete()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            isUploadingPhoto = false,
                            errorMessage = "Не удалось обработать фото"
                        )
                        return@launch
                    }

                    _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
                }

                val msgType = if (photoUrl != null) "photo" else "text"
                chatRepository.sendMessage(
                    text = text.ifEmpty { null },
                    photoUrl = photoUrl,
                    messageType = msgType
                )
                    .onSuccess { newMessage ->
                        android.util.Log.d("InstallerChatVM", "Message sent successfully: ${newMessage.id}")

                        val currentMessages = _uiState.value.messages
                        val messageExists = currentMessages.any { it.id == newMessage.id }

                        val updatedMessages = if (messageExists) {
                            currentMessages
                        } else {
                            (currentMessages + newMessage).sortedBy { it.parsedDate }
                        }

                        _uiState.value = _uiState.value.copy(
                            messages = updatedMessages,
                            messageText = "",
                            selectedPhotoUri = null,
                            isSending = false,
                            errorMessage = null
                        )

                        loadMessages(isRefresh = true)
                    }
                    .onFailure { e ->
                        android.util.Log.e("InstallerChatVM", "Failed to send message", e)
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            errorMessage = e.message ?: "Ошибка отправки сообщения"
                        )
                    }
            } catch (e: Exception) {
                android.util.Log.e("InstallerChatVM", "sendMessage exception", e)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    isUploadingPhoto = false,
                    errorMessage = "Ошибка: ${e.message}"
                )
            }
        }
    }

    private fun createTempFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("chat_photo_", ".jpg", context.cacheDir)
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            tempFile
        } catch (e: Exception) {
            android.util.Log.e("InstallerChatVM", "Failed to create temp file", e)
            null
        }
    }

    fun updateMessageText(text: String) {
        _uiState.value = _uiState.value.copy(messageText = text)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun refresh() {
        loadMessages()
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        voiceTimerJob?.cancel()
        voiceRecorder?.cancelRecording()
    }
}

data class ChatUiState(
    val messages: List<ChatMessageDTO> = emptyList(),
    val messageText: String = "",
    val selectedPhotoUri: Uri? = null,
    val isUploadingPhoto: Boolean = false,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val isRecordingVoice: Boolean = false,
    val voiceRecordingDurationMs: Long = 0L,
    val errorMessage: String? = null
)
