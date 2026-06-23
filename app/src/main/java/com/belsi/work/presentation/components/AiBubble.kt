package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.belsiColors

/**
 * Унифицированный AI-блок (✨ + фиолетовый бренд XeroCode). Molecule.
 *
 * Канонический AI-контейнер 2.1.0. Заменяет AiInsightCard / AiInsightsCard
 * (старые остаются до миграции экранов в Phase 3 — Strangler Fig).
 *
 * @param text основной AI-текст (русский)
 * @param label мелкая uppercase-подпись (напр. «AI · сводка», «AI анализ»)
 * @param actionLabel опц. кнопка-действие (напр. «Открыть тренд»)
 */
@Composable
fun AiBubble(
    text: String,
    modifier: Modifier = Modifier,
    label: String = "AI",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val belsi = MaterialTheme.belsiColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(belsi.aiContainer)
            .border(1.dp, belsi.ai.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("✨", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = belsi.ai,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = belsi.onAiContainer,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = belsi.ai)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AiBubblePreview() {
    BelsiWorkTheme {
        AiBubble(
            text = "«14 смен · темп нормальный. Курешова 2-й простой за неделю на 312 — паттерн.»",
            label = "AI · сводка",
            actionLabel = "Открыть тренд",
            onAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
