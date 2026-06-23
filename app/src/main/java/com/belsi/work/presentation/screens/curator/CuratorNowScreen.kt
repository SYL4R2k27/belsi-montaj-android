package com.belsi.work.presentation.screens.curator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.remote.dto.curator.CuratorPhotoDto
import com.belsi.work.data.remote.dto.curator.IdleWorkerDto
import com.belsi.work.presentation.components.AiBubble
import com.belsi.work.presentation.components.KpiCell
import com.belsi.work.presentation.components.SectionHeader
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Куратор · «Сейчас» (мок 4.1) — главный таб. Реальные данные [CuratorViewModel]:
 * dashboard (на смене / бригадиры / на approve / готовность), aiSummary (AI-сводка дня),
 * photos (очередь на approve с инлайн ✓/✕) + tap → PhotoDetail.
 *
 * Super-фото блок мока требует реального источника алёртов — даём ссылку на реальную
 * ленту AI-алёртов (CuratorAlertsFeed); фейковую super-карточку не показываем.
 */
@Composable
fun CuratorNowTab(
    viewModel: CuratorViewModel,
    navController: NavController,
    onSelectTab: (Int) -> Unit = {},
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val aiSummary by viewModel.aiSummary.collectAsState()
    val photos by viewModel.photos.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadAiSummary() }

    // 2.1.0 (Flow 6): realtime — поллим дашборд каждые 10с, ТОЛЬКО пока экран виден и в foreground
    // (repeatOnLifecycle STARTED). Уходит в фон / на другой таб → цикл отменяется.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(10_000L)
                viewModel.refreshNow()
            }
        }
    }
    // Тактильный отклик при росте числа простоев (новый алёрт «прилетел»).
    val haptic = LocalHapticFeedback.current
    var prevIdle by remember { mutableStateOf(dashboard.idleNow) }
    LaunchedEffect(dashboard.idleNow) {
        if (dashboard.idleNow > prevIdle) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        prevIdle = dashboard.idleNow
    }

    val pending = photos.filter { it.status == null || it.status.equals("pending", true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        // KPI
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // KPI кликабельны → быстрый переход к соответствующему разделу.
                KpiCell("${dashboard.activeInstallersToday}", "на смене", Modifier.weight(1f),
                    onClick = { onSelectTab(2) })   // Люди
                KpiCell("${dashboard.activeForemenToday}/${dashboard.totalForemen}", "бригадиров", Modifier.weight(1f),
                    onClick = { onSelectTab(1) })   // Бригады
                KpiCell("${dashboard.pendingPhotos}", "на approve", Modifier.weight(1f),
                    valueColor = if (dashboard.pendingPhotos > 0) MaterialTheme.belsiColors.warning else MaterialTheme.colorScheme.onSurface,
                    onClick = { onSelectTab(5) })   // Фото
                KpiCell("${dashboard.averageCompletionPercentage.toInt()}%", "готовность", Modifier.weight(1f),
                    onClick = { onSelectTab(3) })   // Объекты
            }
        }

        // 🔴 Простой СЕЙЧАС — slide-in алёрт со spring (Flow 6: live-обновление сверху)
        item {
            AnimatedVisibility(
                visible = dashboard.idleNow > 0,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = fadeOut(),
            ) {
                IdleAlertCard(count = dashboard.idleNow, workers = dashboard.idleWorkers)
            }
        }

        // AI сводка
        aiSummary?.let { s ->
            item {
                AiBubble(
                    text = (listOf(s.headline, s.summary).filter { it.isNotBlank() }.joinToString(" · ")),
                    label = "AI · сводка дня",
                )
            }
        }

        // (Ссылку на «Ленту алёртов» убрали — её backend-эндпоинт отдаёт HTTP 500.
        //  Вернём, когда бэкенд алёртов починят. Super-фото придёт с реальным источником позже.)

        // На approve
        item {
            SectionHeader(
                title = "На approve · ${pending.size}",
                trailing = {
                    Text(
                        "вся лента →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { navController.navigate(AppRoute.CuratorPhotos.route) },
                    )
                },
            )
        }

        if (pending.isEmpty()) {
            item { Text("Очередь пуста 👍", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            // показываем только первые 5 — остальное в полной ленте
            items(pending.take(5), key = { it.id }) { photo ->
                ApproveRow(
                    photo = photo,
                    onOpen = { navController.navigate(AppRoute.CuratorPhotos.route) },
                    onApprove = { viewModel.approvePhoto(photo.id) },
                    onReject = { viewModel.rejectPhoto(photo.id, "На переделку") },
                )
            }
            item {
                Button(
                    onClick = { navController.navigate(AppRoute.CuratorPhotos.route) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Открыть все фото (${pending.size})") }
            }
        }
    }
}

@Composable
private fun ApproveRow(
    photo: CuratorPhotoDto,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (photo.photoUrl.isNotBlank()) {
                    AsyncImage(model = photo.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(photo.userName?.takeIf { it.isNotBlank() } ?: photo.userPhone ?: "—", style = MaterialTheme.typography.titleSmall)
                val sub = buildString {
                    append(timeOf(photo.timestamp))
                    if (photo.aiScore != null) append(" · ✨${photo.aiScore}")
                    photo.comment?.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
                }
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            OutlinedIconButton(onClick = onReject) {
                Icon(Icons.Filled.Close, contentDescription = "Отклонить", tint = MaterialTheme.colorScheme.error)
            }
            FilledIconButton(
                onClick = onApprove,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.belsiColors.success),
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Принять")
            }
        }
    }
}

@Composable
private fun IdleAlertCard(count: Int, workers: List<IdleWorkerDto>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "🔴 НА ПРОСТОЕ СЕЙЧАС · $count",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            workers.take(8).forEach { w ->
                val since = timeOf(w.since)
                val reason = w.reason?.takeIf { it.isNotBlank() }
                Text(
                    buildString {
                        append(w.name ?: "—")
                        if (reason != null) append(" · $reason")
                        if (since.isNotBlank()) append(" · с $since")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (count > 8) {
                Text(
                    "…ещё ${count - 8}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
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
