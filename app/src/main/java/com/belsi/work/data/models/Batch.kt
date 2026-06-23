package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * FIX(2026-05-05): Сущность «Партия» — Pipeline производство → логистика → монтаж.
 * Соответствует серверному production_batches table + ProductionBatch ORM.
 *
 * Жизненный цикл:
 *  draft → in_production → ready_to_ship → in_route → delivered → installed
 *  + cancelled (с любого этапа)
 */

@Serializable
enum class BatchStatus {
    @SerialName("draft") DRAFT,
    @SerialName("in_production") IN_PRODUCTION,
    @SerialName("ready_to_ship") READY_TO_SHIP,
    @SerialName("in_route") IN_ROUTE,
    @SerialName("delivered") DELIVERED,
    @SerialName("installed") INSTALLED,
    @SerialName("cancelled") CANCELLED;

    val label: String
        get() = when (this) {
            DRAFT -> "Черновик"
            IN_PRODUCTION -> "В работе"
            READY_TO_SHIP -> "Готова к отгрузке"
            IN_ROUTE -> "В пути"
            DELIVERED -> "Доставлена"
            INSTALLED -> "Смонтирована"
            CANCELLED -> "Отменена"
        }

    val emoji: String
        get() = when (this) {
            DRAFT -> "📝"
            IN_PRODUCTION -> "🔨"
            READY_TO_SHIP -> "📦"
            IN_ROUTE -> "🚛"
            DELIVERED -> "📍"
            INSTALLED -> "✅"
            CANCELLED -> "❌"
        }

    /** В каком домене партия сейчас «живёт» — для подсветки в UI. */
    val activeDomain: UserDomain
        get() = when (this) {
            DRAFT, IN_PRODUCTION, READY_TO_SHIP -> UserDomain.PRODUCTION
            IN_ROUTE -> UserDomain.LOGISTICS
            DELIVERED, INSTALLED -> UserDomain.INSTALLATION
            CANCELLED -> UserDomain.OBSERVER
        }
}

@Serializable
data class Batch(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val title: String,
    val description: String? = null,
    @SerialName("item_count")
    val itemCount: Int,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("source_facility_id")
    val sourceFacilityId: UUID,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("target_object_id")
    val targetObjectId: UUID? = null,
    val status: BatchStatus,
    val priority: String = "normal",
    val deadline: String? = null,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("created_by")
    val createdBy: UUID,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("responsible_user_id")
    val responsibleUserId: UUID? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class BatchCreateRequest(
    val title: String,
    val description: String? = null,
    @SerialName("item_count")
    val itemCount: Int = 0,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("source_facility_id")
    val sourceFacilityId: UUID,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("target_object_id")
    val targetObjectId: UUID? = null,
    val priority: String = "normal",
    val deadline: String? = null,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("responsible_user_id")
    val responsibleUserId: UUID? = null,
)

@Serializable
data class BatchStatusChangeRequest(
    @SerialName("to_status")
    val toStatus: BatchStatus,
    val comment: String? = null,
)

/**
 * FIX(2026-05-12) BELSI 2.0.0 build15: партия "едет к нам" для бригадира/координатора.
 * Источник: GET /production/batches/incoming
 */
@Serializable
data class IncomingBatchDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("status") val status: String,
    @SerialName("target_object_id") val targetObjectId: String? = null,
    @SerialName("source_facility_id") val sourceFacilityId: String? = null,
    @SerialName("deadline") val deadline: String? = null,
    @SerialName("responsible_user_id") val responsibleUserId: String? = null,
    @SerialName("target_object_name") val targetObjectName: String? = null,
    @SerialName("facility_name") val facilityName: String? = null,
)

@Serializable
data class BatchHistoryItem(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("batch_id")
    val batchId: UUID,
    @SerialName("from_status")
    val fromStatus: String? = null,
    @SerialName("to_status")
    val toStatus: String,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("changed_by")
    val changedBy: UUID,
    @SerialName("changed_at")
    val changedAt: String,
    val comment: String? = null,
)

@Serializable
data class IdleReason(
    val code: String,
    val label: String,
    val position: Int,
    val domain: String,
)
