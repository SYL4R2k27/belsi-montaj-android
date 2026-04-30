package com.belsi.work.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кэш смены для offline режима
 * Критично: активная смена должна быть доступна без интернета
 * Включает полное состояние таймера для восстановления после перезапуска
 */
@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val startAt: String,
    val finishAt: String? = null,
    val pauseStart: String? = null,
    val status: String, // "active", "paused", "finished"
    val durationHours: Double? = null,
    val syncStatus: String = "synced", // "synced", "pending", "error"
    val lastSyncAt: Long = System.currentTimeMillis(),

    // === Offline timer state (persisted every 10 seconds) ===
    val startTimeMillis: Long = 0L, // epoch millis для быстрого доступа
    val elapsedSeconds: Long = 0L,
    val pauseSeconds: Long = 0L, // текущая активная пауза
    val idleSeconds: Long = 0L, // текущий активный простой
    val totalPauseSeconds: Long = 0L, // сумма всех завершённых пауз
    val totalIdleSeconds: Long = 0L, // сумма всех завершённых простоев
    val isPaused: Boolean = false,
    val isIdle: Boolean = false,
    val idleReason: String? = null,
    val pauseStartTime: Long? = null, // epoch millis начала текущей паузы
    val idleStartTime: Long? = null // epoch millis начала текущего простоя
)
