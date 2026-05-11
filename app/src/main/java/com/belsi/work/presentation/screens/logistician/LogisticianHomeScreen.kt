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
import com.belsi.work.presentation.screens.driver.DriverMockData

/**
 * FIX(2026-05-03): Logistician Home (Brandbook · Logistician Board, Этап A — мок-данные).
 * KPI + список pending заявок + активные маршруты водителей.
 */
@Composable
fun LogisticianHomeScreen(
    onCreateRoute: () -> Unit = {},
    onRequestClick: (String) -> Unit = {},
    onRouteClick: (String) -> Unit = {},
    onBatchesClick: () -> Unit = {},  // FIX(2026-05-05): Pipeline партий
    topBar: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        topBar()
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // KPI
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiCard("Заявок открыто", DriverMockData.pendingRequests.size.toString(), MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                    KpiCard("Маршрутов идёт", DriverMockData.activeRoutes.size.toString(), MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                }
            }

            // FIX(2026-05-05): Pipeline партий — ссылка на BatchListScreen
            // с фильтром ready_to_ship и in_route (применит сервер по роли).
            item {
                Surface(
                    onClick = onBatchesClick,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp,
                    color = Color(0xFFFEF3C7),
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
                                color = Color(0xFF92400E),
                            )
                            Text(
                                "Готовы на фабриках, ждут водителя",
                                fontSize = 12.sp,
                                color = Color(0xFFB45309),
                            )
                        }
                        Text("→", fontSize = 20.sp, color = Color(0xFF92400E))
                    }
                }
            }

            // Pending requests
            item {
                SectionHeader("ОТКРЫТЫЕ ЗАЯВКИ", actionText = "Создать маршрут", onAction = onCreateRoute)
            }
            items(DriverMockData.pendingRequests, key = { it.id }) { req ->
                RequestRow(req, onClick = { onRequestClick(req.id) })
            }

            // Active routes
            item { SectionHeader("АКТИВНЫЕ МАРШРУТЫ") }
            items(DriverMockData.activeRoutes, key = { it.id }) { route ->
                ActiveRouteCardCompact(route, onClick = { onRouteClick(route.id) })
            }

            // Drivers fleet
            item { SectionHeader("ВОДИТЕЛИ") }
            items(DriverMockData.fleet, key = { it.id }) { driver ->
                DriverFleetRow(driver)
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

@Composable
private fun RequestRow(req: DriverMockData.DeliveryRequest, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("📋", style = MaterialTheme.typography.titleMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${req.objectName} · ${req.needBy}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    "${req.cargo} · от ${req.createdBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label = { Text("PENDING", style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

@Composable
private fun ActiveRouteCardCompact(route: DriverMockData.Route, onClick: () -> Unit) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.background(gradient).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = { Text("МАРШРУТ #${route.id.takeLast(3)}", style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        labelColor = Color.White
                    )
                )
                Spacer(Modifier.weight(1f))
                Text(route.driverName, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(8.dp))
            val done = route.points.count { it.status == DriverMockData.PointStatus.DELIVERED }
            Text(
                "$done / ${route.points.size} точек · ${(route.progress * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            LinearProgressIndicator(
                progress = { route.progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(99.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
            route.nextPoint?.let { p ->
                Text(
                    "Сейчас: ${p.type.emoji} ${p.address}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DriverFleetRow(driver: DriverMockData.DriverFleetItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(
                        when (driver.status) {
                            DriverMockData.FleetStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                            DriverMockData.FleetStatus.FREE -> MaterialTheme.colorScheme.tertiaryContainer
                            DriverMockData.FleetStatus.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(driver.initials, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(driver.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                driver.current?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val statusText = when (driver.status) {
                DriverMockData.FleetStatus.ACTIVE -> "ACTIVE"
                DriverMockData.FleetStatus.FREE -> "FREE"
                DriverMockData.FleetStatus.OFFLINE -> "OFFLINE"
            }
            AssistChip(
                onClick = {},
                label = { Text(statusText, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
