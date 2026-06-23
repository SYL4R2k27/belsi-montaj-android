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

    /**
     * POST /shift/pause/start { reason }
     * FIX(2026-05-22) P0-2: reason теперь сохраняется в очередь. Раньше его не было,
     * и PendingSyncWorker хардкодил reason=null → офлайн «Обед»/«Перекур» (break:lunch/
     * break:smoke) при синке превращались в обычную паузу (event reason терялся).
     * null = обычная пауза; "break:lunch" = Обед; "break:smoke" = Перекур.
     */
    @Serializable
    data class StartPause(
        val shiftId: String,
        val reason: String? = null,
        // FIX(2026-05-22) P0-1: реальное время старта паузы (epoch ms), захваченное в момент
        // постановки в очередь. Worker конвертит в ISO и шлёт серверу → started_at не схлопнется
        // в NOW() при отложенном синке. По умолчанию = момент создания action (≈ реальный старт).
        val startedAt: Long = System.currentTimeMillis(),
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
        // FIX(2026-05-22) P0-1: реальное время старта простоя (epoch ms) для отложенного синка.
        val startedAt: Long = System.currentTimeMillis(),
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

    /**
     * FIX(2026-05-11) BELSI 2.0.0 build8: производственные перерывы (обед, перекур).
     * POST /shift/break/start { type: "smoke" | "lunch" }
     */
    @Serializable
    data class StartBreak(
        val shiftId: String,
        val type: String,   // "smoke" | "lunch"
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /**
     * FIX(2026-05-11) BELSI 2.0.0 build8: завершение производственного перерыва.
     * POST /shift/break/end
     */
    @Serializable
    data class EndBreak(
        val shiftId: String,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    // ============================================================
    // FIX(2026-05-12) build18 P2: новые типы actions для монтажа.
    // ============================================================

    /** POST /coordinator/photos/{id}/approve  или  /curator/photos/{id}/approve  */
    @Serializable
    data class ApprovePhoto(
        val photoId: String,
        val role: String,  // "coordinator" / "curator" / "foreman"
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /coordinator/photos/{id}/reject  или  /curator/photos/{id}/reject  */
    @Serializable
    data class RejectPhoto(
        val photoId: String,
        val role: String,
        val reason: String? = null,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /foreman/tasks  или  /coordinator/tasks  или  /curator/tasks  */
    @Serializable
    data class CreateTask(
        val title: String,
        val description: String? = null,
        val assignedTo: String,
        val priority: String = "normal",
        val role: String,  // bridge: какой router использовать
        val dueAt: String? = null,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /production/batches/{id}/receive  */
    @Serializable
    data class BatchReceive(
        val batchId: String,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /production/batches/{id}/install  */
    @Serializable
    data class BatchInstall(
        val batchId: String,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /foreman/tools/issue  */
    @Serializable
    data class ToolIssue(
        val toolId: String,
        val installerId: String,
        val comment: String? = null,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()

    /** POST /foreman/tools/return  */
    @Serializable
    data class ToolReturn(
        val transactionId: String,
        val condition: String,   // "good" / "damaged" / "broken"
        val comment: String? = null,
        override val createdAt: Long = System.currentTimeMillis(),
    ) : PendingAction()
}
