package com.belsi.work.presentation.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Утилита для обновления виджета при изменении состояния смены.
 * Вызывать из ShiftViewModel при старте/паузе/завершении смены.
 */
object ShiftWidgetUpdater {

    fun updateWidgetState(
        context: Context,
        isRunning: Boolean,
        isPaused: Boolean = false,
        startTimeMs: Long = 0L,
        shiftId: String? = null
    ) {
        val prefs = context.getSharedPreferences("shift_widget", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_running", isRunning)
            .putBoolean("is_paused", isPaused)
            .putLong("start_time", startTimeMs)
            .putString("shift_id", shiftId)
            .apply()

        // Request widget update
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ShiftWidget().updateAll(context)
                } catch (e: Exception) {
                    android.util.Log.w("ShiftWidgetUpdater", "Failed to update widget: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ShiftWidgetUpdater", "Failed to launch widget update: ${e.message}")
        }
    }

    fun clearWidgetState(context: Context) {
        updateWidgetState(context, isRunning = false)
    }
}
