package com.belsi.work.presentation.screens.curator.objects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.belsi.work.data.remote.dto.brand.TimelineEventDto
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
    onArchive: () -> Unit,
    // FIX(2026-05-11) BELSI 2.0.0 build7: история объекта показывается в InfoTab
    timeline: List<TimelineEventDto> = emptyList(),
    isLoadingTimeline: Boolean = false,
    timelineError: String? = null,
    // FIX(2026-05-12) build18 P2: партии объекта для нового таба.
    batches: List<com.belsi.work.data.models.Batch> = emptyList(),
    isLoadingBatches: Boolean = false,
    onBatchClick: ((batchId: String) -> Unit)? = null,
    onPhotoClick: ((photoId: String) -> Unit)? = null,
    // FIX(2026-05-12) build19 Этап3+: navController для перехода в детали передачи
    navController: androidx.navigation.NavController? = null,
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
                // FIX(2026-05-12) build18 P2: добавлен таб «Партии» (index 4)
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 8.dp,
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        text = { Text("Инфо") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        text = { Text("Работники (${detail.activeWorkers.size})") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                        text = { Text("Фото (${detail.recentPhotos.size})") })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                        text = { Text("Отчёты (${detail.reports.size})") })
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 },
                        text = { Text("Партии (${batches.size})") })
                    // FIX(2026-05-12) build19 Этап3+: таб «Инструмент» — текущий состав + история
                    Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 },
                        text = { Text("Инструмент") })
                }

                // Content
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    when (selectedTab) {
                        0 -> InfoTab(
                            detail = detail,
                            timeline = timeline,
                            isLoadingTimeline = isLoadingTimeline,
                            timelineError = timelineError,
                        )
                        1 -> WorkersTab(detail.activeWorkers)
                        2 -> PhotosTab(detail.recentPhotos, onPhotoClick = onPhotoClick)
                        3 -> ReportsTab(detail.reports)
                        4 -> BatchesTab(batches, isLoading = isLoadingBatches, onBatchClick = onBatchClick)
                        5 -> com.belsi.work.presentation.screens.tools.ObjectToolsTab(
                            siteObjectId = detail.id.toString(),
                            navController = navController,
                        )
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
private fun InfoTab(
    detail: SiteObjectDetailDto,
    // FIX(2026-05-11) BELSI 2.0.0 build7: история объекта прямо в Инфо
    timeline: List<TimelineEventDto> = emptyList(),
    isLoadingTimeline: Boolean = false,
    timelineError: String? = null,
) {
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

        // FIX(2026-05-11) BELSI 2.0.0 build7: блок «История объекта»
        // прямо в Инфо. Источник правды — /objects/{id}/timeline
        // (UNION ALL по 8 таблицам: shifts, photos, audit, deliveries,
        //  route_points, batches, tasks, support_tickets).
        item {
            InfoSection("История объекта") {
                ObjectTimelineBlock(
                    events = timeline,
                    isLoading = isLoadingTimeline,
                    error = timelineError,
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ============================================================
// TIMELINE BLOCK — встроен в Инфо-таб, source of truth = /objects/{id}/timeline
// ============================================================

@Composable
private fun ObjectTimelineBlock(
    events: List<TimelineEventDto>,
    isLoading: Boolean,
    error: String?,
) {
    when {
        isLoading -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Загружаем историю…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        error != null -> {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        events.isEmpty() -> {
            Text(
                "Событий по объекту пока нет — будут появляться по мере начала смен, загрузки фото, отгрузок и партий.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                events.take(20).forEach { ev ->
                    TimelineRow(ev)
                }
                if (events.size > 20) {
                    Text(
                        "…ещё ${events.size - 20} событий",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(ev: TimelineEventDto) {
    val (domainColor, domainLabel) = when (ev.domain) {
        "installation" -> MaterialTheme.colorScheme.primary to "Монтаж"
        "logistics"    -> MaterialTheme.belsiColors.warning to "Логистика"
        "production"   -> MaterialTheme.belsiColors.success to "Производство"
        "curator"      -> MaterialTheme.belsiColors.info to "Куратор"
        else           -> MaterialTheme.colorScheme.onSurfaceVariant to ev.domain
    }
    val icon: ImageVector = when (ev.type) {
        "shift", "shift_start", "shift_end" -> Icons.Default.Schedule
        "photo"                              -> Icons.Default.CameraAlt
        "delivery", "route", "route_point"   -> Icons.Default.LocalShipping
        "batch", "production"                -> Icons.Default.Inventory2
        "task"                               -> Icons.Default.Assignment
        "ticket", "support"                  -> Icons.Default.Support
        "audit"                              -> Icons.Default.History
        else                                 -> Icons.Default.Circle
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(MaterialTheme.shapes.small)
                .background(domainColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = domainColor)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    domainLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = domainColor,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    formatDate(ev.occurredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                ev.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!ev.actorName.isNullOrBlank()) {
                Text(
                    ev.actorName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!ev.detail.isNullOrBlank()) {
                Text(
                    ev.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
private fun PhotosTab(photos: List<ObjectPhotoDto>, onPhotoClick: ((String) -> Unit)? = null) {
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
                ShiftPhotoCard(photo, onClick = if (onPhotoClick != null) { { onPhotoClick(photo.id) } } else null)
            }
        }
    }
}

@Composable
private fun ShiftPhotoCard(photo: ObjectPhotoDto, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
    ) {
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

// ======================= Batches Tab (build18 P2) =======================

@Composable
private fun BatchesTab(
    batches: List<com.belsi.work.data.models.Batch>,
    isLoading: Boolean,
    onBatchClick: ((batchId: String) -> Unit)? = null,
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (batches.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Inventory,
                    null,
                    Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Нет партий",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Партии для этого объекта появятся, когда производство их создаст",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "${batches.size} партий на объекте",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(batches, key = { it.id.toString() }) { batch ->
            BatchTabCard(batch, onClick = if (onBatchClick != null) { { onBatchClick(batch.id.toString()) } } else null)
        }
    }
}

@Composable
private fun BatchTabCard(
    batch: com.belsi.work.data.models.Batch,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    batch.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                val (label, color) = when (batch.status.toString().lowercase()) {
                    "draft" -> "Черновик" to MaterialTheme.colorScheme.outline
                    "in_production" -> "В производстве" to MaterialTheme.belsiColors.info
                    "ready_to_ship" -> "Готова" to MaterialTheme.belsiColors.success
                    "in_route" -> "В пути" to MaterialTheme.belsiColors.warning
                    "delivered" -> "Доставлена" to MaterialTheme.belsiColors.success
                    "installed" -> "Смонтирована" to MaterialTheme.colorScheme.tertiary
                    "cancelled" -> "Отменена" to MaterialTheme.colorScheme.error
                    else -> batch.status.toString() to MaterialTheme.colorScheme.outline
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = color.copy(alpha = 0.15f),
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${batch.itemCount} шт · приоритет: ${batch.priority}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            batch.deadline?.let {
                Text(
                    "Срок: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
