package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.belsi.work.data.models.StockForecastResponse
import com.belsi.work.data.models.StockForecastItem
import com.belsi.work.data.repositories.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-10) BELSI 1.3.0: AI-прогноз исчерпания материалов.
 *
 * Снабженец видит на дашборде список материалов с прогнозом сколько осталось дней,
 * AI-комментарий о причинах, рекомендуемое количество к заказу.
 *
 * Endpoint: GET /production/materials/ai-forecast?facility_id=...
 * Кэш 6 часов.
 */

@HiltViewModel
class StockForecastViewModel @Inject constructor(
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StockForecastState())
    val state: StateFlow<StockForecastState> = _state.asStateFlow()

    fun load(facilityId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            aiRepository.getStockForecast(facilityId).onSuccess { response ->
                _state.value = StockForecastState(loading = false, response = response)
            }.onFailure {
                _state.value = StockForecastState(loading = false, error = it.message)
            }
        }
    }
}

data class StockForecastState(
    val loading: Boolean = false,
    val response: StockForecastResponse? = null,
    val error: String? = null,
)

@Composable
fun StockForecastSection(
    facilityId: String?,
    modifier: Modifier = Modifier,
    viewModel: StockForecastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(facilityId) {
        if (facilityId != null) viewModel.load(facilityId)
    }

    if (facilityId == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "AI-прогноз материалов",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.weight(1f))
                if (!state.loading) {
                    IconButton(
                        onClick = { viewModel.load(facilityId) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Refresh, "Обновить", modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                state.loading -> {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                state.response != null -> {
                    val r = state.response!!

                    // Summary
                    if (r.summaryRu.isNotBlank()) {
                        Text(
                            r.summaryRu,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // Forecast items
                    if (r.forecasts.isEmpty()) {
                        Text(
                            "✅ Все материалы в норме",
                            fontSize = 13.sp,
                            color = Color(0xFF10B981),
                        )
                    } else {
                        r.forecasts.take(5).forEach { item ->
                            ForecastItemRow(item)
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    // Actions
                    if (r.actions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Рекомендации:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        r.actions.take(3).forEach { action ->
                            Text(
                                "• $action",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 1.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "AI processing · XeroCode",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                state.error != null -> {
                    Text(
                        "AI временно недоступен",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastItemRow(item: StockForecastItem) {
    val riskColor = when (item.risk) {
        "critical" -> Color(0xFFEF4444)
        "high" -> Color(0xFFF59E0B)
        "medium" -> Color(0xFFFBBF24)
        else -> Color(0xFF10B981)
    }
    val riskEmoji = when (item.risk) {
        "critical" -> "🔴"
        "high" -> "🟠"
        "medium" -> "🟡"
        else -> "🟢"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(riskColor.copy(alpha = 0.08f))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(riskEmoji, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                item.materialName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${item.daysLeft} дн.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = riskColor,
            )
        }
        Text(
            "Остаток: ${item.currentStock} ${item.unit}  ·  расход ${item.dailyAvgUsage} ${item.unit}/день",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.reasonRu.isNotBlank()) {
            Text(
                item.reasonRu,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (item.recommendedOrderQuantity > 0) {
            Text(
                "💡 Заказать: ${item.recommendedOrderQuantity} ${item.unit}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
