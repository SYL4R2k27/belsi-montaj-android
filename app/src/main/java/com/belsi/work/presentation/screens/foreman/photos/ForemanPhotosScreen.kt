package com.belsi.work.presentation.screens.foreman.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.remote.dto.team.ForemanPhotoDto
import com.belsi.work.presentation.theme.belsiColors
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForemanPhotosScreen(
    navController: NavController,
    viewModel: ForemanPhotosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    var showAiComments by remember { mutableStateOf(prefsManager.isAiAnalysisVisible()) }
    val filteredPhotos = remember(uiState.photos, uiState.selectedFilter) {
        viewModel.getFilteredPhotos()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Фото команды") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showAiComments = !showAiComments
                        prefsManager.setAiAnalysisVisible(showAiComments)
                    }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            "AI-анализ",
                            tint = if (showAiComments) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            PhotoStatusFilterChips(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { viewModel.filterByStatus(it) },
                photosCounts = mapOf(
                    PhotoFilterStatus.ALL to uiState.photos.size,
                    PhotoFilterStatus.APPROVED to uiState.photos.count { it.status == "approved" },
                    PhotoFilterStatus.REJECTED to uiState.photos.count { it.status == "rejected" },
                    PhotoFilterStatus.PENDING to uiState.photos.count { it.status in listOf("uploaded", "pending") }
                )
            )

            HorizontalDivider()

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when {
                    uiState.isLoading && uiState.photos.isEmpty() -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    filteredPhotos.isEmpty() -> {
                        EmptyPhotosView(modifier = Modifier.align(Alignment.Center), filter = uiState.selectedFilter)
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredPhotos, key = { it.id }) { photo ->
                                TeamPhotoCard(
                                    photo = photo,
                                    onApprove = { viewModel.approvePhoto(photo.id) },
                                    onReject = { viewModel.showRejectDialog(photo.id) },
                                    isProcessing = uiState.isProcessing,
                                    showAiComment = showAiComments
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reject dialog
        if (uiState.showRejectDialog) {
            RejectPhotoDialog(
                reason = uiState.rejectReason,
                onReasonChange = { viewModel.updateRejectReason(it) },
                onConfirm = {
                    uiState.selectedPhotoId?.let { photoId ->
                        viewModel.rejectPhoto(photoId, uiState.rejectReason)
                    }
                },
                onDismiss = { viewModel.hideRejectDialog() },
                isProcessing = uiState.isProcessing
            )
        }
    }
}

@Composable
private fun PhotoStatusFilterChips(
    selectedFilter: PhotoFilterStatus,
    onFilterSelected: (PhotoFilterStatus) -> Unit,
    photosCounts: Map<PhotoFilterStatus, Int>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == PhotoFilterStatus.ALL,
            onClick = { onFilterSelected(PhotoFilterStatus.ALL) },
            label = { Text("Все (${photosCounts[PhotoFilterStatus.ALL] ?: 0})") }
        )
        FilterChip(
            selected = selectedFilter == PhotoFilterStatus.APPROVED,
            onClick = { onFilterSelected(PhotoFilterStatus.APPROVED) },
            label = { Text("Одобрено (${photosCounts[PhotoFilterStatus.APPROVED] ?: 0})") },
            leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) }
        )
        FilterChip(
            selected = selectedFilter == PhotoFilterStatus.PENDING,
            onClick = { onFilterSelected(PhotoFilterStatus.PENDING) },
            label = { Text("Проверка (${photosCounts[PhotoFilterStatus.PENDING] ?: 0})") },
            leadingIcon = { Icon(Icons.Default.HourglassEmpty, null, Modifier.size(16.dp)) }
        )
    }
}

@Composable
private fun EmptyPhotosView(modifier: Modifier = Modifier, filter: PhotoFilterStatus) {
    val message = when (filter) {
        PhotoFilterStatus.ALL -> "Нет фотографий"
        PhotoFilterStatus.APPROVED -> "Нет одобренных фото"
        PhotoFilterStatus.REJECTED -> "Нет отклоненных фото"
        PhotoFilterStatus.PENDING -> "Нет фото на проверке"
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TeamPhotoCard(
    photo: ForemanPhotoDto,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    isProcessing: Boolean,
    showAiComment: Boolean = true
) {
    val isProblem = showAiComment && photo.aiScore != null && photo.aiScore < 60
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isProblem) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    MaterialTheme.shapes.medium
                ) else Modifier
            ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = photo.installerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Час ${photo.hourLabel ?: "?"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatPhotoDate(photo.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // AI Score badge
                    if (showAiComment && photo.aiScore != null) {
                        val scoreColor = when {
                            photo.aiScore >= 80 -> MaterialTheme.belsiColors.success
                            photo.aiScore >= 50 -> MaterialTheme.belsiColors.warning
                            else -> MaterialTheme.colorScheme.error
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = scoreColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(12.dp), tint = scoreColor)
                                Spacer(Modifier.width(3.dp))
                                Text("${photo.aiScore}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = scoreColor)
                            }
                        }
                    }
                    ScreenPhotoStatusBadge(status = photo.status)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Photo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = photo.photoUrl,
                    contentDescription = "Фото час ${photo.hourLabel}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Комментарий монтажника
            if (!photo.comment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Comment,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Комментарий:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                photo.comment!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // AI-комментарий
            if (showAiComment && !photo.aiComment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "AI-анализ:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                photo.aiComment,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Actions for pending photos
            if (photo.status in listOf("uploaded", "pending")) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Отклонить")
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.belsiColors.success)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Одобрить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenPhotoStatusBadge(status: String) {
    val (backgroundColor, textColor, label) = when (status) {
        "approved" -> Triple(MaterialTheme.belsiColors.success.copy(alpha = 0.15f), MaterialTheme.belsiColors.success, "Одобрено")
        "rejected" -> Triple(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), MaterialTheme.colorScheme.error, "Отклонено")
        else -> Triple(MaterialTheme.belsiColors.warning.copy(alpha = 0.15f), MaterialTheme.belsiColors.warning, "На проверке")
    }

    Surface(shape = MaterialTheme.shapes.large, color = backgroundColor) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RejectPhotoDialog(
    reason: String,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isProcessing: Boolean
) {
    Dialog(onDismissRequest = { if (!isProcessing) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Отклонить фото", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Укажите причину отклонения:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Например: Размытое фото, неверный ракурс...") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isProcessing
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !isProcessing) { Text("Отмена") }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = reason.isNotBlank() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                        } else {
                            Text("Отклонить")
                        }
                    }
                }
            }
        }
    }
}

private fun formatPhotoDate(createdAt: String): String {
    return try {
        val dateTime = OffsetDateTime.parse(createdAt)
        val now = OffsetDateTime.now()

        val formatter = if (dateTime.toLocalDate() == now.toLocalDate()) {
            DateTimeFormatter.ofPattern("'Сегодня,' HH:mm")
        } else if (dateTime.toLocalDate() == now.toLocalDate().minusDays(1)) {
            DateTimeFormatter.ofPattern("'Вчера,' HH:mm")
        } else {
            DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))
        }

        dateTime.format(formatter)
    } catch (e: Exception) {
        createdAt.take(16).replace("T", " ")
    }
}
