package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.InventoryItem
import com.belsi.work.data.models.MaterialOrder
import com.belsi.work.presentation.components.role.RoleEmptyState
import com.belsi.work.presentation.components.role.RoleStatCard
import com.belsi.work.presentation.components.role.RoleStatusPill
import com.belsi.work.presentation.components.role.Severity
import com.belsi.work.presentation.components.role.colors

/**
 * FIX(2026-05-06): SupplierMainScreen — подключён к API.
 * Заявки + остатки через GET /production/materials/orders и /inventory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierMainScreen(
    navController: NavController,
    viewModel: SupplierViewModel = hiltViewModel(),
    // FIX(2026-05-12) BELSI 2.0.0 build14: externally-controlled tab from Scaffold navbar
    initialTab: Int = 0,
) {
    val state by viewModel.state.collectAsState()
    var tab by remember(initialTab) { mutableStateOf(initialTab) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Снабжение", fontWeight = FontWeight.Bold)
                    Text(
                        state.selectedFacility?.name ?: "Фабрика не выбрана",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } },
                actions = {
                    // FIX(2026-05-12) build19 Этап3+: переход в Tool Transfer Hub
                    IconButton(onClick = {
                        navController.navigate(
                            com.belsi.work.presentation.navigation.AppRoute.ToolTransferHub.createRoute("outgoing")
                        )
                    }) {
                        Icon(Icons.Default.Inventory2, contentDescription = "Передачи инструмента")
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
        floatingActionButton = {
            // FIX(2026-05-12) build19 Этап3+: главная фича комплектатора — формирование передачи
            ExtendedFloatingActionButton(
                onClick = {
                    navController.navigate(
                        com.belsi.work.presentation.navigation.AppRoute.ToolTransferCreate.createRoute()
                    )
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Передача") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Стат-карточки — единый стиль через RoleStatCard
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoleStatCard("Заявки", "${state.pendingOrders.size}", Severity.WARNING, modifier = Modifier.weight(1f))
                RoleStatCard("Низкий остаток", "${state.lowStockCount}", Severity.ERROR, modifier = Modifier.weight(1f))
            }

            // FIX(2026-05-10) BELSI 1.3.0: AI-прогноз исчерпания материалов.
            // Видна только если facility выбрана. При сбое AI — секция скрыта.
            state.selectedFacility?.let { facility ->
                com.belsi.work.presentation.components.StockForecastSection(
                    facilityId = facility.id,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }) {
                    Text("Заявки", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = tab == 1, onClick = { tab = 1 }) {
                    Text("Склад", modifier = Modifier.padding(12.dp))
                }
            }

            if (state.loading && state.pendingOrders.isEmpty() && state.inventory.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            when (tab) {
                0 -> OrdersTab(state.pendingOrders, viewModel)
                else -> InventoryTab(state.inventory, viewModel)
            }
        }
    }
}

@Composable
private fun OrdersTab(orders: List<MaterialOrder>, viewModel: SupplierViewModel) {
    if (orders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RoleEmptyState(emoji = "✅", title = "Нет открытых заявок")
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(orders) { o ->
            OrderCard(o, viewModel)
        }
    }
}

@Composable
private fun OrderCard(order: MaterialOrder, viewModel: SupplierViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.materialName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusBadge(order.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${order.quantityRequested} ${order.materialUnit} · код ${order.materialCode}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (order.requestedByName != null) {
                Text(
                    "Запросил: ${order.requestedByName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (order.note != null) {
                Spacer(Modifier.height(4.dp))
                Text(order.note, fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.status == "pending" || order.status == "approved") {
                    OutlinedButton(
                        onClick = { viewModel.approveOrder(order.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Заказать")
                    }
                }
                if (order.status == "ordered") {
                    Button(
                        onClick = { viewModel.deliverOrder(order.id, order.quantityRequested) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Доставлено")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (text, severity) = when (status) {
        "pending"   -> "Новая"      to Severity.WARNING
        "approved"  -> "Утверждена" to Severity.PRIMARY
        "ordered"   -> "Заказана"   to Severity.AI
        "delivered" -> "Доставлена" to Severity.SUCCESS
        "cancelled" -> "Отменена"   to Severity.NEUTRAL
        else        -> status        to Severity.NEUTRAL
    }
    RoleStatusPill(text = text, severity = severity)
}

@Composable
private fun InventoryTab(items: List<InventoryItem>, viewModel: SupplierViewModel) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Каталог пуст", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val groups = items.groupBy { it.category ?: "Прочее" }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { (cat, list) ->
            item {
                Text(
                    cat.replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(list) { item -> InventoryRow(item, viewModel) }
        }
    }
}

@Composable
private fun InventoryRow(item: InventoryItem, viewModel: SupplierViewModel) {
    val (errFg, errBg) = Severity.ERROR.colors()
    val (warnFg, _) = Severity.WARNING.colors()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isLow) errBg.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Код: ${item.code}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${item.quantity} ${item.unit}",
                    fontWeight = FontWeight.Bold,
                    color = if (item.isLow) errFg else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "min ${item.minStock}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.isLow) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { viewModel.createOrderForLowStock(item) }) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = "Заявка", tint = warnFg)
                }
            }
        }
    }
}
