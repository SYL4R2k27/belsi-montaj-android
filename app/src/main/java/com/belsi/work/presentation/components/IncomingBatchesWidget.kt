package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.belsi.work.data.models.IncomingBatchDto
import com.belsi.work.data.repositories.BatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) BELSI 2.0.0 build15: виджет «партии едут к нам» для бригадира/координатора.
 *
 * Источник: GET /production/batches/incoming — backend фильтрует по объектам где user
 * в memberships (для не-куратора), возвращает партии в статусах ready_to_ship/in_route/delivered.
 *
 * Действия:
 *  - in_route → «Принять» (POST /receive → status='delivered' + push)
 *  - delivered → «Закрыть монтаж» (POST /install → status='installed' + push)
 *  - ready_to_ship → информативно (на фабрике, ещё не выехала)
 *
 * Polling 30 сек — синхронно с другими live-обновлениями (build10 pattern).
 */
@HiltViewModel
class IncomingBatchesViewModel @Inject constructor(
    private val repo: BatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(
        val loading: Boolean = false,
        val batches: List<IncomingBatchDto> = emptyList(),
        val error: String? = null,
        val busy: Set<String> = emptySet(),
    )

    init {
        load()
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                load(silent = true)
            }
        }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.value = _state.value.copy(loading = true, error = null)
            repo.getIncomingBatches()
                .onSuccess { _state.value = _state.value.copy(loading = false, batches = it, error = null) }
                .onFailure {
                    if (!silent) _state.value = _state.value.copy(loading = false, error = it.message)
                }
        }
    }

    fun receive(batchId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = _state.value.busy + batchId)
            repo.receiveBatch(batchId)
                .onSuccess { load() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            _state.value = _state.value.copy(busy = _state.value.busy - batchId)
        }
    }

    fun install(batchId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = _state.value.busy + batchId)
            repo.installBatch(batchId)
                .onSuccess { load() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            _state.value = _state.value.copy(busy = _state.value.busy - batchId)
        }
    }
}

@Composable
fun IncomingBatchesWidget(
    modifier: Modifier = Modifier,
    onBatchClick: ((batchId: String) -> Unit)? = null,
    vm: IncomingBatchesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    if (state.batches.isEmpty() && !state.loading) return  // скрываем виджет если ничего нет

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalShipping, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("К нам едут", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            "${state.batches.size}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            state.batches.forEach { batch ->
                IncomingBatchRow(
                    batch = batch,
                    busy = batch.id in state.busy,
                    onReceive = { vm.receive(batch.id) },
                    onInstall = { vm.install(batch.id) },
                    onClick = { onBatchClick?.invoke(batch.id) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun IncomingBatchRow(
    batch: IncomingBatchDto,
    busy: Boolean,
    onReceive: () -> Unit,
    onInstall: () -> Unit,
    onClick: () -> Unit,
) {
    val (statusColor, statusLabel) = when (batch.status) {
        "ready_to_ship" -> Color(0xFF94A3B8) to "📦 готова"
        "in_route" -> Color(0xFFFBBF24) to "🚛 в пути"
        "delivered" -> Color(0xFF10B981) to "📍 доставлена"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to batch.status
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(16.dp), tint = statusColor)
                Spacer(Modifier.width(6.dp))
                Text(batch.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("${batch.itemCount} шт", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "$statusLabel${batch.targetObjectName?.let { " · $it" } ?: ""}",
                fontSize = 11.sp,
                color = statusColor,
                modifier = Modifier.padding(top = 2.dp, start = 22.dp),
            )
            // Кнопки действий
            when (batch.status) {
                "in_route" -> {
                    Button(
                        onClick = onReceive,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        else {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Принять", fontSize = 12.sp)
                        }
                    }
                }
                "delivered" -> {
                    Button(
                        onClick = onInstall,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        else {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Закрыть монтаж", fontSize = 12.sp)
                        }
                    }
                }
                else -> {} // ready_to_ship — пока ничего не делаем (партия на фабрике)
            }
        }
    }
}
