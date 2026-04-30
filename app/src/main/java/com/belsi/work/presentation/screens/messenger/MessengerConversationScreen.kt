package com.belsi.work.presentation.screens.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.belsi.work.data.models.MessengerMessageDTO
import com.belsi.work.presentation.screens.chat.components.FullscreenPhotoViewer
import com.belsi.work.presentation.screens.chat.components.VoiceMessagePlayer
import com.belsi.work.presentation.screens.chat.components.VoiceRecordButton
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerConversationScreen(
    onBack: () -> Unit,
    onNavigateToGroupInfo: (String) -> Unit = {},
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId = uiState.currentUserId
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var fullscreenPhotoUrl by remember { mutableStateOf<String?>(null) }

    // Context menu state
    var menuMessage by remember { mutableStateOf<MessengerMessageDTO?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.selectPhoto(uri)
    }

    // File picker for documents
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendFile(context, uri)
        }
    }

    // Auto-scroll to bottom (index 0 with reverseLayout) on new messages
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Build display list: reversed messages with date headers
    val chatItems = remember(uiState.messages) {
        buildChatItems(uiState.messages)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.thread?.displayName ?: "Чат",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val participantCount = uiState.thread?.participants?.size ?: 0
                        if (uiState.thread?.type == "group" && participantCount > 0) {
                            Text(
                                "$participantCount участников",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        } else if (uiState.thread?.type == "direct") {
                            val otherParticipant = uiState.thread?.participants
                                ?.firstOrNull { it.userId != currentUserId }
                            val statusText = when {
                                otherParticipant?.isOnline == true -> "в сети"
                                otherParticipant?.lastSeen != null -> formatLastSeen(otherParticipant.lastSeen)
                                else -> null
                            }
                            if (statusText != null) {
                                Text(
                                    statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (otherParticipant?.isOnline == true)
                                        Color(0xFF81C784)
                                    else
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Messages
            if (uiState.isLoading && uiState.messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (uiState.messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Начните общение!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    chatItems.forEachIndexed { index, item ->
                        when (item) {
                            is ChatItem.Message -> {
                                item(key = item.message.id) {
                                    SwipeToReplyWrapper(
                                        onSwipeToReply = { viewModel.setReplyTo(item.message) }
                                    ) {
                                        MessageBubble(
                                            message = item.message,
                                            isFromCurrentUser = item.message.senderId == currentUserId,
                                            isGroupChat = uiState.thread?.type == "group",
                                            onPhotoClick = { url -> fullscreenPhotoUrl = url },
                                            onLongClick = { menuMessage = item.message }
                                        )
                                    }
                                }
                            }
                            is ChatItem.DateHeader -> {
                                item(key = "date_${item.dateLabel}") {
                                    DateSeparator(item.dateLabel)
                                }
                            }
                        }
                    }

                    // Load older button — at the top (end of reversed list)
                    if (uiState.hasMoreMessages) {
                        item(key = "load_older") {
                            TextButton(
                                onClick = { viewModel.loadOlderMessages() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoadingOlder
                            ) {
                                if (uiState.isLoadingOlder) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Загрузить старые сообщения")
                                }
                            }
                        }
                    }
                }
            }

            // Selected photo preview
            if (uiState.selectedPhotoUri != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uiState.selectedPhotoUri,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp).clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearPhoto() }) {
                            Icon(Icons.Default.Close, "Убрать фото")
                        }
                    }
                }
            }

            // Reply preview bar
            AnimatedVisibility(
                visible = uiState.replyingTo != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                uiState.replyingTo?.let { replyMsg ->
                    ReplyBar(
                        senderName = replyMsg.senderName,
                        preview = replyMsg.preview,
                        onClose = { viewModel.clearReply() }
                    )
                }
            }

            // Typing indicator
            if (uiState.typingUserName != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        "${uiState.typingUserName} печатает...",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Input bar
            MessengerInputBar(
                messageText = uiState.messageText,
                onMessageTextChange = { viewModel.updateMessageText(it) },
                onSendClick = { viewModel.sendMessage(context) },
                onPhotoClick = { photoPickerLauncher.launch("image/*") },
                onFileClick = { filePickerLauncher.launch("*/*") },
                isSending = uiState.isSending,
                isRecording = uiState.isRecordingVoice,
                recordingDurationMs = uiState.voiceRecordingDurationMs,
                onStartRecording = { viewModel.startVoiceRecording() },
                onStopRecording = { viewModel.stopVoiceRecording() },
                onCancelRecording = { viewModel.cancelVoiceRecording() },
                hasPhotoSelected = uiState.selectedPhotoUri != null
            )
        }
    }

    // Fullscreen photo viewer
    if (fullscreenPhotoUrl != null) {
        FullscreenPhotoViewer(
            photoUrl = fullscreenPhotoUrl!!,
            onDismiss = { fullscreenPhotoUrl = null }
        )
    }

    // Context menu (long press)
    if (menuMessage != null) {
        val msg = menuMessage!!
        val isOwn = msg.senderId == currentUserId
        MessageContextMenu(
            message = msg,
            isOwnMessage = isOwn,
            onDismiss = { menuMessage = null },
            onReply = {
                viewModel.setReplyTo(msg)
                menuMessage = null
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("message", msg.text ?: ""))
                menuMessage = null
            },
            onDelete = {
                viewModel.deleteMessage(msg.id)
                menuMessage = null
            }
        )
    }
}

// ---- Swipe to reply wrapper ----

@Composable
private fun SwipeToReplyWrapper(
    onSwipeToReply: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 100f // px
    val triggered = remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(
        targetValue = if (offsetX != 0f) offsetX else 0f,
        label = "swipe_offset"
    )

    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > swipeThreshold && !triggered.value) {
                            triggered.value = true
                            onSwipeToReply()
                        }
                        offsetX = 0f
                        triggered.value = false
                    },
                    onDragCancel = {
                        offsetX = 0f
                        triggered.value = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        // Only allow right swipe, cap at 120px
                        offsetX = (offsetX + dragAmount).coerceIn(0f, 120f)
                    }
                )
            }
    ) {
        // Reply icon appears behind the message during swipe
        if (animatedOffset > 20f) {
            Icon(
                Icons.Default.Reply,
                contentDescription = "Ответить",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .graphicsLayer {
                        alpha = (animatedOffset / swipeThreshold).coerceIn(0f, 1f)
                    },
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier.offset { IntOffset(animatedOffset.roundToInt(), 0) }
        ) {
            content()
        }
    }
}

// ---- Reply bar above input ----

@Composable
private fun ReplyBar(
    senderName: String,
    preview: String,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vertical accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Отменить ответ",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---- Context menu on long press ----

@Composable
private fun MessageContextMenu(
    message: MessengerMessageDTO,
    isOwnMessage: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = null,
        text = {
            Column {
                // Reply
                TextButton(
                    onClick = onReply,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Reply, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Ответить", modifier = Modifier.weight(1f))
                }
                // Copy (only if text exists)
                if (!message.text.isNullOrBlank()) {
                    TextButton(
                        onClick = onCopy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Копировать", modifier = Modifier.weight(1f))
                    }
                }
                // Delete (own messages only)
                if (isOwnMessage) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Удалить", modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    )
}

// ---- Message bubble ----

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessengerMessageDTO,
    isFromCurrentUser: Boolean,
    isGroupChat: Boolean,
    onPhotoClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    // System messages
    if (message.messageType == "system") {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    message.text ?: "",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
    ) {
        // Sender name for group chats
        if (isGroupChat && !isFromCurrentUser) {
            Text(
                message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    start = if (isFromCurrentUser) 0.dp else 12.dp,
                    bottom = 2.dp
                )
            )
        }

        // Forwarded label
        if (message.forwardedFrom != null) {
            Text(
                "Переслано от ${message.forwardedFrom}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(
                    start = if (isFromCurrentUser) 0.dp else 12.dp,
                    bottom = 2.dp
                )
            )
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromCurrentUser) 16.dp else 4.dp,
                bottomEnd = if (isFromCurrentUser) 4.dp else 16.dp
            ),
            color = if (isFromCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Column(Modifier.padding(4.dp)) {
                // Reply quote
                if (message.replyTo != null) {
                    ReplyQuote(
                        senderName = message.replyTo.senderName,
                        preview = message.replyTo.preview,
                        isFromCurrentUser = isFromCurrentUser
                    )
                }

                // Photo
                if (message.photoUrl != null) {
                    AsyncImage(
                        model = message.photoUrl,
                        contentDescription = "Фото",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 250.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onPhotoClick(message.photoUrl) },
                        contentScale = ContentScale.Crop
                    )
                }

                // Voice
                if (message.messageType == "voice" && message.voiceUrl != null) {
                    VoiceMessagePlayer(
                        voiceUrl = message.voiceUrl,
                        durationSeconds = message.voiceDurationSeconds,
                        isFromCurrentUser = isFromCurrentUser,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // File
                if (message.messageType == "file" && message.fileUrl != null) {
                    val fileContext = LocalContext.current
                    FileAttachment(
                        fileName = message.fileName ?: "Файл",
                        fileSize = message.fileSize,
                        isFromCurrentUser = isFromCurrentUser,
                        onClick = {
                            downloadAndOpenFile(
                                context = fileContext,
                                url = message.fileUrl,
                                fileName = message.fileName ?: "file"
                            )
                        }
                    )
                }

                // Text
                if (!message.text.isNullOrBlank() && message.messageType != "voice") {
                    Text(
                        message.text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Timestamp + read status
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        formatMessageTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Checkmarks for own messages
                    if (isFromCurrentUser) {
                        val tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        if (message.isRead) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Прочитано",
                                modifier = Modifier.size(14.dp),
                                tint = tint
                            )
                        } else {
                            Icon(
                                Icons.Default.Done,
                                contentDescription = "Отправлено",
                                modifier = Modifier.size(14.dp),
                                tint = tint
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Reply quote inside bubble ----

@Composable
private fun ReplyQuote(
    senderName: String,
    preview: String,
    isFromCurrentUser: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isFromCurrentUser)
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ) {
        Row(modifier = Modifier.padding(6.dp)) {
            // Vertical accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(
                        if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    senderName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isFromCurrentUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFromCurrentUser)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---- File attachment inside bubble ----

@Composable
private fun FileAttachment(
    fileName: String,
    fileSize: Long?,
    isFromCurrentUser: Boolean,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = if (isFromCurrentUser)
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            else
                MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (fileSize != null && fileSize > 0) {
                Text(
                    formatFileSize(fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFromCurrentUser)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---- Input bar ----

@Composable
private fun MessengerInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onFileClick: () -> Unit,
    isSending: Boolean,
    isRecording: Boolean,
    recordingDurationMs: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    hasPhotoSelected: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        if (isRecording) {
            VoiceRecordButton(
                isRecording = true,
                recordingDurationMs = recordingDurationMs,
                onStartRecording = {},
                onStopRecording = onStopRecording,
                onCancelRecording = onCancelRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach button with dropdown
                var showAttachMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showAttachMenu = true }, enabled = !isSending) {
                        Icon(
                            Icons.Default.AttachFile,
                            "Прикрепить",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Фото") },
                            onClick = {
                                showAttachMenu = false
                                onPhotoClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Image, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Файл") },
                            onClick = {
                                showAttachMenu = false
                                onFileClick()
                            },
                            leadingIcon = { Icon(Icons.Default.InsertDriveFile, null) }
                        )
                    }
                }

                // Text field
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    enabled = !isSending
                )

                Spacer(Modifier.width(4.dp))

                // Send or mic button
                if (messageText.isNotBlank() || hasPhotoSelected) {
                    IconButton(
                        onClick = onSendClick,
                        enabled = !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, "Отправить", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    VoiceRecordButton(
                        isRecording = false,
                        recordingDurationMs = 0L,
                        onStartRecording = onStartRecording,
                        onStopRecording = {},
                        onCancelRecording = {},
                        enabled = !isSending
                    )
                }
            }
        }
    }
}

// ---- File download & open ----

private fun downloadAndOpenFile(context: Context, url: String, fileName: String) {
    try {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val uri = android.net.Uri.parse(url)
        val request = android.app.DownloadManager.Request(uri).apply {
            setTitle(fileName)
            setDescription("Скачивание файла...")
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        dm.enqueue(request)
        android.widget.Toast.makeText(context, "Скачивание: $fileName", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        // Fallback: open URL in browser
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            context.startActivity(intent)
        } catch (e2: Exception) {
            android.widget.Toast.makeText(context, "Не удалось открыть файл", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

// ---- Utility functions ----

private fun formatMessageTime(isoString: String): String {
    return try {
        val dt = OffsetDateTime.parse(isoString)
        dt.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        ""
    }
}

private fun formatLastSeen(isoString: String): String {
    return try {
        val dt = OffsetDateTime.parse(isoString)
        val now = OffsetDateTime.now()
        val minutes = java.time.Duration.between(dt, now).toMinutes()
        when {
            minutes < 1 -> "только что"
            minutes < 60 -> "был(а) $minutes мин назад"
            minutes < 1440 -> "был(а) ${minutes / 60} ч назад"
            else -> "был(а) ${minutes / 1440} дн назад"
        }
    } catch (e: Exception) {
        ""
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        else -> String.format("%.1f МБ", bytes / (1024.0 * 1024.0))
    }
}

// ---- Chat items model for reverseLayout + date grouping ----

private sealed class ChatItem {
    data class Message(val message: MessengerMessageDTO) : ChatItem()
    data class DateHeader(val dateLabel: String) : ChatItem()
}

/**
 * Build a flat list: newest message first (for reverseLayout).
 * Date headers are inserted AFTER a group of messages from the same day
 * (which visually appear ABOVE them due to reverseLayout).
 */
private fun buildChatItems(messages: List<MessengerMessageDTO>): List<ChatItem> {
    if (messages.isEmpty()) return emptyList()

    // Дедупликация: WS + polling могут дать одно сообщение дважды
    val reversed = messages.distinctBy { it.id }.reversed() // newest first
    return buildList {
        reversed.forEachIndexed { index, message ->
            add(ChatItem.Message(message))
            val olderMessage = reversed.getOrNull(index + 1)
            val currentDate = parseDate(message.createdAt)
            val olderDate = olderMessage?.let { parseDate(it.createdAt) }
            if (olderDate == null || currentDate != olderDate) {
                add(ChatItem.DateHeader(formatDateHeader(message.createdAt)))
            }
        }
    }
}

private fun parseDate(isoString: String): LocalDate? {
    return try {
        OffsetDateTime.parse(isoString).toLocalDate()
    } catch (e: Exception) {
        null
    }
}

private fun formatDateHeader(isoString: String): String {
    return try {
        val dt = OffsetDateTime.parse(isoString)
        val today = LocalDate.now()
        val messageDate = dt.toLocalDate()
        when {
            messageDate == today -> "Сегодня"
            messageDate == today.minusDays(1) -> "Вчера"
            else -> dt.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
        }
    } catch (e: Exception) {
        ""
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
