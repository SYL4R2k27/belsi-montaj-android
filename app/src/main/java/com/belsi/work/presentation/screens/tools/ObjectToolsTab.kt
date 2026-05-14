package com.belsi.work.presentation.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.tool_transfer.InventoryItemDto
import com.belsi.work.data.remote.dto.tool_transfer.ToolTransferDto
import com.belsi.work.data.repositories.ToolTransferRepository
import com.belsi.work.presentation.components.role.*
import com.belsi.work.presentation.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) build19 Этап3+: вкладка «Инструмент» в детали объекта.
 *
 * 2 секции:
 *   - На объекте сейчас (inventory.location=site, site_object_id=this)
 *   - История передач (последние N transfers с этим объектом-получателем)
 */
data class ObjectToolsState(
    val onSite: List<InventoryItemDto> = emptyList(),
    val transfers: List<ToolTransferDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ObjectToolsTabViewModel @Inject constructor(
    private val repo: ToolTransferRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ObjectToolsState())
    val state: StateFlow<ObjectToolsState> = _state.asStateFlow()

    fun load(siteObjectId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            // Sequential — простой и достаточный для UI-таба
            val inv = repo.getInventory(location = "site", siteObjectId = siteObjectId)
                .getOrElse { emptyList() }
            val tr = repo.listByObject(siteObjectId).getOrElse { emptyList() }
            _state.update {
                it.copy(
                    onSite = inv,
                    transfers = tr,
                    isLoading = false,
                )
            }
        }
    }
}

@Composable
fun ObjectToolsTab(
    siteObjectId: String,
    navController: NavController?,
    viewModel: ObjectToolsTabViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(siteObjectId) {
        viewModel.load(siteObjectId)
    }

    if (state.isLoading && state.onSite.isEmpty() && state.transfers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.onSite.isEmpty() && state.transfers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RoleEmptyState(
                emoji = "🔧",
                title = "Нет инструмента",
                subtitle = "На объекте нет инструмента и не было передач",
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.onSite.isNotEmpty()) {
            item {
                RoleSectionHeader(
                    title = "Сейчас на объекте",
                    subtitle = "${state.onSite.size} ${pluralPos(state.onSite.size)}",
                )
            }
            items(state.onSite, key = { "site_${it.toolId}" }) { item ->
                OnSiteRow(item)
            }
        }

        if (state.transfers.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                RoleSectionHeader(
                    title = "История передач",
                    subtitle = "${state.transfers.size} ${pluralTransfer(state.transfers.size)}",
                )
            }
            items(state.transfers, key = { "tr_${it.id}" }) { t ->
                TransferHistoryRow(t, onClick = {
                    navController?.navigate(AppRoute.ToolTransferDetail.createRoute(t.id))
                })
            }
        }
    }
}

@Composable
private fun OnSiteRow(item: InventoryItemDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val tail = listOfNotNull(
                    item.serialNumber?.let { "S/N $it" },
                    item.inventoryNumber?.let { "№ $it" },
                    if (item.quantity > 1) "${item.quantity} шт" else null,
                ).joinToString(" · ")
                if (tail.isNotEmpty()) {
                    Text(
                        tail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.holderUserName != null) {
                    Spacer(Modifier.height(4.dp))
                    RoleStatusPill(
                        text = "У: ${item.holderUserName}",
                        severity = Severity.PRIMARY,
                    )
                }
            }
            if (item.kind == "consumable") {
                RoleStatusPill(text = "Расходник", severity = Severity.WARNING)
            }
        }
    }
}

@Composable
private fun TransferHistoryRow(t: ToolTransferDto, onClick: () -> Unit) {
    val (label, severity) = statusLabelSeverity(t.status)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${t.toolName ?: "Инструмент"} × ${t.quantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val from = listOfNotNull(
                    t.createdByName?.let { "От: $it" },
                    t.driverName?.let { "Везёт: $it" },
                ).joinToString(" · ")
                if (from.isNotEmpty()) {
                    Text(
                        from,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                RoleStatusPill(text = label, severity = severity)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun pluralPos(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "позиция"
        mod10 in 2..4 && mod100 !in 12..14 -> "позиции"
        else -> "позиций"
    }
}

private fun pluralTransfer(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "передача"
        mod10 in 2..4 && mod100 !in 12..14 -> "передачи"
        else -> "передач"
    }
}
