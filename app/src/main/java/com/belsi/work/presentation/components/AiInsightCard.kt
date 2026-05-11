package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FIX(2026-05-10): Переиспользуемый AI-блок для карточек фото / простоев / партий.
 *
 * Брендинг XeroCode (зафиксировано в AI_INTEGRATION.md):
 *   🤖 AI 8/10
 *   Качественное фото монтажа. Видна рабочая зона...
 *   AI-анализ · XeroCode  ← мелкий вторичный текст
 *
 * Используется только в 1.3.0+ (отображение AI-полей из ai_analyses).
 *
 * @param score 0-100 (как в shift_photos.ai_score). Внутри конвертируется в N/10.
 * @param comment основной AI-комментарий (русский).
 * @param showFooter false когда брендинг уже виден на родительском экране.
 */
@Composable
fun AiInsightCard(
    score: Int?,
    comment: String?,
    modifier: Modifier = Modifier,
    showFooter: Boolean = true,
) {
    if (score == null && comment.isNullOrBlank()) return  // нечего показывать

    val score10 = score?.let { (it / 10).coerceIn(0, 10) }
    val accentColor = when {
        score10 == null -> Color(0xFF6366F1)  // нейтральный
        score10 >= 8 -> Color(0xFF10B981)     // зелёный (хорошо)
        score10 >= 5 -> Color(0xFFF59E0B)     // жёлтый (средне)
        else -> Color(0xFFEF4444)             // красный (плохо)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Заголовок: 🤖 AI N/10
        if (score10 != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "AI",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "$score10/10",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
        }

        // Комментарий
        if (!comment.isNullOrBlank()) {
            Text(
                comment,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Footer: AI-анализ · XeroCode (мелкий, серый)
        if (showFooter) {
            Spacer(Modifier.height(2.dp))
            Text(
                "AI-анализ · XeroCode",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
