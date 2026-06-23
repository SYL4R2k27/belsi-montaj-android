package com.belsi.work.presentation.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.tool_transfer.ToolTransferDto
import com.belsi.work.presentation.components.role.RoleEmptyState
import com.belsi.work.presentation.components.role.RoleStatusPill
import com.belsi.work.presentation.components.role.Severity
import com.belsi.work.presentation.navigation.AppRoute

/**
 * FIX(2026-05-12) build19 Этап3: Список входящих передач для приёмщика.
 * Backend: GET /tools/transfers/incoming (для своего объекта или объектов команды)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTransferIncomingScreen(
    navController: NavController,
    viewModel: ToolTransferIncomingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Входящий инструмент") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        }
    ) { padding ->
        if (state.isLoading && state.transfers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (state.transfers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                RoleEmptyState(
                    emoji = "🔧",
                    title = "Нет входящих передач",
                    subtitle = "Когда комплектатор отгрузит инструмент — он появится здесь",
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.transfers, key = { it.id }) { transfer ->
                TransferCard(
                    transfer = transfer,
                    onClick = {
                        navController.navigate(
                            AppRoute.ToolTransferDetail.createRoute(transfer.id)
                        )
                    },
                )
            }

            if (state.error != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                    ) {
                        Text(
                            state.error ?: "",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferCard(transfer: ToolTransferDto, onClick: () -> Unit) {
    val severity = when (transfer.status) {
        "in_transit" -> Severity.WARNING       // в пути
        "delivered" -> Severity.INFO            // ждёт приёмки
        "accepted", "in_use" -> Severity.SUCCESS
        "cancelled", "lost" -> Severity.ERROR
        else -> Severity.NEUTRAL
    }
    val statusLabel = when (transfer.status) {
        "pending"     -> "Готовится"
        "in_transit"  -> "Везут"
        "delivered"   -> "Привезли — нужно принять"
        "accepted"    -> "Принят"
        "in_use"      -> "В работе"
        "returning"   -> "Возврат"
        "returned"    -> "Возвращён"
        "cancelled"   -> "Отменён"
        "lost"        -> "Утерян"
        else -> transfer.status
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${transfer.toolName ?: "Инструмент"} × ${transfer.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "→ ${transfer.toSiteObjectName ?: "объект"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (transfer.driverName != null) {
                    Text(
                        "Везёт: ${transfer.driverName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                RoleStatusPill(text = statusLabel, severity = severity)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
