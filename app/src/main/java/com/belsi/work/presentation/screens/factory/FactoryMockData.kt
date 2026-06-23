package com.belsi.work.presentation.screens.factory

/**
 * FIX(2026-05-05): мок-данные для производственных экранов.
 * FIX(2026-05-12) BELSI 2.0.0 build14: оставлены только структуры реально используемые
 * как fallback или для UI compatibility. Удалены мёртвые поля:
 *  - groupWorkers (Senior/Chief используют реальный BrigadeMember)
 *  - supplyRequests (Supplier использует реальный MaterialOrder)
 *  - engineerTasks (Engineer использует реальный EngineerTask)
 *  - factoryPhotos / activeIdles / facilityKpi (Chief использует реальный FacilityDashboard)
 *  - workerTasks (Worker использует EngineerTask через /production/engineer/tasks?mine=true)
 *  - Facility (заменён реальным data.models.Facility через ProductionRepository)
 *
 * Остались для fallback / UI shape compatibility:
 *  - FACILITY_NAME (placeholder когда activeFacilityId ещё не загружен)
 *  - productionIdleReasons (fallback при offline когда API не ответил)
 *  - BatchStatus + Batch + toMockShape (UI рисует одинаково mock и real)
 */
object FactoryMockData {

    // ─────────── Placeholder фабрики ───────────
    const val FACILITY_NAME = "Углич — фабрика №1"
    const val FACILITY_ADDRESS = "Ярославская обл., г. Углич, ул. Пролетарская 14"

    // ─────────── Причины простоя (fallback при offline) ───────────
    val productionIdleReasons = listOf(
        "Жду материалы",
        "Нет работы",
        "Поломка станка",
        "Жду комплектатора",
        "Жду чертежи от инженера",
        "Жду ОТК / приёмку",
        "Отключение электричества",
        "Другое",
    )

    // ─────────── Партии — Batch shape для UI ───────────
    enum class BatchStatus(val label: String, val emoji: String) {
        DRAFT("Черновик", "📝"),
        IN_PRODUCTION("В работе", "🔨"),
        READY_TO_SHIP("Готова к отгрузке", "📦"),
        IN_ROUTE("В пути", "🚛"),
        DELIVERED("Доставлена", "📍"),
        INSTALLED("Смонтирована", "✅"),
    }

    /** Локальная UI-форма Batch для совместимости (real Batch.toMockShape() → этот тип). */
    data class Batch(
        val id: String,
        val title: String,
        val targetObject: String,
        val itemCount: Int,
        val deadline: String,
        val status: BatchStatus,
        val responsible: String,
    )

    /**
     * FIX(2026-05-12) build14: batches list УБРАН.
     * BatchListScreen больше не делает mock-fallback — empty state honest.
     */

    // ─────────── Sub-types для UI (минимум) ───────────
    // Используются в WorkerMainScreen для статус-pills (даже после real-timers
    // эти типы могут пригодиться для legacy кода).
    data class WorkerTask(
        val id: String,
        val title: String,
        val from: String,
        val priority: String,
        val done: Boolean,
    )
}
