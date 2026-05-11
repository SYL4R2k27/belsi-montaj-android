package com.belsi.work.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.belsi.work.MainActivity

/**
 * Виджет на домашний экран — таймер смены, статус, кнопка открытия приложения.
 */
class ShiftWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("shift_widget", Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean("is_running", false)
        val isPaused = prefs.getBoolean("is_paused", false)
        val startTimeMs = prefs.getLong("start_time", 0L)

        provideContent {
            GlanceTheme {
                ShiftWidgetContent(
                    isRunning = isRunning,
                    isPaused = isPaused,
                    startTimeMs = startTimeMs
                )
            }
        }
    }
}

private val Green = com.belsi.work.presentation.theme.Emerald500
private val Orange = com.belsi.work.presentation.theme.Amber500
private val Gray = Color(0xFF9E9E9E)
private val Blue = com.belsi.work.presentation.theme.Sky500
private val White = Color(0xFFFFFFFF)

@Composable
private fun ShiftWidgetContent(
    isRunning: Boolean,
    isPaused: Boolean,
    startTimeMs: Long
) {
    val elapsed = if (isRunning && startTimeMs > 0) {
        val seconds = (System.currentTimeMillis() - startTimeMs) / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        String.format("%02d:%02d", h, m)
    } else {
        "--:--"
    }

    val statusText = when {
        !isRunning -> "Нет смены"
        isPaused -> "На паузе"
        else -> "В работе"
    }

    val statusColor = when {
        !isRunning -> Gray
        isPaused -> Orange
        else -> Green
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(White)
            .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = "Belsi.Монтаж",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(Gray)
                )
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                text = elapsed,
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(statusColor)
                )
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = statusText,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = ColorProvider(statusColor)
                )
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            val btnColor = if (isRunning) Green else Blue
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(12.dp)
                    .background(btnColor)
                    .padding(vertical = 8.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRunning) "Открыть смену" else "Начать смену",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(White)
                    )
                )
            }
        }
    }
}

class ShiftWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShiftWidget()
}
