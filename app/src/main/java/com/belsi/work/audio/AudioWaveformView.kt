package com.belsi.work.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * FIX(2026-05-14) BELSI 2.0.1: Compose-визуализация audio waveform.
 *
 * Использование:
 *   val waveform = remember(bitstream) { AudioWaveform(bitstream, bitsPerSample = 5) }
 *   AudioWaveformView(
 *       waveform = waveform,
 *       progress = 0.3f,   // 30% проиграно
 *       playedColor = MaterialTheme.colorScheme.primary,
 *       unplayedColor = MaterialTheme.colorScheme.outlineVariant,
 *   )
 *
 * Дизайн (соответствует Telegram-style):
 *  - Бары шириной 2dp, расстояние между ними 2dp
 *  - Высота каждого пропорциональна `normalizedAmplitude(index)`
 *  - Закруглённые концы (StrokeCap.Round)
 *  - Минимальная видимая высота — 2dp (даже для тишины — чтобы трек был виден)
 */
@Composable
fun AudioWaveformView(
    waveform: AudioWaveform,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    playedColor: Color = Color(0xFF4F46E5),       // Indigo600
    unplayedColor: Color = Color(0xFFC7D2FE),      // Indigo200
    barWidth: Dp = 2.dp,
    barSpacing: Dp = 2.dp,
    height: Dp = 32.dp,
    minBarHeight: Dp = 2.dp,
) {
    val n = waveform.sampleCount
    if (n <= 0) return

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val totalBarSpace = (barWidth.toPx() + barSpacing.toPx())
        val maxBars = (size.width / totalBarSpace).toInt().coerceAtLeast(1)

        // Если waveform слишком длинная — даунсэмплим (берём каждый N-й бар)
        // Если слишком короткая — повторяем
        val barsToDraw = minOf(maxBars, n)
        val step = if (n > maxBars) n.toFloat() / maxBars else 1f

        val centerY = size.height / 2f
        val minHalfHeight = max(1f, minBarHeight.toPx() / 2f)
        val playedBars = (progress.coerceIn(0f, 1f) * barsToDraw).toInt()

        for (i in 0 until barsToDraw) {
            val sourceIdx = (i * step).toInt().coerceIn(0, n - 1)
            val amplitude = waveform.normalizedAmplitude(sourceIdx)
            val halfHeight = max(minHalfHeight, amplitude * centerY)

            val x = i * totalBarSpace + barWidth.toPx() / 2f
            val color = if (i < playedBars) playedColor else unplayedColor

            drawLine(
                color = color,
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = barWidth.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
