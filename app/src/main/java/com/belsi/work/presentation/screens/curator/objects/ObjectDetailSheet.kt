package com.belsi.work.presentation.screens.curator.objects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.belsi.work.data.remote.dto.objects.*
import com.belsi.work.presentation.theme.belsiColors
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Полноэкранный диалог-лист с деталями объекта.
 * Содержит вкладки: Инфо, Работники, Фото, Отчёты.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectDetailSheet(
    detail: SiteObjectDetailDto,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onArchive: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showArchiveConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, "Закрыть", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Text(
                                "Объект",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            if (detail.status != "archived") {
                                IconButton(onClick = { showArchiveConfirm = true }) {
                                    Icon(Icons.Default.Archive, "Архивировать", tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            } else {
                                Spacer(Modifier.size(48.dp))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            detail.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        if (!detail.address.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                detail.address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Quick stats
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            QuickStat("Работников", "${detail.activeWorkersCount}", Icons.Default.People)
                            QuickStat("Смен сегодня", "${detail.shiftsToday}", Icons.Default.Schedule)
                            QuickStat("Всего фото", "${detail.totalPhotos}", Icons.Default.CameraAlt)
                        }
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        text = { Text("Инфо") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        text = { Text("Работники (${detail.activeWorkers.size})") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                        text = { Text("Фото (${detail.recentPhotos.size})") })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                        text = { Text("Отчёты (${detail.reports.size})") })
                }

                // Content
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    when (selectedTab) {
                        0 -> InfoTab(detail)
                        1 -> WorkersTab(detail.activeWorkers)
                        2 -> PhotosTab(detail.recentPhotos)
                        3 -> ReportsTab(detail.reports)
                    }
                }
            }
        }
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Архивировать объект?") },
            text = { Text("Объект \"${detail.name}\" будет перемещён в архив. Активные смены на нём продолжат работать.") },
            confirmButton = {
                Button(
                    onClick = { showArchiveConfirm = false; onArchive() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Архивировать") }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun QuickStat(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
        Spacer(Modifier.width(4.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
        }
    }
}

// ======================= Info Tab =======================

@Composable
private fun InfoTab(detail: SiteObjectDetailDto) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status
        item {
            InfoSection("Статус") {
                val (label, color) = when (detail.status) {
                    "active" -> "Активный" to MaterialTheme.belsiColors.success
                    "completed" -> "Завершён" to MaterialTheme.belsiColors.info
                    "archived" -> "В архиве" to MaterialTheme.colorScheme.outline
                    else -> detail.status to MaterialTheme.colorScheme.outline
                }
                Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.15f)) {
                    Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Description
        if (!detail.description.isNullOrBlank()) {
            item {
                InfoSection("Описание") {
                    Text(detail.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Coordinator
        if (!detail.coordinatorName.isNullOrBlank()) {
            item {
                InfoSection("Координатор") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SupervisorAccount, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(detail.coordinatorName, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Measurements
        if (detail.measurements.isNotEmpty()) {
            item {
                InfoSection("Замеры") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.measurements.forEach { (key, value) ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(key, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Comments
        if (!detail.comments.isNullOrBlank()) {
            item {
                InfoSection("Комментарии") {
                    Text(detail.comments, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Object photos
        if (detail.photoUrls.isNotEmpty()) {
            item {
                InfoSection("Фото объекта (${detail.photoUrls.size})") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(detail.photoUrls) { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = "Фото объекта",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // Segments today
        if (detail.segmentsToday.isNotEmpty()) {
            item {
                InfoSection("Сегменты сегодня") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.segmentsToday.forEach { seg ->
                            SegmentRow(seg)
                        }
                    }
                }
            }
        }

        // Created info
        item {
            InfoSection("Создание") {
                Column {
                    if (!detail.creatorName.isNullOrBlank()) {
                        Text("Создал: ${detail.creatorName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!detail.createdAt.isNullOrBlank()) {
                        Text("Дата: ${formatDate(detail.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SegmentRow(segment: SegmentDto) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Person, null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(segment.workerName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            "${formatTime(segment.startedAt)}${if (segment.endedAt != null) " — ${formatTime(segment.endedAt)}" else " — ..."}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ======================= Workers Tab =======================

@Composable
private fun WorkersTab(workers: List<WorkerOnSiteDto>) {
    if (workers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PersonOff, null, Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("Нет активных работников", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Сейчас никто не работает на этом объекте",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("${workers.size} работников на объекте",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
            }
            items(workers, key = { it.id }) { worker ->
                WorkerCard(worker)
            }
        }
    }
}

@Composable
private fun WorkerCard(worker: WorkerOnSiteDto) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val roleColor = when (worker.role) {
                "foreman" -> MaterialTheme.colorScheme.primary
                "coordinator" -> MaterialTheme.belsiColors.info
                else -> MaterialTheme.colorScheme.tertiary
            }
            Surface(shape = MaterialTheme.shapes.small, color = roleColor.copy(alpha = 0.1f)) {
                Icon(
                    when (worker.role) {
                        "foreman" -> Icons.Default.SupervisorAccount
                        "coordinator" -> Icons.Default.ManageAccounts
                        else -> Icons.Default.Person
                    },
                    null, Modifier.padding(10.dp).size(24.dp), tint = roleColor
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(worker.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    when (worker.role) {
                        "foreman" -> "Бригадир"
                        "coordinator" -> "Координатор"
                        else -> "Монтажник"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = roleColor
                )
            }
            if (!worker.shiftStart.isNullOrBlank()) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("С ${formatTime(worker.shiftStart)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Circle, null, Modifier.size(8.dp), tint = MaterialTheme.belsiColors.success)
                        Spacer(Modifier.width(4.dp))
                        Text("На смене", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.belsiColors.success)
                    }
                }
            }
        }
    }
}

// ======================= Photos Tab =======================

@Composable
private fun PhotosTab(photos: List<ObjectPhotoDto>) {
    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("Нет фото", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Фото со смен на этом объекте появятся здесь",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("${photos.size} фото",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            items(photos, key = { it.id }) { photo ->
                ShiftPhotoCard(photo)
            }
        }
    }
}

@Composable
private fun ShiftPhotoCard(photo: ObjectPhotoDto) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(photo.userName ?: "Неизвестный",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold)
                }
                Text(formatDate(photo.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            // Photo
            AsyncImage(
                model = photo.photoUrl,
                contentDescription = "Фото смены",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            // Status + comment
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (statusText, statusColor) = when (photo.status) {
                    "approved" -> "Одобрено" to MaterialTheme.belsiColors.success
                    "rejected" -> "Отклонено" to MaterialTheme.colorScheme.error
                    "pending" -> "На проверке" to MaterialTheme.belsiColors.warning
                    else -> photo.status to MaterialTheme.colorScheme.outline
                }
                Surface(shape = MaterialTheme.shapes.small, color = statusColor.copy(alpha = 0.15f)) {
                    Text(statusText, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = statusColor)
                }

                Text(photo.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (!photo.comment.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(photo.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ======================= Reports Tab =======================

@Composable
private fun ReportsTab(reports: List<ObjectReportDto>) {
    if (reports.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Description, null, Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("Нет отчётов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Отчёты координатора по объекту появятся здесь",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("${reports.size} отчётов",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            items(reports, key = { it.id }) { report ->
                ReportCard(report)
            }
        }
    }
}

@Composable
private fun ReportCard(report: ObjectReportDto) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Отчёт за ${report.reportDate ?: "—"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                val (statusText, statusColor) = when (report.status) {
                    "submitted" -> "Отправлен" to MaterialTheme.belsiColors.info
                    "reviewed" -> "Проверен" to MaterialTheme.belsiColors.success
                    else -> report.status to MaterialTheme.colorScheme.outline
                }
                Surface(shape = MaterialTheme.shapes.small, color = statusColor.copy(alpha = 0.15f)) {
                    Text(statusText, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }

            if (report.content.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(report.content, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
            }

            if (report.photoUrls.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(report.photoUrls) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Фото отчёта",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            if (!report.curatorFeedback.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Feedback, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Обратная связь куратора:",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(report.curatorFeedback,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ======================= Helpers =======================

private fun formatDate(isoString: String?): String {
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
    } catch (e: Exception) {
        isoString.take(16)
    }
}

private fun formatTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        val dt = OffsetDateTime.parse(isoString)
        dt.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        isoString.take(5)
    }
}
