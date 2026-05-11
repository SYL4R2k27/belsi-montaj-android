package com.belsi.work.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.belsi.work.presentation.screens.shift.PhotoSlot
import com.belsi.work.presentation.screens.shift.PhotoSlotStatus

/**
 * Визуальный таймлайн смены — горизонтальная шкала рабочего дня.
 *
 * Цвета:
 * - Зелёный: работа
 * - Жёлтый: пауза
 * - Красный: простой
 * - Серый: ещё не наступило
 *
 * Маркеры фото:
 * - Зелёный кружок: одобрено/загружено
 * - Оранжевый: на модерации
 * - Красный: отклонено
 */
@Composable
fun ShiftTimeline(
    startTimeMillis: Long,
    elapsedSeconds: Long,
    pauseSeconds: Long,
    idleSeconds: Long,
    photoSlots: List<PhotoSlot>,
    modifier: Modifier = Modifier
) {
    val workColor = com.belsi.work.presentation.theme.Emerald500
    val pauseColor = Color(0xFFFFC107)
    val idleColor = com.belsi.work.presentation.theme.Rose500
    val trackColor = Color(0xFFE0E0E0)
    val photoApproved = com.belsi.work.presentation.theme.Emerald500
    val photoPending = com.belsi.work.presentation.theme.Amber500
    val photoRejected = com.belsi.work.presentation.theme.Rose500

    // Calculate total shift duration for proportions
    val totalSeconds = elapsedSeconds.coerceAtLeast(1)
    val workSeconds = (totalSeconds - pauseSeconds - idleSeconds).coerceAtLeast(0)

    // Hour labels (8:00 - 20:00 range)
    val dayStartHour = 8
    val dayEndHour = 20
    val totalDayHours = dayEndHour - dayStartHour

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Таймлайн смены",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        // Main timeline bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val barHeight = size.height * 0.5f
            val barY = (size.height - barHeight) / 2
            val cornerRadius = CornerRadius(barHeight / 2, barHeight / 2)

            // Background track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, barY),
                size = Size(size.width, barHeight),
                cornerRadius = cornerRadius
            )

            if (totalSeconds > 0) {
                // Work segment (green)
                val workFraction = workSeconds.toFloat() / totalSeconds
                val workWidth = size.width * workFraction * (elapsedSeconds.toFloat() / (totalDayHours * 3600).coerceAtLeast(1))
                    .coerceAtMost(1f)
                if (workWidth > 0) {
                    drawRoundRect(
                        color = workColor,
                        topLeft = Offset(0f, barY),
                        size = Size(workWidth.coerceAtMost(size.width), barHeight),
                        cornerRadius = cornerRadius
                    )
                }

                // Pause segment (yellow) after work
                val pauseFraction = pauseSeconds.toFloat() / totalSeconds
                val pauseWidth = size.width * pauseFraction * (elapsedSeconds.toFloat() / (totalDayHours * 3600).coerceAtLeast(1))
                    .coerceAtMost(1f)
                if (pauseWidth > 0) {
                    drawRect(
                        color = pauseColor,
                        topLeft = Offset(workWidth, barY),
                        size = Size(pauseWidth.coerceAtMost(size.width - workWidth), barHeight)
                    )
                }

                // Idle segment (red) after pause
                val idleFraction = idleSeconds.toFloat() / totalSeconds
                val idleWidth = size.width * idleFraction * (elapsedSeconds.toFloat() / (totalDayHours * 3600).coerceAtLeast(1))
                    .coerceAtMost(1f)
                if (idleWidth > 0) {
                    drawRect(
                        color = idleColor,
                        topLeft = Offset(workWidth + pauseWidth, barY),
                        size = Size(idleWidth.coerceAtMost(size.width - workWidth - pauseWidth), barHeight)
                    )
                }
            }

            // Photo markers
            photoSlots.forEach { slot ->
                if (slot.status != PhotoSlotStatus.LOCKED && slot.status != PhotoSlotStatus.EMPTY) {
                    // Parse hour from timeLabel (e.g. "10:00 - 11:00" → 10)
                    val hour = try {
                        slot.timeLabel.take(2).trim().replace(":", "").toIntOrNull()
                            ?: slot.timeLabel.split(":").firstOrNull()?.trim()?.toIntOrNull()
                            ?: return@forEach
                    } catch (_: Exception) { return@forEach }

                    val hourOffset = (hour - dayStartHour).toFloat() / totalDayHours
                    if (hourOffset in 0f..1f) {
                        val markerX = size.width * hourOffset
                        val markerColor = when (slot.status) {
                            PhotoSlotStatus.UPLOADED -> photoApproved
                            PhotoSlotStatus.PENDING -> photoPending
                            PhotoSlotStatus.REJECTED -> photoRejected
                            else -> Color.Gray
                        }
                        // Draw circle marker above bar
                        drawCircle(
                            color = markerColor,
                            radius = 5.dp.toPx(),
                            center = Offset(markerX, barY - 2.dp.toPx())
                        )
                        // White border
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = Offset(markerX, barY - 2.dp.toPx())
                        )
                        drawCircle(
                            color = markerColor,
                            radius = 3.dp.toPx(),
                            center = Offset(markerX, barY - 2.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Hour labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (h in dayStartHour..dayEndHour step 2) {
                Text(
                    text = "$h:00",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimelineLegendItem("Работа", workColor)
            TimelineLegendItem("Пауза", pauseColor)
            TimelineLegendItem("Простой", idleColor)
        }
    }
}

@Composable
private fun TimelineLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
