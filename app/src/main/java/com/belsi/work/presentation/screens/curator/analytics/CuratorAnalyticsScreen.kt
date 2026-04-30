package com.belsi.work.presentation.screens.curator.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.remote.api.AnalyticsDayEntry
import com.belsi.work.presentation.theme.BelsiPrimary
import com.belsi.work.presentation.theme.BelsiSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorAnalyticsScreen(
    navController: NavController,
    viewModel: CuratorAnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аналитика") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BelsiPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Period selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.period == "week",
                        onClick = { viewModel.switchPeriod("week") },
                        label = { Text("Неделя") },
                        leadingIcon = if (uiState.period == "week") {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = uiState.period == "month",
                        onClick = { viewModel.switchPeriod("month") },
                        label = { Text("Месяц") },
                        leadingIcon = if (uiState.period == "month") {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BelsiPrimary)
                        }
                    }
                }
                uiState.error != null -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    uiState.error ?: "Ошибка",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { viewModel.loadAnalytics(uiState.period) }) {
                                    Text("Повторить")
                                }
                            }
                        }
                    }
                }
                uiState.days.isNotEmpty() -> {
                    val days = uiState.days

                    // Summary cards
                    item {
                        val totalPhotos = days.sumOf { it.photos }
                        val totalShifts = days.sumOf { it.shifts }
                        val totalWorkHours = days.sumOf { it.workHours }
                        val totalIdleHours = days.sumOf { it.idleHours }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryCard(
                                modifier = Modifier.weight(1f),
                                label = "Фото",
                                value = "$totalPhotos",
                                icon = Icons.Default.PhotoCamera,
                                color = BelsiPrimary
                            )
                            SummaryCard(
                                modifier = Modifier.weight(1f),
                                label = "Смены",
                                value = "$totalShifts",
                                icon = Icons.Default.Schedule,
                                color = BelsiSuccess
                            )
                        }
                    }
                    item {
                        val totalWorkHours = days.sumOf { it.workHours }
                        val totalIdleHours = days.sumOf { it.idleHours }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryCard(
                                modifier = Modifier.weight(1f),
                                label = "Рабочих ч.",
                                value = "%.1f".format(totalWorkHours),
                                icon = Icons.Default.AccessTime,
                                color = Color(0xFF3B82F6)
                            )
                            SummaryCard(
                                modifier = Modifier.weight(1f),
                                label = "Простой ч.",
                                value = "%.1f".format(totalIdleHours),
                                icon = Icons.Default.PauseCircle,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }

                    // Photos chart
                    item {
                        ChartCard(
                            title = "Фото по дням",
                            days = days,
                            valueSelector = { it.photos.toFloat() },
                            color = BelsiPrimary
                        )
                    }

                    // Shifts chart
                    item {
                        ChartCard(
                            title = "Смены по дням",
                            days = days,
                            valueSelector = { it.shifts.toFloat() },
                            color = BelsiSuccess
                        )
                    }

                    // Work hours chart
                    item {
                        ChartCard(
                            title = "Рабочие часы",
                            days = days,
                            valueSelector = { it.workHours.toFloat() },
                            color = Color(0xFF3B82F6)
                        )
                    }

                    // Idle hours chart
                    item {
                        ChartCard(
                            title = "Часы простоя",
                            days = days,
                            valueSelector = { it.idleHours.toFloat() },
                            color = Color(0xFFEF4444)
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
                else -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Нет данных за выбранный период",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    days: List<AnalyticsDayEntry>,
    valueSelector: (AnalyticsDayEntry) -> Float,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            val values = days.map(valueSelector)
            val maxValue = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f

            // Line chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val width = size.width
                val height = size.height
                val padding = 8f

                if (values.size < 2) return@Canvas

                val stepX = (width - padding * 2) / (values.size - 1).coerceAtLeast(1)

                // Grid lines
                val gridColor = Color.LightGray.copy(alpha = 0.3f)
                for (i in 0..4) {
                    val y = padding + (height - padding * 2) * i / 4
                    drawLine(gridColor, Offset(padding, y), Offset(width - padding, y))
                }

                // Line path
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = padding + index * stepX
                    val y = height - padding - (value / maxValue) * (height - padding * 2)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )

                // Dots
                values.forEachIndexed { index, value ->
                    val x = padding + index * stepX
                    val y = height - padding - (value / maxValue) * (height - padding * 2)
                    drawCircle(color = color, radius = 5f, center = Offset(x, y))
                    drawCircle(color = Color.White, radius = 3f, center = Offset(x, y))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Date labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val labelCount = if (days.size <= 7) days.size else 7
                val step = (days.size / labelCount).coerceAtLeast(1)
                days.filterIndexed { index, _ -> index % step == 0 || index == days.lastIndex }
                    .forEach { day ->
                        Text(
                            text = day.date.takeLast(5), // MM-DD
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
            }
        }
    }
}
