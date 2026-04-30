package com.belsi.work.presentation.screens.chat

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.models.ChatMessageDTO
import com.belsi.work.presentation.screens.chat.components.FullscreenPhotoViewer
import com.belsi.work.presentation.screens.chat.components.VoiceMessagePlayer
import com.belsi.work.presentation.screens.chat.components.VoiceRecordButton
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerChatScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: InstallerChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Fullscreen photo viewer state
    var fullscreenPhotoUrl by remember { mutableStateOf<String?>(null) }

    // Launcher для выбора фото из галереи
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectPhoto(it) }
    }

    // Автоскролл вниз при новых сообщениях
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Показ ошибок
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Чат с поддержкой", style = MaterialTheme.typography.titleLarge) },
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
        ) {
            // Список сообщений
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
                        CircularProgressIndicator()
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
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Начните диалог с поддержкой",
                                style = MaterialTheme.typography.bodyMedium,
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
                        if (uiState.hasMoreMessages) {
                            item {
                                LoadOlderMessagesButton(
                                    isLoading = uiState.isLoadingOlder,
                                    onClick = { viewModel.loadOlderMessages() }
                                )
                            }
                        }

                        items(uiState.messages) { message ->
                            ChatMessageBubble(
                                message = message,
                                onPhotoClick = { url -> fullscreenPhotoUrl = url }
                            )
                        }
                    }
                }
            }

            // Поле ввода
            ChatInputField(
                text = uiState.messageText,
                onTextChange = { viewModel.updateMessageText(it) },
                onSendClick = { viewModel.sendMessage(context) },
                onAttachClick = { photoPickerLauncher.launch("image/*") },
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

    // Fullscreen photo viewer
    fullscreenPhotoUrl?.let { url ->
        FullscreenPhotoViewer(
            photoUrl = url,
            onDismiss = { fullscreenPhotoUrl = null }
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessageDTO,
    onPhotoClick: (String) -> Unit = {}
) {
    val isUser = message.senderRole == "USER" || message.senderRole == "INSTALLER"

    val (replyPreview, actualMessage) = parseMessageWithReply(message.text)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Reply preview
                if (replyPreview != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isUser)
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = replyPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isUser)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
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
                        isFromCurrentUser = isUser
                    )
                }

                // Фотография (тап открывает fullscreen)
                if (message.photoUrl != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPhotoClick(message.photoUrl!!) }
                    ) {
                        AsyncImage(
                            model = message.photoUrl,
                            contentDescription = "Прикрепленное фото",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (actualMessage.isNotBlank() && message.voiceUrl == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Текст сообщения (скрываем placeholder для голосовых)
                if (actualMessage.isNotBlank() && message.voiceUrl == null) {
                    Text(
                        text = actualMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Время
                Text(
                    text = formatMessageTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
private fun ChatInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Превью выбранного фото
            if (selectedPhotoUri != null && !isRecordingVoice) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = "Выбранное фото",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    IconButton(
                        onClick = onClearPhoto,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(32.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = MaterialTheme.shapes.large
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Удалить фото",
                            tint = MaterialTheme.colorScheme.error
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
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Поле ввода и кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка прикрепления фото
                    IconButton(
                        onClick = onAttachClick,
                        enabled = !isSending && !isUploadingPhoto,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Прикрепить фото",
                            tint = if (!isSending && !isUploadingPhoto)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    }

                    // Текстовое поле
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        placeholder = { Text("Введите сообщение...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        enabled = !isSending && !isUploadingPhoto
                    )

                    // Кнопка микрофона или отправки
                    if (text.trim().isNotEmpty() || selectedPhotoUri != null) {
                        // Есть текст/фото — показываем кнопку отправки
                        IconButton(
                            onClick = onSendClick,
                            enabled = !isSending && !isUploadingPhoto,
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (isSending || isUploadingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
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
                        // Нет текста — показываем кнопку микрофона
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

@Composable
private fun LoadOlderMessagesButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.padding(vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Загрузить старые сообщения")
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
