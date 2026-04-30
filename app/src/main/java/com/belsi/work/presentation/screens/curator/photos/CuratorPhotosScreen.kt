package com.belsi.work.presentation.screens.curator.photos

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.remote.dto.curator.CuratorPhotoDto
import com.belsi.work.presentation.theme.belsiColors
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorPhotosScreen(
    navController: NavController,
    viewModel: CuratorPhotosViewModel = hiltViewModel(),
    embedded: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    var showAiComments by remember { mutableStateOf(prefsManager.isAiAnalysisVisible()) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    if (embedded) {
        CuratorPhotosContent(uiState, viewModel, showAiComments)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Модерация фото") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showAiComments = !showAiComments
                        prefsManager.setAiAnalysisVisible(showAiComments)
                    }) {
                        Icon(
                            if (showAiComments) Icons.Default.AutoAwesome else Icons.Default.AutoAwesome,
                            "AI-анализ",
                            tint = if (showAiComments) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
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
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading && uiState.photos.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                }
                uiState.photos.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.belsiColors.success)
                        Spacer(Modifier.height(16.dp))
                        Text("Нет фото на модерации", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Все фото обработаны!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.photos, key = { it.id }) { photo ->
                            PhotoReviewCard(
                                photo = photo,
                                onApprove = { viewModel.approvePhoto(photo.id) },
                                onReject = { viewModel.showRejectDialog(photo.id) },
                                isProcessing = uiState.isProcessing
                            )
                        }
                    }
                }
            }
        }

        if (uiState.showRejectDialog) {
            RejectPhotoDialog(
                reason = uiState.rejectReason,
                onReasonChange = { viewModel.updateRejectReason(it) },
                onConfirm = {
                    uiState.selectedPhotoId?.let { viewModel.rejectPhoto(it, uiState.rejectReason) }
                },
                onDismiss = { viewModel.hideRejectDialog() },
                isProcessing = uiState.isProcessing
            )
        }
    }
}

@Composable
private fun CuratorPhotosContent(
    uiState: CuratorPhotosUiState,
    viewModel: CuratorPhotosViewModel,
    showAiComments: Boolean = true
) {
    Box(Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.photos.isEmpty() -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            }
            uiState.photos.isEmpty() -> {
                Column(
                    Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.belsiColors.success)
                    Spacer(Modifier.height(16.dp))
                    Text("Нет фото на модерации", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Все фото обработаны!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.photos, key = { it.id }) { photo ->
                        PhotoReviewCard(
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

    if (uiState.showRejectDialog) {
        RejectPhotoDialog(
            reason = uiState.rejectReason,
            onReasonChange = { viewModel.updateRejectReason(it) },
            onConfirm = {
                uiState.selectedPhotoId?.let { viewModel.rejectPhoto(it, uiState.rejectReason) }
            },
            onDismiss = { viewModel.hideRejectDialog() },
            isProcessing = uiState.isProcessing
        )
    }
}

@Composable
private fun PhotoReviewCard(
    photo: CuratorPhotoDto,
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
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(photo.userName ?: "Неизвестный", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (photo.foremanName != null) {
                        Text("Бригада: ${photo.foremanName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(formatPhotoDate(photo.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

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
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(14.dp), tint = scoreColor)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${photo.aiScore}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth().height(250.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(model = photo.photoUrl, contentDescription = "Фото", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }

            // Комментарий монтажника
            if (!photo.comment.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
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
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Комментарий:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                photo.comment,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // AI-комментарий
            if (showAiComment && !photo.aiComment.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
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
                        Spacer(Modifier.width(8.dp))
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

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f), enabled = !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Отклонить")
                }
                Button(onClick = onApprove, modifier = Modifier.weight(1f), enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.belsiColors.success)) {
                    if (isProcessing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Одобрить")
                    }
                }
            }
        }
    }
}

@Composable
private fun RejectPhotoDialog(reason: String, onReasonChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, isProcessing: Boolean) {
    Dialog(onDismissRequest = { if (!isProcessing) onDismiss() }) {
        Card(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp)) {
                Text("Отклонить фото", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Укажите причину отклонения:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = reason, onValueChange = onReasonChange, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Размытое фото, неверный ракурс...") }, minLines = 3, maxLines = 5, enabled = !isProcessing)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !isProcessing) { Text("Отмена") }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f),
                        enabled = reason.isNotBlank() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        if (isProcessing) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                        else Text("Отклонить")
                    }
                }
            }
        }
    }
}

private fun formatPhotoDate(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        val dt = OffsetDateTime.parse(isoString)
        val now = OffsetDateTime.now()
        val formatter = when {
            dt.toLocalDate() == now.toLocalDate() -> DateTimeFormatter.ofPattern("'Сегодня,' HH:mm")
            dt.toLocalDate() == now.toLocalDate().minusDays(1) -> DateTimeFormatter.ofPattern("'Вчера,' HH:mm")
            else -> DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))
        }
        dt.format(formatter)
    } catch (e: Exception) { isoString.take(16) }
}
