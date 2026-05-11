package com.belsi.work.presentation.screens.factory

/**
 * FIX(2026-05-05): мок-данные для производственных экранов.
 * Используются в RoleTestPlayground пока сервер не отдаёт реальные batch/shift API.
 * После подключения backend — этот файл удалится.
 */
object FactoryMockData {

    // ─────────── Текущая фабрика ───────────
    const val FACILITY_NAME = "Углич — фабрика №1"
    const val FACILITY_ADDRESS = "Ярославская обл., г. Углич, ул. Пролетарская 14"

    // ─────────── Список фабрик (для переключателя) ───────────
    data class Facility(val id: String, val name: String, val address: String, val active: Boolean = false)

    val facilities = listOf(
        Facility("ugl-1", "Углич — фабрика №1", "Ярославская обл., Углич", active = true),
        Facility("ugl-2", "Углич — цех №2", "Ярославская обл., Углич, новый цех"),
        Facility("ros-1", "Ростов — площадка А", "Ростовская обл., будущая фабрика"),
    )

    // ─────────── Причины простоя для производства ───────────
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

    // ─────────── Партии ───────────
    enum class BatchStatus(val label: String, val emoji: String) {
        DRAFT("Черновик", "📝"),
        IN_PRODUCTION("В работе", "🔨"),
        READY_TO_SHIP("Готова к отгрузке", "📦"),
        IN_ROUTE("В пути", "🚛"),
        DELIVERED("Доставлена", "📍"),
        INSTALLED("Смонтирована", "✅"),
    }

    data class Batch(
        val id: String,
        val title: String,
        val targetObject: String,
        val itemCount: Int,
        val deadline: String,
        val status: BatchStatus,
        val responsible: String,
    )

    val batches = listOf(
        Batch("UGL-2026-05-05-0042", "Подоконники белые 1.5м", "Школа №7, Коломенская 16", 20, "Сегодня 14:00", BatchStatus.IN_PRODUCTION, "Старший Петров"),
        Batch("UGL-2026-05-05-0041", "Шкафы-купе серия А", "ЖК «Солнечный», к2", 8, "Завтра 10:00", BatchStatus.READY_TO_SHIP, "Старший Иванов"),
        Batch("UGL-2026-05-05-0040", "Столешницы дуб", "Школа №12, Шипиловский 23", 15, "06.05 12:00", BatchStatus.DRAFT, "—"),
        Batch("UGL-2026-05-04-0039", "Полки навесные", "Школа №7, Коломенская 16", 30, "Вчера 16:00", BatchStatus.DELIVERED, "Старший Петров"),
        Batch("UGL-2026-05-04-0038", "Двери межкомнатные", "ЖК «Парус»", 12, "Вчера 18:00", BatchStatus.INSTALLED, "Старший Сидоров"),
    )

    // ─────────── Команда (для Старшего работника / Начальника) ───────────
    data class Worker(
        val id: String,
        val name: String,
        val status: String,        // "На смене 4ч 12мин" / "Перекур 8мин" / "Простой 12мин"
        val statusType: WorkerStatusType,
        val currentTask: String?,
    )

    enum class WorkerStatusType { WORKING, BREAK, LUNCH, IDLE, OFFLINE }

    val groupWorkers = listOf(
        Worker("w1", "Иван Петров", "На смене 4ч 12мин", WorkerStatusType.WORKING, "Партия UGL-0042"),
        Worker("w2", "Сергей Иванов", "Перекур 6мин", WorkerStatusType.BREAK, "Партия UGL-0042"),
        Worker("w3", "Дмитрий Соколов", "Обед 32мин", WorkerStatusType.LUNCH, null),
        Worker("w4", "Алексей Морозов", "Простой 14мин — жду материалы", WorkerStatusType.IDLE, "Партия UGL-0040"),
        Worker("w5", "Николай Кузнецов", "На смене 5ч 04мин", WorkerStatusType.WORKING, "Партия UGL-0041"),
    )

    // ─────────── Запросы материалов (для Комплектатора) ───────────
    data class SupplyRequest(
        val id: String,
        val from: String,
        val item: String,
        val qty: String,
        val urgency: SupplyUrgency,
        val time: String,
    )

    enum class SupplyUrgency(val label: String) {
        URGENT("Срочно"),
        NORMAL("Обычная"),
    }

    val supplyRequests = listOf(
        SupplyRequest("r1", "Иван Петров", "ЛДСП белая 16мм", "5 листов", SupplyUrgency.URGENT, "только что"),
        SupplyRequest("r2", "Сергей Иванов", "Кромка ПВХ 0.4мм", "30 м", SupplyUrgency.NORMAL, "3 мин"),
        SupplyRequest("r3", "Алексей Морозов", "Конфирмат 6.4×50", "200 шт", SupplyUrgency.URGENT, "8 мин"),
    )

    // ─────────── Self-tasks инженера ───────────
    data class EngineerTask(
        val id: String,
        val title: String,
        val status: String,        // "Открыто" / "В работе" / "Готово"
        val createdAt: String,
        val targetRole: String?,   // null = для себя
    )

    val engineerTasks = listOf(
        EngineerTask("e1", "Чертёж шкафа-купе серии Б", "В работе", "Сегодня", null),
        EngineerTask("e2", "Программа раскроя для ЛДСП дуб", "Готово", "Вчера", null),
        EngineerTask("e3", "Замерить углы на новом станке", "Открыто", "2 ч назад", "Работнику Петрову"),
    )

    // ─────────── Лента фото (для Начальника производства) ───────────
    data class FactoryPhoto(
        val id: String,
        val author: String,
        val role: String,
        val time: String,
        val comment: String,
        val tag: String?, // null или "брак" / "вопрос"
    )

    val factoryPhotos = listOf(
        FactoryPhoto("p1", "Иван Петров", "Работник", "Только что", "Боковина левая, готова", null),
        FactoryPhoto("p2", "Сергей Иванов", "Работник", "12 мин назад", "Кромка наклеена, проверьте качество", "вопрос"),
        FactoryPhoto("p3", "Алексей Морозов", "Работник", "30 мин", "Скол на углу — отбраковка", "брак"),
        FactoryPhoto("p4", "Николай Кузнецов", "Работник", "1 ч", "Партия UGL-0041 собрана", null),
    )

    // ─────────── Активные простои на фабрике (для Начальника) ───────────
    data class ActiveIdle(
        val worker: String,
        val reason: String,
        val durationMin: Int,
    )

    val activeIdles = listOf(
        ActiveIdle("Алексей Морозов", "Жду материалы", 14),
    )

    // ─────────── KPI фабрики (для Начальника) ───────────
    data class FacilityKpi(
        val workersOnShift: Int,
        val workersTotal: Int,
        val activeIdleCount: Int,
        val batchesInProduction: Int,
        val batchesReadyToShip: Int,
    )

    val facilityKpi = FacilityKpi(
        workersOnShift = 12,
        workersTotal = 14,
        activeIdleCount = 1,
        batchesInProduction = 3,
        batchesReadyToShip = 2,
    )

    // ─────────── Задачи Работника ───────────
    data class WorkerTask(
        val id: String,
        val title: String,
        val from: String,
        val priority: String, // "Высокий" / "Обычный"
        val done: Boolean,
    )

    val workerTasks = listOf(
        WorkerTask("t1", "Собрать боковины для UGL-0042", "Старший Петров", "Высокий", done = false),
        WorkerTask("t2", "Проверить кромку на партии 0041", "Инженер", "Обычный", done = false),
        WorkerTask("t3", "Сделать замер угла", "Старший Петров", "Обычный", done = true),
    )
}
