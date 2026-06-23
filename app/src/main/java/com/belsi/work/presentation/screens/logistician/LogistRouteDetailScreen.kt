package com.belsi.work.presentation.screens.logistician

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.belsi.work.data.remote.dto.driver.DriverPointEventDto
import com.belsi.work.data.remote.dto.driver.RouteFullDto
import com.belsi.work.data.remote.dto.driver.RoutePointWithEventsDto
import com.belsi.work.data.repositories.LogistRepository
import com.belsi.work.utils.NavigationIntents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-11) BELSI 2.0.0 build13: read-only детали маршрута для логиста.
 *
 * Бриф BELSI.Driver «Просмотр маршрутов»:
 *  - Логистик может просматривать детали любого маршрута (read-only режим)
 *  - Видит все события, фото, таймлайн каждой точки
 *
 * Источник: GET /logistician/routes/{id}/full — composite endpoint build13.
 */
@HiltViewModel
class LogistRouteDetailViewModel @Inject constructor(
    private val repo: LogistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(
        val loading: Boolean = false,
        val route: RouteFullDto? = null,
        val error: String? = null,
    )

    fun load(routeId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.routeFull(routeId)
                .onSuccess { _state.value = State(loading = false, route = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogistRouteDetailScreen(
    routeId: String,
    onBack: () -> Unit = {},
    vm: LogistRouteDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(routeId) { vm.load(routeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Маршрут") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    val route = state.route ?: return@TopAppBar
                    val pts = route.points.mapNotNull {
                        val lat = it.latitude ?: return@mapNotNull null
                        val lng = it.longitude ?: return@mapNotNull null
                        lat to lng
                    }
                    if (pts.size >= 2) {
                        IconButton(onClick = {
                            NavigationIntents.openYandexNavigation(context, pts)
                        }) { Icon(Icons.Default.Map, "Открыть в картах") }
                    }
                    route.driverPhone?.let { phone ->
                        IconButton(onClick = { NavigationIntents.makePhoneCall(context, phone) }) {
                            Icon(Icons.Default.Phone, "Позвонить водителю")
                        }
                    }
                },
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

            state.route != null -> {
                val route = state.route!!
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { RouteHeaderCard(route) }
                    item { ProgressCard(route) }

                    items(route.points, key = { it.id }) { point ->
                        PointTimelineCard(point)
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteHeaderCard(route: RouteFullDto) {
    val (statusColor, statusLabel) = when (route.status) {
        "planned" -> Color(0xFF94A3B8) to "⏳ Запланирован"
        "active" -> Color(0xFF10B981) to "🟢 Идёт"
        "completed" -> Color(0xFF0EA5E9) to "✅ Завершён"
        "cancelled" -> Color(0xFFEF4444) to "❌ Отменён"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to route.status
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    route.plannedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Маршрут №${route.id.take(8)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            route.driverName?.let { Text(
                "👤 $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) }
            if (!route.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(route.notes, style = MaterialTheme.typography.bodySmall)
            }
            if (route.startedAt != null || route.completedAt != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    route.startedAt?.let { Text("▶ ${formatTime(it)}", style = MaterialTheme.typography.labelSmall) }
                    route.completedAt?.let { Text("⏹ ${formatTime(it)}", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(route: RouteFullDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ПРОГРЕСС",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${route.completedPoints} / ${route.totalPoints} точек",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { route.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
private fun PointTimelineCard(point: RoutePointWithEventsDto) {
    val (statusColor, statusLabel) = when (point.status) {
        "pending" -> Color(0xFF94A3B8) to "⏳ Ожидает"
        "arrived" -> Color(0xFFFBBF24) to "🟡 Прибыл"
        "delivered" -> Color(0xFF10B981) to "✅ Доставлено"
        "skipped" -> Color(0xFFEF4444) to "⏭ Пропущена"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to point.status
    }
    val (typeEmoji, typeLabel) = when (point.pointType) {
        "pickup" -> "📦↑" to "Забор"
        "delivery" -> "📦↓" to "Доставка"
        "transit" -> "🚛" to "Транзит"
        "return" -> "↩️" to "Возврат"
        else -> "•" to point.pointType
    }
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(statusColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${point.seq}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        point.address,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Row {
                        Text(
                            "$typeEmoji $typeLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        point.scheduledTime?.let {
                            Text(
                                " · ⏰ $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (!point.cargo.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        "Груз: ${point.cargo}",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (!point.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    point.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Кнопка карта для точки если есть координаты
            if (point.latitude != null && point.longitude != null) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        NavigationIntents.openYandexMapPoint(
                            context, point.latitude, point.longitude, point.address,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Map, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Показать на карте", fontSize = 13.sp)
                }
            }

            // Таймлайн событий точки
            if (point.events.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "ТАЙМЛАЙН СОБЫТИЙ · ${point.events.size}",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    point.events.forEach { event ->
                        EventRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: DriverPointEventDto) {
    val (icon, color, label) = when (event.eventType) {
        "arrival" -> Triple(Icons.Default.Login, Color(0xFFFBBF24), "Прибыл")
        "delivery_complete" -> Triple(Icons.Default.CheckCircle, Color(0xFF10B981), "Доставлено")
        "departure" -> Triple(Icons.Default.Logout, Color(0xFF94A3B8), "Отъезд")
        "skip" -> Triple(Icons.Default.SkipNext, Color(0xFFEF4444), "Пропущено")
        "shift_start" -> Triple(Icons.Default.LocalShipping, Color(0xFF10B981), "Старт смены")
        "shift_end" -> Triple(Icons.Default.LocalShipping, Color(0xFF0EA5E9), "Конец смены")
        else -> Triple(Icons.Default.CameraAlt, MaterialTheme.colorScheme.onSurfaceVariant, event.eventType)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = color,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatTime(event.occurredAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (event.latitude != null && event.longitude != null) {
                Text(
                    "📍 %.5f, %.5f%s".format(
                        event.latitude, event.longitude,
                        event.accuracyMeters?.let { " · ±%.0fм".format(it) } ?: "",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (!event.notes.isNullOrBlank()) {
                Text(
                    event.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            event.photoUrl?.let { url ->
                Spacer(Modifier.height(6.dp))
                AsyncImage(
                    model = url,
                    contentDescription = "Фото события",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

private fun formatTime(iso: String): String {
    if (iso.length < 16) return iso
    val date = iso.substring(0, 10)
    val time = iso.substring(11, 16)
    val today = java.time.LocalDate.now().toString()
    return if (date == today) time else "$date $time"
}
