package com.belsi.work.data.models

import java.util.Date
import java.util.UUID

enum class SlotStatus {
    UPCOMING,       // Час ещё не наступил
    NEED_PHOTO,     // Час идёт/прошёл, фото нет
    HAS_PHOTO,      // Фото сделано
    PENDING_REVIEW, // На модерации
    APPROVED,       // Одобрено
    REJECTED;       // Отклонено
    
    val displayName: String
        get() = when (this) {
            UPCOMING -> "Ожидается"
            NEED_PHOTO -> "Требуется фото"
            HAS_PHOTO -> "Фото сделано"
            PENDING_REVIEW -> "На модерации"
            APPROVED -> "Одобрено"
            REJECTED -> "Отклонено"
        }
}

enum class ShiftStatus {
    NOT_STARTED,    // Смена не началась
    IN_PROGRESS,    // Смена идёт
    ON_PAUSE,       // Смена на паузе
    COMPLETED,      // Смена завершена
    CANCELLED;      // Смена отменена
    
    val displayName: String
        get() = when (this) {
            NOT_STARTED -> "Не начата"
            IN_PROGRESS -> "В процессе"
            ON_PAUSE -> "На паузе"
            COMPLETED -> "Завершена"
            CANCELLED -> "Отменена"
        }
}

data class ShiftSlot(
    val id: UUID = UUID.randomUUID(),
    val index: Int,
    val plannedStart: Date,
    var status: SlotStatus = SlotStatus.UPCOMING,
    var localPhotoID: UUID? = null,
    val remotePhotoUrl: String? = null,
    val rejectionReason: String? = null
)

data class Shift(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    var status: ShiftStatus = ShiftStatus.NOT_STARTED,
    val slots: MutableList<ShiftSlot> = mutableListOf(),
    var totalElapsed: Long = 0,
    var pauseElapsed: Long = 0,
    val pauseStartedAt: Long? = null,
    val objectName: String? = null,
    val objectAddress: String? = null,
    val note: String? = null
) {
    val workingTime: Long
        get() = totalElapsed - pauseElapsed
    
    val completedSlots: Int
        get() = slots.count { it.status == SlotStatus.APPROVED }
    
    val pendingSlots: Int
        get() = slots.count { it.status == SlotStatus.PENDING_REVIEW }
}

