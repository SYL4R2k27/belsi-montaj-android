package com.belsi.work.data.remote.dto.tool_kit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * FIX(2026-05-18) BELSI 2.0.1: DTOs для tool-kits (Тележка и др. шаблоны).
 * Backend: /opt/belsi-api/app/tool_kits.py
 *
 * Концепция: kit = стабильный шаблон позиций (Тележка = 60 позиций для команды 2).
 * Supplier выдаёт kit одним кликом → создаётся kit_batch_id + N tool_transfers.
 * Driver и receiver работают с batch как с одной единицей.
 */

// ─────────── Output (ответы от backend) ────────────────────────────

@Serializable
data class ToolKitItemDto(
    val id: String,
    @SerialName("sort_order") val sortOrder: Int,
    val section: String,                    // tools/consumables/measurement/auxiliary/transport
    @SerialName("item_no") val itemNo: String? = null,
    val name: String,
    val spec: String? = null,
    @SerialName("quantity_per_team") val quantityPerTeam: Int,
    @SerialName("tool_id") val toolId: String? = null,
    val notes: String? = null,
)

@Serializable
data class ToolKitDto(
    val id: String,
    val code: String,
    val name: String,
    val description: String? = null,
    @SerialName("team_size") val teamSize: Int,
    val active: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("items_count") val itemsCount: Int = 0,
    val items: List<ToolKitItemDto> = emptyList(),
)

// ─────────── Input (запросы к backend) ───────────────────────────────

@Serializable
data class ToolKitCreateRequest(
    val code: String,
    val name: String,
    val description: String? = null,
    @SerialName("team_size") val teamSize: Int = 2,
)

@Serializable
data class ToolKitPatchRequest(
    val name: String? = null,
    val description: String? = null,
    @SerialName("team_size") val teamSize: Int? = null,
    val active: Boolean? = null,
)

@Serializable
data class ToolKitItemCreateRequest(
    val section: String,
    val name: String,
    @SerialName("item_no") val itemNo: String? = null,
    val spec: String? = null,
    @SerialName("quantity_per_team") val quantityPerTeam: Int = 1,
    @SerialName("tool_id") val toolId: String? = null,
    val notes: String? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
)

@Serializable
data class KitDispatchRequest(
    @SerialName("to_site_object_id") val toSiteObjectId: String,
    @SerialName("driver_user_id") val driverUserId: String,
    @SerialName("team_count") val teamCount: Int,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("excluded_item_ids") val excludedItemIds: List<String>? = null,
    @SerialName("quantity_overrides") val quantityOverrides: Map<String, Int>? = null,
)

@Serializable
data class KitDispatchResponse(
    @SerialName("kit_batch_id") val kitBatchId: String,
    @SerialName("kit_id") val kitId: String,
    @SerialName("kit_name") val kitName: String,
    @SerialName("transfer_ids") val transferIds: List<String>,
    @SerialName("total_items") val totalItems: Int,
    @SerialName("team_count") val teamCount: Int,
)

@Serializable
data class BatchAcceptRequest(
    @SerialName("accepted_item_ids") val acceptedItemIds: List<String>? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val comment: String? = null,
)

// ─────────── Batch view (GET /by-kit-batch/{id}) ──────────────────

@Serializable
data class BatchItemDto(
    val id: String,
    val name: String,
    val quantity: Int,
    val status: String,
    @SerialName("accepted_quantity") val acceptedQuantity: Int? = null,
)

@Serializable
data class BatchViewDto(
    @SerialName("batch_id") val batchId: String,
    @SerialName("kit_name") val kitName: String? = null,
    @SerialName("total_items") val totalItems: Int,
    val statuses: Map<String, Int>,
    val sections: Map<String, List<BatchItemDto>>,
)

@Serializable
data class BulkOpResponse(
    val updated: Int,
    val status: String,
)

// ─────────── Phase 3: Tools catalog + Dispatch preview ─────────────

@Serializable
data class ToolCatalogItem(
    val id: String,
    val name: String,
    val category: String? = null,
    val kind: String,
    val quantity: Int,
    val description: String? = null,
)

@Serializable
data class DispatchPreviewWarning(
    @SerialName("kit_item_id") val kitItemId: String,
    @SerialName("kit_item_name") val kitItemName: String,
    @SerialName("tool_id") val toolId: String? = null,
    val requested: Int,
    val available: Int,
    @SerialName("in_transit") val inTransit: Int,
    val severity: String,  // ok | low | insufficient
)

@Serializable
data class DispatchPreviewResponse(
    @SerialName("kit_id") val kitId: String,
    @SerialName("kit_name") val kitName: String,
    @SerialName("team_count") val teamCount: Int,
    @SerialName("total_items") val totalItems: Int,
    @SerialName("insufficient_count") val insufficientCount: Int,
    @SerialName("low_count") val lowCount: Int,
    val warnings: List<DispatchPreviewWarning>,
)
