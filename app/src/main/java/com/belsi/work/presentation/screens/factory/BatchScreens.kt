package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.belsi.work.data.models.Batch
import com.belsi.work.data.models.BatchCreateRequest
import com.belsi.work.data.models.BatchStatus
import com.belsi.work.presentation.navigation.AppRoute
import java.util.UUID

/**
 * FIX(2026-05-05): Универсальные экраны Pipeline партии.
 * Используются всеми ролями: Производство (создание), Логистика (отгрузка),
 * Монтаж (приёмка), Куратор (наблюдение). Фильтр по роли — на сервере.
 */

// ─────────────── BatchListScreen ───────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchListScreen(
    navController: NavController,
    viewModel: BatchListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var filter by remember { mutableStateOf<BatchStatus?>(null) }

    // FIX(2026-05-05): fallback на mock когда сервер недоступен (Pipeline endpoints
    // живут только на feature/driver-integration, не задеплоены).
    // Когда деплой будет — реальные данные из API подменят mock без переписывания UI.
    val batches: List<FactoryMockData.Batch> = if (state.batches.isNotEmpty()) {
        state.batches.map { it.toMockShape() }
    } else {
        FactoryMockData.batches
    }

    LaunchedEffect(filter) { viewModel.setFilter(filter) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Партии") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { /* search */ }) { Icon(Icons.Default.Search, contentDescription = null) }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(AppRoute.BatchCreate.route) },
                containerColor = AmberPrimary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Создать")
            }
        }
    ) { padding ->
        // FIX(2026-05-11) BELSI 2.0.0: max-width 1000dp + центрирование для широких экранов
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth().fillMaxHeight()) {
            // Фильтр-чипы по статусам
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(filter == null, "Все") { filter = null }
                        BatchStatus.values().filter { it != BatchStatus.CANCELLED }.forEach { st ->
                            FilterChip(filter == st, "${st.emoji} ${st.label}") { filter = st }
                        }
                    }
                }
                items(
                    if (filter == null) batches
                    else batches.filter { mockStatusMatch(it.status, filter!!) }
                ) { b ->
                    BatchListRow(b) { navController.navigate(AppRoute.BatchDetail.createRoute(b.id)) }
                }
            }
        }
        }  // FIX(2026-05-11): close Box max-width wrapper
    }
}

@Composable
private fun BatchListRow(b: FactoryMockData.Batch, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.status.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, fontWeight = FontWeight.SemiBold)
                    Text("→ ${b.targetObject}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${b.itemCount} шт", fontWeight = FontWeight.Bold, color = AmberPrimary)
                    Text(b.deadline, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .background(statusBg(b.status), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(b.status.label, fontSize = 11.sp, color = statusFg(b.status), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
    )
}

// ─────────────── BatchDetailScreen ───────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailScreen(
    navController: NavController,
    batchId: String,
    viewModel: BatchDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(batchId) { viewModel.load(batchId) }
    val state by viewModel.state.collectAsState()

    // FIX(2026-05-05): real-batch если пришёл с сервера → toMockShape для UI.
    // Иначе fallback на mock (поиск по batchId, иначе первая партия).
    val batch: FactoryMockData.Batch = state.batch?.toMockShape()
        ?: FactoryMockData.batches.firstOrNull { it.id == batchId }
        ?: FactoryMockData.batches.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Партия", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(batch.id, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                } },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(Modifier.padding(16.dp)) {
                    val nextAction = nextActionFor(batch.status)
                    val nextStatus = nextStatusFor(batch.status)
                    if (nextAction != null && nextStatus != null) {
                        Button(
                            // FIX(2026-05-05): реальный переход партии по pipeline через ViewModel.
                            // changeStatus вызывает /production/batches/{id}/status → audit log.
                            onClick = { viewModel.changeStatus(nextStatus) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                            enabled = !state.isLoading,
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text(nextAction, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (state.error != null) {
                            Text(
                                "⚠️ ${state.error}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Заголовок
            Card(
                colors = CardDefaults.cardColors(containerColor = AmberPrimary.copy(alpha = 0.1f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(batch.status.emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(batch.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(batch.status.label, fontSize = 12.sp, color = AmberPrimary,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    InfoRow("Количество", "${batch.itemCount} шт")
                    InfoRow("Объект-цель", batch.targetObject)
                    InfoRow("Дедлайн", batch.deadline)
                    InfoRow("Ответственный", batch.responsible)
                }
            }

            // Pipeline визуализация
            Text("Жизненный цикл", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp))
            PipelineSteps(batch.status)

            // История (мок)
            Text("История", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp))
            Card { Column(Modifier.padding(12.dp)) {
                HistoryRow("Создана", "Начальник производства", "сегодня 09:00")
                HistoryRow("В работе", "Старший Петров", "сегодня 09:15")
                if (batch.status == FactoryMockData.BatchStatus.READY_TO_SHIP ||
                    batch.status == FactoryMockData.BatchStatus.IN_ROUTE ||
                    batch.status == FactoryMockData.BatchStatus.DELIVERED ||
                    batch.status == FactoryMockData.BatchStatus.INSTALLED) {
                    HistoryRow("Готова к отгрузке", "Начальник производства", "сегодня 18:30")
                }
            } }
        }
    }
}

// ─────────────── BatchCreateScreen ───────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchCreateScreen(
    navController: NavController,
    viewModel: BatchCreateViewModel = hiltViewModel(),
) {
    var title by remember { mutableStateOf("") }
    var itemCount by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }

    val state by viewModel.state.collectAsState()

    // FIX(2026-05-05): после успешного создания → возврат назад
    LaunchedEffect(state.createdId) {
        if (state.createdId != null) {
            navController.popBackStack()
            viewModel.reset()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая партия") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(Modifier.padding(16.dp)) {
                    Button(
                        // FIX(2026-05-05): реальный submit через ViewModel.
                        // facility_id — TODO подтянуть из ActiveRoleManager.activeFacilityId.
                        // Сейчас передаём dummy UUID для тестирования формы.
                        onClick = {
                            viewModel.submit(
                                BatchCreateRequest(
                                    title = title,
                                    itemCount = itemCount.toIntOrNull() ?: 0,
                                    sourceFacilityId = UUID.fromString("00000000-0000-0000-0000-000000000000"),
                                    targetObjectId = null,  // TODO: выбор объекта в форме
                                    deadline = deadline.takeIf { it.isNotBlank() },
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !state.isSubmitting && title.isNotBlank() && itemCount.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White,
                            )
                        } else {
                            Text("Создать партию", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (state.error != null) {
                        Text(
                            "⚠️ ${state.error}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Партия попадёт в очередь производства. Когда будет готова — переведите в «Готова к отгрузке».",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Название партии") },
                placeholder = { Text("Напр.: Подоконники белые 1.5м") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = itemCount, onValueChange = { itemCount = it.filter { c -> c.isDigit() } },
                label = { Text("Количество (шт)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = target, onValueChange = { target = it },
                label = { Text("Объект-цель") },
                placeholder = { Text("Напр.: Школа №7, Коломенская 16") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = deadline, onValueChange = { deadline = it },
                label = { Text("Дедлайн") },
                placeholder = { Text("Напр.: 06.05.2026 14:00") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────── Helpers ───────────────

@Composable
private fun PipelineSteps(current: FactoryMockData.BatchStatus) {
    val steps = listOf(
        FactoryMockData.BatchStatus.IN_PRODUCTION,
        FactoryMockData.BatchStatus.READY_TO_SHIP,
        FactoryMockData.BatchStatus.IN_ROUTE,
        FactoryMockData.BatchStatus.DELIVERED,
        FactoryMockData.BatchStatus.INSTALLED,
    )
    val currentIdx = steps.indexOf(current)
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { idx, st ->
            val state = when {
                idx < currentIdx -> "done"
                idx == currentIdx -> "active"
                else -> "future"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = when (state) {
                                "done" -> Color(0xFF10B981)
                                "active" -> AmberPrimary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state == "done") Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    else Text("${idx + 1}", fontSize = 11.sp, color = if (state == "active") Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${st.emoji} ${st.label}",
                    fontSize = 13.sp,
                    fontWeight = if (state == "active") FontWeight.Bold else FontWeight.Normal,
                    color = if (state == "future") MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    } }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HistoryRow(action: String, by: String, when_: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .background(AmberPrimary, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(action, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("$by · $when_", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun nextActionFor(status: FactoryMockData.BatchStatus): String? = when (status) {
    FactoryMockData.BatchStatus.DRAFT -> "Запустить в производство"
    FactoryMockData.BatchStatus.IN_PRODUCTION -> "Готова к отгрузке"
    FactoryMockData.BatchStatus.READY_TO_SHIP -> "Включить в маршрут"
    FactoryMockData.BatchStatus.IN_ROUTE -> "Подтвердить приёмку"
    FactoryMockData.BatchStatus.DELIVERED -> "Закрыть монтаж"
    FactoryMockData.BatchStatus.INSTALLED -> null
}

// FIX(2026-05-05): следующий статус для перехода через ViewModel.changeStatus.
// Соответствует TRANSITIONS-graph на сервере (production_batches.py).
private fun nextStatusFor(status: FactoryMockData.BatchStatus): BatchStatus? = when (status) {
    FactoryMockData.BatchStatus.DRAFT -> BatchStatus.IN_PRODUCTION
    FactoryMockData.BatchStatus.IN_PRODUCTION -> BatchStatus.READY_TO_SHIP
    FactoryMockData.BatchStatus.READY_TO_SHIP -> BatchStatus.IN_ROUTE
    FactoryMockData.BatchStatus.IN_ROUTE -> BatchStatus.DELIVERED
    FactoryMockData.BatchStatus.DELIVERED -> BatchStatus.INSTALLED
    FactoryMockData.BatchStatus.INSTALLED -> null
}

private fun statusBg(s: FactoryMockData.BatchStatus): Color = when (s) {
    FactoryMockData.BatchStatus.DRAFT -> Color(0xFFF1F5F9)
    FactoryMockData.BatchStatus.IN_PRODUCTION -> Color(0xFFFEF3C7)
    FactoryMockData.BatchStatus.READY_TO_SHIP -> Color(0xFFFEF3C7)
    FactoryMockData.BatchStatus.IN_ROUTE -> Color(0xFFE0F2FE)
    FactoryMockData.BatchStatus.DELIVERED -> Color(0xFFE0E7FF)
    FactoryMockData.BatchStatus.INSTALLED -> Color(0xFFD1FAE5)
}

private fun statusFg(s: FactoryMockData.BatchStatus): Color = when (s) {
    FactoryMockData.BatchStatus.DRAFT -> Color(0xFF475569)
    FactoryMockData.BatchStatus.IN_PRODUCTION -> Color(0xFF92400E)
    FactoryMockData.BatchStatus.READY_TO_SHIP -> Color(0xFF92400E)
    FactoryMockData.BatchStatus.IN_ROUTE -> Color(0xFF0369A1)
    FactoryMockData.BatchStatus.DELIVERED -> Color(0xFF4338CA)
    FactoryMockData.BatchStatus.INSTALLED -> Color(0xFF065F46)
}

private fun mockStatusMatch(mock: FactoryMockData.BatchStatus, target: BatchStatus): Boolean = when (target) {
    BatchStatus.DRAFT -> mock == FactoryMockData.BatchStatus.DRAFT
    BatchStatus.IN_PRODUCTION -> mock == FactoryMockData.BatchStatus.IN_PRODUCTION
    BatchStatus.READY_TO_SHIP -> mock == FactoryMockData.BatchStatus.READY_TO_SHIP
    BatchStatus.IN_ROUTE -> mock == FactoryMockData.BatchStatus.IN_ROUTE
    BatchStatus.DELIVERED -> mock == FactoryMockData.BatchStatus.DELIVERED
    BatchStatus.INSTALLED -> mock == FactoryMockData.BatchStatus.INSTALLED
    BatchStatus.CANCELLED -> false
}

// FIX(2026-05-05): мост Real Batch → mock-shape для UI compatibility.
// Так UI рисует одинаково и mock, и real данные.
internal fun Batch.toMockShape(): FactoryMockData.Batch = FactoryMockData.Batch(
    id = this.id.toString(),
    title = this.title,
    targetObject = "—",  // TODO: подтянуть имя объекта через ObjectsRepo
    itemCount = this.itemCount,
    deadline = this.deadline ?: "—",
    status = when (this.status) {
        BatchStatus.DRAFT -> FactoryMockData.BatchStatus.DRAFT
        BatchStatus.IN_PRODUCTION -> FactoryMockData.BatchStatus.IN_PRODUCTION
        BatchStatus.READY_TO_SHIP -> FactoryMockData.BatchStatus.READY_TO_SHIP
        BatchStatus.IN_ROUTE -> FactoryMockData.BatchStatus.IN_ROUTE
        BatchStatus.DELIVERED -> FactoryMockData.BatchStatus.DELIVERED
        BatchStatus.INSTALLED -> FactoryMockData.BatchStatus.INSTALLED
        BatchStatus.CANCELLED -> FactoryMockData.BatchStatus.DRAFT
    },
    responsible = "—",
)
