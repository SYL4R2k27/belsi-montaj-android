package com.belsi.work.presentation.screens.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.tool_transfer.ToolTransferDto
import com.belsi.work.presentation.components.role.RolePrimaryButton
import com.belsi.work.presentation.components.role.RoleSectionHeader
import com.belsi.work.presentation.components.role.RoleStatusPill
import com.belsi.work.presentation.components.role.Severity
// FIX(2026-05-14) BELSI 2.0.1: imports для DriverPickerBottomSheet
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * FIX(2026-05-12) build19 Этап3: детальный экран передачи + actions
 * (accept / reject / take / release).
 *
 * Action-buttons показываются по статусу:
 *   delivered / in_transit → Принять / Отказать
 *   accepted               → Взять в работу
 *   in_use                 → Положить обратно
 *   accepted/in_use        → (для куратора может быть архив-action — пока скрыто)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTransferDetailScreen(
    navController: NavController,
    transferId: String,
    viewModel: ToolTransferDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }
    var actualQty by remember { mutableStateOf("") }
    // FIX(2026-05-14) BELSI 2.0.1: driver-picker для return-assign
    var showDriverPicker by remember { mutableStateOf(false) }

    LaunchedEffect(transferId) {
        viewModel.load(transferId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Передача") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { padding ->
        if (state.isLoading && state.transfer == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val t = state.transfer
        if (t == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Передача не найдена")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Заголовок
            Text(
                "${t.toolName ?: "Инструмент"} × ${t.quantity}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            StatusPillFor(t.status)

            // Маршрут
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Назначение",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "→ ${t.toSiteObjectName ?: "объект"}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!t.driverName.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Везёт",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(t.driverName, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!t.createdByName.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Отправил",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(t.createdByName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // История
            RoleSectionHeader(title = "История")
            TimelineEntries(t)

            if (!t.comment.isNullOrBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Комментарий", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(t.comment, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (!t.rejectReason.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Причина отказа", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(t.rejectReason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Actions по статусу
            Spacer(Modifier.height(8.dp))
            when (t.status) {
                "delivered", "in_transit" -> {
                    OutlinedTextField(
                        value = actualQty,
                        onValueChange = { v -> actualQty = v.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Фактическое количество (необязательно)") },
                        placeholder = { Text("Если меньше ${t.quantity} — впишите факт") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    RolePrimaryButton(
                        text = if (state.isActing) "..." else "Принять",
                        onClick = {
                            viewModel.accept(
                                transferId = transferId,
                                actualQuantity = actualQty.toIntOrNull(),
                            )
                        },
                        enabled = !state.isActing,
                        severity = Severity.SUCCESS,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showRejectDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isActing,
                    ) {
                        Text("Отказать")
                    }
                }
                "accepted" -> {
                    RolePrimaryButton(
                        text = if (state.isActing) "..." else "Взять в работу",
                        onClick = { viewModel.take(transferId) },
                        enabled = !state.isActing,
                    )
                    Spacer(Modifier.height(8.dp))
                    // FIX(2026-05-14) BELSI 2.0.1: возврат из accepted
                    OutlinedButton(
                        onClick = {
                            navController.navigate("tools/return-request/${t.id}")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isActing,
                    ) { Text("⏪ Вернуть на завод") }
                }
                "in_use" -> {
                    OutlinedButton(
                        onClick = { viewModel.release(transferId) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isActing,
                    ) { Text("Положить обратно") }
                    Spacer(Modifier.height(8.dp))
                    // FIX(2026-05-14) BELSI 2.0.1: возврат из in_use
                    OutlinedButton(
                        onClick = {
                            navController.navigate("tools/return-request/${t.id}")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isActing,
                    ) { Text("⏪ Вернуть на завод") }
                }
                // ─── Return flow actions (FIX 2026-05-14 BELSI 2.0.1) ──
                "returning_requested" -> {
                    Text(
                        "Запрос на возврат отправлен. Назначь водителя:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    RolePrimaryButton(
                        text = if (state.isActing) "..." else "🚚 Назначить водителя",
                        onClick = { showDriverPicker = true },
                        enabled = !state.isActing,
                    )
                }
                "returning" -> {
                    Text(
                        "Водитель назначен — ${t.returnDriverUserId?.take(8) ?: "—"}. Ожидаем pickup.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Driver-action: pickup
                    RolePrimaryButton(
                        text = if (state.isActing) "..." else "📦 Забрал с объекта",
                        onClick = {
                            navController.navigate("tools/return-pickup/${t.id}")
                        },
                        enabled = !state.isActing,
                    )
                }
                "returning_in_transit" -> {
                    Text(
                        "Везу на завод…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    RolePrimaryButton(
                        text = if (state.isActing) "..." else "🏭 Доставил на завод",
                        onClick = {
                            navController.navigate("tools/return-deliver/${t.id}")
                        },
                        enabled = !state.isActing,
                        severity = Severity.SUCCESS,
                    )
                }
                "returning_delivered" -> {
                    Text(
                        "Привезено на завод. Ожидаем приёмки комплектатором.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    RolePrimaryButton(
                        text = if (state.isActing) "..." else "✅ Принять возврат",
                        onClick = {
                            navController.navigate("tools/return-accept/${t.id}")
                        },
                        enabled = !state.isActing,
                        severity = Severity.SUCCESS,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showRejectDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isActing,
                    ) { Text("Отклонить возврат") }
                }
                "return_rejected" -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Возврат отклонён комплектатором",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            if (!t.returnRejectReason.isNullOrBlank()) {
                                Text(
                                    t.returnRejectReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Куратор должен разобрать ситуацию.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                "returned" -> {
                    Text(
                        "✅ Инструмент успешно возвращён в фонд.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
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

    if (showRejectDialog) {
        // FIX(2026-05-14) BELSI 2.0.1: в зависимости от статуса вызываем forward-reject
        // (для delivered/in_transit) или return-reject (для returning_delivered).
        val currentStatus = state.transfer?.status
        val isReturnReject = currentStatus == "returning_delivered"
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text(if (isReturnReject) "Отказ от приёмки возврата" else "Причина отказа") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = {
                        Text(if (isReturnReject) "Что не так: повреждение, не тот инструмент…"
                             else "Например: брак, не тот инструмент…")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isReturnReject) {
                            viewModel.returnReject(transferId, rejectReason)
                        } else {
                            viewModel.reject(transferId, rejectReason)
                        }
                        showRejectDialog = false
                    },
                    enabled = rejectReason.isNotBlank(),
                ) { Text("Отказать") }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("Отмена") }
            },
        )
    }

    // FIX(2026-05-14) BELSI 2.0.1: bottom-sheet выбора водителя для return-assign
    if (showDriverPicker) {
        DriverPickerBottomSheet(
            onDismiss = { showDriverPicker = false },
            onPicked = { driverId ->
                viewModel.returnAssignDriver(transferId, driverId) {
                    showDriverPicker = false
                }
            },
        )
    }
}

/**
 * FIX(2026-05-14) BELSI 2.0.1: BottomSheet для выбора водителя при назначении
 * на возврат инструмента. Загружает список через ToolTransferRepository.
 * Показывает busy-флаг (есть активная перевозка) и phone для контакта.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverPickerBottomSheet(
    onDismiss: () -> Unit,
    onPicked: (driverId: String) -> Unit,
    pickerVm: DriverPickerViewModel = hiltViewModel(),
) {
    val drivers by pickerVm.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { pickerVm.load() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Назначить водителя",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Свободные водители — без активных перевозок",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            when {
                drivers.isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                drivers.error != null -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                    ) {
                        Text(drivers.error ?: "", modifier = Modifier.padding(12.dp))
                    }
                }
                drivers.items.isEmpty() -> {
                    Text(
                        "Нет доступных водителей.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    drivers.items.forEach { driver ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = { onPicked(driver.id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        driver.name ?: driver.phone ?: "Без имени",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (driver.name != null && driver.phone != null) {
                                        Text(
                                            driver.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (driver.busy) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("занят") },
                                        enabled = false,
                                    )
                                } else {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("свободен") },
                                        enabled = false,
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * FIX(2026-05-14) BELSI 2.0.1: ViewModel для DriverPickerBottomSheet.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class DriverPickerViewModel @javax.inject.Inject constructor(
    private val repo: com.belsi.work.data.repositories.ToolTransferRepository,
) : androidx.lifecycle.ViewModel() {

    data class State(
        val isLoading: Boolean = false,
        val items: List<com.belsi.work.data.remote.dto.tool_transfer.DriverPickItemDto> = emptyList(),
        val error: String? = null,
    )

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(State())
    val state: kotlinx.coroutines.flow.StateFlow<State> = _state

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repo.listDriversForReturn().onSuccess { list ->
                _state.value = State(items = list)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}

@Composable
private fun StatusPillFor(status: String) {
    val (text, severity) = when (status) {
        "pending"             -> "Готовится" to Severity.NEUTRAL
        "in_transit"          -> "В пути" to Severity.WARNING
        "delivered"           -> "Привезли — нужно принять" to Severity.INFO
        "accepted"            -> "На объекте" to Severity.SUCCESS
        "in_use"              -> "В работе" to Severity.PRIMARY
        // FIX(2026-05-14) BELSI 2.0.1: return flow статусы
        "returning_requested" -> "⏪ Запрос на возврат" to Severity.WARNING
        "returning"           -> "⏪ Назначен водитель" to Severity.WARNING
        "returning_in_transit"-> "⏪ Везут на завод" to Severity.WARNING
        "returning_delivered" -> "⏪ На заводе — принять" to Severity.INFO
        "return_rejected"     -> "⚠ Возврат отклонён" to Severity.ERROR
        "returned"            -> "✅ Возвращён" to Severity.SUCCESS
        "cancelled"           -> "Отменён / отказ" to Severity.ERROR
        "lost"                -> "Утерян" to Severity.ERROR
        else                  -> status to Severity.NEUTRAL
    }
    RoleStatusPill(text = text, severity = severity)
}

@Composable
private fun TimelineEntries(t: ToolTransferDto) {
    val items = buildList<Pair<String, String?>> {
        add("Создано" to t.createdAt)
        t.dispatchedAt?.let { add("Отгрузка водителю" to it) }
        t.inTransitAt?.let { add("Везут" to it) }
        t.deliveredAt?.let { add("Привезли" to it) }
        t.acceptedAt?.let { add("Принято" to it) }
        t.inUseAt?.let { add("Взято в работу" to it) }
        // FIX(2026-05-14) BELSI 2.0.1: return flow timeline
        t.returnRequestedAt?.let { add("⏪ Запрос на возврат" to it) }
        t.returnPickupAt?.let { add("⏪ Забрано с объекта" to it) }
        t.returnDeliveredAt?.let { add("⏪ Доставлено на завод" to it) }
        t.returnAcceptedAt?.let { add("✅ Возврат принят" to it) }
        t.returnRejectedAt?.let { add("⚠ Возврат отклонён" to it) }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (label, dt) ->
                Row {
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        dt?.take(19)?.replace("T", " ") ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
