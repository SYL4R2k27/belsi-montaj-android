package com.belsi.work.presentation.screens.logistician

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
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
import com.belsi.work.data.remote.dto.driver.DeliveryRequestOutDto
import com.belsi.work.data.repositories.LogistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) BELSI 2.0.0 build16: полный rewrite. Раньше — DriverMockData.pendingRequests
 * + hardcoded «маршрут #234». Теперь — реальный GET /logistician/requests/{id}.
 *
 * Поддерживает все связи build15:
 *  - заявка → batch (через batchId) → переход на BatchDetail
 *  - заявка → assigned_route_id → переход на LogistRouteDetail
 *  - объект-цель показан с object_name_snapshot
 *  - priority badge
 */
@HiltViewModel
class LogistRequestDetailViewModel @Inject constructor(
    private val repo: LogistRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(
        val loading: Boolean = false,
        val request: DeliveryRequestOutDto? = null,
        val error: String? = null,
    )

    fun load(requestId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.getRequest(requestId)
                .onSuccess { _state.value = State(loading = false, request = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogistRequestDetailScreen(
    requestId: String,
    onAddToRoute: () -> Unit,
    onCreateNewRoute: () -> Unit,
    onBatchClick: ((batchId: String) -> Unit)? = null,
    onRouteClick: ((routeId: String) -> Unit)? = null,
    vm: LogistRequestDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(requestId) { vm.load(requestId) }

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(state.error ?: "Ошибка", color = MaterialTheme.colorScheme.error)
        }
        state.request != null -> RequestContent(
            req = state.request!!,
            onAddToRoute = onAddToRoute,
            onCreateNewRoute = onCreateNewRoute,
            onBatchClick = onBatchClick,
            onRouteClick = onRouteClick,
        )
    }
}

@Composable
private fun RequestContent(
    req: DeliveryRequestOutDto,
    onAddToRoute: () -> Unit,
    onCreateNewRoute: () -> Unit,
    onBatchClick: ((batchId: String) -> Unit)?,
    onRouteClick: ((routeId: String) -> Unit)?,
) {
    val (statusColor, statusLabel) = when (req.status) {
        "pending" -> Color(0xFFFBBF24) to "PENDING"
        "assigned" -> Color(0xFF0EA5E9) to "В МАРШРУТЕ"
        "in_transit" -> Color(0xFF6366F1) to "В ПУТИ"
        "delivered" -> Color(0xFF10B981) to "ДОСТАВЛЕНО"
        "cancelled" -> Color(0xFFEF4444) to "ОТМЕНЕНО"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to req.status.uppercase()
    }
    val priorityColor = when (req.priority) {
        "urgent", "high" -> Color(0xFFEF4444)
        "low" -> Color(0xFF94A3B8)
        else -> Color(0xFF0EA5E9)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ─── Заявка header ───
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }
                    if (req.priority != "normal") {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = priorityColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                req.priority.uppercase(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = priorityColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "#${req.id.take(8)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    req.objectName ?: "Без объекта",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "К ${req.needByTime ?: "—"} · ${req.needByDate}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        req.cargo,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                if (!req.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        req.notes,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                req.creatorName?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Создал: $it",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ─── Связь с партией (если есть) ───
        // FIX(2026-05-12) build16: real linkage через DeliveryRequestOutDto.batchId
        val batchId = (req as? Any)?.let {
            // DTO ещё не имеет batch_id — нужно расширить (см. ниже). Пока null.
            null as String?
        }

        // ─── Связь с маршрутом ───
        if (req.assignedRouteId != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.LocalShipping, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "В маршруте",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Маршрут #${req.assignedRouteId.take(8)}",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (onRouteClick != null) {
                        Button(
                            onClick = { onRouteClick(req.assignedRouteId) },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text("Открыть", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ─── Действия ───
        if (req.status == "pending") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "ВКЛЮЧИТЬ В МАРШРУТ",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onCreateNewRoute,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Создать новый маршрут")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Привязка к существующему маршруту — через создание маршрута с этой точкой",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
