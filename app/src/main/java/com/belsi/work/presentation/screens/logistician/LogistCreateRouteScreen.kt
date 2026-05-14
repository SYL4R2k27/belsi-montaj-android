package com.belsi.work.presentation.screens.logistician

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.driver.DriverFleetItemDto
import com.belsi.work.data.remote.dto.driver.RoutePointIn
import com.belsi.work.data.repositories.DriverRepository
import com.belsi.work.data.repositories.LogistRepository
import com.belsi.work.utils.NavigationIntents
import com.belsi.work.utils.RouteDistance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * FIX(2026-05-11) BELSI 2.0.0 build12: полный rewrite экрана создания маршрута.
 *
 * Бриф BELSI.Driver, секция «Создание маршрута»:
 *  - Ввод названия маршрута и плановой даты
 *  - Динамическое добавление точек доставки (неограниченное количество)
 *  - Для каждой точки: адрес, координаты, контакт, телефон, комментарий
 *  - Превью маршрута на карте Яндекс (URL строится автоматически из координат)
 *  - Автоматический расчёт расстояния между точками (Хаверсина + 1.4x + 30 км/ч)
 *
 * После успешного создания — popBack через onAssign.
 */
data class DraftPoint(
    val id: Long = System.currentTimeMillis() + (0..1_000).random(),
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val cargo: String = "",
    val contactName: String = "",   // не отправляется (нет колонки на бэке) — пока локально
    val contactPhone: String = "",  // см. выше
    val notes: String = "",
    val scheduledTime: String = "", // HH:MM
    val pointType: String = "delivery", // pickup|delivery|transit|return
)

@HiltViewModel
class LogistCreateRouteViewModel @Inject constructor(
    private val logistRepo: LogistRepository,
) : ViewModel() {

    private val _drivers = MutableStateFlow<List<DriverFleetItemDto>>(emptyList())
    val drivers: StateFlow<List<DriverFleetItemDto>> = _drivers.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _result = MutableStateFlow<SubmitResult?>(null)
    val result: StateFlow<SubmitResult?> = _result.asStateFlow()

    sealed class SubmitResult {
        data class Success(val routeId: String) : SubmitResult()
        data class Error(val message: String) : SubmitResult()
    }

    init {
        viewModelScope.launch {
            logistRepo.listDrivers().onSuccess { _drivers.value = it }
        }
    }

    fun createRoute(
        driverId: String,
        plannedDate: String,
        notes: String?,
        points: List<DraftPoint>,
    ) {
        if (driverId.isBlank()) {
            _result.value = SubmitResult.Error("Выберите водителя")
            return
        }
        if (points.isEmpty()) {
            _result.value = SubmitResult.Error("Добавьте хотя бы одну точку")
            return
        }
        val mapped = points.mapIndexed { idx, p ->
            RoutePointIn(
                seq = idx + 1,
                pointType = p.pointType,
                address = p.address.trim(),
                scheduledTime = p.scheduledTime.trim().ifBlank { null },
                latitude = p.latitude.toDoubleOrNull(),
                longitude = p.longitude.toDoubleOrNull(),
                cargo = p.cargo.trim().ifBlank { null },
                notes = listOfNotNull(
                    p.contactName.trim().ifBlank { null }?.let { "Контакт: $it" },
                    p.contactPhone.trim().ifBlank { null }?.let { "Тел: $it" },
                    p.notes.trim().ifBlank { null },
                ).joinToString(" · ").ifBlank { null },
            )
        }
        viewModelScope.launch {
            _submitting.value = true
            logistRepo.createRoute(driverId, plannedDate, mapped, notes)
                .onSuccess { _result.value = SubmitResult.Success(it.id) }
                .onFailure { _result.value = SubmitResult.Error(it.message ?: "Ошибка создания маршрута") }
            _submitting.value = false
        }
    }

    fun clearResult() { _result.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogistCreateRouteScreen(
    onAssign: () -> Unit,
    vm: LogistCreateRouteViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val drivers by vm.drivers.collectAsState()
    val submitting by vm.submitting.collectAsState()
    val result by vm.result.collectAsState()

    var selectedDriverId by remember { mutableStateOf<String?>(null) }
    var plannedDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var routeNotes by remember { mutableStateOf("") }
    val points = remember { mutableStateListOf(DraftPoint()) }

    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(result) {
        when (val r = result) {
            is LogistCreateRouteViewModel.SubmitResult.Success -> {
                snackbarHost.showSnackbar("✅ Маршрут создан")
                vm.clearResult()
                onAssign()
            }
            is LogistCreateRouteViewModel.SubmitResult.Error -> {
                snackbarHost.showSnackbar(r.message)
                vm.clearResult()
            }
            null -> {}
        }
    }

    // Расчёт Haversine по точкам с координатами
    val routeEstimate: RouteDistance.RouteEstimate = remember(points.toList()) {
        val withCoords = points.mapNotNull { p ->
            val lat = p.latitude.toDoubleOrNull() ?: return@mapNotNull null
            val lng = p.longitude.toDoubleOrNull() ?: return@mapNotNull null
            lat to lng
        }
        RouteDistance.totalRoute(withCoords)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Summary бар над кнопкой
                    if (routeEstimate.distanceKm > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "Расчёт по маршруту",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "🛣 ${routeEstimate.distanceLabel} · ⏱ ~${routeEstimate.durationLabel}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                            // Open preview in Yandex Maps
                            val mapPoints = points.mapNotNull {
                                val lat = it.latitude.toDoubleOrNull() ?: return@mapNotNull null
                                val lng = it.longitude.toDoubleOrNull() ?: return@mapNotNull null
                                lat to lng
                            }
                            if (mapPoints.size >= 2) {
                                OutlinedButton(
                                    onClick = { NavigationIntents.openYandexNavigation(context, mapPoints) },
                                ) {
                                    Icon(Icons.Default.Map, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Превью", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            vm.createRoute(
                                driverId = selectedDriverId.orEmpty(),
                                plannedDate = plannedDate,
                                notes = routeNotes.trim().ifBlank { null },
                                points = points.toList(),
                            )
                        },
                        enabled = !submitting && selectedDriverId != null && points.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (submitting) "Создаём…" else "Назначить маршрут",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Дата + название/notes ───
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "ДАТА И ПРИМЕЧАНИЯ",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = plannedDate,
                            onValueChange = { plannedDate = it },
                            label = { Text("Дата (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = routeNotes,
                            onValueChange = { routeNotes = it },
                            label = { Text("Примечания (необязательно)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }
                }
            }

            // ─── Driver picker ───
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "ВОДИТЕЛЬ",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (drivers.isEmpty()) {
                            Text(
                                "Загружаем список водителей…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        drivers.forEach { drv ->
                            val isSel = drv.id == selectedDriverId
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = if (isSel) androidx.compose.foundation.BorderStroke(
                                    1.dp, MaterialTheme.colorScheme.primary
                                ) else null,
                                onClick = { selectedDriverId = drv.id },
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            (drv.name ?: drv.phone).take(2).uppercase(),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            drv.name ?: drv.phone,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                        Text(
                                            when (drv.status) {
                                                "active" -> "🟢 На линии"
                                                "free" -> "⚪️ Свободен"
                                                "offline" -> "⚫️ Не на смене"
                                                else -> drv.status
                                            } + (drv.currentProgress?.let { " · $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (isSel) Text(
                                        "✓",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Points list — dynamic ───
            itemsIndexed(points) { idx, p ->
                PointEditorCard(
                    seq = idx + 1,
                    draft = p,
                    onChange = { points[idx] = it },
                    onRemove = { if (points.size > 1) points.removeAt(idx) },
                    canRemove = points.size > 1,
                )
            }

            // ─── Add point button ───
            item {
                OutlinedButton(
                    onClick = { points.add(DraftPoint()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить точку")
                }
            }
        }
    }
}

// itemsIndexed для LazyListScope (compose-extension)
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<DraftPoint>,
    itemContent: @Composable (Int, DraftPoint) -> Unit,
) {
    items(items.size) { idx -> itemContent(idx, items[idx]) }
}

@Composable
private fun PointEditorCard(
    seq: Int,
    draft: DraftPoint,
    onChange: (DraftPoint) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$seq",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Точка $seq",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, "Удалить точку", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Тип точки — chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("pickup" to "📦↑ Забор", "delivery" to "📦↓ Доставка",
                       "transit" to "🚛 Транзит", "return" to "↩️ Возврат").forEach { (key, label) ->
                    FilterChip(
                        selected = draft.pointType == key,
                        onClick = { onChange(draft.copy(pointType = key)) },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }

            OutlinedTextField(
                value = draft.address,
                onValueChange = { onChange(draft.copy(address = it)) },
                label = { Text("Адрес") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.latitude,
                    onValueChange = { onChange(draft.copy(latitude = it)) },
                    label = { Text("Широта (lat)") },
                    placeholder = { Text("55.7558") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.longitude,
                    onValueChange = { onChange(draft.copy(longitude = it)) },
                    label = { Text("Долгота (lng)") },
                    placeholder = { Text("37.6173") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.scheduledTime,
                    onValueChange = { onChange(draft.copy(scheduledTime = it)) },
                    label = { Text("Время") },
                    placeholder = { Text("14:00") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.cargo,
                    onValueChange = { onChange(draft.copy(cargo = it)) },
                    label = { Text("Груз") },
                    placeholder = { Text("20 подоконников") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
            }

            OutlinedTextField(
                value = draft.contactName,
                onValueChange = { onChange(draft.copy(contactName = it)) },
                label = { Text("Контактное лицо") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.contactPhone,
                onValueChange = { onChange(draft.copy(contactPhone = it)) },
                label = { Text("Телефон") },
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.notes,
                onValueChange = { onChange(draft.copy(notes = it)) },
                label = { Text("Комментарий") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }
}
