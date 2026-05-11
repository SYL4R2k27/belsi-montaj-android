package com.belsi.work.presentation.screens.curator.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.theme.belsiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDashboardScreen(
    navController: NavController,
    viewModel: AiDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI-аналитика") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // FIX(2026-05-10) BELSI 1.3.0: AI-сводка дня от Llama 70B (XeroCode).
            // Главный блок сверху — что куратор видит первым.
            AiDailySummarySection(
                summary = uiState.aiSummary,
                loading = uiState.aiSummaryLoading,
                onRefresh = { viewModel.loadAiSummary() },
            )

            // Period selector
            PeriodSelector(
                selected = uiState.period,
                onSelect = { viewModel.setPeriod(it) }
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                // Summary cards row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AiStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Analytics,
                        label = "Проанализировано",
                        value = "${uiState.totalAnalyzed}",
                        color = MaterialTheme.colorScheme.primary
                    )
                    AiStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.VerifiedUser,
                        label = "Автоодобрено",
                        value = "${uiState.autoApproved}",
                        color = MaterialTheme.belsiColors.success
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AiStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Warning,
                        label = "Требуют внимания",
                        value = "${uiState.needsAttention}",
                        color = MaterialTheme.colorScheme.error
                    )
                    AiStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AutoAwesome,
                        label = "Средний балл",
                        value = "${uiState.avgScore}",
                        color = when {
                            uiState.avgScore >= 80 -> MaterialTheme.belsiColors.success
                            uiState.avgScore >= 50 -> MaterialTheme.belsiColors.warning
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }

                // Category breakdown
                if (uiState.categoryCounts.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Категории проблем",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))
                            uiState.categoryCounts.forEach { (category, count) ->
                                CategoryRow(
                                    category = category,
                                    count = count,
                                    total = uiState.totalAnalyzed
                                )
                            }
                        }
                    }
                }

                // Problem installers
                if (uiState.problemInstallers.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.PersonOff,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Проблемные монтажники",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            uiState.problemInstallers.forEach { installer ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(32.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                installer.name.firstOrNull()?.uppercase() ?: "?",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            installer.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            "${installer.problemCount} проблем",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Empty state
                if (uiState.totalAnalyzed == 0 && !uiState.isLoading) {
                    Box(
                        Modifier.fillMaxWidth().height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                null,
                                Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Нет данных за выбранный период",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("today" to "Сегодня", "week" to "Неделя", "month" to "Месяц").forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AiStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(modifier) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, Modifier.size(28.dp), tint = color)
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryRow(category: String, count: Int, total: Int) {
    val (label, color) = when (category) {
        "good" -> "Хорошие" to MaterialTheme.belsiColors.success
        "blur" -> "Размытые" to MaterialTheme.colorScheme.error
        "dark" -> "Тёмные" to Color(0xFF795548)
        "bright" -> "Засвеченные" to com.belsi.work.presentation.theme.Amber500
        "low_contrast" -> "Низкий контраст" to Color(0xFF9E9E9E)
        "low_res" -> "Низкое разрешение" to Color(0xFF607D8B)
        "unreadable" -> "Нечитаемые" to MaterialTheme.colorScheme.error
        else -> "Другое" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val fraction = if (total > 0) count.toFloat() / total else 0f

    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$count (${(fraction * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}


/**
 * FIX(2026-05-10) BELSI 1.3.0: AI-сводка дня — главный блок AiDashboardScreen.
 *
 * Показывает результат /curator/ai-daily-summary (Llama 70B через XeroCode):
 * - Заголовок (headline)
 * - Сводка-параграф (summary)
 * - Аномалии списком
 * - Рекомендации списком
 * - Кнопка "Обновить"
 *
 * При недоступности AI — секция скрывается.
 */
@Composable
private fun AiDailySummarySection(
    summary: com.belsi.work.data.models.AiDailySummaryResponse?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
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
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "AI-сводка дня",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, "Обновить", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (summary == null) {
                Text(
                    if (loading) "Генерация AI-сводки…" else "AI временно недоступен",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "AI processing · XeroCode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                return@Card
            }

            // Headline
            if (summary.headline.isNotBlank()) {
                Text(
                    summary.headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Summary paragraph
            if (summary.summary.isNotBlank()) {
                Text(
                    summary.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(12.dp))
            }

            // Anomalies
            if (summary.anomalies.isNotEmpty()) {
                Text(
                    "Аномалии",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                summary.anomalies.forEach { anomaly ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("⚠ ", color = MaterialTheme.colorScheme.error)
                        val text = buildString {
                            anomaly.user?.let { append("$it · ") }
                            anomaly.`object`?.let { append("$it · ") }
                            append(anomaly.value ?: anomaly.type)
                        }
                        Text(
                            text.trim(' ', '·'),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Recommendations
            if (summary.recommendations.isNotEmpty()) {
                Text(
                    "Рекомендации",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                summary.recommendations.forEach { rec ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("💡 ")
                        Text(rec, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // XeroCode брендинг
            Text(
                "AI processing · XeroCode" + if (summary.cached) " · кэш 1 час" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
