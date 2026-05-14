package com.belsi.work.presentation.screens.logistician

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.belsi.work.presentation.components.role.Severity
import com.belsi.work.presentation.components.role.colors
import com.belsi.work.presentation.screens.driver.LogistHomeViewModel

/**
 * FIX(2026-05-03): Logistician Home (Brandbook · Logistician Board).
 * FIX(2026-05-11) BELSI 2.0.0: данные с backend через LogistHomeViewModel.
 * KPI + список pending заявок + активные маршруты водителей.
 */
@Composable
fun LogisticianHomeScreen(
    onCreateRoute: () -> Unit = {},
    onRequestClick: (String) -> Unit = {},
    onRouteClick: (String) -> Unit = {},
    onBatchesClick: () -> Unit = {},
    // FIX(2026-05-11) BELSI 2.0.0 build12: переход в Driver list (мониторинг всех водителей)
    onDriverListClick: () -> Unit = {},
    topBar: @Composable () -> Unit = {},
    viewModel: LogistHomeViewModel = hiltViewModel(),
) {
    val vmState by viewModel.state.collectAsState()
    // FIX(2026-05-12) build19 P2: убран mock-fallback. Если dashboard null — показываем "—",
    // как принято для отсутствующих данных.
    val pendingReqCount: String = vmState.dashboard?.pendingRequests?.toString() ?: "—"
    val activeRouteCount: String = vmState.dashboard?.activeRoutes?.toString() ?: "—"
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        topBar()
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // FIX(2026-05-11) BELSI 2.0.0 build10: контрол смены логиста.
            // Backend выставит shifts.domain='logistics' автоматически по роли.
            item { com.belsi.work.presentation.components.ShiftControlBar() }

            // KPI
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiCard("Заявок открыто", pendingReqCount, MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                    KpiCard("Маршрутов идёт", activeRouteCount, MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                }
            }

            // FIX(2026-05-11) BELSI 2.0.0 build12: переход к Driver list (мониторинг).
            // Бриф BELSI.Driver: главный экран логиста — список всех водителей.
            item {
                Surface(
                    onClick = onDriverListClick,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🚛", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Водители",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "Мониторинг смен · live-обновление 30 сек",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                        Text("→", fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // FIX(2026-05-05): Pipeline партий — ссылка на BatchListScreen.
            // FIX(2026-05-12) build19 hotfix: оранжевые hardcoded цвета заменены
            // на WARNING-семантику (тёплый акцент готовности к отгрузке).
            item {
                val (warnFg, warnBg) = Severity.WARNING.colors()
                Surface(
                    onClick = onBatchesClick,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp,
                    color = warnBg.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📦", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Партии к отгрузке",
                                fontWeight = FontWeight.Bold,
                                color = warnFg,
                            )
                            Text(
                                "Готовы на фабриках, ждут водителя",
                                fontSize = 12.sp,
                                color = warnFg.copy(alpha = 0.8f),
                            )
                        }
                        Text("→", fontSize = 20.sp, color = warnFg)
                    }
                }
            }

            // FIX(2026-05-12) BELSI 2.0.0 build15: убран DriverMockData fallback
            // (pendingRequests / activeRoutes / fleet). Реальные данные — через
            // соответствующие экраны: DriverList (build12), CreateRoute (build12),
            // RouteDetail (build13). Главный экран остаётся компактным дашбордом
            // с KPI + переходами вместо ленты из 15 мок-карточек.
            item {
                SectionHeader("ДЕЙСТВИЯ", actionText = null, onAction = {})
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = onCreateRoute,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🗺", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Создать маршрут", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Динамические точки + Haversine + Yandex preview",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("→", fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = accent
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAction) {
                Text(actionText, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// FIX(2026-05-12) build19 hotfix: удалены 3 dead composables (RequestRow, ActiveRouteCardCompact,
// DriverFleetRow) — они использовали DriverMockData типы, но не вызывались. Реальные карточки
// заявок/маршрутов/водителей живут в LogistRequestDetailScreen / LogistRouteDetailScreen /
// LogistDriverListScreen.
