package com.belsi.work.presentation.screens.curator.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.remote.dto.curator.CuratorPhotoDto
import com.belsi.work.presentation.components.AiBubble
import com.belsi.work.presentation.components.ZoomablePhotoViewer
import com.belsi.work.presentation.components.montage.PhotoFeedCard
import com.belsi.work.presentation.theme.belsiColors
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Фото-лента куратора (мок 4.5) — приоритет №1.
 * Реальные данные: [CuratorPhotosViewModel] (photos_feed). Свежие сверху.
 *
 * Взаимодействие:
 *  - тап по фото → bottom-sheet деталей + Принять/Вернуть (данные в руках, без PhotoApi.getPhoto);
 *  - долгое нажатие → режим мультивыбора (кружок-галочка), тап переключает выбор,
 *    внизу панель «Одобрить (N) / Отклонить (N)» (batch) + отмена.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorPhotoFeedScreen(
    navController: NavController,
    embedded: Boolean = false,
    viewModel: CuratorPhotosViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    // Фото-лента = общая галерея: «Свежие сверху» = все статусы, новые сверху.
    // Один раз при входе сбрасываем фильтр статуса на ALL (no-op, если уже ALL).
    LaunchedEffect(Unit) { viewModel.filterByStatus(CuratorPhotoStatusFilter.ALL) }

    // 2.1.0 — супер-фото (urgent + ещё не отвеченные) всегда наверху «Требует внимания»;
    // после ответа куратора статус сменится → фото авто-уходит из приоритета.
    val photos = ui.photos.sortedWith(
        compareByDescending<CuratorPhotoDto> { it.category == "urgent" && (it.status == "pending" || it.status == null) }
            .thenByDescending { it.timestamp ?: "" }
    )
    // Объект ▾ — клиентский фильтр поверх уже загруженной (по статусу) ленты.
    val displayPhotos = photos.filter { ui.selectedObjectId == null || it.siteObjectId == ui.selectedObjectId }
    // Опции для дропдауна «Объект» — уникальные объекты из текущей ленты.
    val objectOptions = remember(ui.photos) {
        ui.photos.mapNotNull { p -> p.siteObjectId?.let { id -> id to (p.siteObjectName ?: "Объект") } }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
    var detail by remember { mutableStateOf<CuratorPhotoDto?>(null) }

    val body: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            PhotoFilterBar(
                selectedStatus = ui.selectedStatus,
                onStatus = { viewModel.filterByStatus(it) },
                objectOptions = objectOptions,
                selectedObjectId = ui.selectedObjectId,
                onObject = { viewModel.filterByObject(it) },
            )
            Box(Modifier.weight(1f).fillMaxSize()) {
                FeedContent(
                    photos = displayPhotos,
                    isLoading = ui.isLoading,
                    isSelectionMode = ui.isSelectionMode,
                    selectedIds = ui.selectedPhotoIds,
                    onTap = { p -> if (ui.isSelectionMode) viewModel.togglePhotoSelection(p.id) else detail = p },
                    onLongPress = { p -> viewModel.enterSelectionMode(p.id) },
                    modifier = Modifier.fillMaxSize(),
                )
                if (ui.isSelectionMode) {
                    SelectionBar(
                        count = ui.selectedPhotoIds.size,
                        processing = ui.isProcessing,
                        onApprove = { viewModel.batchApprove() },
                        onReject = { viewModel.batchReject("На переделку") },
                        onCancel = { viewModel.exitSelectionMode() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    if (embedded) {
        body(Modifier.fillMaxSize())
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (ui.isSelectionMode) "Выбрано: ${ui.selectedPhotoIds.size}" else "Фото", style = MaterialTheme.typography.titleMedium)
                            if (!ui.isSelectionMode) Text("${displayPhotos.size} фото · свежие сверху · долгий тап = выбрать", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (ui.isSelectionMode) viewModel.exitSelectionMode() else navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding -> body(Modifier.fillMaxSize().padding(padding)) }
    }

    detail?.let { photo ->
        PhotoDetailSheet(
            photo = photo,
            onDismiss = { detail = null },
            onApprove = { viewModel.approvePhoto(photo.id); detail = null },
            onReject = { reason -> viewModel.rejectPhoto(photo.id, reason); detail = null },
            onKudos = { c -> viewModel.giveKudos(photo.id, c); detail = null },
        )
    }
}

/**
 * Панель сортировки/фильтра ленты (мок 4.5): «Свежие сверху · Объект ▾ · Отклонено · Одобрено».
 * «Свежие сверху» = статус ALL (новые сверху). «Отклонено/Одобрено» = серверный статус-фильтр.
 * «Объект ▾» = клиентский фильтр по объекту (опции строятся из загруженной ленты).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoFilterBar(
    selectedStatus: CuratorPhotoStatusFilter,
    onStatus: (CuratorPhotoStatusFilter) -> Unit,
    objectOptions: List<Pair<String, String>>,
    selectedObjectId: String?,
    onObject: (String?) -> Unit,
) {
    var objMenu by remember { mutableStateOf(false) }
    val selName = objectOptions.firstOrNull { it.first == selectedObjectId }?.second
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedStatus == CuratorPhotoStatusFilter.ALL,
            onClick = { onStatus(CuratorPhotoStatusFilter.ALL) },
            label = { Text("Свежие сверху") },
        )
        Box {
            FilterChip(
                selected = selectedObjectId != null,
                onClick = { objMenu = true },
                label = { Text(selName ?: "Объект", maxLines = 1) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = objMenu, onDismissRequest = { objMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Все объекты") },
                    onClick = { onObject(null); objMenu = false },
                )
                objectOptions.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onObject(id); objMenu = false },
                    )
                }
            }
        }
        FilterChip(
            selected = selectedStatus == CuratorPhotoStatusFilter.REJECTED,
            onClick = { onStatus(CuratorPhotoStatusFilter.REJECTED) },
            label = { Text("Отклонено") },
        )
        FilterChip(
            selected = selectedStatus == CuratorPhotoStatusFilter.APPROVED,
            onClick = { onStatus(CuratorPhotoStatusFilter.APPROVED) },
            label = { Text("Одобрено") },
        )
    }
}

@Composable
private fun FeedContent(
    photos: List<CuratorPhotoDto>,
    isLoading: Boolean,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onTap: (CuratorPhotoDto) -> Unit,
    onLongPress: (CuratorPhotoDto) -> Unit,
    modifier: Modifier,
) {
    when {
        isLoading && photos.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        photos.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("Фото пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> {
            val hero = photos.first()
            val rest = photos.drop(1)
            LazyColumn(
                modifier = modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = if (isSelectionMode) 88.dp else 12.dp),
            ) {
                // isNew = «🆕 только что» теперь по РЕАЛЬНОЙ свежести загрузки (≤30 мин),
                // а не «первая карточка» — иначе вчерашнее фото-приоритет показывало «только что».
                item { FeedCard(hero, isNew = isRecent(hero.timestamp), hero = true, isSelectionMode, hero.id in selectedIds, onTap, onLongPress) }
                items(rest.chunked(2)) { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { p ->
                            Box(Modifier.weight(1f)) { FeedCard(p, isNew = isRecent(p.timestamp), hero = false, isSelectionMode, p.id in selectedIds, onTap, onLongPress) }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedCard(
    photo: CuratorPhotoDto,
    isNew: Boolean,
    hero: Boolean,
    selectionActive: Boolean,
    selected: Boolean,
    onTap: (CuratorPhotoDto) -> Unit,
    onLongPress: (CuratorPhotoDto) -> Unit,
) {
    val author = photo.userName?.takeIf { it.isNotBlank() } ?: photo.userPhone ?: "—"
    val context = buildString {
        photo.comment?.takeIf { it.isNotBlank() }?.let { append(it) }
        if (photo.aiScore != null) { if (isNotEmpty()) append(" · "); append("✨${photo.aiScore}") }
    }.ifBlank { photo.status ?: "" }

    PhotoFeedCard(
        author = author,
        timeLabel = whenOf(photo.timestamp, compact = true),
        context = context,
        isNew = isNew,
        hero = hero,
        selectionActive = selectionActive,
        selected = selected,
        onOpen = { onTap(photo) },
        onLongClick = { onLongPress(photo) },
        image = {
            if (photo.photoUrl.isNotBlank()) {
                AsyncImage(model = photo.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        },
    )
}

@Composable
private fun SelectionBar(
    count: Int,
    processing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp, shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Отмена") }
            Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onReject, enabled = count > 0 && !processing) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(18.dp))
                Spacer(Modifier.height(0.dp))
                Text("Отклонить")
            }
            Button(
                onClick = onApprove,
                enabled = count > 0 && !processing,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.belsiColors.success),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.height(0.dp))
                Text("Одобрить")
            }
        }
    }
}

/** Детальный просмотр фото куратором + approve/reject (мок 4.2, photo-уровень). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoDetailSheet(
    photo: CuratorPhotoDto,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
    onKudos: (String?) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }

    if (fullscreen && photo.photoUrl.isNotBlank()) {
        ZoomablePhotoViewer(photoUrl = photo.photoUrl, onDismiss = { fullscreen = false })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${photo.userName?.takeIf { it.isNotBlank() } ?: photo.userPhone ?: "—"} · ${whenOf(photo.timestamp, compact = false)}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = photo.photoUrl.isNotBlank()) { fullscreen = true },
                contentAlignment = Alignment.Center,
            ) {
                if (photo.photoUrl.isNotBlank()) {
                    AsyncImage(model = photo.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("⤢ во весь экран", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text("Нет изображения", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (photo.aiScore != null || !photo.aiComment.isNullOrBlank()) {
                AiBubble(
                    text = buildString {
                        if (photo.aiScore != null) append("Оценка ${photo.aiScore}/100. ")
                        photo.aiComment?.let { append(it) }
                    }.ifBlank { "AI-анализ недоступен" },
                    label = "AI анализ · XeroCode",
                )
            }
            photo.comment?.takeIf { it.isNotBlank() }?.let {
                Text("Комментарий: $it", style = MaterialTheme.typography.bodyMedium)
            }
            // 2.1.0 — 👏 благодарность монтажнику (kudos, не меняет статус фото)
            Button(
                onClick = { onKudos(reason.takeIf { it.isNotBlank() }) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.belsiColors.warning),
            ) { Text("👏 Молодец! Поблагодарить монтажника") }
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Текст: причина возврата или к 👏") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                singleLine = false,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onReject(reason.ifBlank { "На переделку" }) }, modifier = Modifier.weight(1f)) {
                    Text("Вернуть")
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.belsiColors.success),
                ) { Text("Принять") }
            }
        }
    }
}

private fun timeOf(ts: String?): String {
    if (ts.isNullOrBlank()) return ""
    for (f in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss")) {
        try {
            val d = SimpleDateFormat(f, Locale.US).apply { isLenient = true }.parse(ts) ?: continue
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
        } catch (_: Exception) {
        }
    }
    return ts.take(16).replace("T", " ")
}

/**
 * FIX(2026-05-23): дата + время загрузки фото. Раньше показывали только HH:mm — при
 * сортировке (приоритет pending/urgent наверху + по времени загрузки) вчерашнее фото-
 * приоритет «13:43» оказывалось выше сегодняшнего «18:02» без даты → выглядело как
 * рассинхрон. compact=true — для чипа на карточке («Вчера 18:02» / «13:43» / «20.05 16:14»),
 * compact=false — для шапки детального просмотра («Вчера, 18:02»).
 */
private fun whenOf(ts: String?, compact: Boolean): String {
    if (ts.isNullOrBlank()) return ""
    return try {
        val dt = OffsetDateTime.parse(ts)
        val today = OffsetDateTime.now().toLocalDate()
        val d = dt.toLocalDate()
        val time = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
        when (d) {
            today -> if (compact) time else "Сегодня, $time"
            today.minusDays(1) -> if (compact) "Вчера $time" else "Вчера, $time"
            else -> {
                val datePat = if (compact) "dd.MM" else "d MMM"
                val ds = dt.format(DateTimeFormatter.ofPattern(datePat, Locale("ru")))
                if (compact) "$ds $time" else "$ds, $time"
            }
        }
    } catch (_: Exception) {
        timeOf(ts)
    }
}

/** FIX(2026-05-23): «🆕 только что» только для реально свежих загрузок (≤30 мин). */
private fun isRecent(ts: String?): Boolean {
    if (ts.isNullOrBlank()) return false
    return try {
        Duration.between(OffsetDateTime.parse(ts), OffsetDateTime.now()).toMinutes() in 0..30
    } catch (_: Exception) {
        false
    }
}
