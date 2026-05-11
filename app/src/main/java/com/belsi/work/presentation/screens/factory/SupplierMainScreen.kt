package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.InventoryItem
import com.belsi.work.data.models.MaterialOrder

/**
 * FIX(2026-05-06): SupplierMainScreen — подключён к API.
 * Заявки + остатки через GET /production/materials/orders и /inventory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierMainScreen(
    navController: NavController,
    viewModel: SupplierViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(0) }

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
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmberPrimary.copy(alpha = 0.1f))
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Стат-карточки
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniStatCard("Заявки", "${state.pendingOrders.size}", Color(0xFFFBBF24), Modifier.weight(1f))
                MiniStatCard("Низкий остаток", "${state.lowStockCount}", Color(0xFFF43F5E), Modifier.weight(1f))
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
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✅", fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text("Нет открытых заявок", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    val (text, color) = when (status) {
        "pending" -> "Новая" to Color(0xFFFBBF24)
        "approved" -> "Утверждена" to Color(0xFF6366F1)
        "ordered" -> "Заказана" to Color(0xFF8B5CF6)
        "delivered" -> "Доставлена" to Color(0xFF10B981)
        "cancelled" -> "Отменена" to Color.Gray
        else -> status to Color.Gray
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isLow) Color(0xFFFFF1F2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
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
                    color = if (item.isLow) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurface,
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
                    Icon(Icons.Default.AddShoppingCart, contentDescription = "Заявка", tint = Color(0xFFF59E0B))
                }
            }
        }
    }
}

@Composable
private fun MiniStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
