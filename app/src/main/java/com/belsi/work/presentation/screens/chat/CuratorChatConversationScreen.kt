package com.belsi.work.presentation.screens.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.models.ChatMessageDTO
import com.belsi.work.data.repositories.ChatRepository
import com.belsi.work.presentation.screens.chat.components.FullscreenPhotoViewer
import com.belsi.work.presentation.screens.chat.components.VoiceMessagePlayer
import com.belsi.work.presentation.screens.chat.components.VoiceRecordButton
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
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

/**
 * ViewModel для переписки куратора с конкретным пользователем
 */
@HiltViewModel
class CuratorChatConversationViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var currentTicketId: String? = null
    private var voiceRecorder: VoiceRecorderHelper? = null
    private var voiceTimerJob: Job? = null

    fun setReplyTo(message: ChatMessageDTO?) {
        _uiState.value = _uiState.value.copy(replyingTo = message)
    }

    fun selectPhoto(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedPhotoUri = uri)
    }

    fun clearPhoto() {
        _uiState.value = _uiState.value.copy(selectedPhotoUri = null)
    }

    fun loadConversation(ticketId: String) {
        currentTicketId = ticketId
        loadMessages()
        startPolling()
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
                            ticketId = currentTicketId,
                            voiceUrl = voiceUrl,
                            voiceDuration = dur,
                            messageType = "voice"
                        )
                            .onSuccess { newMessage ->
                                val updatedMessages = _uiState.value.messages + newMessage
                                _uiState.value = _uiState.value.copy(
                                    messages = updatedMessages.sortedBy { it.parsedDate },
                                    isSending = false,
                                    errorMessage = null
                                )
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

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                loadMessages(isRefresh = true)
            }
        }
    }

    private fun loadMessages(isRefresh: Boolean = false) {
        val ticketId = currentTicketId ?: return

        if (!isRefresh) {
            _uiState.value = _uiState.value.copy(isLoading = true)
        }

        viewModelScope.launch {
            android.util.Log.d("CuratorChatConversationVM", "Loading messages for ticketId: $ticketId")
            chatRepository.getMessages(ticketId = ticketId)
                .onSuccess { messages ->
                    android.util.Log.d("CuratorChatConversationVM", "Loaded ${messages.size} messages")
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        ticketId = ticketId,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("CuratorChatConversationVM", "Failed to load messages", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Ошибка загрузки сообщений"
                    )
                }
        }
    }

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
                                android.util.Log.d("CuratorChatVM", "Photo uploaded: $url")
                            }
                            .onFailure { e ->
                                android.util.Log.e("CuratorChatVM", "Failed to upload photo", e)
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
                    text = text,
                    ticketId = currentTicketId,
                    photoUrl = photoUrl,
                    messageType = msgType
                )
                    .onSuccess { newMessage ->
                        val updatedMessages = _uiState.value.messages + newMessage
                        _uiState.value = _uiState.value.copy(
                            messages = updatedMessages.sortedBy { it.parsedDate },
                            messageText = "",
                            selectedPhotoUri = null,
                            replyingTo = null,
                            isSending = false,
                            errorMessage = null
                        )
                    }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            errorMessage = e.message ?: "Ошибка отправки сообщения"
                        )
                    }
            } catch (e: Exception) {
                android.util.Log.e("CuratorChatVM", "sendMessage exception", e)
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
            android.util.Log.e("CuratorChatVM", "Failed to create temp file", e)
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

data class ConversationUiState(
    val ticketId: String = "",
    val messages: List<ChatMessageDTO> = emptyList(),
    val messageText: String = "",
    val replyingTo: ChatMessageDTO? = null,
    val selectedPhotoUri: Uri? = null,
    val isUploadingPhoto: Boolean = false,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isRecordingVoice: Boolean = false,
    val voiceRecordingDurationMs: Long = 0L,
    val errorMessage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorChatConversationScreen(
    navController: NavController,
    ticketId: String,
    userPhone: String? = null,
    viewModel: CuratorChatConversationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var fullscreenPhotoUrl by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectPhoto(it) }
    }

    LaunchedEffect(ticketId) {
        viewModel.loadConversation(ticketId)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    val chatTitle = userPhone ?: "Ticket #${ticketId.take(8)}"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(chatTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.isLoading && uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Нет сообщений",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.messages) { message ->
                            CuratorChatMessageBubble(
                                message = message,
                                onPhotoClick = { url -> fullscreenPhotoUrl = url }
                            )
                        }
                    }
                }
            }

            CuratorChatInputField(
                text = uiState.messageText,
                onTextChange = { viewModel.updateMessageText(it) },
                onSendClick = { viewModel.sendMessage(context) },
                onAttachPhotoClick = { photoPickerLauncher.launch("image/*") },
                selectedPhotoUri = uiState.selectedPhotoUri,
                onClearPhoto = { viewModel.clearPhoto() },
                isSending = uiState.isSending,
                isUploadingPhoto = uiState.isUploadingPhoto,
                isRecordingVoice = uiState.isRecordingVoice,
                voiceRecordingDurationMs = uiState.voiceRecordingDurationMs,
                onStartVoiceRecording = { viewModel.startVoiceRecording() },
                onStopVoiceRecording = { viewModel.stopVoiceRecording() },
                onCancelVoiceRecording = { viewModel.cancelVoiceRecording() }
            )
        }
    }

    fullscreenPhotoUrl?.let { url ->
        FullscreenPhotoViewer(
            photoUrl = url,
            onDismiss = { fullscreenPhotoUrl = null }
        )
    }
}

@Composable
private fun CuratorChatMessageBubble(
    message: ChatMessageDTO,
    onPhotoClick: (String) -> Unit = {}
) {
    val isCuratorMessage = message.senderRole == "CURATOR" || message.senderRole == "SUPPORT"

    val (replyPreview, actualMessage) = parseMessageWithReply(message.text)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCuratorMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isCuratorMessage) 16.dp else 4.dp,
                bottomEnd = if (isCuratorMessage) 4.dp else 16.dp
            ),
            color = if (isCuratorMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Reply preview
                if (replyPreview != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isCuratorMessage)
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = replyPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCuratorMessage)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Голосовое сообщение
                if (message.voiceUrl != null) {
                    VoiceMessagePlayer(
                        voiceUrl = message.voiceUrl,
                        durationSeconds = message.voiceDuration,
                        isFromCurrentUser = isCuratorMessage
                    )
                }

                // Фотография (тап открывает fullscreen)
                if (message.photoUrl != null) {
                    AsyncImage(
                        model = message.photoUrl,
                        contentDescription = "Прикрепленное фото",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), MaterialTheme.shapes.small)
                            .clickable { onPhotoClick(message.photoUrl!!) },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Текст сообщения (скрываем placeholder для голосовых)
                if (actualMessage.isNotBlank() && message.voiceUrl == null) {
                    Text(
                        text = actualMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCuratorMessage) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Время
                Text(
                    text = formatMessageTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCuratorMessage) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Парсит сообщения с reply из старого формата
 */
private fun parseMessageWithReply(text: String): Pair<String?, String> {
    val replyPattern = Regex("^↩️\\s+(.+?)\\n\\n(.+)$", RegexOption.DOT_MATCHES_ALL)
    val match = replyPattern.find(text)

    return if (match != null) {
        val replyPart = "↩️ " + match.groupValues[1]
        val messagePart = match.groupValues[2]
        Pair(replyPart, messagePart)
    } else {
        Pair(null, text)
    }
}

@Composable
private fun CuratorChatInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachPhotoClick: () -> Unit,
    selectedPhotoUri: Uri?,
    onClearPhoto: () -> Unit,
    isSending: Boolean,
    isUploadingPhoto: Boolean,
    isRecordingVoice: Boolean,
    voiceRecordingDurationMs: Long,
    onStartVoiceRecording: () -> Unit,
    onStopVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Превью выбранной фотографии
            if (selectedPhotoUri != null && !isRecordingVoice) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    AsyncImage(
                        model = selectedPhotoUri,
                        contentDescription = "Выбранное фото",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )

                    IconButton(
                        onClick = onClearPhoto,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.large)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Удалить фото",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isUploadingPhoto) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }

            // Режим записи голоса
            if (isRecordingVoice) {
                VoiceRecordButton(
                    isRecording = true,
                    recordingDurationMs = voiceRecordingDurationMs,
                    onStartRecording = {},
                    onStopRecording = onStopVoiceRecording,
                    onCancelRecording = onCancelVoiceRecording,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка прикрепления фото
                    IconButton(
                        onClick = onAttachPhotoClick,
                        enabled = !isSending && !isUploadingPhoto,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Прикрепить фото",
                            tint = if (!isSending && !isUploadingPhoto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }

                    // Текстовое поле
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        placeholder = { Text("Введите сообщение...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        maxLines = 4,
                        enabled = !isSending && !isUploadingPhoto
                    )

                    // Кнопка микрофона или отправки
                    if (text.trim().isNotEmpty() || selectedPhotoUri != null) {
                        IconButton(
                            onClick = onSendClick,
                            enabled = !isSending && !isUploadingPhoto,
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Отправить",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        VoiceRecordButton(
                            isRecording = false,
                            recordingDurationMs = 0L,
                            onStartRecording = onStartVoiceRecording,
                            onStopRecording = {},
                            onCancelRecording = {},
                            enabled = !isSending && !isUploadingPhoto
                        )
                    }
                }
            }
        }
    }
}

/**
 * Форматирование времени сообщения
 */
private fun formatMessageTime(createdAt: String): String {
    return try {
        val dateTime = java.time.OffsetDateTime.parse(createdAt)
        val now = java.time.OffsetDateTime.now()

        val formatter = if (dateTime.toLocalDate() == now.toLocalDate()) {
            DateTimeFormatter.ofPattern("HH:mm")
        } else {
            DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))
        }

        dateTime.format(formatter)
    } catch (e: Exception) {
        "??:??"
    }
}
