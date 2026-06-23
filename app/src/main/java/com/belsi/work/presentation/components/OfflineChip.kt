package com.belsi.work.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.local.database.entities.PendingActionEntity
import com.belsi.work.data.offline.OfflineQueueRepository
import com.belsi.work.presentation.theme.Amber500
import com.belsi.work.presentation.theme.Emerald500
import com.belsi.work.presentation.theme.Rose500
import com.belsi.work.presentation.theme.Slate400
import com.belsi.work.presentation.utils.PinnedFontScale
import dagger.hilt.android.lifecycle.HiltViewModel
import com.belsi.work.utils.NetworkMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * BELSI 2.1.0 — OfflineChip (Flow 6, master-spec §11).
 *
 * Единый индикатор связи в TopAppBar на всех ролях. 4 состояния:
 *   • ОНЛАЙН          emerald — всё ок, очередь пуста (компактная точка)
 *   • «N в очереди»   amber   — есть pending/failed действия → тап открывает sheet
 *   • ОФЛАЙН          rose    — нет сети → тап открывает sheet
 *   • «Синхронизация» slate   — Worker сейчас отправляет (вращающаяся иконка)
 *
 * Заменяет старый PendingBadge (который был невидим при пустой очереди).
 * Источник истины — NetworkMonitor.isOnline + OfflineQueueRepository.allActions
 * (успешно отправленные удаляются из таблицы, поэтому всё, что осталось = «не дошло»).
 */
enum class OfflineKind { ONLINE, QUEUED, OFFLINE, SYNCING }

data class OfflineChipState(
    val kind: OfflineKind = OfflineKind.ONLINE,
    val queueCount: Int = 0,
    val failedCount: Int = 0,
)

@HiltViewModel
class OfflineChipViewModel @Inject constructor(
    networkMonitor: NetworkMonitor,
    queue: OfflineQueueRepository,
) : ViewModel() {

    val state: StateFlow<OfflineChipState> =
        combine(networkMonitor.isOnline, queue.allActions) { online, actions ->
            val total = actions.size
            val sending = actions.any { it.status == "sending" }
            val failed = actions.count { it.status == "failed" }
            val kind = when {
                sending -> OfflineKind.SYNCING
                !online -> OfflineKind.OFFLINE
                total > 0 -> OfflineKind.QUEUED
                else -> OfflineKind.ONLINE
            }
            OfflineChipState(kind = kind, queueCount = total, failedCount = failed)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OfflineChipState(),
        )
}

@Composable
fun OfflineChip(
    modifier: Modifier = Modifier,
    navController: NavController? = null,
    viewModel: OfflineChipViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

    val tappable = state.kind == OfflineKind.QUEUED || state.kind == OfflineKind.OFFLINE

    PinnedFontScale {
        when (state.kind) {
            // Компактная точка: всё хорошо, не отвлекаем.
            OfflineKind.ONLINE -> Box(
                modifier = modifier
                    .padding(end = 6.dp)
                    .size(10.dp)
                    .background(Emerald500, CircleShape),
            )

            OfflineKind.QUEUED -> StatusPill(
                modifier = modifier,
                color = Amber500,
                text = "${state.queueCount} в очереди",
                onClick = { showSheet = true },
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }

            OfflineKind.OFFLINE -> StatusPill(
                modifier = modifier,
                color = Rose500,
                text = if (state.queueCount > 0) "Офлайн · ${state.queueCount}" else "Офлайн",
                onClick = { showSheet = true },
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }

            OfflineKind.SYNCING -> {
                val transition = rememberInfiniteTransition(label = "sync")
                val angle by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "sync-rotate",
                )
                StatusPill(
                    modifier = modifier,
                    color = Slate400,
                    text = "Синхронизация",
                    onClick = null,
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp).rotate(angle),
                    )
                }
            }
        }
    }

    if (showSheet && tappable) {
        OfflineQueueSheet(
            navController = navController,
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun StatusPill(
    color: Color,
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .padding(end = 4.dp)
            .background(color, RoundedCornerShape(50))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Полный человекочитаемый ярлык для всех 18 типов offline-действий. */
fun describePendingAction(actionType: String): Pair<String, String> = when (actionType) {
    "StartShift" -> "▶️" to "Начало смены"
    "FinishShift" -> "⏹" to "Завершение смены"
    "StartPause" -> "⏸" to "Начало паузы"
    "EndPause" -> "▶️" to "Конец паузы"
    "StartIdle" -> "⚠️" to "Начало простоя"
    "EndIdle" -> "▶️" to "Конец простоя"
    "StartBreak" -> "☕" to "Начало перерыва"
    "EndBreak" -> "▶️" to "Конец перерыва"
    "UploadPhoto" -> "📷" to "Загрузка фото"
    "UpdateTaskStatus" -> "✅" to "Изменение задачи"
    "ApprovePhoto" -> "👍" to "Одобрение фото"
    "RejectPhoto" -> "👎" to "Отклонение фото"
    "CreateTask" -> "📝" to "Создание задачи"
    "BatchReceive" -> "📦" to "Приёмка партии"
    "BatchInstall" -> "🔧" to "Монтаж партии"
    "ToolIssue" -> "🛠" to "Выдача инструмента"
    "ToolReturn" -> "↩️" to "Возврат инструмента"
    else -> "📤" to actionType
}

/** Статус элемента очереди в терминах спека: в очереди / отправляется / ошибка. */
fun pendingStatusLabel(status: String): String = when (status) {
    "failed" -> "ошибка"
    "sending" -> "отправляется…"
    else -> "в очереди"
}

fun pendingStatusColor(status: String): Color = when (status) {
    "failed" -> Rose500
    "sending" -> Amber500
    else -> Slate400
}

/** Заглушка-форматтер для отображения относительного времени постановки в очередь. */
fun formatQueuedTime(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    return when {
        diff < 60_000 -> "только что"
        diff < 3_600_000 -> "${diff / 60_000} мин назад"
        diff < 86_400_000 -> "${diff / 3_600_000} ч назад"
        else -> "${diff / 86_400_000} дн назад"
    }
}

/** Для удобства внешнего кода: список действий «требующих внимания» (failed/pending). */
fun List<PendingActionEntity>.needsAttention(): Boolean = any { it.status != "sending" }
