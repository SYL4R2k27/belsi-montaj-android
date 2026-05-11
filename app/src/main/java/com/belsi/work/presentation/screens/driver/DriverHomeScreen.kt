package com.belsi.work.presentation.screens.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
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
import com.belsi.work.presentation.screens.driver.DriverMockData.PointStatus
import com.belsi.work.presentation.screens.driver.DriverMockData.RoutePoint

/**
 * FIX(2026-05-03): Driver Home (Brandbook · Driver Home, Этап A — мок-данные).
 * Показывает активный маршрут, следующую точку и список точек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHomeScreen(
    onPointClick: (String) -> Unit = {},
    onMenuClick: () -> Unit = {},
    topBar: @Composable () -> Unit = {},
) {
    val route = DriverMockData.activeRoute
    val next = route.nextPoint

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        topBar()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Активная карточка маршрута
            item { ActiveRouteCard(route, onMapClick = onMenuClick) }

            // Заголовок секции
            item {
                Text(
                    "ТОЧКИ МАРШРУТА",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            items(route.points, key = { it.id }) { point ->
                RoutePointRow(point, isNext = point.id == next?.id, onClick = { onPointClick(point.id) })
            }

            // Сегодня
            item { TodayStatsCard() }
        }
    }
}

@Composable
private fun ActiveRouteCard(
    route: DriverMockData.Route,
    onMapClick: () -> Unit,
) {
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        )
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.background(gradient).padding(20.dp)) {
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
                Text(route.plannedDate, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(8.dp))
            val done = route.points.count { it.status == PointStatus.DELIVERED }
            Text(
                "$done из ${route.points.size} точек",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            LinearProgressIndicator(
                progress = { route.progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(99.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )

            route.nextPoint?.let { p ->
                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "СЛЕДУЮЩАЯ ТОЧКА",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Text("${p.type.emoji} ${p.address}", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("${p.time} · ${p.cargo ?: ""}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onMapClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Map, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("В Картах", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Я приехал", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutePointRow(point: RoutePoint, isNext: Boolean, onClick: () -> Unit) {
    val containerColor = when {
        isNext -> MaterialTheme.colorScheme.primaryContainer
        point.status == PointStatus.DELIVERED -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        tonalElevation = if (point.status == PointStatus.DELIVERED) 0.dp else 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Иконка статуса
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when (point.status) {
                            PointStatus.DELIVERED -> MaterialTheme.colorScheme.tertiaryContainer
                            PointStatus.ARRIVED -> MaterialTheme.colorScheme.secondaryContainer
                            PointStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (point.status) {
                        PointStatus.DELIVERED -> "✓"
                        PointStatus.ARRIVED -> "→"
                        PointStatus.PENDING -> "⌛"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    point.address,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                point.cargo?.let { cargo ->
                    Text(
                        "${point.type.emoji} $cargo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                point.time,
                style = MaterialTheme.typography.labelMedium,
                color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun TodayStatsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "СЕГОДНЯ",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatBlock("23 км", "Пробег")
                StatBlock("1:14", "В пути")
                StatBlock("1/4", "Точек")
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
