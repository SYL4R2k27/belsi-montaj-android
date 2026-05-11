package com.belsi.work.data.offline

import kotlinx.serialization.Serializable

/**
 * FIX(2026-05-05): Типы действий в offline-очереди.
 *
 * Каждое действие сериализуется в JSON и пишется в pending_actions.
 * При появлении сети WorkManager берёт по одному и пытается отправить.
 *
 * Можно добавлять новые типы — старые юзеры просто проигнорируют (по строковому
 * actionType) и оставят в очереди до следующего апдейта.
 *
 * Сейчас покрываем самые критичные действия для строительной смены:
 * - Старт/финиш смены
 * - Pause/Idle (перерывы и простои)
 * - Загрузка фото (с локальным путём к файлу)
 * - Закрытие задачи
 */
@Serializable
sealed class PendingAction {

    abstract val createdAt: Long

    /** POST /shift/start */
    @Serializable
    data class StartShift(
        val siteObjectId: String? = null,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /shift/finish */
    @Serializable
    data class FinishShift(
        val shiftId: String,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /shift/pause/start */
    @Serializable
    data class StartPause(
        val shiftId: String,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /shift/pause/end */
    @Serializable
    data class EndPause(
        val shiftId: String,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /shift/idle/start { reason } */
    @Serializable
    data class StartIdle(
        val shiftId: String,
        val reason: String,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /shift/idle/end */
    @Serializable
    data class EndIdle(
        val shiftId: String,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /**
     * POST /photos/upload (multipart).
     * localFilePath — путь к файлу на диске устройства (Internal Storage).
     * Файл живёт до момента успешной отправки или истечения retries=10.
     */
    @Serializable
    data class UploadPhoto(
        val shiftId: String,
        val localFilePath: String,
        val hourLabel: String? = null,
        val comment: String? = null,
        val category: String = "hourly",   // hourly / problem / question
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** PATCH /tasks/{id} { status } */
    @Serializable
    data class UpdateTaskStatus(
        val taskId: String,
        val newStatus: String,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()
}
