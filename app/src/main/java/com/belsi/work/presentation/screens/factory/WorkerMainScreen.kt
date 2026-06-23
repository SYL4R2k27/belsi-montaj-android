package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.presentation.components.role.Severity
import com.belsi.work.presentation.components.role.colors
import com.belsi.work.presentation.theme.belsiColors
import com.belsi.work.data.local.database.dao.ShiftDao
import com.belsi.work.data.repositories.BatchRepository
import com.belsi.work.data.repositories.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-05): Главный экран Работника на производстве.
 * Brandbook: смена + 3 кнопки (перекур/обед/простой) + камера + задачи + автоген отчёт.
 * Привязан к одной фабрике (Углич), переключатель в шапке.
 */
/**
 * FIX(2026-05-12) BELSI 2.0.0 build14: + real timers, + real tasks from /production/engineer/tasks.
 * См. предыдущий FIX от build9 (shifts через ShiftRepository, breaks через BatchRepository).
 */
@HiltViewModel
class WorkerShiftViewModel @Inject constructor(
    private val batchRepo: BatchRepository,
    private val shiftRepo: ShiftRepository,
    private val shiftDao: ShiftDao,
    // FIX(2026-05-12) build14: задачи и тикер из реальных API
    private val productionRepo: com.belsi.work.data.repositories.ProductionRepository,
    // FIX(2026-05-12) build19 hotfix: реальное имя фабрики вместо mock "Углич — фабрика №1"
    private val activeRoleManager: com.belsi.work.data.local.ActiveRoleManager,
) : ViewModel() {

    /** FIX(2026-05-12) build14: реальные задачи рабочего (assignee=me) */
    private val _myTasks = kotlinx.coroutines.flow.MutableStateFlow<List<com.belsi.work.data.models.EngineerTask>>(emptyList())
    val myTasks: kotlinx.coroutines.flow.StateFlow<List<com.belsi.work.data.models.EngineerTask>> = _myTasks.asStateFlow()

    /** Тикер каждую секунду — для real-time таймеров смены/паузы */
    private val _now = kotlinx.coroutines.flow.MutableStateFlow(System.currentTimeMillis())
    val now: kotlinx.coroutines.flow.StateFlow<Long> = _now.asStateFlow()

    /** FIX(2026-05-12) build19 hotfix: имя активной фабрики из API (а не FactoryMockData). */
    private val _facilityName = MutableStateFlow<String?>(null)
    val facilityName: StateFlow<String?> = _facilityName.asStateFlow()

    init {
        viewModelScope.launch {
            productionRepo.getEngineerTasks(mine = true).onSuccess { _myTasks.value = it }
        }
        viewModelScope.launch {
            // Подтягиваем имя активной фабрики из API.
            // Если activeFacilityId не выбрана — берём первую из listFacilities().
            var activeId: String? = null
            activeRoleManager.activeFacilityId.collect { id ->
                activeId = id
                productionRepo.listFacilities().onSuccess { list ->
                    val match = list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
                    _facilityName.value = match?.name
                }
                // Подписываемся постоянно — при переключении фабрики обновится автоматически.
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                _now.value = System.currentTimeMillis()
            }
        }
    }

    fun refreshTasks() {
        viewModelScope.launch {
            productionRepo.getEngineerTasks(mine = true).onSuccess { _myTasks.value = it }
        }
    }

    fun markTaskDone(taskId: String) {
        viewModelScope.launch {
            productionRepo.updateEngineerTaskStatus(taskId, "done")
                .onSuccess { refreshTasks() }
        }
    }

    /** Активная смена производственника (Flow из Room — observable). */
    val activeShift = shiftDao.getActiveShiftFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentBreak = MutableStateFlow<String?>(null)
    val currentBreak: StateFlow<String?> = _currentBreak.asStateFlow()

    /** FIX(2026-05-12) build14: timestamp начала текущего перерыва для real-time таймера */
    private val _currentBreakStartedMs = MutableStateFlow<Long>(0L)
    val currentBreakStartedMs: StateFlow<Long> = _currentBreakStartedMs.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Старт производственной смены — реально создаём shifts в БД. */
    fun startShift() = viewModelScope.launch {
        shiftRepo.startShift(siteObjectId = null)
            .onFailure { e -> _error.value = "Ошибка старта смены: ${e.message}" }
    }

    /** Конец производственной смены. */
    fun finishShift() = viewModelScope.launch {
        val sid = shiftDao.getActiveShift()?.id ?: return@launch
        shiftRepo.endShift(sid)
            .onFailure { e -> _error.value = "Ошибка завершения смены: ${e.message}" }
        _currentBreak.value = null
    }

    fun startSmoke() = viewModelScope.launch {
        _currentBreak.value = "smoke"
        _currentBreakStartedMs.value = System.currentTimeMillis()
        batchRepo.startSmokeBreak()
            .onFailure { e ->
                // OfflineQueuedException — UI остаётся в state перерыва, action в очереди
                if (e !is com.belsi.work.data.offline.OfflineQueuedException) {
                    _currentBreak.value = null
                    _error.value = "Ошибка перекура: ${e.message}"
                }
            }
    }

    fun startLunch() = viewModelScope.launch {
        _currentBreak.value = "lunch"
        _currentBreakStartedMs.value = System.currentTimeMillis()
        batchRepo.startLunchBreak()
            .onFailure { e ->
                if (e !is com.belsi.work.data.offline.OfflineQueuedException) {
                    _currentBreak.value = null
                    _error.value = "Ошибка обеда: ${e.message}"
                }
            }
    }

    fun endBreak() = viewModelScope.launch {
        batchRepo.endBreak()
            .onFailure { e ->
                if (e !is com.belsi.work.data.offline.OfflineQueuedException) {
                    _error.value = "Ошибка завершения перерыва: ${e.message}"
                }
            }
        _currentBreak.value = null
        _currentBreakStartedMs.value = 0L
    }

    fun clearError() { _error.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerMainScreen(
    navController: NavController,
    viewModel: WorkerShiftViewModel = hiltViewModel(),
) {
    // FIX(2026-05-12) build19 hotfix: имя фабрики из VM (реальный API) вместо
    // hardcoded FactoryMockData.FACILITY_NAME = "Углич — фабрика №1".
    val facilityName by viewModel.facilityName.collectAsState()

    // FIX(2026-05-11) BELSI 2.0.0 build9: реальная смена из БД (Room),
    // не локальный compose var. После старта shifts row создаётся на сервере.
    val activeShiftEntity by viewModel.activeShift.collectAsState()
    val shiftActive = activeShiftEntity != null
    val currentBreakStr by viewModel.currentBreak.collectAsState()
    val currentBreak: BreakType? = when (currentBreakStr) {
        "smoke" -> BreakType.SMOKE
        "lunch" -> BreakType.LUNCH
        else -> null
    }
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Работник", fontWeight = FontWeight.Bold)
                    Text(
                        facilityName ?: "Фабрика не выбрана",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } },
                actions = {
                    // FIX(2026-05-12) BELSI 2.0.0 build14: реальный facility switch
                    IconButton(onClick = {
                        navController.navigate(com.belsi.work.presentation.navigation.AppRoute.FactoryFacilitySwitch.route)
                    }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Сменить фабрику")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Большая карточка статуса смены ───
            // FIX(2026-05-12) BELSI 2.0.0 build14: real-time таймер от activeShift.startAt
            val nowMs by viewModel.now.collectAsState()
            ShiftStatusCard(
                active = shiftActive,
                currentBreak = currentBreak,
                shiftStartIso = activeShiftEntity?.startAt,
                nowMs = nowMs,
                onStartShift = { viewModel.startShift() },
                onFinishShift = { viewModel.finishShift() }
            )

            // ─── 3 кнопки: Перекур · Обед · Простой ───
            if (shiftActive && currentBreak == null) {
                // FIX(2026-05-12) build19 hotfix: цвета кнопок берутся из Severity.
                // Перекур/Обед — WARNING (жёлтый), Простой — ERROR (розовый).
                val (smokeFg, _) = Severity.WARNING.colors()
                val (lunchFg, _) = Severity.WARNING.colors()
                val (idleFg, _)  = Severity.ERROR.colors()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BreakButton(
                        modifier = Modifier.weight(1f),
                        label = "Перекур", emoji = "☕", color = smokeFg,
                        onClick = { viewModel.startSmoke() }
                    )
                    BreakButton(
                        modifier = Modifier.weight(1f),
                        label = "Обед", emoji = "🍱", color = lunchFg,
                        onClick = { viewModel.startLunch() }
                    )
                    BreakButton(
                        modifier = Modifier.weight(1f),
                        label = "Простой", emoji = "⚠️", color = idleFg,
                        onClick = { navController.navigate("factory/idle/reasons") }
                    )
                }
            } else if (currentBreak != null) {
                val breakStartedMs by viewModel.currentBreakStartedMs.collectAsState()
                ActiveBreakCard(
                    breakType = currentBreak!!,
                    breakStartedMs = breakStartedMs.takeIf { it > 0 } ?: nowMs,
                    nowMs = nowMs,
                    onEnd = { viewModel.endBreak() },
                )
            }

            // ─── Камера ───
            // FIX(2026-05-12) BELSI 2.0.0 build14: реальный переход на Camera screen
            ActionCard(
                title = "Сделать фото",
                subtitle = "Обязателен комментарий",
                icon = Icons.Default.PhotoCamera,
                onClick = {
                    navController.navigate(
                        com.belsi.work.presentation.navigation.AppRoute.Camera.route
                    )
                }
            )

            // ─── Задачи (build14: реальные из /production/engineer/tasks?mine=true) ───
            val myTasks by viewModel.myTasks.collectAsState()
            val openCount = myTasks.count { it.status != "done" && it.status != "cancelled" }
            SectionHeader("Мои задачи", count = openCount)
            if (myTasks.isEmpty()) {
                Text(
                    "Задач нет",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            } else {
                myTasks.forEach { task ->
                    RealTaskRow(task, onDone = { viewModel.markTaskDone(task.id) })
                }
            }

            // ─── Кнопка отчёта смены ───
            // FIX(2026-05-10): убрана навигация на factory/shift/report/{shiftId}
            // потому что роута нет в NavGraph → IllegalArgumentException и crash.
            // Пока этот экран не реализован — направляем на ShiftHistory
            // (история всех смен, включая текущую).
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    navController.navigate(
                        com.belsi.work.presentation.navigation.AppRoute.ShiftHistory.route
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("История смен", fontWeight = FontWeight.Medium)
            }
        }
    }
}

private enum class BreakType(val label: String, val emoji: String) {
    SMOKE("Перекур", "☕"),
    LUNCH("Обед", "🍱"),
}

@Composable
private fun ShiftStatusCard(
    active: Boolean,
    currentBreak: BreakType?,
    // FIX(2026-05-12) build14: real-time таймер
    shiftStartIso: String? = null,
    nowMs: Long = System.currentTimeMillis(),
    onStartShift: () -> Unit,
    onFinishShift: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (active) "📍 Смена идёт" else "Смена не начата",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            if (active) {
                // FIX(2026-05-12) build14: считаем real time от startAt
                val startMs = remember(shiftStartIso) {
                    try {
                        if (shiftStartIso != null)
                            java.time.OffsetDateTime.parse(shiftStartIso).toInstant().toEpochMilli()
                        else nowMs
                    } catch (e: Exception) { nowMs }
                }
                val elapsedSec = ((nowMs - startMs) / 1000).coerceAtLeast(0)
                val h = elapsedSec / 3600
                val m = (elapsedSec % 3600) / 60
                val s = elapsedSec % 60
                Text(
                    "%02d:%02d:%02d".format(h, m, s),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onFinishShift,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Закрыть смену") }
            } else {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onStartShift,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Старт смены", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun BreakButton(
    modifier: Modifier = Modifier,
    label: String, emoji: String, color: Color,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@Composable
private fun ActiveBreakCard(
    breakType: BreakType,
    breakStartedMs: Long = System.currentTimeMillis(),
    nowMs: Long = System.currentTimeMillis(),
    onEnd: () -> Unit,
) {
    // FIX(2026-05-12) build14: real-time таймер паузы
    val elapsed = ((nowMs - breakStartedMs) / 1000).coerceAtLeast(0)
    val mm = elapsed / 60
    val ss = elapsed % 60
    val (_, warnBg) = Severity.WARNING.colors()
    Card(
        colors = CardDefaults.cardColors(containerColor = warnBg.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(breakType.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${breakType.label} идёт", fontWeight = FontWeight.Bold)
                Text("%02d:%02d".format(mm, ss), fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold)
            }
            Button(onClick = onEnd) { Text("Вернуться") }
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("$count", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * FIX(2026-05-12) BELSI 2.0.0 build14: реальная задача из /production/engineer/tasks.
 */
@Composable
private fun RealTaskRow(
    task: com.belsi.work.data.models.EngineerTask,
    onDone: () -> Unit,
) {
    val done = task.status == "done"
    val cancelled = task.status == "cancelled"
    // FIX(2026-05-12) build19 hotfix: priority цвета берутся из Severity.
    val (priorityColor, _) = when (task.priority) {
        "urgent" -> Severity.ERROR
        "high"   -> Severity.WARNING
        "low"    -> Severity.NEUTRAL
        else     -> Severity.INFO
    }.colors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (done || cancelled) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
        ),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = done,
                onCheckedChange = { if (!done && !cancelled) onDone() },
                enabled = !done && !cancelled,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                )
                if (!task.description.isNullOrBlank()) {
                    Text(
                        task.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (task.priority != "normal") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = priorityColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                task.priority.uppercase(),
                                fontSize = 9.sp,
                                color = priorityColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    task.batchTitle?.let {
                        Text(
                            "📦 $it",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// FIX(2026-05-14) BELSI 2.0.1: удалён мёртвый TaskRow(FactoryMockData.WorkerTask) —
// никем не вызывался, RealTaskRow выше использует реальную модель TeamMemberTaskDto.

@Composable
private fun StatPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

