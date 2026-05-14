package com.belsi.work.presentation.screens.curator.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
                // FIX(2026-05-12) build19 hotfix: при клике «↻» сбрасываем серверный кэш.
                onRefresh = { viewModel.loadAiSummary(forceRefresh = true) },
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
                            // FIX(2026-05-13) BELSI 2.0.1: расшифровка категорий + клик на детали монтажника
                            uiState.problemInstallers.forEachIndexed { idx, installer ->
                                ProblemInstallerRow(
                                    installer = installer,
                                    onClick = {
                                        navController.navigate(
                                            AppRoute.CuratorUserDetail.createRoute(installer.userId)
                                        )
                                    },
                                )
                                if (idx < uiState.problemInstallers.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    )
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

/**
 * FIX(2026-05-13) BELSI 2.0.1: строка проблемного монтажника с расшифровкой.
 * Под именем — категории проблем + время последней. Вся строка clickable → детали монтажника.
 */
@Composable
private fun ProblemInstallerRow(
    installer: com.belsi.work.data.remote.api.ProblemInstaller,
    onClick: () -> Unit,
) {
    val breakdownText = remember(installer.categoryBreakdown) {
        problemCategoryBreakdownText(installer.categoryBreakdown)
    }
    val lastText = remember(installer.lastProblemAt) {
        installer.lastProblemAt?.let { formatRelativeTimeRu(it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        Box(
            Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                installer.name.firstOrNull()?.uppercase() ?: "?",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.width(12.dp))

        // Name + breakdown subtext
        Column(modifier = Modifier.weight(1f)) {
            Text(
                installer.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (breakdownText.isNotEmpty() || lastText != null) {
                Spacer(Modifier.height(2.dp))
                val subtitle = buildString {
                    if (breakdownText.isNotEmpty()) append(breakdownText)
                    if (lastText != null) {
                        if (isNotEmpty()) append(" · ")
                        append(lastText)
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Count badge
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        ) {
            Text(
                "${installer.problemCount} ${pluralProblems(installer.problemCount)}",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.width(4.dp))

        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * Категории → русские лейблы для подписи в строке проблемного монтажника.
 * FIX(2026-05-13) BELSI 2.0.1: расширено под реальные категории XeroCode photo_quality v2.
 *
 * Реально приходящие из бэка (по данным prod БД за 30 дней):
 *   good (282) workplace (175) warning (77) documentation (39) violation (9)
 *   neutral (4) dark (3) wall (3) info (2) unclear (1)
 *
 * Legacy (старая photo_quality v1 — оставлены как fallback):
 *   blur, bright, low_contrast, low_res, unreadable
 */
private fun problemCategoryLabel(category: String): String = when (category) {
    // Reality categories (XeroCode v2)
    "good" -> "хорошее"
    "workplace" -> "рабочее место"
    "warning" -> "не по теме"
    "documentation" -> "документ"
    "violation" -> "нарушение"
    "neutral" -> "нейтральное"
    "dark" -> "тёмное"
    "wall" -> "стена"
    "info" -> "инфо"
    "unclear" -> "нечитаемо"
    // Legacy v1 fallback
    "blur" -> "размытое"
    "bright" -> "засвечено"
    "low_contrast" -> "контраст"
    "low_res" -> "разрешение"
    "unreadable" -> "нечитаемо"
    else -> "другое"
}

/** «1 размытое · 1 тёмное» — упорядочено по убыванию count. */
private fun problemCategoryBreakdownText(breakdown: Map<String, Int>): String {
    if (breakdown.isEmpty()) return ""
    return breakdown.entries
        .sortedByDescending { it.value }
        .joinToString(" · ") { (k, v) -> "$v ${problemCategoryLabel(k)}" }
}

/** «3 проблемы / 2 проблем / 1 проблема» по русской плюрализации. */
private fun pluralProblems(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "проблема"
        mod10 in 2..4 && mod100 !in 12..14 -> "проблемы"
        else -> "проблем"
    }
}

/** «5 мин назад» / «2 ч назад» / «вчера» / «12 мая». */
private fun formatRelativeTimeRu(iso: String): String? {
    return try {
        val dt = OffsetDateTime.parse(iso)
        val now = OffsetDateTime.now(ZoneId.systemDefault())
        val diff = Duration.between(dt, now)
        val mins = diff.toMinutes()
        when {
            mins < 1 -> "только что"
            mins < 60 -> "$mins ${pluralMinutes(mins.toInt())} назад"
            mins < 60 * 24 -> {
                val h = (mins / 60).toInt()
                "$h ${pluralHours(h)} назад"
            }
            mins < 60 * 48 -> "вчера"
            else -> dt.atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM"))
        }
    } catch (e: Exception) {
        null
    }
}

private fun pluralMinutes(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "минуту"
        mod10 in 2..4 && mod100 !in 12..14 -> "минуты"
        else -> "минут"
    }
}

private fun pluralHours(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "час"
        mod10 in 2..4 && mod100 !in 12..14 -> "часа"
        else -> "часов"
    }
}

@Composable
private fun CategoryRow(category: String, count: Int, total: Int) {
    // FIX(2026-05-13) BELSI 2.0.1: расширен маппинг под реальные категории XeroCode v2.
    // Раньше: 90% фото попадало в «Другое» т.к. backend отдавал workplace/warning/
    // documentation/violation/neutral/wall/info/unclear — все без лейбла.
    val (label, color) = when (category) {
        // Хорошие — зелёный
        "good" -> "Хорошие" to MaterialTheme.belsiColors.success
        // Рабочее — синий (нейтральная норма)
        "workplace" -> "Рабочее место" to MaterialTheme.colorScheme.primary
        "documentation" -> "Документы" to Color(0xFF607D8B)
        "neutral" -> "Нейтральные" to Color(0xFF9E9E9E)
        "info" -> "Информационные" to MaterialTheme.colorScheme.tertiary
        // Проблемные — оттенки красного / янтаря
        "violation" -> "Нарушения" to MaterialTheme.colorScheme.error
        "warning" -> "Не по теме" to com.belsi.work.presentation.theme.Amber500
        "wall" -> "Стена / не контекст" to Color(0xFF795548)
        "dark" -> "Тёмные" to Color(0xFF424242)
        "unclear" -> "Нечитаемые" to MaterialTheme.colorScheme.error
        // Legacy XeroCode v1 fallback (на случай старых записей)
        "blur" -> "Размытые" to MaterialTheme.colorScheme.error
        "bright" -> "Засвеченные" to com.belsi.work.presentation.theme.Amber500
        "low_contrast" -> "Низкий контраст" to Color(0xFF9E9E9E)
        "low_res" -> "Низкое разрешение" to Color(0xFF607D8B)
        "unreadable" -> "Нечитаемые" to MaterialTheme.colorScheme.error
        else -> "Другое ($category)" to MaterialTheme.colorScheme.onSurfaceVariant
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
                // FIX(2026-05-12) build19 hotfix: "XeroCode" убран из UI AI-блоков.
                Text(
                    "AI processing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                return@Card
            }

            // FIX(2026-05-12) build19 hotfix: ground-truth блок поверх AI-нарратива.
            // Если AI ошибся / выдал stale из кэша — пользователь видит реальные цифры
            // прямо сверху. raw_stats считается на каждом запросе свежий (не из кэша).
            summary.rawStats?.let { s ->
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Реально по БД (сейчас):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• ${s.activeNow} смен сейчас активны",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "• ${s.startedToday} начато сегодня · ${s.finishedToday} закрыто",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "• ${s.workHours} ч работы · ${s.idleHours} ч простоя · ${s.pauseHours} ч пауз",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
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

            // FIX(2026-05-12) build19 hotfix: фильтруем аномалии-пустышки
            // (тип "raw_stats" / value="0" / "0.0" — это были не аномалии, а статистика).
            val realAnomalies = summary.anomalies.filter { a ->
                if (!a.user.isNullOrBlank() || !a.`object`.isNullOrBlank()) return@filter true
                val v = a.value?.trim().orEmpty()
                if (v.isBlank()) return@filter false
                v.toDoubleOrNull() == null  // оставляем только текстовые value
            }
            if (realAnomalies.isNotEmpty()) {
                Text(
                    "Аномалии",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                realAnomalies.forEach { anomaly ->
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

            // FIX(2026-05-12) build19 hotfix: "XeroCode" убран из UI AI-блоков.
            // Брендинг провайдера остаётся в Settings, About, LegalTexts, UpdateGate.
            Text(
                "AI processing" + if (summary.cached) " · кэш 1 час" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
