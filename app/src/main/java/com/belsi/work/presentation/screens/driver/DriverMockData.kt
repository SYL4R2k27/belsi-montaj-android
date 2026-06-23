package com.belsi.work.presentation.screens.driver

import com.belsi.work.data.remote.dto.driver.RouteOutDto
import com.belsi.work.data.remote.dto.driver.RoutePointOutDto
import java.util.UUID

/**
 * FIX(2026-05-03): mock-данные для Этапа A.
 * FIX(2026-05-11) BELSI 2.0.0: backend (driver_logist.py, 14 endpoint'ов) и Retrofit
 * клиент (DriverApi/LogistApi + DriverViewModels) подключены.
 * MockData оставлен как fallback для role-switcher demo (когда у dev-юзера
 * нет реально назначенных маршрутов) — adapters внизу файла.
 */
object DriverMockData {

    enum class PointType(val emoji: String, val title: String) {
        PICKUP("📦↑", "Получение"),
        DELIVERY("📦↓", "Доставка"),
        TRANSIT("🚚", "В пути"),
        RETURN("↩️", "Возврат"),
    }

    enum class PointStatus { PENDING, ARRIVED, DELIVERED }

    data class RoutePoint(
        val id: String,
        val seq: Int,
        val time: String,
        val type: PointType,
        val address: String,
        val cargo: String?,
        val status: PointStatus,
    )

    data class Route(
        val id: String,
        val driverName: String,
        val driverInitials: String,
        val plannedDate: String,
        val points: List<RoutePoint>,
    ) {
        val progress: Float
            get() = if (points.isEmpty()) 0f else
                points.count { it.status == PointStatus.DELIVERED }.toFloat() / points.size

        val nextPoint: RoutePoint? get() = points.firstOrNull { it.status != PointStatus.DELIVERED }
    }

    data class DeliveryRequest(
        val id: String,
        val objectName: String,
        val needBy: String,
        val cargo: String,
        val createdBy: String,
    )

    data class DriverFleetItem(
        val id: String,
        val initials: String,
        val name: String,
        val status: FleetStatus,
        val current: String? = null, // text like "Маршрут #234 · 1/4"
    )

    enum class FleetStatus { ACTIVE, FREE, OFFLINE }

    val activeRoute = Route(
        id = "rt-234",
        driverName = "Иванов Сергей",
        driverInitials = "ИС",
        plannedDate = "03 мая",
        points = listOf(
            RoutePoint("p1", 1, "08:00", PointType.PICKUP, "Склад «Белая дача»", "Получение материалов", PointStatus.DELIVERED),
            RoutePoint("p2", 2, "11:00", PointType.DELIVERY, "Шипиловский 23А", "5 коробок саморезов", PointStatus.ARRIVED),
            RoutePoint("p3", 3, "12:00", PointType.DELIVERY, "Годовикова 16А", "20 листов профиля", PointStatus.PENDING),
            RoutePoint("p4", 4, "14:00", PointType.DELIVERY, "Коломенская набережная 16", "12 окон", PointStatus.PENDING),
            RoutePoint("p5", 5, "16:00", PointType.RETURN, "Возврат на склад", "Упаковка / возвраты", PointStatus.PENDING),
        )
    )

    val pendingRequests = listOf(
        DeliveryRequest("req-1", "Шипиловский 23А", "к 11:00", "5 коробок саморезов", "Сибилев"),
        DeliveryRequest("req-2", "Годовикова 16А", "к 12:00", "20 листов профиля", "Хрулёв"),
        DeliveryRequest("req-3", "Коломенская 16", "к 14:00", "12 окон", "Сибилев"),
        DeliveryRequest("req-4", "Стасова 5А", "к 15:30", "Профиль алюминий", "Сибилев"),
    )

    val activeRoutes = listOf(activeRoute)

    val fleet = listOf(
        DriverFleetItem("d1", "ИС", "Иванов Сергей", FleetStatus.ACTIVE, "Маршрут #234 · 1/4"),
        DriverFleetItem("d2", "ПА", "Петров Алексей", FleetStatus.ACTIVE, "Маршрут #235 · 3/5"),
        DriverFleetItem("d3", "СМ", "Сидоров Михаил", FleetStatus.FREE, "Свободен"),
        DriverFleetItem("d4", "КИ", "Кузнецов Иван", FleetStatus.FREE, "В офисе"),
        DriverFleetItem("d5", "ВД", "Волков Денис", FleetStatus.OFFLINE, "Последний онлайн вчера 18:00"),
    )

    data class HistoryEvent(
        val time: String,
        val emoji: String,
        val title: String,
        val detail: String?,
        val type: HistoryType,
    )

    enum class HistoryType { DELIVERY, SHIFT, PHOTO, ADMIN, CREATE }

    val historyToday = listOf(
        HistoryEvent("09:30", "🚛", "Иванов привёз окна", "12 окон, 3 поддона профиля · 8 фото", HistoryType.DELIVERY),
        HistoryEvent("10:00", "🔨", "Петров открыл смену", "Бригадир: Хрулёв", HistoryType.SHIFT),
        HistoryEvent("11:00", "📸", "Петров: фото \"11:00\"", "✓ Подтверждено бригадиром", HistoryType.PHOTO),
        HistoryEvent("12:30", "🚛", "Сидоров привёз профиль", "⚠ Опоздание 30 мин", HistoryType.DELIVERY),
        HistoryEvent("14:00", "⚙️", "Куратор скорректировал смену", "Петров · сместил начало 10:00→09:45 · «опечатка»", HistoryType.ADMIN),
    )

    val historyYesterday = listOf(
        HistoryEvent("18:30", "✅", "Иванов завершил смену", "8 ч 15 мин чистого времени", HistoryType.SHIFT),
        HistoryEvent("10:00", "🏗", "Объект создан", "Координатор: Сибилев · 3 фото осмотра", HistoryType.CREATE),
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// FIX(2026-05-11) BELSI 2.0.0: Adapters от backend DTO → DriverMockData type system
// (для повторного использования существующего UI с реальными данными)
// ═══════════════════════════════════════════════════════════════════════════

private fun parseHHMM(iso: String?): String =
    iso?.takeIf { it.length >= 16 }?.substring(11, 16) ?: "—"

fun RoutePointOutDto.toMockPoint(): DriverMockData.RoutePoint = DriverMockData.RoutePoint(
    id = id,
    seq = seq,
    time = scheduledTime ?: "—",
    type = when (pointType) {
        "pickup" -> DriverMockData.PointType.PICKUP
        "delivery" -> DriverMockData.PointType.DELIVERY
        "transit" -> DriverMockData.PointType.TRANSIT
        "return" -> DriverMockData.PointType.RETURN
        else -> DriverMockData.PointType.DELIVERY
    },
    address = address,
    cargo = cargo,
    status = when (status) {
        "pending" -> DriverMockData.PointStatus.PENDING
        "arrived" -> DriverMockData.PointStatus.ARRIVED
        "delivered", "skipped" -> DriverMockData.PointStatus.DELIVERED
        else -> DriverMockData.PointStatus.PENDING
    },
)

fun RouteOutDto.toMockRoute(): DriverMockData.Route {
    val initials = (driverName ?: "??").split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "ВД" }
    return DriverMockData.Route(
        id = id,
        driverName = driverName ?: "Водитель",
        driverInitials = initials,
        plannedDate = plannedDate.takeLast(5).replace("-", "."),
        points = points.map { it.toMockPoint() },
    )
}
