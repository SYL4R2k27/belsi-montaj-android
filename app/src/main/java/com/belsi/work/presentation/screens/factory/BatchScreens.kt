package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
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

    // FIX(2026-05-12) BELSI 2.0.0 build14: убрали mock-fallback.
    // Backend pipeline уже deployed (build9-14). Пустой список — пустой,
    // empty state ниже, не выдумываем 5 фейковых партий.
    val batches: List<FactoryMockData.Batch> = state.batches.map { it.toMockShape() }

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
                    // FIX(2026-05-12) BELSI 2.0.0 build14: refresh — поиск отдельный экран на потом
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(AppRoute.BatchCreate.route) },
                containerColor = MaterialTheme.colorScheme.primary,
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
                val visibleBatches = if (filter == null) batches
                    else batches.filter { mockStatusMatch(it.status, filter!!) }
                if (visibleBatches.isEmpty()) {
                    item {
                        // FIX(2026-05-12) BELSI 2.0.0 build14: честный empty state
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("📦", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (filter == null) "Партий пока нет"
                                else "Нет партий со статусом «${filter!!.label}»",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Создайте первую партию через «+» внизу",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items(visibleBatches) { b ->
                        BatchListRow(b) { navController.navigate(AppRoute.BatchDetail.createRoute(b.id)) }
                    }
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
                    Text("${b.itemCount} шт", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

    // FIX(2026-05-12) BELSI 2.0.0 build14: убрали mock fallback. Если бек не отдал партию
    // — показываем loader или ошибку.
    val batchNullable: FactoryMockData.Batch? = state.batch?.toMockShape()

    if (batchNullable == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.isLoading) CircularProgressIndicator()
            else Text(state.error ?: "Партия не найдена", color = MaterialTheme.colorScheme.error)
        }
        return
    }
    val batch = batchNullable

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
                // FIX(2026-06-02): navigationBarsPadding — кнопки не уходят под системную навигацию Android.
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    val nextAction = nextActionFor(batch.status)
                    val nextStatus = nextStatusFor(batch.status)
                    if (nextAction != null && nextStatus != null) {
                        Button(
                            // FIX(2026-05-05): реальный переход партии по pipeline через ViewModel.
                            // changeStatus вызывает /production/batches/{id}/status → audit log.
                            onClick = { viewModel.changeStatus(nextStatus) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(batch.status.emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(batch.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(batch.status.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
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

            // FIX(2026-05-12) BELSI 2.0.0 build15: «Доставка» — связанная заявка/маршрут.
            // Сейчас отображаем минимум — расширенный delivery info требует backend enrichment
            // через композитный endpoint (BatchOut.delivery_info). Пока показываем статус
            // и подсказку, что партия едет / доставлена / в маршруте.
            if (batch.status in listOf(
                    FactoryMockData.BatchStatus.READY_TO_SHIP,
                    FactoryMockData.BatchStatus.IN_ROUTE,
                    FactoryMockData.BatchStatus.DELIVERED,
                    FactoryMockData.BatchStatus.INSTALLED,
                )) {
                Text("Доставка", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚛", fontSize = 22.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val deliveryLabel = when (batch.status) {
                                    FactoryMockData.BatchStatus.READY_TO_SHIP -> "Готова к отгрузке"
                                    FactoryMockData.BatchStatus.IN_ROUTE -> "Партия в маршруте"
                                    FactoryMockData.BatchStatus.DELIVERED -> "Доставлена, ожидает приёмки"
                                    FactoryMockData.BatchStatus.INSTALLED -> "Монтаж закрыт"
                                    else -> ""
                                }
                                Text(deliveryLabel, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Подробности маршрута — в журнале логиста",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // FIX(2026-05-12) BELSI 2.0.0 build14: реальная история переходов статусов
            // из backend batch_status_history. Раньше — хардкод из 3 строк.
            Text("История", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp))
            Card { Column(Modifier.padding(12.dp)) {
                if (state.history.isEmpty()) {
                    Text(
                        "Истории нет",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.history.forEach { item ->
                        HistoryRow(
                            action = "${item.fromStatus ?: "—"} → ${item.toStatus}",
                            by = item.changedBy.toString().take(8),
                            when_ = item.changedAt.take(16).replace("T", " "),
                        )
                        if (!item.comment.isNullOrBlank()) {
                            Text(
                                item.comment,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 22.dp, bottom = 4.dp),
                            )
                        }
                    }
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
    var deadline by remember { mutableStateOf("") }
    // FIX(2026-05-12) BELSI 2.0.0 build14: реальный выбор фабрики и целевого объекта
    var selectedFacilityId by remember { mutableStateOf<UUID?>(null) }
    var selectedTargetId by remember { mutableStateOf<UUID?>(null) }
    var facilityMenuOpen by remember { mutableStateOf(false) }
    var targetMenuOpen by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsState()
    // Авто-выбор первой фабрики если есть
    LaunchedEffect(state.facilities) {
        if (selectedFacilityId == null && state.facilities.isNotEmpty()) {
            selectedFacilityId = try { UUID.fromString(state.facilities.first().id) } catch (e: Exception) { null }
        }
    }

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
                        // FIX(2026-05-12) BELSI 2.0.0 build14: реальные UUID из dropdown.
                        onClick = {
                            val fid = selectedFacilityId ?: return@Button
                            viewModel.submit(
                                BatchCreateRequest(
                                    title = title,
                                    itemCount = itemCount.toIntOrNull() ?: 0,
                                    sourceFacilityId = fid,
                                    targetObjectId = selectedTargetId,
                                    deadline = deadline.takeIf { it.isNotBlank() },
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !state.isSubmitting && title.isNotBlank()
                            && itemCount.isNotBlank() && selectedFacilityId != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
            // FIX(2026-05-12) BELSI 2.0.0 build14: реальный dropdown фабрик
            ExposedDropdownMenuBox(
                expanded = facilityMenuOpen,
                onExpandedChange = { facilityMenuOpen = it },
            ) {
                val selectedFacility = state.facilities.firstOrNull { it.id == selectedFacilityId?.toString() }
                OutlinedTextField(
                    value = selectedFacility?.name ?: "Выберите фабрику…",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Фабрика (источник)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = facilityMenuOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = facilityMenuOpen,
                    onDismissRequest = { facilityMenuOpen = false },
                ) {
                    state.facilities.forEach { facility ->
                        DropdownMenuItem(
                            text = { Text(facility.name) },
                            onClick = {
                                selectedFacilityId = try { UUID.fromString(facility.id) } catch (e: Exception) { null }
                                facilityMenuOpen = false
                            },
                        )
                    }
                    if (state.facilities.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Фабрик нет — обратитесь к куратору", color = MaterialTheme.colorScheme.error) },
                            onClick = { facilityMenuOpen = false },
                        )
                    }
                }
            }
            // FIX(2026-05-12) build14: dropdown объекта-цели (опционально — можно создать партию без цели)
            ExposedDropdownMenuBox(
                expanded = targetMenuOpen,
                onExpandedChange = { targetMenuOpen = it },
            ) {
                val selectedTarget = state.targetObjects.firstOrNull { it.id == selectedTargetId?.toString() }
                OutlinedTextField(
                    value = selectedTarget?.name ?: "Без объекта-цели",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Объект-цель (опционально)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetMenuOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = targetMenuOpen,
                    onDismissRequest = { targetMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("— без цели —") },
                        onClick = { selectedTargetId = null; targetMenuOpen = false },
                    )
                    state.targetObjects.forEach { obj ->
                        DropdownMenuItem(
                            text = { Text("${obj.name}${obj.address?.let { " · $it" } ?: ""}") },
                            onClick = {
                                selectedTargetId = try { UUID.fromString(obj.id) } catch (e: Exception) { null }
                                targetMenuOpen = false
                            },
                        )
                    }
                }
            }
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
                                "active" -> MaterialTheme.colorScheme.primary
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
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
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

// FIX(2026-05-12) build19 hotfix: статусные цвета из theme/Color.kt константов
// (а не hardcoded HEX). Single source of truth — 1.2.5 design tokens.
private fun statusBg(s: FactoryMockData.BatchStatus): Color = when (s) {
    FactoryMockData.BatchStatus.DRAFT          -> com.belsi.work.presentation.theme.Slate100
    FactoryMockData.BatchStatus.IN_PRODUCTION  -> Color(0xFFFEF3C7)  // warning container (Amber50-ish)
    FactoryMockData.BatchStatus.READY_TO_SHIP  -> Color(0xFFFEF3C7)
    FactoryMockData.BatchStatus.IN_ROUTE       -> Color(0xFFDBEAFE)  // info container (Sky50-ish)
    FactoryMockData.BatchStatus.DELIVERED      -> com.belsi.work.presentation.theme.Indigo100
    FactoryMockData.BatchStatus.INSTALLED      -> Color(0xFFDCFCE7)  // success container (Emerald50)
}

private fun statusFg(s: FactoryMockData.BatchStatus): Color = when (s) {
    FactoryMockData.BatchStatus.DRAFT          -> com.belsi.work.presentation.theme.Slate600
    FactoryMockData.BatchStatus.IN_PRODUCTION  -> com.belsi.work.presentation.theme.Amber600
    FactoryMockData.BatchStatus.READY_TO_SHIP  -> com.belsi.work.presentation.theme.Amber600
    FactoryMockData.BatchStatus.IN_ROUTE       -> com.belsi.work.presentation.theme.Sky500
    FactoryMockData.BatchStatus.DELIVERED      -> com.belsi.work.presentation.theme.Indigo700
    FactoryMockData.BatchStatus.INSTALLED      -> com.belsi.work.presentation.theme.Emerald600
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
    // FIX(2026-05-12) BELSI 2.0.0 build14: показываем targetObjectId (backend без target_object_name)
    // — для UI достаточно first 8 chars если объект-цель указан, иначе «—».
    targetObject = this.targetObjectId?.let { "Объект ${it.toString().take(8)}" } ?: "без цели",
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
