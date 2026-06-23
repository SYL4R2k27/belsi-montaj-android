package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * FIX(2026-05-06): DTOs для production-домена (variant C).
 * Соответствуют Pydantic-моделям в:
 *   - backend/belsi-api/app/production_brigade.py
 *   - backend/belsi-api/app/production_materials.py
 *   - backend/belsi-api/app/production_engineer.py
 */

// ─────────────────────────────────────────────────────────────────
// Бригада
// ─────────────────────────────────────────────────────────────────

@Serializable
data class Brigade(
    val id: String,
    val name: String,
    @SerialName("facility_id") val facilityId: String,
    @SerialName("senior_worker_id") val seniorWorkerId: String? = null,
    @SerialName("senior_name") val seniorName: String? = null,
    @SerialName("members_count") val membersCount: Int = 0,
    @SerialName("active_count") val activeCount: Int = 0,
    @SerialName("idle_count") val idleCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class BrigadeMember(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    @SerialName("role_in_brigade") val roleInBrigade: String,
    val phone: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_on_shift") val isOnShift: Boolean = false,
    @SerialName("on_pause") val onPause: Boolean = false,
    @SerialName("on_idle") val onIdle: Boolean = false,
    @SerialName("idle_reason") val idleReason: String? = null,
)

@Serializable
data class BrigadeCreateRequest(
    val name: String,
    @SerialName("facility_id") val facilityId: String,
    @SerialName("senior_worker_id") val seniorWorkerId: String? = null,
)

@Serializable
data class BrigadeMemberAddRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("role_in_brigade") val roleInBrigade: String = "worker",
)

// ─────────────────────────────────────────────────────────────────
// Фабрика
// ─────────────────────────────────────────────────────────────────

@Serializable
data class Facility(
    val id: String,
    val name: String,
    val address: String? = null,
)

@Serializable
data class FacilityDashboard(
    @SerialName("facility_id") val facilityId: String,
    @SerialName("facility_name") val facilityName: String,
    @SerialName("batches_total") val batchesTotal: Int = 0,
    @SerialName("batches_in_production") val batchesInProduction: Int = 0,
    @SerialName("batches_ready_to_ship") val batchesReadyToShip: Int = 0,
    @SerialName("batches_completed_today") val batchesCompletedToday: Int = 0,
    @SerialName("brigades_count") val brigadesCount: Int = 0,
    @SerialName("workers_total") val workersTotal: Int = 0,
    /** Все рабочие у которых finish_at IS NULL — включая обед, перекур, простой. */
    @SerialName("workers_on_shift") val workersOnShift: Int = 0,
    /** FIX(2026-05-14) BELSI 2.0.1: реально работают сейчас (без открытых пауз). */
    @SerialName("workers_actively_working") val workersActivelyWorking: Int = 0,
    /** Открытая пауза без reason (короткая). */
    @SerialName("workers_on_pause") val workersOnPause: Int = 0,
    /** Обед (break:lunch). */
    @SerialName("workers_on_lunch") val workersOnLunch: Int = 0,
    /** Перекур (break:smoke). */
    @SerialName("workers_on_smoke") val workersOnSmoke: Int = 0,
    /** Простой с причиной (idle:*). */
    @SerialName("workers_on_idle") val workersOnIdle: Int = 0,
    /** Сумма lunch+smoke. */
    @SerialName("workers_on_break") val workersOnBreak: Int = 0,
    @SerialName("idle_hours_today") val idleHoursToday: Double = 0.0,
    @SerialName("work_hours_today") val workHoursToday: Double = 0.0,
    @SerialName("break_hours_today") val breakHoursToday: Double = 0.0,
    @SerialName("lunch_hours_today") val lunchHoursToday: Double = 0.0,
    @SerialName("material_orders_pending") val materialOrdersPending: Int = 0,
)

// ─────────────────────────────────────────────────────────────────
// Материалы
// ─────────────────────────────────────────────────────────────────

@Serializable
data class Material(
    val id: String,
    val code: String,
    val name: String,
    val unit: String = "шт",
    val category: String? = null,
    @SerialName("min_stock") val minStock: Int = 0,
    val active: Boolean = true,
)

@Serializable
data class InventoryItem(
    @SerialName("facility_id") val facilityId: String,
    @SerialName("material_id") val materialId: String,
    val code: String,
    val name: String,
    val unit: String,
    val category: String? = null,
    val quantity: Int = 0,
    @SerialName("min_stock") val minStock: Int = 0,
    @SerialName("is_low") val isLow: Boolean = false,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class MaterialOrder(
    val id: String,
    @SerialName("facility_id") val facilityId: String,
    @SerialName("facility_name") val facilityName: String? = null,
    @SerialName("material_id") val materialId: String,
    @SerialName("material_code") val materialCode: String,
    @SerialName("material_name") val materialName: String,
    @SerialName("material_unit") val materialUnit: String,
    @SerialName("quantity_requested") val quantityRequested: Int,
    @SerialName("quantity_delivered") val quantityDelivered: Int = 0,
    val status: String,  // pending / approved / ordered / delivered / cancelled
    @SerialName("requested_by") val requestedBy: String,
    @SerialName("requested_by_name") val requestedByName: String? = null,
    @SerialName("approved_by") val approvedBy: String? = null,
    @SerialName("supplier_id") val supplierId: String? = null,
    @SerialName("supplier_name") val supplierName: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class MaterialOrderCreateRequest(
    @SerialName("facility_id") val facilityId: String,
    @SerialName("material_id") val materialId: String,
    @SerialName("quantity_requested") val quantityRequested: Int,
    val note: String? = null,
)

@Serializable
data class MaterialOrderStatusRequest(
    val status: String,
    @SerialName("quantity_delivered") val quantityDelivered: Int? = null,
    val note: String? = null,
)

@Serializable
data class InventoryAdjustRequest(
    @SerialName("facility_id") val facilityId: String,
    @SerialName("material_id") val materialId: String,
    val delta: Int,
    val reason: String? = null,
)

// ─────────────────────────────────────────────────────────────────
// Инженерные задачи
// ─────────────────────────────────────────────────────────────────

@Serializable
data class EngineerTask(
    val id: String,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("batch_title") val batchTitle: String? = null,
    @SerialName("facility_id") val facilityId: String? = null,
    @SerialName("facility_name") val facilityName: String? = null,
    val type: String,
    val title: String,
    val description: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("assigned_to_name") val assignedToName: String? = null,
    val status: String,
    val priority: String,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_by_name") val createdByName: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class EngineerTaskCreateRequest(
    val title: String,
    val type: String = "general",
    val description: String? = null,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("facility_id") val facilityId: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    val priority: String = "normal",
    @SerialName("due_at") val dueAt: String? = null,
)

@Serializable
data class EngineerTaskStatusRequest(
    val status: String,  // open / in_progress / done / cancelled
)

/** FIX(2026-05-14) BELSI 2.0.1: передача задачи другому инженеру. */
@Serializable
data class EngineerTaskReassignRequest(
    @SerialName("new_assignee_id") val newAssigneeId: String,
    val comment: String? = null,
)

/** Элемент списка инженеров для UI. */
@Serializable
data class EngineerPickItem(
    val id: String,
    val name: String? = null,
    val phone: String? = null,
    val role: String,            // engineer | senior_worker
    @SerialName("active_tasks") val activeTasks: Int = 0,
)

// FIX(2026-05-12) BELSI 2.0.0 build14: реальный tools catalog из tools_catalog таблицы.
@Serializable
data class ToolCatalogItem(
    val id: String,
    val code: String,
    val name: String,
    val category: String,
    @SerialName("inventory_number") val inventoryNumber: String? = null,
    val description: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("is_consumable") val isConsumable: Boolean = false,
)
