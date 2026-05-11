package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.belsi.work.data.remote.dto.curator.CuratorDashboardDto
import com.belsi.work.data.remote.dto.curator.CuratorForemanDto
import com.belsi.work.data.remote.dto.curator.CuratorInstallerDto
import com.belsi.work.presentation.theme.Amber500
import com.belsi.work.presentation.theme.Emerald500
import com.belsi.work.presentation.theme.Rose500
import com.belsi.work.presentation.theme.Sky500
import com.belsi.work.presentation.theme.Violet500
import com.belsi.work.presentation.theme.Violet600
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * AI инсайты — карточка на дашборде куратора.
 *
 * Brandbook reference: violet gradient (Violet500 → Violet600) + ⚡ + bullet list.
 * Каждая строка имеет иконку статуса и цвет акцента (critical/warning/positive/info).
 *
 * Inspiration:
 *  • «Денисов А. сегодня без фото 4ч. Возможен простой.»
 *  • «Бригада Хрулёва — рост +12% часов vs прошлая неделя»
 *  • «2 эскалации требуют внимания»
 */
data class AiInsight(
    val text: String,
    val severity: Severity,
    val icon: ImageVector? = null
) {
    enum class Severity { INFO, POSITIVE, WARNING, CRITICAL }
}

@Composable
fun AiInsightsCard(
    insights: List<AiInsight>,
    modifier: Modifier = Modifier,
) {
    if (insights.isEmpty()) return

    val gradient = Brush.linearGradient(colors = listOf(Violet500, Violet600))

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(gradient)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Header — ⚡ + title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Bolt,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI инсайты",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Insights list (max 4)
                insights.take(4).forEach { insight ->
                    InsightRow(insight)
                }
            }
        }
    }
}

@Composable
private fun InsightRow(insight: AiInsight) {
    val accent: Color = when (insight.severity) {
        AiInsight.Severity.CRITICAL -> Rose500
        AiInsight.Severity.WARNING -> Amber500
        AiInsight.Severity.POSITIVE -> Emerald500
        AiInsight.Severity.INFO -> Sky500
    }
    val icon: ImageVector = insight.icon ?: when (insight.severity) {
        AiInsight.Severity.CRITICAL -> Icons.Default.Warning
        AiInsight.Severity.WARNING -> Icons.Default.Schedule
        AiInsight.Severity.POSITIVE -> Icons.Default.TrendingUp
        AiInsight.Severity.INFO -> Icons.Default.Info
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            insight.text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// CLIENT-SIDE INSIGHTS DERIVATION
// ─────────────────────────────────────────────────────────────────────
//
// Эвристики на основе данных из CuratorDashboardDto + List<CuratorForemanDto>.
// Server-side endpoint /curator/insights можно подключить позже —
// тогда заменим на полученный список без изменений UI.
//
// Порядок приоритета: critical → warning → positive → info.

object CuratorInsightsBuilder {

    /**
     * FIX(2026-05-10) BELSI 1.3.0: преобразование настоящего AI-ответа от XeroCode
     * (через /curator/ai-daily-summary) в List<AiInsight> для отображения.
     *
     * AI возвращает:
     *   {headline, summary, anomalies: [{type, user, value}], recommendations: [str]}
     *
     * Конвертируем:
     * - headline → INFO insight (или CRITICAL если есть critical anomaly)
     * - anomalies[type=long_idle/no_photos/overwork] → WARNING/CRITICAL
     * - recommendations → INFO insights (мелко)
     *
     * Максимум 4 пункта (как у эвристик), приоритет: critical → warning → info.
     */
    fun fromAiSummary(
        summary: com.belsi.work.data.models.AiDailySummaryResponse,
    ): List<AiInsight> {
        val out = mutableListOf<AiInsight>()

        // Аномалии (главное содержание)
        for (anomaly in summary.anomalies) {
            val severity = when (anomaly.type.lowercase()) {
                "critical", "blocker", "outage" -> AiInsight.Severity.CRITICAL
                "long_idle", "overwork", "no_photos", "warning" -> AiInsight.Severity.WARNING
                "positive", "achievement" -> AiInsight.Severity.POSITIVE
                else -> AiInsight.Severity.INFO
            }
            val text = buildString {
                anomaly.user?.let { append("$it · ") }
                anomaly.`object`?.let { append("$it · ") }
                append(anomaly.value ?: anomaly.type)
            }
            out += AiInsight(text = text.trim(' ', '·'), severity = severity)
        }

        // Если аномалий нет — показываем headline как INFO
        if (out.isEmpty() && summary.headline.isNotBlank()) {
            out += AiInsight(text = summary.headline, severity = AiInsight.Severity.INFO)
        }

        // Топ-1 рекомендация (если есть место)
        if (out.size < 4 && summary.recommendations.isNotEmpty()) {
            out += AiInsight(
                text = "💡 " + summary.recommendations.first(),
                severity = AiInsight.Severity.INFO,
            )
        }

        return out.take(4)
    }

    fun build(
        dashboard: CuratorDashboardDto,
        foremen: List<CuratorForemanDto>,
    ): List<AiInsight> {
        val out = mutableListOf<AiInsight>()

        // 1. CRITICAL — эскалации поддержки
        when {
            dashboard.openSupportTickets >= 2 -> out += AiInsight(
                text = "${dashboard.openSupportTickets} ${pluralEsc(dashboard.openSupportTickets)} требуют внимания",
                severity = AiInsight.Severity.CRITICAL
            )
            dashboard.openSupportTickets == 1 -> out += AiInsight(
                text = "1 эскалация требует внимания",
                severity = AiInsight.Severity.WARNING
            )
        }

        // 2. WARNING — простаивающий монтажник (на смене, но без фото 4ч+)
        val idleInstaller = findIdleInstaller(foremen)
        if (idleInstaller != null) {
            val hours = idleInstaller.second
            out += AiInsight(
                text = "${shortName(idleInstaller.first.fullName)} — без фото ${hours}ч. Возможен простой.",
                severity = AiInsight.Severity.WARNING
            )
        }

        // 3. WARNING — много фото в очереди модерации
        if (dashboard.pendingPhotos >= 20) {
            out += AiInsight(
                text = "${dashboard.pendingPhotos} фото ждут модерации — нужна разгрузка",
                severity = AiInsight.Severity.WARNING
            )
        } else if (dashboard.pendingPhotos in 1..19) {
            out += AiInsight(
                text = "${dashboard.pendingPhotos} ${pluralPhoto(dashboard.pendingPhotos)} на проверке",
                severity = AiInsight.Severity.INFO
            )
        }

        // 4. POSITIVE — лучшая бригада по выполнению
        val topForeman = foremen
            .filter { it.completionPercentage >= 70.0 && it.activeInstallersCount > 0 }
            .maxByOrNull { it.completionPercentage }
        if (topForeman != null) {
            out += AiInsight(
                text = "Бригада ${shortLastName(topForeman.fullName)} — ${topForeman.completionPercentage.toInt()}% выполнено",
                severity = AiInsight.Severity.POSITIVE
            )
        }

        // 5. WARNING — низкая активность команды
        if (dashboard.totalInstallers >= 5 && dashboard.activeInstallersToday > 0) {
            val ratio = dashboard.activeInstallersToday * 100 / dashboard.totalInstallers
            if (ratio < 40) {
                out += AiInsight(
                    text = "Активны сегодня: ${dashboard.activeInstallersToday} из ${dashboard.totalInstallers} монтажников ($ratio%)",
                    severity = AiInsight.Severity.WARNING
                )
            }
        }

        // 6. INFO — итог дня (если ничего критичного нет)
        if (out.isEmpty() && dashboard.totalShiftsToday > 0) {
            out += AiInsight(
                text = "${dashboard.totalShiftsToday} ${pluralShift(dashboard.totalShiftsToday)} в работе сейчас",
                severity = AiInsight.Severity.INFO
            )
        }

        return out
    }

    /** Находит первого монтажника на смене без фото за 4+ часов. */
    private fun findIdleInstaller(foremen: List<CuratorForemanDto>): Pair<CuratorInstallerDto, Long>? {
        val now = OffsetDateTime.now()
        return foremen
            .flatMap { it.installers }
            .mapNotNull { installer ->
                val lastActivity = parseIso(installer.lastActivityAt) ?: return@mapNotNull null
                val hoursSince = ChronoUnit.HOURS.between(lastActivity, now)
                // Считаем "простаивающим" если активен в системе сегодня (есть смены),
                // но нет фото 4+ часов
                if (installer.totalShifts > 0 && installer.pendingPhotosCount == 0 && hoursSince in 4..12) {
                    installer to hoursSince
                } else null
            }
            .maxByOrNull { it.second }
    }

    private fun parseIso(iso: String?): OffsetDateTime? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso)
        } catch (e: Exception) {
            null
        }
    }

    private fun shortName(full: String): String {
        // "Иванов Иван Петрович" → "Иванов И."
        val parts = full.trim().split(" ").filter { it.isNotBlank() }
        if (parts.size < 2) return full
        return "${parts[0]} ${parts[1].first()}."
    }

    private fun shortLastName(full: String): String {
        // "Иванов Иван" → "Иванов"
        return full.trim().split(" ").firstOrNull() ?: full
    }

    private fun pluralEsc(n: Int): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "эскалация"
            mod10 in 2..4 && mod100 !in 12..14 -> "эскалации"
            else -> "эскалаций"
        }
    }

    private fun pluralPhoto(n: Int): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "фото"
            else -> "фото"
        }
    }

    private fun pluralShift(n: Int): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "смена"
            mod10 in 2..4 && mod100 !in 12..14 -> "смены"
            else -> "смен"
        }
    }
}
