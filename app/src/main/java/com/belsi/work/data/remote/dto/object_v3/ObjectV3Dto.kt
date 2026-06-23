package com.belsi.work.data.remote.dto.object_v3

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO модели данных v3 (object → floor → zone → cabinet → window).
 * Соответствуют JSON-ответам backend /v3/... (site_object_v3.py, raw SQL .mappings()).
 * Контракт: docs/plans/2026-05-20-2.1.0-object-schema-v3-real-data.md
 */

@Serializable
data class FloorDto(
    @SerialName("id") val id: String,
    @SerialName("site_object_id") val siteObjectId: String? = null,
    @SerialName("floor_number") val floorNumber: Int,
    @SerialName("floor_plan_pdf_url") val floorPlanPdfUrl: String? = null,
    @SerialName("notes") val notes: String? = null,
)

@Serializable
data class ZoneDto(
    @SerialName("id") val id: String,
    @SerialName("floor_id") val floorId: String? = null,
    @SerialName("zone_code") val zoneCode: String,
    @SerialName("color_hex") val colorHex: String? = null,
    @SerialName("total_pm") val totalPm: Double? = null,
    @SerialName("notes") val notes: String? = null,
)

@Serializable
data class CabinetDto(
    @SerialName("id") val id: String,
    @SerialName("zone_id") val zoneId: String? = null,
    @SerialName("cabinet_number") val cabinetNumber: String,
    @SerialName("parent_cabinet_id") val parentCabinetId: String? = null,
    @SerialName("total_length_mm") val totalLengthMm: Int? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("ready_windows") val readyWindows: Boolean = false,
    @SerialName("ready_otkosy") val readyOtkosy: Boolean = false,
    @SerialName("ready_radiators") val readyRadiators: Boolean = false,
    @SerialName("ready_pipes") val readyPipes: Boolean = false,
    @SerialName("ready_plinth") val readyPlinth: Boolean = false,
    @SerialName("ready_walls") val readyWalls: Boolean = false,
    @SerialName("ready_floor") val readyFloor: Boolean = false,
    @SerialName("ready_comments") val readyComments: String? = null,
    // Статус кабинета целиком (упрощённая модель 2.1.0)
    @SerialName("status") val status: String = "not_started",
    @SerialName("status_comment") val statusComment: String? = null,
) {
    /** Множество ключей ready_*, которые = true (для ReadinessChecklist). */
    fun readyKeys(): Set<String> = buildSet {
        if (readyWindows) add("ready_windows")
        if (readyOtkosy) add("ready_otkosy")
        if (readyRadiators) add("ready_radiators")
        if (readyPipes) add("ready_pipes")
        if (readyPlinth) add("ready_plinth")
        if (readyWalls) add("ready_walls")
        if (readyFloor) add("ready_floor")
    }
}

@Serializable
data class WindowDto(
    @SerialName("id") val id: String,
    @SerialName("cabinet_id") val cabinetId: String? = null,
    @SerialName("window_number") val windowNumber: Int,
    @SerialName("width_mm") val widthMm: Int? = null,
    @SerialName("height_mm") val heightMm: Int? = null,
    @SerialName("column_width_mm") val columnWidthMm: Int? = null,
    @SerialName("depth_podokonnik_mm") val depthPodokonnikMm: Int? = null,
    @SerialName("depth_radiator_mm") val depthRadiatorMm: Int? = null,
    @SerialName("depth_otkos_mm") val depthOtkosMm: Int? = null,
    @SerialName("status_karkas") val statusKarkas: String = "not_started",
    @SerialName("status_podokonnik") val statusPodokonnik: String = "not_started",
    @SerialName("status_ekran") val statusEkran: String = "not_started",
)

@Serializable
data class ReadinessRequest(
    @SerialName("ready_windows") val readyWindows: Boolean,
    @SerialName("ready_otkosy") val readyOtkosy: Boolean,
    @SerialName("ready_radiators") val readyRadiators: Boolean,
    @SerialName("ready_pipes") val readyPipes: Boolean,
    @SerialName("ready_plinth") val readyPlinth: Boolean,
    @SerialName("ready_walls") val readyWalls: Boolean,
    @SerialName("ready_floor") val readyFloor: Boolean,
    @SerialName("ready_comments") val readyComments: String? = null,
)

@Serializable
data class StageStatusRequest(
    @SerialName("stage") val stage: String,   // karkas | podokonnik | ekran
    @SerialName("status") val status: String, // not_started | in_progress | done | accepted | approved | rework
)

@Serializable
data class CabinetStatusRequest(
    @SerialName("status") val status: String,         // not_started | in_progress | done | problem
    @SerialName("comment") val comment: String? = null,
)

/** Тело POST /v3/zones/{zoneId}/cabinets — создание кабинета на лету (2.1.0 fast-follow). */
@Serializable
data class CabinetCreateRequest(
    @SerialName("cabinet_number") val cabinetNumber: String,
    @SerialName("parent_cabinet_id") val parentCabinetId: String? = null,
    @SerialName("total_length_mm") val totalLengthMm: Int? = null,
    @SerialName("notes") val notes: String? = null,
)

/** Тело POST /v3/cabinets/{cabinetId}/windows — создание окна на лету (2.1.0 fast-follow). */
@Serializable
data class WindowCreateRequest(
    @SerialName("window_number") val windowNumber: Int,
    @SerialName("width_mm") val widthMm: Int? = null,
    @SerialName("height_mm") val heightMm: Int? = null,
    @SerialName("column_width_mm") val columnWidthMm: Int? = null,
    @SerialName("depth_podokonnik_mm") val depthPodokonnikMm: Int? = null,
    @SerialName("depth_radiator_mm") val depthRadiatorMm: Int? = null,
    @SerialName("depth_otkos_mm") val depthOtkosMm: Int? = null,
    @SerialName("notes") val notes: String? = null,
)

@Serializable
data class ImportZameryResultDto(
    @SerialName("floors") val floors: Int = 0,
    @SerialName("zones") val zones: Int = 0,
    @SerialName("cabinets") val cabinets: Int = 0,
    @SerialName("windows") val windows: Int = 0,
    @SerialName("skipped") val skipped: Int = 0,
    @SerialName("warnings") val warnings: List<String> = emptyList(),
    @SerialName("errors") val errors: List<String> = emptyList(),
)

/** Агрегат прогресса объекта по КАБИНЕТАМ (GET /v3/progress) — для Tab AI курaтора (4.4). */
@Serializable
data class ObjectProgressDto(
    @SerialName("object_id") val objectId: String,
    @SerialName("name") val name: String = "",
    @SerialName("total") val total: Int = 0,
    @SerialName("done") val done: Int = 0,
    @SerialName("in_progress") val inProgress: Int = 0,
    @SerialName("problem") val problem: Int = 0,
) {
    val donePercent: Int get() = if (total > 0) done * 100 / total else 0
    val doneFraction: Float get() = if (total > 0) done.toFloat() / total else 0f
    val inProgressFraction: Float get() = if (total > 0) inProgress.toFloat() / total else 0f
}
