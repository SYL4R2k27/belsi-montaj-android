package com.belsi.work.presentation.components.role

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.belsi.work.presentation.theme.belsiColors

/**
 * FIX(2026-05-12) build19 hotfix: единая дизайн-система для всех role-экранов.
 *
 * До этого в каждом из 11 ролевых main-экранов был свой набор Color(0xFF...) —
 * Worker рисовал «На смене» в `#10B981`, Senior — в Emerald500, Chief — в
 * `Color(0xFF6366F1)` напрямую. Это рассогласовывало визуальный язык.
 *
 * Все role-экраны теперь используют **только** эти компоненты + Severity.
 * Если нужен новый цвет — добавляем в `theme/Color.kt` (BelsiExtendedColors),
 * а не лепим прямо на месте.
 */

/**
 * Семантика статуса/значения — единая для всех экранов.
 *
 *   PRIMARY  — индиго, фирменный (нейтральный акцент: счётчики, primary CTA)
 *   SUCCESS  — изумрудный (на смене, OK, активен)
 *   WARNING  — жёлтый (пауза, перекур, ожидание)
 *   ERROR    — розовый (простой, проблема, отказ)
 *   INFO     — голубой (информация, нейтральный счётчик)
 *   AI       — фиолетовый (всё что от AI: scores, инсайты)
 *   NEUTRAL  — серый (неактивно, пусто, archived)
 */
enum class Severity { PRIMARY, SUCCESS, WARNING, ERROR, INFO, AI, NEUTRAL }

/** Получить fg/bg цвета по Severity из текущей темы. */
@Composable
fun Severity.colors(): Pair<Color, Color> {
    val bc = MaterialTheme.belsiColors
    val cs = MaterialTheme.colorScheme
    return when (this) {
        Severity.PRIMARY -> cs.primary to cs.primaryContainer
        Severity.SUCCESS -> bc.success to bc.successContainer
        Severity.WARNING -> bc.warning to bc.warningContainer
        Severity.ERROR   -> cs.error to cs.errorContainer
        Severity.INFO    -> bc.info to bc.infoContainer
        Severity.AI      -> bc.ai to bc.aiContainer
        Severity.NEUTRAL -> bc.neutral to bc.neutralContainer
    }
}

// ──────────────────────────────────────────────────────────────────────
// Шапка ролевого экрана: эмодзи + заголовок + подзаголовок + refresh
// ──────────────────────────────────────────────────────────────────────

@Composable
fun RoleHomeHeader(
    emoji: String,
    title: String,
    subtitle: String? = null,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Обновить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Карточка-метрика (большая) для дашбордов: число + лейбл
// Используется в строках по 2-4 штуки.
// ──────────────────────────────────────────────────────────────────────

@Composable
fun RoleStatCard(
    label: String,
    value: String,
    severity: Severity = Severity.PRIMARY,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val (fg, bg) = severity.colors()
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Мини-метрика (для строк бок-о-бок): только число + лейбл
// ──────────────────────────────────────────────────────────────────────

@Composable
fun RoleMiniStat(
    label: String,
    value: String,
    severity: Severity = Severity.PRIMARY,
    modifier: Modifier = Modifier,
) {
    val (fg, _) = severity.colors()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
// Status pill — компактный бейдж (для членов бригады, batch'ей и т.п.)
// Заменяет много hardcoded background+text паттернов.
// ──────────────────────────────────────────────────────────────────────

@Composable
fun RoleStatusPill(
    text: String,
    severity: Severity,
    modifier: Modifier = Modifier,
) {
    val (fg, bg) = severity.colors()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
// Status dot — кружочек 10dp для списков (член бригады, рабочий и т.п.)
// ──────────────────────────────────────────────────────────────────────

@Composable
fun RoleStatusDot(
    severity: Severity,
    modifier: Modifier = Modifier,
) {
    val (fg, _) = severity.colors()
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(RoundedCornerShape(50))
            .background(fg),
    )
}

// ──────────────────────────────────────────────────────────────────────
// Empty state — единая «пустая» картинка с emoji + текст
// Используется когда нет данных для дашборда.
// ──────────────────────────────────────────────────────────────────────

@Composable
fun RoleEmptyState(
    emoji: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            emoji,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Section header — для логичного разделения секций на дашборде.
// ──────────────────────────────────────────────────────────────────────

@Composable
fun RoleSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Primary action button — обёртка над Button с фирменными размерами/радиусом
// Используется для главных CTA: «Старт смены», «Принять партию» и т.п.
// ──────────────────────────────────────────────────────────────────────

@Composable
fun RolePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    severity: Severity = Severity.PRIMARY,
) {
    val (fg, _) = severity.colors()
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = fg,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
