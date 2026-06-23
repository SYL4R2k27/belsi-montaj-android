package com.belsi.work.data.remote.dto.driver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * FIX(2026-05-11) BELSI 2.0.0: DTO для driver/logistician backend (10 endpoints driver + 9 logist).
 *
 * Поля точно соответствуют backend Pydantic-моделям из driver_logist.py.
 */

@Serializable
data class RoutePointOutDto(
    @SerialName("id") val id: String,
    @SerialName("seq") val seq: Int,
    @SerialName("point_type") val pointType: String, // pickup|delivery|transit|return
    @SerialName("address") val address: String,
    @SerialName("scheduled_time") val scheduledTime: String? = null,
    @SerialName("site_object_id") val siteObjectId: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("cargo") val cargo: String? = null,
    @SerialName("status") val status: String, // pending|arrived|delivered|skipped
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("skip_reason") val skipReason: String? = null,
    @SerialName("notes") val notes: String? = null,
    // FIX(2026-05-12) BELSI 2.0.0 build15: связь с партией
    @SerialName("batch_id") val batchId: String? = null,
)

@Serializable
data class RouteOutDto(
    @SerialName("id") val id: String,
    @SerialName("driver_id") val driverId: String,
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("logistician_id") val logisticianId: String? = null,
    @SerialName("logistician_name") val logisticianName: String? = null,
    @SerialName("planned_date") val plannedDate: String, // ISO date
    @SerialName("status") val status: String, // planned|active|completed|cancelled
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("total_points") val totalPoints: Int = 0,
    @SerialName("completed_points") val completedPoints: Int = 0,
    @SerialName("notes") val notes: String? = null,
    @SerialName("points") val points: List<RoutePointOutDto> = emptyList(),
) {
    val progress: Float get() = if (totalPoints == 0) 0f else completedPoints.toFloat() / totalPoints

    val nextPoint: RoutePointOutDto? get() =
        points.firstOrNull { it.status == "pending" || it.status == "arrived" }
}

@Serializable
data class RoutePointIn(
    @SerialName("seq") val seq: Int,
    @SerialName("point_type") val pointType: String,
    @SerialName("address") val address: String,
    @SerialName("scheduled_time") val scheduledTime: String? = null,
    @SerialName("site_object_id") val siteObjectId: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("cargo") val cargo: String? = null,
    @SerialName("notes") val notes: String? = null,
    // FIX(2026-05-12) BELSI 2.0.0 build15: связь точки с партией
    @SerialName("batch_id") val batchId: String? = null,
)

@Serializable
data class RouteCreateIn(
    @SerialName("driver_id") val driverId: String,
    @SerialName("planned_date") val plannedDate: String,
    @SerialName("notes") val notes: String? = null,
    @SerialName("points") val points: List<RoutePointIn>,
)

/**
 * FIX(2026-05-11) BELSI 2.0.0 build11: событие точки.
 * Источник: таблица `driver_point_events` на бэке.
 */
@Serializable
data class DriverPointEventDto(
    @SerialName("id") val id: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("accuracy_meters") val accuracyMeters: Float? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
)

// ═══════════════════════════════════════════════════════════════════════════
// FIX(2026-05-11) BELSI 2.0.0 build13: composite DTO для logist UI.
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Composite ответ /logistician/drivers/{id}/detail:
 *   driver + active_shift + routes + shift_history.
 */
@Serializable
data class DriverDetailDto(
    @SerialName("driver") val driver: DriverInfoDto,
    @SerialName("active_shift") val activeShift: DriverShiftDto? = null,
    @SerialName("routes") val routes: List<DriverRouteSummaryDto> = emptyList(),
    @SerialName("shift_history") val shiftHistory: List<DriverShiftDto> = emptyList(),
)

@Serializable
data class DriverInfoDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("phone") val phone: String,
    @SerialName("role") val role: String,
    @SerialName("online") val online: Boolean = false,
)

@Serializable
data class DriverShiftDto(
    @SerialName("id") val id: String,
    @SerialName("start_at") val startAt: String,
    @SerialName("finish_at") val finishAt: String? = null,
    @SerialName("status") val status: String,
    @SerialName("total_seconds") val totalSeconds: Long? = null,
    @SerialName("pause_seconds") val pauseSeconds: Long? = null,
    @SerialName("idle_seconds") val idleSeconds: Long? = null,
    // FIX(2026-05-18): backend теперь раздельно агрегирует lunch + smoke breaks.
    @SerialName("lunch_seconds") val lunchSeconds: Long? = null,
    @SerialName("break_seconds") val breakSeconds: Long? = null,
    @SerialName("domain") val domain: String? = null,
    @SerialName("idle_reason") val idleReason: String? = null,
    @SerialName("shift_start_photo_url") val shiftStartPhotoUrl: String? = null,
    @SerialName("shift_end_photo_url") val shiftEndPhotoUrl: String? = null,
    @SerialName("shift_start_lat") val shiftStartLat: Double? = null,
    @SerialName("shift_start_lng") val shiftStartLng: Double? = null,
    @SerialName("shift_end_lat") val shiftEndLat: Double? = null,
    @SerialName("shift_end_lng") val shiftEndLng: Double? = null,
    @SerialName("site_object_id") val siteObjectId: String? = null,
)

@Serializable
data class DriverRouteSummaryDto(
    @SerialName("id") val id: String,
    @SerialName("planned_date") val plannedDate: String,
    @SerialName("status") val status: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("total_points") val totalPoints: Int = 0,
    @SerialName("completed_points") val completedPoints: Int = 0,
    @SerialName("notes") val notes: String? = null,
    @SerialName("logist_name") val logistName: String? = null,
) {
    val progress: Float get() = if (totalPoints == 0) 0f else completedPoints.toFloat() / totalPoints
}

/**
 * Composite /logistician/routes/{id}/full: маршрут + точки + события каждой точки.
 */
@Serializable
data class RouteFullDto(
    @SerialName("id") val id: String,
    @SerialName("driver_id") val driverId: String,
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("driver_phone") val driverPhone: String? = null,
    @SerialName("logistician_id") val logisticianId: String? = null,
    @SerialName("planned_date") val plannedDate: String,
    @SerialName("status") val status: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("total_points") val totalPoints: Int = 0,
    @SerialName("completed_points") val completedPoints: Int = 0,
    @SerialName("notes") val notes: String? = null,
    @SerialName("points") val points: List<RoutePointWithEventsDto> = emptyList(),
) {
    val progress: Float get() = if (totalPoints == 0) 0f else completedPoints.toFloat() / totalPoints
}

@Serializable
data class RoutePointWithEventsDto(
    @SerialName("id") val id: String,
    @SerialName("seq") val seq: Int,
    @SerialName("point_type") val pointType: String,
    @SerialName("address") val address: String,
    @SerialName("scheduled_time") val scheduledTime: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("cargo") val cargo: String? = null,
    @SerialName("status") val status: String,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("notes") val notes: String? = null,
    // FIX(2026-05-12) BELSI 2.0.0 build15: связь точки с партией и объектом
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("site_object_id") val siteObjectId: String? = null,
    @SerialName("events") val events: List<DriverPointEventDto> = emptyList(),
)

@Serializable
data class DeliveryRequestOutDto(
    @SerialName("id") val id: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("creator_name") val creatorName: String? = null,
    @SerialName("site_object_id") val siteObjectId: String? = null,
    @SerialName("object_name_snapshot") val objectName: String? = null,
    @SerialName("cargo") val cargo: String,
    @SerialName("need_by_time") val needByTime: String? = null,
    @SerialName("need_by_date") val needByDate: String,
    @SerialName("priority") val priority: String,
    @SerialName("status") val status: String,
    @SerialName("assigned_route_id") val assignedRouteId: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class DeliveryRequestIn(
    @SerialName("site_object_id") val siteObjectId: String? = null,
    @SerialName("object_name_snapshot") val objectName: String? = null,
    @SerialName("cargo") val cargo: String,
    @SerialName("need_by_time") val needByTime: String? = null,
    @SerialName("need_by_date") val needByDate: String? = null,
    @SerialName("priority") val priority: String = "normal",
    @SerialName("notes") val notes: String? = null,
    // FIX(2026-05-12) BELSI 2.0.0 build17: координатор может связать заявку с конкретной партией.
    // На бэке колонка delivery_requests.batch_id есть с build15.
    @SerialName("batch_id") val batchId: String? = null,
)

@Serializable
data class DriverFleetItemDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("phone") val phone: String,
    @SerialName("status") val status: String, // active|free|offline
    @SerialName("current_route_id") val currentRouteId: String? = null,
    @SerialName("current_progress") val currentProgress: String? = null,
)

@Serializable
data class SkipPointIn(
    @SerialName("reason") val reason: String,
)

@Serializable
data class AssignRequestIn(
    @SerialName("route_id") val routeId: String,
    @SerialName("point_id") val pointId: String? = null,
)

@Serializable
data class DriverDashboardDto(
    @SerialName("active_route_id") val activeRouteId: String? = null,
    @SerialName("progress") val progress: String? = null,
    @SerialName("routes_today") val routesToday: Int = 0,
)

@Serializable
data class LogistDashboardDto(
    @SerialName("routes_today") val routesToday: Int = 0,
    @SerialName("active_routes") val activeRoutes: Int = 0,
    @SerialName("pending_requests") val pendingRequests: Int = 0,
    @SerialName("free_drivers") val freeDrivers: Int = 0,
)
