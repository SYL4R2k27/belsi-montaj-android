package com.belsi.work.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Отчет по сменам
 */
@Serializable
data class ShiftReport(
    val entries: List<ShiftReportEntry>,
    val totalShifts: Int,
    val totalWorkHours: Double,
    val totalAmount: Double,
    val periodStart: String,  // ISO date
    val periodEnd: String     // ISO date
)

/**
 * Запись в отчете - данные одной смены одного сотрудника
 */
@Serializable
data class ShiftReportEntry(
    @Serializable(with = UUIDSerializer::class)
    val shiftId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val userName: String,
    val userFullName: String?,
    val userPhone: String,
    val shiftDate: String,  // ISO date
    val startTime: Long,    // timestamp
    val endTime: Long?,     // timestamp, null if shift is active
    val totalSeconds: Long, // общее время смены в секундах
    val workSeconds: Long,  // чистое время работы (без пауз и простоя)
    val pauseSeconds: Long, // суммарное время пауз
    val idleSeconds: Long,  // суммарное время простоя
    val idleReason: String?, // причина простоя
    val hourlyRate: Double,  // ставка руб/час
    val totalAmount: Double, // итоговая сумма за смену
    @Serializable(with = UUIDSerializer::class)
    val foremanId: UUID?,
    val foremanName: String?,
    @Serializable(with = UUIDSerializer::class)
    val curatorId: UUID?,
    val curatorName: String?,
    val status: ShiftReportStatus
) {
    /**
     * Форматированное общее время
     */
    val formattedTotalTime: String
        get() {
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return String.format("%02d:%02d", hours, minutes)
        }

    /**
     * Форматированное время работы
     */
    val formattedWorkTime: String
        get() {
            val hours = workSeconds / 3600
            val minutes = (workSeconds % 3600) / 60
            return String.format("%02d:%02d", hours, minutes)
        }

    /**
     * Форматированное время паузы
     */
    val formattedPauseTime: String
        get() {
            val hours = pauseSeconds / 3600
            val minutes = (pauseSeconds % 3600) / 60
            return String.format("%02d:%02d", hours, minutes)
        }

    /**
     * Форматированное время простоя
     */
    val formattedIdleTime: String
        get() {
            val hours = idleSeconds / 3600
            val minutes = (idleSeconds % 3600) / 60
            return String.format("%02d:%02d", hours, minutes)
        }

    /**
     * Часы работы в десятичном формате (для расчетов)
     */
    val workHours: Double
        get() = workSeconds / 3600.0

    /**
     * Закреплен за (бригадир или куратор)
     */
    val assignedTo: String
        get() = foremanName ?: curatorName ?: "Не назначен"
}

@Serializable
enum class ShiftReportStatus {
    ACTIVE,    // Смена активна
    COMPLETED, // Смена завершена
    FINISHED,  // Смена завершена (альтернативный статус)
    CANCELLED  // Смена отменена
}
