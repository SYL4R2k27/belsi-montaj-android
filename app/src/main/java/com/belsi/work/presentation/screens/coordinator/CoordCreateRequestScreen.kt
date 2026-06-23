package com.belsi.work.presentation.screens.coordinator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.IncomingBatchDto
import com.belsi.work.data.remote.api.BatchApi
import com.belsi.work.data.remote.dto.driver.DeliveryRequestIn
import com.belsi.work.data.repositories.CoordinatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * FIX(2026-05-12) build17 P0: реальный экран координатора для создания заявки на доставку.
 *
 * Раньше был мок-плейграунд с хардкод-данными ("12 окон ПВХ 1500x1200", "📍 Коломенская набережная 16").
 * Теперь:
 *   - подгружается реальный объект координатора через CoordinatorRepository.getSite()
 *   - подгружается список партий объекта через BatchApi.getIncomingBatches()
 *   - реальный POST /logistician/requests через CoordinatorRepository.createDeliveryRequest()
 *   - поля priority (low/normal/high/urgent), need_by_date, need_by_time, batch_id, notes
 */
@HiltViewModel
class CoordCreateRequestViewModel @Inject constructor(
    private val coordinatorRepo: CoordinatorRepository,
    private val batchApi: BatchApi,
) : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val submitting: Boolean = false,
        val siteId: String? = null,
        val siteName: String? = null,
        val siteAddress: String? = null,
        val availableBatches: List<IncomingBatchDto> = emptyList(),
        val error: String? = null,
        val success: Boolean = false,
        val createdRequestId: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            coordinatorRepo.getSite().fold(
                onSuccess = { site ->
                    _state.update {
                        it.copy(
                            loading = false,
                            siteId = site?.id,
                            siteName = site?.name,
                            siteAddress = site?.address,
                        )
                    }
                    // Параллельно тянем партии (могут быть нужны при создании заявки).
                    loadBatches()
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Ошибка загрузки объекта") }
                }
            )
        }
    }

    private fun loadBatches() {
        viewModelScope.launch {
            try {
                val resp = batchApi.getIncomingBatches()
                if (resp.isSuccessful) {
                    _state.update { it.copy(availableBatches = resp.body() ?: emptyList()) }
                }
            } catch (e: Exception) {
                // ignore — батчей может не быть
            }
        }
    }

    fun submit(
        cargo: String,
        priority: String,
        needByDate: String?,
        needByTime: String?,
        batchId: String?,
        notes: String?,
    ) {
        val site = _state.value.siteId
        if (site == null) {
            _state.update { it.copy(error = "Не задан объект координатора") }
            return
        }
        if (cargo.isBlank()) {
            _state.update { it.copy(error = "Заполните, что нужно привезти") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val req = DeliveryRequestIn(
                siteObjectId = site,
                objectName = _state.value.siteName,
                cargo = cargo.trim(),
                needByTime = needByTime?.takeIf { it.isNotBlank() },
                needByDate = needByDate?.takeIf { it.isNotBlank() },
                priority = priority,
                notes = notes?.takeIf { it.isNotBlank() },
                batchId = batchId?.takeIf { it.isNotBlank() },
            )
            coordinatorRepo.createDeliveryRequest(req).fold(
                onSuccess = { dto ->
                    _state.update {
                        it.copy(
                            submitting = false,
                            success = true,
                            createdRequestId = dto.id,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(submitting = false, error = e.message ?: "Ошибка отправки") }
                }
            )
        }
    }

    fun clearSuccess() {
        _state.update { it.copy(success = false, createdRequestId = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordCreateRequestScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    initialBatchId: String? = null,
    vm: CoordCreateRequestViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.success) {
        if (state.success) {
            onCreated()
            vm.clearSuccess()
        }
    }

    var cargo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var priorityIdx by remember { mutableStateOf(1) } // 0=low, 1=normal, 2=high, 3=urgent
    val priorities = listOf(
        "low" to "Низкий",
        "normal" to "Обычный",
        "high" to "Высокий",
        "urgent" to "Срочный",
    )
    var batchExpanded by remember { mutableStateOf(false) }
    var selectedBatchId by remember(initialBatchId) { mutableStateOf(initialBatchId) }

    // Дата и время по умолчанию — сегодня, 14:00.
    val today = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    var needByDate by remember { mutableStateOf(today) }
    var needByTime by remember { mutableStateOf("14:00") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заявка на доставку") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Объект ───
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "ОБЪЕКТ",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (state.siteId == null) {
                        Text(
                            "Объект координатора не назначен",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            state.siteName ?: "—",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        state.siteAddress?.let {
                            Text(
                                "📍 $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ─── Привязка к партии (опционально) ───
            if (state.availableBatches.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "ПАРТИЯ (опционально)",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = batchExpanded,
                            onExpandedChange = { batchExpanded = !batchExpanded }
                        ) {
                            val selectedTitle = state.availableBatches.firstOrNull { it.id == selectedBatchId }?.title
                            OutlinedTextField(
                                value = selectedTitle ?: "Без привязки",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = batchExpanded) },
                            )
                            ExposedDropdownMenu(
                                expanded = batchExpanded,
                                onDismissRequest = { batchExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Без привязки") },
                                    onClick = {
                                        selectedBatchId = null
                                        batchExpanded = false
                                    }
                                )
                                state.availableBatches.forEach { b ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(b.title, fontWeight = FontWeight.Medium)
                                                Text(
                                                    "Статус: ${b.status}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedBatchId = b.id
                                            batchExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Груз ───
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "ЧТО НУЖНО ПРИВЕЗТИ *",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cargo,
                        onValueChange = { cargo = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                        placeholder = { Text("Например: 12 окон ПВХ 1500x1200, 3 поддона профиля") },
                        shape = RoundedCornerShape(10.dp),
                        minLines = 3,
                    )
                }
            }

            // ─── Дата и время ───
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "КОГДА ПРИВЕЗТИ",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = needByDate,
                            onValueChange = { needByDate = it },
                            label = { Text("Дата (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = needByTime,
                            onValueChange = { needByTime = it },
                            label = { Text("Время (HH:MM)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                    }
                }
            }

            // ─── Приоритет ───
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "ПРИОРИТЕТ",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        priorities.forEachIndexed { idx, (_, label) ->
                            FilterChip(
                                selected = priorityIdx == idx,
                                onClick = { priorityIdx = idx },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ─── Заметки ───
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "ЗАМЕТКИ ДЛЯ ЛОГИСТА",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Например: погрузить осторожно, водитель должен позвонить") },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "💡 Логист увидит вашу заявку и подберёт водителя. Вам придёт push, когда маршрут будет назначен.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            state.error?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    vm.submit(
                        cargo = cargo,
                        priority = priorities[priorityIdx].first,
                        needByDate = needByDate,
                        needByTime = needByTime,
                        batchId = selectedBatchId,
                        notes = notes,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                enabled = !state.submitting && cargo.isNotBlank() && state.siteId != null,
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        "Создать заявку",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
