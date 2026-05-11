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
import com.belsi.work.data.models.Brigade

/**
 * FIX(2026-05-06): ProductionChiefMainScreen — подключён к API.
 * Дашборд фабрики через GET /production/facility/{id}/dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionChiefMainScreen(
    navController: NavController,
    viewModel: ProductionChiefViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val facility = state.selectedFacility
    val dashboard = state.dashboard

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Начальник производства", fontWeight = FontWeight.Bold)
                    Text(
                        facility?.name ?: "Фабрика не выбрана",
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
        if (state.loading && dashboard == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (dashboard == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏭", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Фабрика не настроена",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Обратитесь к куратору",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Партии") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Всего", "${dashboard.batchesTotal}", AmberPrimary, Modifier.weight(1f))
                    StatCard("В произв.", "${dashboard.batchesInProduction}", Color(0xFF6366F1), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("К отгрузке", "${dashboard.batchesReadyToShip}", Color(0xFF10B981), Modifier.weight(1f))
                    StatCard("Сдано сегодня", "${dashboard.batchesCompletedToday}", Color(0xFF14B8A6), Modifier.weight(1f))
                }
            }

            item { SectionTitle("Бригады и рабочие") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Бригад", "${dashboard.brigadesCount}", AmberPrimary, Modifier.weight(1f))
                    StatCard("Рабочих", "${dashboard.workersTotal}", Color(0xFF6366F1), Modifier.weight(1f))
                    StatCard("На смене", "${dashboard.workersOnShift}", Color(0xFF10B981), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("На паузе", "${dashboard.workersOnPause}", Color(0xFFFBBF24), Modifier.weight(1f))
                    StatCard("Простой", "${dashboard.workersOnIdle}", Color(0xFFF43F5E), Modifier.weight(1f))
                }
            }

            item { SectionTitle("Время сегодня") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Работа", "${dashboard.workHoursToday} ч", Color(0xFF10B981), Modifier.weight(1f))
                    StatCard("Простой", "${dashboard.idleHoursToday} ч", Color(0xFFF43F5E), Modifier.weight(1f))
                }
            }

            if (dashboard.materialOrdersPending > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Заявки требуют утверждения", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${dashboard.materialOrdersPending} заявок",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (state.brigades.isNotEmpty()) {
                item { SectionTitle("Бригады фабрики") }
                items(state.brigades) { brigade -> BrigadeRow(brigade) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun BrigadeRow(brigade: Brigade) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = AmberPrimary)
                Spacer(Modifier.width(8.dp))
                Text(brigade.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${brigade.activeCount}/${brigade.membersCount}",
                    color = AmberPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (brigade.seniorName != null && brigade.seniorName.trim().isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Старший: ${brigade.seniorName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (brigade.idleCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "⚠ ${brigade.idleCount} в простое",
                    fontSize = 12.sp,
                    color = Color(0xFFF43F5E),
                )
            }
        }
    }
}
