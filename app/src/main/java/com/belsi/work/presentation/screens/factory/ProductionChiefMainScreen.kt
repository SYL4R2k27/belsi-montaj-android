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
import com.belsi.work.data.models.Brigade
import com.belsi.work.presentation.components.role.RoleEmptyState
import com.belsi.work.presentation.components.role.RoleSectionHeader
import com.belsi.work.presentation.components.role.RoleStatCard
import com.belsi.work.presentation.components.role.Severity
import com.belsi.work.presentation.components.role.colors

/**
 * ProductionChiefMainScreen — дашборд начальника производства.
 *
 * FIX(2026-05-12) build19 hotfix: переведён на единую дизайн-систему
 * (RoleStatCard / Severity). Удалены 12 hardcoded Color(0xFF...) — теперь
 * все цвета через MaterialTheme.belsiColors + colorScheme. Партии «к
 * отгрузке/сдано» — Severity.SUCCESS, простой — Severity.ERROR, паузы —
 * Severity.WARNING, бригады/рабочие — Severity.PRIMARY/INFO.
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
                    IconButton(onClick = {
                        navController.navigate(
                            com.belsi.work.presentation.navigation.AppRoute.FactoryFacilitySwitch.route
                        )
                    }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Сменить фабрику")
                    }
                    IconButton(onClick = {
                        navController.navigate(
                            com.belsi.work.presentation.navigation.AppRoute.CuratorPhotos.route
                        )
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Лента фабрики")
                    }
                    // FIX(2026-05-12) build19 Этап3+: Tool Transfer Hub
                    IconButton(onClick = {
                        navController.navigate(
                            com.belsi.work.presentation.navigation.AppRoute.ToolTransferHub.createRoute("incoming")
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
    ) { padding ->
        if (state.loading && dashboard == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (dashboard == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                RoleEmptyState(
                    emoji = "🏭",
                    title = "Фабрика не настроена",
                    subtitle = "Обратитесь к куратору",
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RoleSectionHeader("Партии") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleStatCard("Всего", "${dashboard.batchesTotal}", Severity.PRIMARY, modifier = Modifier.weight(1f))
                    RoleStatCard("В произв.", "${dashboard.batchesInProduction}", Severity.INFO, modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleStatCard("К отгрузке", "${dashboard.batchesReadyToShip}", Severity.SUCCESS, modifier = Modifier.weight(1f))
                    RoleStatCard("Сдано сегодня", "${dashboard.batchesCompletedToday}", Severity.SUCCESS, modifier = Modifier.weight(1f))
                }
            }

            item { RoleSectionHeader("Бригады и рабочие") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleStatCard("Бригад", "${dashboard.brigadesCount}", Severity.PRIMARY, modifier = Modifier.weight(1f))
                    RoleStatCard("Рабочих", "${dashboard.workersTotal}", Severity.INFO, modifier = Modifier.weight(1f))
                    RoleStatCard("На смене", "${dashboard.workersOnShift}", Severity.SUCCESS, modifier = Modifier.weight(1f))
                }
            }
            // FIX(2026-05-14) BELSI 2.0.1: расширенная разбивка состояний.
            // Раньше: только pause/idle (на обеде «пропадал из on_shift»).
            // Теперь: реально работают / пауза / обед / перекур / простой — все независимо.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleStatCard(
                        "Работают", "${dashboard.workersActivelyWorking}",
                        Severity.SUCCESS, modifier = Modifier.weight(1f),
                    )
                    RoleStatCard(
                        "Пауза", "${dashboard.workersOnPause}",
                        Severity.WARNING, modifier = Modifier.weight(1f),
                    )
                    RoleStatCard(
                        "Простой", "${dashboard.workersOnIdle}",
                        Severity.ERROR, modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleStatCard(
                        "🍽 Обед", "${dashboard.workersOnLunch}",
                        Severity.INFO, modifier = Modifier.weight(1f),
                    )
                    RoleStatCard(
                        "🚬 Перекур", "${dashboard.workersOnSmoke}",
                        Severity.NEUTRAL, modifier = Modifier.weight(1f),
                    )
                }
            }

            item { RoleSectionHeader("Время сегодня") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleStatCard("Работа", "${dashboard.workHoursToday} ч", Severity.SUCCESS, modifier = Modifier.weight(1f))
                    RoleStatCard("Простой", "${dashboard.idleHoursToday} ч", Severity.ERROR, modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleStatCard(
                        "🍽 Обеды", "${dashboard.lunchHoursToday} ч",
                        Severity.INFO, modifier = Modifier.weight(1f),
                    )
                    RoleStatCard(
                        "☕ Перерывы", "${dashboard.breakHoursToday} ч",
                        Severity.NEUTRAL, modifier = Modifier.weight(1f),
                    )
                }
            }

            if (dashboard.materialOrdersPending > 0) {
                item {
                    val (warnFg, warnBg) = Severity.WARNING.colors()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = warnBg.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = warnFg)
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
                item { RoleSectionHeader("Бригады фабрики") }
                items(state.brigades) { brigade -> BrigadeRow(brigade) }
            }
        }
    }
}

@Composable
private fun BrigadeRow(brigade: Brigade) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(brigade.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${brigade.activeCount}/${brigade.membersCount}",
                    color = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
