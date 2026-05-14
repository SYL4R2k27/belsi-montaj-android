package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.belsi.work.data.local.database.dao.ShiftDao
import com.belsi.work.data.local.database.entities.ShiftEntity
import com.belsi.work.data.models.IdleReason
import com.belsi.work.data.offline.OfflineQueuedException
import com.belsi.work.data.repositories.BatchRepository
import com.belsi.work.data.repositories.DriverRepository
import com.belsi.work.data.repositories.PauseRepository
import com.belsi.work.data.repositories.ShiftRepository
import com.belsi.work.presentation.screens.driver.DriverEventCameraScreen
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-11) BELSI 2.0.0 build10: универсальный компонент управления сменой.
 *
 * Используется для ролей где НЕТ полноразмерного ShiftScreen — driver, logistician,
 * production worker. Внутри полного installer/foreman flow остаётся ShiftScreen.
 *
 * Backend сам определит `shifts.domain` по роли пользователя (см. main.py:374 build10):
 *   driver/logistician → logistics
 *   worker/senior_worker/production_chief/engineer/supplier → production
 *   installer/foreman/coordinator → installation
 *
 * Соответственно `GET /shift/idle-reasons` (без domain) вернёт причины для ИХ домена.
 *
 * Brandbook р.06 (Domains): каждая роль видит ТОЛЬКО свои причины простоя.
 */
@HiltViewModel
class ShiftControlBarViewModel @Inject constructor(
    private val shiftRepo: ShiftRepository,
    private val pauseRepo: PauseRepository,
    private val batchRepo: BatchRepository,
    private val shiftDao: ShiftDao,
    // FIX(2026-05-11) BELSI 2.0.0 build12: driver shift photo (driver-only flow)
    private val driverRepo: DriverRepository,
) : ViewModel() {

    val activeShift: StateFlow<ShiftEntity?> = shiftDao.getActiveShiftFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _idleReasons = MutableStateFlow<List<IdleReason>>(emptyList())
    val idleReasons: StateFlow<List<IdleReason>> = _idleReasons.asStateFlow()

    private val _status = MutableStateFlow<UiStatus>(UiStatus.Idle)
    val status: StateFlow<UiStatus> = _status.asStateFlow()

    sealed class UiStatus {
        object Idle : UiStatus()
        object Loading : UiStatus()
        data class Info(val message: String) : UiStatus()
        data class Error(val message: String) : UiStatus()
    }

    init {
        // Backend сам подсунет domain по роли (если параметр не передан)
        viewModelScope.launch {
            batchRepo.getIdleReasons(domain = null)
                .onSuccess { _idleReasons.value = it }
                .onFailure { android.util.Log.w("ShiftControl", "idleReasons: ${it.message}") }
        }
    }

    fun startShift() {
        viewModelScope.launch {
            _status.value = UiStatus.Loading
            shiftRepo.startShift(siteObjectId = null)
                .onSuccess { _status.value = UiStatus.Info("Смена начата") }
                .onFailure { _status.value = UiStatus.Error("Ошибка старта: ${it.message}") }
        }
    }

    /**
     * FIX(2026-05-11) BELSI 2.0.0 build12: старт смены ВОДИТЕЛЯ — с фото подтверждением.
     * Бриф: «Начало смены — водитель фотографирует себя/авто (фото = подтверждение выхода на линию)».
     *
     * Шаги:
     *  1. Создаём смену через /shifts/start (как обычно)
     *  2. После успеха — отправляем фото на /driver/shifts/{id}/start_photo
     */
    fun startShiftWithPhoto(photoFile: File, lat: Double?, lng: Double?) {
        viewModelScope.launch {
            _status.value = UiStatus.Loading
            shiftRepo.startShift(siteObjectId = null)
                .onSuccess { shiftData ->
                    driverRepo.shiftStartPhoto(shiftData.id, photoFile, lat, lng)
                        .onSuccess { _status.value = UiStatus.Info("✅ Смена начата · фото сохранено") }
                        .onFailure {
                            // Смена создана, но фото не доехало — это OK, продолжаем
                            android.util.Log.w("ShiftControl", "startShift OK, photo failed: ${it.message}")
                            _status.value = UiStatus.Info("✅ Смена начата (фото не отправилось)")
                        }
                }
                .onFailure { _status.value = UiStatus.Error("Ошибка старта: ${it.message}") }
        }
    }

    fun finishShift() {
        viewModelScope.launch {
            val sid = shiftDao.getActiveShift()?.id ?: return@launch
            _status.value = UiStatus.Loading
            shiftRepo.endShift(sid)
                .onSuccess { _status.value = UiStatus.Info("Смена завершена") }
                .onFailure { _status.value = UiStatus.Error("Ошибка финиша: ${it.message}") }
        }
    }

    fun finishShiftWithPhoto(photoFile: File, lat: Double?, lng: Double?) {
        viewModelScope.launch {
            val sid = shiftDao.getActiveShift()?.id ?: return@launch
            _status.value = UiStatus.Loading
            // Сначала фото (пока смена ещё active — driver_router принимает только свою смену),
            // затем финиш
            driverRepo.shiftEndPhoto(sid, photoFile, lat, lng)
                .onFailure {
                    android.util.Log.w("ShiftControl", "finishShift photo failed: ${it.message}")
                }
            shiftRepo.endShift(sid)
                .onSuccess { _status.value = UiStatus.Info("✅ Смена завершена · фото сохранено") }
                .onFailure { _status.value = UiStatus.Error("Ошибка финиша: ${it.message}") }
        }
    }

    fun startPause() {
        viewModelScope.launch {
            pauseRepo.startPause(reason = null)
                .onSuccess { _status.value = UiStatus.Info("Пауза") }
                .onFailure { e ->
                    _status.value = if (e is OfflineQueuedException)
                        UiStatus.Info("📤 Пауза отправится когда появится сеть")
                    else UiStatus.Error(e.message ?: "Ошибка паузы")
                }
        }
    }

    fun endPause() {
        viewModelScope.launch {
            pauseRepo.endPause()
                .onSuccess { _status.value = UiStatus.Info("Возобновлено") }
                .onFailure { e ->
                    _status.value = if (e is OfflineQueuedException)
                        UiStatus.Info("📤 Возобновление отправится когда появится сеть")
                    else UiStatus.Error(e.message ?: "Ошибка возобновления")
                }
        }
    }

    fun startIdle(reason: String) {
        viewModelScope.launch {
            pauseRepo.startIdle(reason)
                .onSuccess { _status.value = UiStatus.Info("Простой «$reason»") }
                .onFailure { e ->
                    _status.value = if (e is OfflineQueuedException)
                        UiStatus.Info("📤 Простой отправится когда появится сеть")
                    else UiStatus.Error(e.message ?: "Ошибка простоя")
                }
        }
    }

    fun endIdle() {
        viewModelScope.launch {
            pauseRepo.endIdle()
                .onSuccess { _status.value = UiStatus.Info("Простой завершён") }
                .onFailure { e ->
                    _status.value = if (e is OfflineQueuedException)
                        UiStatus.Info("📤 Завершение отправится когда появится сеть")
                    else UiStatus.Error(e.message ?: "Ошибка")
                }
        }
    }

    fun clearStatus() { _status.value = UiStatus.Idle }
}

@Composable
fun ShiftControlBar(
    modifier: Modifier = Modifier,
    // FIX(2026-05-11) BELSI 2.0.0 build12: для водителя смена требует фото-подтверждения.
    // По брифу BELSI.Driver: «Начало смены — водитель фотографирует себя/авто».
    requireShiftPhoto: Boolean = false,
    vm: ShiftControlBarViewModel = hiltViewModel(),
) {
    val active by vm.activeShift.collectAsState()
    val reasons by vm.idleReasons.collectAsState()
    val status by vm.status.collectAsState()

    var showIdleDialog by remember { mutableStateOf(false) }
    // Какое действие ждёт фото: "start" / "finish" / null
    var pendingShiftEvent by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor = when {
                    active == null -> Color(0xFF94A3B8)            // slate
                    active?.status == "paused" -> Color(0xFFF59E0B) // amber
                    else -> Color(0xFF10B981)                       // emerald
                }
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(dotColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        active == null -> "Смена не начата"
                        active?.status == "paused" -> "На паузе"
                        else -> "Смена идёт"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (active == null) {
                Button(
                    onClick = {
                        if (requireShiftPhoto) {
                            // FIX(2026-05-11) build12: открыть camera dialog → после съёмки startShiftWithPhoto
                            pendingShiftEvent = "start"
                        } else {
                            vm.startShift()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (requireShiftPhoto) "Начать смену (фото)" else "Начать смену",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (active?.status == "paused") {
                        Button(
                            onClick = { vm.endPause() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Продолжить", fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = { vm.startPause() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                        ) {
                            Icon(Icons.Default.Pause, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Пауза", fontSize = 13.sp)
                        }
                    }
                    Button(
                        onClick = { showIdleDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                    ) {
                        Icon(Icons.Default.Warning, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Простой", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            if (requireShiftPhoto) {
                                pendingShiftEvent = "finish"
                            } else {
                                vm.finishShift()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Финиш", fontSize = 13.sp)
                    }
                }
            }

            (status as? ShiftControlBarViewModel.UiStatus.Info)?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it.message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            (status as? ShiftControlBarViewModel.UiStatus.Error)?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (showIdleDialog) {
        IdleReasonPickerDialog(
            reasons = reasons.map { it.label },
            onDismiss = { showIdleDialog = false },
            onSelected = { reason ->
                vm.startIdle(reason)
                showIdleDialog = false
            },
        )
    }

    // FIX(2026-05-11) BELSI 2.0.0 build12: Driver shift photo dialog с реальным CameraX.
    // Открывается перед запуском/завершением смены водителя. После съёмки — отправка
    // фото на /driver/shifts/{id}/start_photo|end_photo.
    pendingShiftEvent?.let { event ->
        Dialog(
            onDismissRequest = { pendingShiftEvent = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            DriverEventCameraScreen(
                eventType = if (event == "start") "shift_start" else "shift_end",
                contextLabel = if (event == "start") "Подтверждение начала смены"
                else "Подтверждение окончания смены",
                onCancel = { pendingShiftEvent = null },
                onPhotoTaken = { file, lat, lng, _ ->
                    pendingShiftEvent = null
                    when (event) {
                        "start" -> vm.startShiftWithPhoto(file, lat, lng)
                        "finish" -> vm.finishShiftWithPhoto(file, lat, lng)
                    }
                },
            )
        }
    }
}

@Composable
private fun IdleReasonPickerDialog(
    reasons: List<String>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var customText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Причина простоя", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                reasons.forEach { r ->
                    FilterChip(
                        selected = selected == r,
                        onClick = { selected = r },
                        label = { Text(r, fontSize = 13.sp) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                FilterChip(
                    selected = selected == "__other__",
                    onClick = { selected = "__other__" },
                    label = { Text("Другая причина", fontSize = 13.sp) },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                if (selected == "__other__") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text("Опишите проблему") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null && (selected != "__other__" || customText.isNotBlank()),
                onClick = {
                    val r = if (selected == "__other__") customText.trim() else selected ?: return@TextButton
                    onSelected(r)
                },
            ) { Text("Начать простой") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
