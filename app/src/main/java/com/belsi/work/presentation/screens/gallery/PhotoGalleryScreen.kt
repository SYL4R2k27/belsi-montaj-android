package com.belsi.work.presentation.screens.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.repositories.ShiftPhotoData
import com.belsi.work.presentation.components.ZoomablePhotoViewer
import com.belsi.work.presentation.theme.belsiColors
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

enum class PhotoFilter(val label: String) {
    ALL("Все"), PENDING("На проверке"), APPROVED("Одобрено"), REJECTED("Отклонено")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGalleryScreen(
    navController: NavController,
    viewModel: PhotoGalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf(PhotoFilter.ALL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Все фотоотчёты") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAllPhotos() }) {
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
    ) { padding ->
        when {
            uiState.isLoading && uiState.shifts.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            uiState.errorMessage != null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadAllPhotos() }) {
                            Text("Повторить")
                        }
                    }
                }
            }

            uiState.shifts.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Фотографии отсутствуют",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // FIX(2026-04-30): фильтр по статусу — Все / На проверке / Одобрено / Отклонено
                    ScrollableTabRow(
                        selectedTabIndex = PhotoFilter.values().indexOf(filter),
                        edgePadding = 0.dp,
                    ) {
                        PhotoFilter.values().forEachIndexed { idx, f ->
                            val count = uiState.shifts
                                .flatMap { it.photos }
                                .count { p -> filterMatches(p.status, f) }
                            Tab(
                                selected = filter == f,
                                onClick = { filter = f },
                                text = { Text("${f.label} ($count)") },
                            )
                        }
                    }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    uiState.shifts.forEachIndexed { shiftIndex, shiftWithPhotos ->
                        // Заголовок смены
                        item(key = "header_${shiftWithPhotos.shiftId}") {
                            ShiftHeader(shiftWithPhotos)
                        }

                        // Группируем фото по часам — после применения фильтра
                        val filteredPhotos = shiftWithPhotos.photos
                            .filter { p -> filterMatches(p.status, filter) }
                        val photosByHour = filteredPhotos
                            .groupBy { it.hourLabel ?: "Неизвестно" }
                            .toSortedMap()

                        photosByHour.entries.forEachIndexed { hourIndex, (hour, photos) ->
                            item(key = "hour_${shiftWithPhotos.shiftId}_$hour") {
                                HourLabel(hour)
                            }

                            items(
                                items = photos,
                                key = { photo -> "photo_${photo.id}" }
                            ) { photo ->
                                PhotoCard(photo)
                            }
                        }

                        // Разделитель между сменами
                        if (shiftIndex < uiState.shifts.size - 1) {
                            item(key = "divider_${shiftWithPhotos.shiftId}") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
                }  // close Column (filter wrapper)
            }
        }
    }
}

private fun filterMatches(status: String?, filter: PhotoFilter): Boolean {
    val s = status?.lowercase() ?: ""
    return when (filter) {
        PhotoFilter.ALL -> true
        PhotoFilter.PENDING -> s == "pending" || s == "uploaded"
        PhotoFilter.APPROVED -> s == "approved"
        PhotoFilter.REJECTED -> s == "rejected"
    }
}

@Composable
fun ShiftHeader(shiftWithPhotos: ShiftWithPhotos) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatShiftDate(shiftWithPhotos.shiftDate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${shiftWithPhotos.photos.size} фото",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HourLabel(hour: String) {
    Text(
        text = hour,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun PhotoCard(photo: ShiftPhotoData) {
    var showFullScreen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { showFullScreen = true },
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Фото
            AsyncImage(
                model = photo.photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )

            // Метаданные
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhotoStatusBadge(photo.status)

                Text(
                    text = formatPhotoTime(photo.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Комментарий если есть
            photo.comment?.takeIf { it.isNotEmpty() }?.let { comment ->
                HorizontalDivider()
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }

    if (showFullScreen) {
        ZoomablePhotoViewer(
            photoUrl = photo.photoUrl,
            onDismiss = { showFullScreen = false }
        )
    }
}

@Composable
fun PhotoStatusBadge(status: String) {
    val (text, color) = when (status.lowercase()) {
        "approved" -> "Одобрено" to MaterialTheme.belsiColors.success
        "rejected" -> "Отклонено" to MaterialTheme.colorScheme.error
        "uploaded", "pending" -> "На проверке" to MaterialTheme.belsiColors.warning
        else -> status to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun FullScreenPhotoDialog(
    photoUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column {
                Box {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp),
                        contentScale = ContentScale.Fit
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatShiftDate(dateString: String): String {
    return try {
        val dateTime = OffsetDateTime.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val formatter = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
        formatter.format(Date.from(dateTime.toInstant()))
    } catch (e: Exception) {
        dateString
    }
}

private fun formatPhotoTime(dateString: String): String {
    return try {
        val dateTime = OffsetDateTime.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        formatter.format(Date.from(dateTime.toInstant()))
    } catch (e: Exception) {
        dateString
    }
}
