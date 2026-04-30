package com.belsi.work.data.local.database.dao

import androidx.room.*
import com.belsi.work.data.local.database.entities.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {

    @Query("SELECT * FROM shifts WHERE status = 'active' OR status = 'paused' LIMIT 1")
    fun getActiveShiftFlow(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE status = 'active' OR status = 'paused' LIMIT 1")
    suspend fun getActiveShift(): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getShiftById(id: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftEntity)

    @Update
    suspend fun updateShift(shift: ShiftEntity)

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun deleteShift(id: String)

    @Query("DELETE FROM shifts WHERE status = 'finished' AND lastSyncAt < :timestampMillis")
    suspend fun deleteOldFinishedShifts(timestampMillis: Long)

    @Query("SELECT * FROM shifts WHERE syncStatus = 'pending'")
    suspend fun getPendingShifts(): List<ShiftEntity>

    @Query("UPDATE shifts SET status = :status, syncStatus = 'synced' WHERE id = :shiftId")
    suspend fun updateShiftStatus(shiftId: String, status: String)

    @Query("UPDATE shifts SET finishAt = :finishAt WHERE id = :shiftId")
    suspend fun updateShiftEndTime(shiftId: String, finishAt: String)

    @Query("UPDATE shifts SET syncStatus = :syncStatus WHERE id = :shiftId")
    suspend fun updateSyncStatus(shiftId: String, syncStatus: String)

    @Query("DELETE FROM shifts WHERE status != 'active' AND status != 'paused'")
    suspend fun clearInactiveShifts()

    // === Offline timer persistence ===

    /**
     * Сохранение состояния таймера каждые 10 секунд.
     * Позволяет восстановить точное время при перезапуске приложения.
     */
    @Query("""
        UPDATE shifts SET
            elapsedSeconds = :elapsed,
            pauseSeconds = :pause,
            idleSeconds = :idle,
            totalPauseSeconds = :totalPause,
            totalIdleSeconds = :totalIdle,
            isPaused = :isPaused,
            isIdle = :isIdle,
            lastSyncAt = :now
        WHERE id = :shiftId
    """)
    suspend fun saveTimerState(
        shiftId: String,
        elapsed: Long,
        pause: Long,
        idle: Long,
        totalPause: Long,
        totalIdle: Long,
        isPaused: Boolean,
        isIdle: Boolean,
        now: Long = System.currentTimeMillis()
    )

    /**
     * Сохранение начала/конца паузы
     */
    @Query("UPDATE shifts SET isPaused = :isPaused, pauseStartTime = :pauseStartTime WHERE id = :shiftId")
    suspend fun updatePauseState(shiftId: String, isPaused: Boolean, pauseStartTime: Long?)

    /**
     * Сохранение начала/конца простоя
     */
    @Query("UPDATE shifts SET isIdle = :isIdle, idleStartTime = :idleStartTime, idleReason = :reason WHERE id = :shiftId")
    suspend fun updateIdleState(shiftId: String, isIdle: Boolean, idleStartTime: Long?, reason: String?)
}
