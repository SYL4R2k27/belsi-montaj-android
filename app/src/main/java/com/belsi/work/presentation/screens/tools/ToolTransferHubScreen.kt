package com.belsi.work.presentation.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.tool_transfer.InventoryItemDto
import com.belsi.work.data.remote.dto.tool_transfer.ToolTransferDto
import com.belsi.work.presentation.components.role.*
import com.belsi.work.presentation.navigation.AppRoute

/**
 * FIX(2026-05-12) build19 Этап3+: универсальный hub.
 *
 *   - 3 таба: Входящие (роль-зависимый счёт) / Отправленные / Склад
 *   - FAB «Сформировать передачу» для supplier/curator/coordinator/chief
 *   - Pull-to-refresh каждого таба
 *   - Deep-link на таб через ?tab=incoming|outgoing|inventory
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTransferHubScreen(
    navController: NavController,
    initialTab: String,
    viewModel: ToolTransferHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val visibleTabs = remember(state.canSeeOutgoing) {
        listOfNotNull(
            "incoming" to "Входящие",
            if (state.canSeeOutgoing) "outgoing" to "Отправленные" else null,
            "inventory" to "Склад",
        )
    }
    var selectedTab by remember(initialTab) {
        mutableStateOf(visibleTabs.indexOfFirst { it.first == initialTab }.coerceAtLeast(0))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инструменты") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canCreate) {
                ExtendedFloatingActionButton(
                    onClick = {
                        navController.navigate(AppRoute.ToolTransferCreate.createRoute())
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Передача") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                visibleTabs.forEachIndexed { idx, (_, label) ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label)
                                if (idx == 0 && state.incoming.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("${state.incoming.size}")
                                    }
                                }
                            }
                        },
                    )
                }
            }

            when (visibleTabs.getOrNull(selectedTab)?.first) {
                "incoming" -> IncomingTab(state, navController)
                "outgoing" -> OutgoingTab(state, navController)
                "inventory" -> InventoryTab(state, viewModel)
                else -> {}
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// TAB: Incoming
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun IncomingTab(state: HubState, navController: NavController) {
    if (state.isLoadingIncoming && state.incoming.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.incoming.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RoleEmptyState(
                emoji = "🔧",
                title = "Нет входящих передач",
                subtitle = "Когда комплектатор отгрузит инструмент — он появится здесь",
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.incoming, key = { it.id }) { t ->
            TransferRow(t, onClick = {
                navController.navigate(AppRoute.ToolTransferDetail.createRoute(t.id))
            })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// TAB: Outgoing
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun OutgoingTab(state: HubState, navController: NavController) {
    if (state.isLoadingOutgoing && state.outgoing.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.outgoing.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RoleEmptyState(
                emoji = "📤",
                title = "Нет отправленных передач",
                subtitle = "Нажми «Передача» внизу чтобы сформировать первую",
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.outgoing, key = { it.id }) { t ->
            TransferRow(t, onClick = {
                navController.navigate(AppRoute.ToolTransferDetail.createRoute(t.id))
            })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// TAB: Inventory
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun InventoryTab(state: HubState, viewModel: ToolTransferHubViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "all" to "Всё",
                "warehouse" to "Склад",
                "site" to "На объекте",
                "in_transit" to "В пути",
            ).forEach { (key, label) ->
                FilterChip(
                    selected = state.inventoryFilter == key,
                    onClick = { viewModel.loadInventory(key) },
                    label = { Text(label) },
                )
            }
        }
        // Stats
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val totals = state.inventory.groupBy { it.location }
            val warehouseCount = totals["warehouse"]?.size ?: 0
            val siteCount = totals["site"]?.size ?: 0
            val transitCount = totals["in_transit"]?.size ?: 0
            RoleMiniStat("Склад", "$warehouseCount", Severity.NEUTRAL, Modifier.weight(1f))
            RoleMiniStat("На объектах", "$siteCount", Severity.SUCCESS, Modifier.weight(1f))
            RoleMiniStat("В пути", "$transitCount", Severity.WARNING, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))

        if (state.isLoadingInventory && state.inventory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        if (state.inventory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RoleEmptyState(emoji = "📦", title = "Пусто", subtitle = "По текущему фильтру ничего")
            }
            return
        }

        val byCategory = state.inventory.groupBy { it.category ?: "Без категории" }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            byCategory.forEach { (cat, items) ->
                item { RoleSectionHeader(title = cat, subtitle = "${items.size} шт") }
                items(items, key = { it.toolId }) { item ->
                    InventoryRow(item)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Rows
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun TransferRow(t: ToolTransferDto, onClick: () -> Unit) {
    val (statusLabel, severity) = statusLabelSeverity(t.status)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${t.toolName ?: "Инструмент"} × ${t.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "→ ${t.toSiteObjectName ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (t.driverName != null) {
                    Text(
                        "Везёт: ${t.driverName}",
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

@Composable
private fun InventoryRow(item: InventoryItemDto) {
    val (locLabel, locSeverity) = when (item.location) {
        "warehouse"  -> "Склад" to Severity.NEUTRAL
        "site"       -> (item.siteObjectName ?: "На объекте") to Severity.SUCCESS
        "in_transit" -> "В пути · ${item.holderUserName ?: "—"}" to Severity.WARNING
        else         -> item.location to Severity.NEUTRAL
    }
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
                if (item.kind == "consumable") {
                    Spacer(Modifier.height(4.dp))
                    RoleStatusPill(text = "Расходник", severity = Severity.WARNING)
                }
            }
            RoleStatusPill(text = locLabel, severity = locSeverity)
        }
    }
}

internal fun statusLabelSeverity(status: String): Pair<String, Severity> = when (status) {
    "pending"     -> "Готовится" to Severity.NEUTRAL
    "in_transit"  -> "Везут" to Severity.WARNING
    "delivered"   -> "Привезли — нужно принять" to Severity.INFO
    "accepted"    -> "Принят" to Severity.SUCCESS
    "in_use"      -> "В работе" to Severity.PRIMARY
    "returning"   -> "Возврат" to Severity.WARNING
    "returned"    -> "Возвращён" to Severity.NEUTRAL
    "cancelled"   -> "Отменён" to Severity.ERROR
    "lost"        -> "Утерян" to Severity.ERROR
    else          -> status to Severity.NEUTRAL
}
