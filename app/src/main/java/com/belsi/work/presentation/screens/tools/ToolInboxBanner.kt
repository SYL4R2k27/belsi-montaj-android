package com.belsi.work.presentation.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.ToolTransferRepository
import com.belsi.work.presentation.components.role.Severity
import com.belsi.work.presentation.components.role.colors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) build19 Этап3+: универсальный inbox-баннер.
 *
 * Вставляется на главные экраны (foreman/installer/curator/coordinator).
 * Показывается только если есть входящие передачи. Тап → hub на табе Incoming.
 *
 * Также экспортирует helper composable `ToolHubEntryButton` — фирменная кнопка
 * входа в hub, для меню/боковой панели.
 */

@HiltViewModel
class ToolInboxCountViewModel @Inject constructor(
    private val repo: ToolTransferRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxCountState())
    val state: StateFlow<InboxCountState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            repo.listIncoming().onSuccess { list ->
                // Показываем баннер если есть delivered (ждут приёмки) или in_transit (везут)
                val pending = list.count { it.status in listOf("delivered", "in_transit") }
                _state.update { it.copy(pendingCount = pending, totalIncoming = list.size) }
            }
        }
    }
}

data class InboxCountState(
    val pendingCount: Int = 0,
    val totalIncoming: Int = 0,
)

/**
 * Баннер «Входящий инструмент: N позиций ждут приёмки».
 * Возвращает `true` если был отрендерен (для расчёта layout-spacing в parent).
 */
@Composable
fun ToolInboxBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolInboxCountViewModel = hiltViewModel(),
): Boolean {
    val state by viewModel.state.collectAsState()
    if (state.pendingCount <= 0) return false

    val (fg, bg) = Severity.WARNING.colors()
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bg.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Входящий инструмент",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                )
                Text(
                    when (state.pendingCount) {
                        1 -> "1 передача ждёт приёмки"
                        in 2..4 -> "${state.pendingCount} передачи ждут приёмки"
                        else -> "${state.pendingCount} передач ждут приёмки"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.85f),
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = fg)
        }
    }
    return true
}

/**
 * Кнопка-плитка «Инструменты» для menu/grid в ролевых экранах.
 * Показывает badge с числом ожидающих приёмки.
 */
@Composable
fun ToolHubEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolInboxCountViewModel = hiltViewModel(),
    label: String = "Инструменты",
) {
    val state by viewModel.state.collectAsState()
    Card(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.totalIncoming > 0) {
                    Text(
                        "${state.totalIncoming} активных",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.pendingCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text("${state.pendingCount}")
                }
            }
        }
    }
}
