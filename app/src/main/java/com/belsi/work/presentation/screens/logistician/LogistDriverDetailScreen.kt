package com.belsi.work.presentation.screens.logistician

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.belsi.work.data.remote.dto.driver.DriverDetailDto
import com.belsi.work.data.remote.dto.driver.DriverRouteSummaryDto
import com.belsi.work.data.remote.dto.driver.DriverShiftDto
import com.belsi.work.data.repositories.LogistRepository
import com.belsi.work.utils.NavigationIntents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-11) BELSI 2.0.0 build13: полная переделка экрана деталей водителя.
 *
 * Бриф BELSI.Driver «Детали водителя (DriverDetail)»:
 *  - Активная смена с временем начала и фото
 *  - Список маршрутов водителя (все статусы)
 *  - История смен
 *  - FAB-кнопка "Создать маршрут"
 *
 * Источник: GET /logistician/drivers/{id}/detail — composite endpoint build13.
 */
@HiltViewModel
class LogistDriverDetailViewModel @Inject constructor(
    private val repo: LogistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(
        val loading: Boolean = false,
        val detail: DriverDetailDto? = null,
        val error: String? = null,
    )

    fun load(driverId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.driverDetail(driverId)
                .onSuccess { _state.value = State(loading = false, detail = it) }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogistDriverDetailScreen(
    driverId: String,
    onCreateRoute: () -> Unit,
    onRouteClick: (routeId: String) -> Unit = {},
    vm: LogistDriverDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(driverId) { vm.load(driverId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.driver?.name ?: "Водитель") },
                actions = {
                    state.detail?.driver?.phone?.let { phone ->
                        IconButton(onClick = { NavigationIntents.makePhoneCall(context, phone) }) {
                            Icon(Icons.Default.Phone, "Позвонить")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRoute,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Создать маршрут") },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.error ?: "Ошибка", color = MaterialTheme.colorScheme.error)
            }

            state.detail != null -> {
                val detail = state.detail!!
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { DriverHeaderCard(detail) }
                    item { ActiveShiftCard(detail.activeShift) }

                    item { SectionTitle("МАРШРУТЫ · ${detail.routes.size}") }
                    if (detail.routes.isEmpty()) {
                        item { EmptyHint("Маршрутов за последние 14 дней нет") }
                    } else {
                        items(detail.routes, key = { it.id }) { route ->
                            RouteRow(route, onClick = { onRouteClick(route.id) })
                        }
                    }

                    item { SectionTitle("ИСТОРИЯ СМЕН · ${detail.shiftHistory.size}") }
                    if (detail.shiftHistory.isEmpty()) {
                        item { EmptyHint("Завершённых смен за 14 дней нет") }
                    } else {
                        items(detail.shiftHistory, key = { it.id }) { sh ->
                            HistoryShiftRow(sh)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverHeaderCard(d: DriverDetailDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    d.driver.name.take(2).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(d.driver.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    d.driver.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (d.driver.online) Color(0xFF10B981).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            if (d.driver.online) "🟢 Онлайн" else "⚫️ Не на связи",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (d.driver.online) Color(0xFF065F46)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveShiftCard(shift: DriverShiftDto?) {
    if (shift == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "АКТИВНАЯ СМЕНА",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("⚪️ Не на смене", fontWeight = FontWeight.SemiBold)
                Text(
                    "Водитель не открыл смену сегодня",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF10B981))
                Spacer(Modifier.width(6.dp))
                Text(
                    "СМЕНА ИДЁТ",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 0.6.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color(0xFF065F46),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "с ${formatShortTime(shift.startAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF065F46),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                formatDuration(shift.totalSeconds),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                shift.pauseSeconds?.takeIf { it > 0 }?.let {
                    Text("⏸ ${formatDuration(it)}", style = MaterialTheme.typography.labelSmall)
                }
                shift.idleSeconds?.takeIf { it > 0 }?.let {
                    Text("⚠ ${formatDuration(it)}", style = MaterialTheme.typography.labelSmall)
                }
            }

            shift.shiftStartPhotoUrl?.let { url ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "Фото при старте смены:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                AsyncImage(
                    model = url,
                    contentDescription = "Фото начала смены",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                shift.shiftStartLat?.let { lat ->
                    shift.shiftStartLng?.let { lng ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "📍 %.5f, %.5f".format(lat, lng),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RouteRow(route: DriverRouteSummaryDto, onClick: () -> Unit) {
    val (statusColor, statusLabel) = when (route.status) {
        "planned" -> Color(0xFF94A3B8) to "⏳ Запланирован"
        "active" -> Color(0xFF10B981) to "🟢 Идёт"
        "completed" -> Color(0xFF0EA5E9) to "✅ Завершён"
        "cancelled" -> Color(0xFFEF4444) to "❌ Отменён"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to route.status
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        onClick = onClick,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Маршрут от ${route.plannedDate}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(modifier = Modifier.padding(top = 2.dp)) {
                    Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor)
                    Text(
                        " · ${route.completedPoints}/${route.totalPoints} точек",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { route.progress },
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.2f),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                null,
                modifier = Modifier.padding(start = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryShiftRow(shift: DriverShiftDto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AccessTime,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${formatShortDate(shift.startAt)} · ${formatDuration(shift.totalSeconds)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                val extras = listOfNotNull(
                    shift.pauseSeconds?.takeIf { it > 0 }?.let { "пауза ${formatDuration(it)}" },
                    shift.idleSeconds?.takeIf { it > 0 }?.let { "простой ${formatDuration(it)}" },
                    shift.idleReason?.takeIf { it.startsWith("auto-closed") }?.let { "автозакрыта" },
                ).joinToString(" · ")
                if (extras.isNotBlank()) {
                    Text(
                        extras,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            shift.shiftStartPhotoUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

// ─── helpers ───
private fun formatDuration(seconds: Long?): String {
    val s = seconds ?: return "—"
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "${h}ч ${m}мин" else "${m}мин"
}
private fun formatShortTime(iso: String): String =
    iso.takeIf { it.length >= 16 }?.substring(11, 16) ?: "—"
private fun formatShortDate(iso: String): String {
    if (iso.length < 16) return iso.take(10)
    val date = iso.substring(0, 10)
    val time = iso.substring(11, 16)
    return "$date $time"
}
