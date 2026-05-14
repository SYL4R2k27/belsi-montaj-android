package com.belsi.work.data.remote.dto.tool_transfer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * FIX(2026-05-12) build19 Этап3: DTOs для tool-transfer pipeline.
 * Соответствуют backend схемам в /opt/belsi-api/app/tool_transfers.py.
 */

// ─────────── Input ─────────────────────────────────────────────────

@Serializable
data class BulkTransferRequest(
    @SerialName("to_site_object_id") val toSiteObjectId: String,
    val items: List<TransferItemIn>,
    @SerialName("driver_user_id") val driverUserId: String? = null,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("route_point_id") val routePointId: String? = null,
    val comment: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
)

@Serializable
data class TransferItemIn(
    @SerialName("tool_id") val toolId: String,
    val quantity: Int = 1,
    val comment: String? = null,
)

@Serializable
data class DispatchRequest(
    @SerialName("driver_user_id") val driverUserId: String,
    @SerialName("photo_url") val photoUrl: String? = null,
)

@Serializable
data class AcceptRequest(
    @SerialName("accepted_quantity") val acceptedQuantity: Int? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val comment: String? = null,
)

@Serializable
data class RejectRequest(
    val reason: String,
    @SerialName("photo_url") val photoUrl: String? = null,
)

// ─────────── Return flow (FIX 2026-05-14 BELSI 2.0.1) ──────────────

@Serializable
data class ReturnRequestBody(
    val reason: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
)

@Serializable
data class ReturnAssignDriverBody(
    @SerialName("driver_user_id") val driverUserId: String,
    @SerialName("route_id") val routeId: String? = null,
)

@Serializable
data class ReturnPickupBody(
    @SerialName("photo_url") val photoUrl: String,
    val comment: String? = null,
)

@Serializable
data class ReturnDeliverBody(
    @SerialName("photo_url") val photoUrl: String,
)

@Serializable
data class ReturnAcceptBody(
    @SerialName("accepted_quantity") val acceptedQuantity: Int? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val comment: String? = null,
)

@Serializable
data class ReturnRejectBody(
    val reason: String,
)

@Serializable
data class CuratorResolveRejectionBody(
    val action: String,     // "accept" | "discard" | "lost"
    val comment: String? = null,
)

@Serializable
data class DriverPickItemDto(
    val id: String,
    val name: String? = null,
    val phone: String? = null,
    val busy: Boolean = false,
)

@Serializable
data class BulkCreateResponse(
    val created: Int,
    @SerialName("transfer_ids") val transferIds: List<String>,
)

// ─────────── Output ─────────────────────────────────────────────────

@Serializable
data class ToolTransferDto(
    val id: String,
    @SerialName("tool_id") val toolId: String,
    @SerialName("tool_name") val toolName: String? = null,
    @SerialName("tool_kind") val toolKind: String? = null,           // tool | consumable
    @SerialName("tool_category") val toolCategory: String? = null,
    val quantity: Int,
    @SerialName("accepted_quantity") val acceptedQuantity: Int? = null,
    val status: String,    // pending | in_transit | delivered | accepted | in_use | returning | returned | lost | cancelled
    @SerialName("from_user_id") val fromUserId: String? = null,
    @SerialName("from_site_object_id") val fromSiteObjectId: String? = null,
    @SerialName("to_user_id") val toUserId: String? = null,
    @SerialName("to_site_object_id") val toSiteObjectId: String? = null,
    @SerialName("to_site_object_name") val toSiteObjectName: String? = null,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("route_point_id") val routePointId: String? = null,
    @SerialName("driver_user_id") val driverUserId: String? = null,
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_by_name") val createdByName: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("dispatched_at") val dispatchedAt: String? = null,
    @SerialName("in_transit_at") val inTransitAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("accepted_by") val acceptedBy: String? = null,
    @SerialName("accepted_by_name") val acceptedByName: String? = null,
    @SerialName("in_use_at") val inUseAt: String? = null,
    @SerialName("in_use_by") val inUseBy: String? = null,
    @SerialName("returned_at") val returnedAt: String? = null,
    @SerialName("dispatch_photo_url") val dispatchPhotoUrl: String? = null,
    @SerialName("delivery_photo_url") val deliveryPhotoUrl: String? = null,
    @SerialName("accept_photo_url") val acceptPhotoUrl: String? = null,
    @SerialName("return_photo_url") val returnPhotoUrl: String? = null,
    val comment: String? = null,
    @SerialName("cancel_reason") val cancelReason: String? = null,
    @SerialName("reject_reason") val rejectReason: String? = null,
    // FIX(2026-05-14) BELSI 2.0.1: return flow (зеркальный возврат)
    @SerialName("return_requested_at") val returnRequestedAt: String? = null,
    @SerialName("return_requested_by") val returnRequestedBy: String? = null,
    @SerialName("return_reason") val returnReason: String? = null,
    @SerialName("return_request_photo_url") val returnRequestPhotoUrl: String? = null,
    @SerialName("return_driver_user_id") val returnDriverUserId: String? = null,
    @SerialName("return_pickup_at") val returnPickupAt: String? = null,
    @SerialName("return_pickup_photo_url") val returnPickupPhotoUrl: String? = null,
    @SerialName("return_delivered_at") val returnDeliveredAt: String? = null,
    @SerialName("return_delivered_photo_url") val returnDeliveredPhotoUrl: String? = null,
    @SerialName("return_accepted_at") val returnAcceptedAt: String? = null,
    @SerialName("return_accepted_by") val returnAcceptedBy: String? = null,
    @SerialName("return_accept_photo_url") val returnAcceptPhotoUrl: String? = null,
    @SerialName("return_accepted_quantity") val returnAcceptedQuantity: Int? = null,
    @SerialName("return_rejected_at") val returnRejectedAt: String? = null,
    @SerialName("return_reject_reason") val returnRejectReason: String? = null,
)

@Serializable
data class InventoryItemDto(
    @SerialName("tool_id") val toolId: String,
    val name: String,
    val kind: String,                                                  // tool | consumable
    val category: String? = null,
    val quantity: Int,
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("inventory_number") val inventoryNumber: String? = null,
    val location: String,                                              // warehouse | site | in_transit
    @SerialName("site_object_id") val siteObjectId: String? = null,
    @SerialName("site_object_name") val siteObjectName: String? = null,
    @SerialName("holder_user_id") val holderUserId: String? = null,
    @SerialName("holder_user_name") val holderUserName: String? = null,
)
