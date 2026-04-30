package com.belsi.work.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Swipe-to-start кнопка — пользователь свайпает вправо для начала смены.
 * Снижает случайные нажатия, добавляет тактильную обратную связь.
 */
@Composable
fun SwipeToStartButton(
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Свайпните для начала смены"
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 56.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val threshold = 0.85f

    var offsetX by remember(enabled) { mutableFloatStateOf(0f) }
    var confirmed by remember(enabled) { mutableStateOf(false) }

    // Reset confirmed state when enabled changes back to true
    LaunchedEffect(enabled) {
        if (enabled) {
            confirmed = false
            offsetX = 0f
        }
    }

    val maxDrag = (containerWidthPx - thumbSizePx).coerceAtLeast(0f)
    val progress = if (maxDrag > 0f) (offsetX / maxDrag).coerceIn(0f, 1f) else 0f

    // Анимация возврата при отпускании
    val animatedOffset by animateFloatAsState(
        targetValue = if (confirmed) maxDrag else offsetX,
        animationSpec = tween(durationMillis = 300),
        label = "swipeOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbSizeDp + 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                if (enabled) com.belsi.work.presentation.theme.Emerald500.copy(alpha = 0.15f)
                else Color.Gray.copy(alpha = 0.1f)
            )
            .onSizeChanged { containerWidthPx = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        // Подсказка-текст
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (enabled) com.belsi.work.presentation.theme.Emerald500.copy(alpha = 0.6f - progress * 0.4f)
            else Color.Gray.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        // Кнопка-ползунок
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .padding(4.dp)
                .size(thumbSizeDp)
                .clip(CircleShape)
                .background(
                    if (enabled) com.belsi.work.presentation.theme.Emerald500 else Color.Gray
                )
                .then(
                    if (enabled && !confirmed) {
                        Modifier.draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                offsetX = (offsetX + delta).coerceIn(0f, maxDrag)
                                if (progress > 0.3f && progress < 0.9f) {
                                    // Лёгкая вибрация при перетаскивании
                                }
                            },
                            onDragStopped = {
                                if (progress >= threshold) {
                                    confirmed = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConfirmed()
                                } else {
                                    offsetX = 0f
                                }
                            }
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Свайпните вправо",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
