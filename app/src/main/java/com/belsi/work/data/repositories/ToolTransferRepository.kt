package com.belsi.work.data.repositories

import com.belsi.work.data.remote.api.ToolTransferApi
import com.belsi.work.data.remote.dto.tool_transfer.*
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX(2026-05-12) build19 Этап3: Repository для tool-transfer pipeline.
 */
@Singleton
class ToolTransferRepository @Inject constructor(
    private val api: ToolTransferApi,
    private val json: Json,
) {

    suspend fun createBulk(req: BulkTransferRequest): Result<BulkCreateResponse> =
        safe("createBulk") { api.createBulkTransfer(req) }

    suspend fun dispatch(transferId: String, driverUserId: String, photoUrl: String? = null): Result<Unit> =
        safe("dispatch") {
            api.dispatchTransfer(transferId, DispatchRequest(driverUserId, photoUrl))
        }.map { }

    suspend fun accept(
        transferId: String,
        acceptedQuantity: Int? = null,
        photoUrl: String? = null,
        comment: String? = null,
    ): Result<Unit> =
        safe("accept") {
            api.acceptTransfer(transferId, AcceptRequest(acceptedQuantity, photoUrl, comment))
        }.map { }

    suspend fun reject(transferId: String, reason: String, photoUrl: String? = null): Result<Unit> =
        safe("reject") {
            api.rejectTransfer(transferId, RejectRequest(reason, photoUrl))
        }.map { }

    suspend fun take(transferId: String): Result<Unit> =
        safe("take") { api.takeTransfer(transferId) }.map { }

    suspend fun release(transferId: String): Result<Unit> =
        safe("release") { api.releaseTransfer(transferId) }.map { }

    // ─── FIX 2026-05-18: двух-стадийный driver flow ──────────────────
    /** Driver: подтверждает что забрал инструмент. dispatched → in_transit */
    suspend fun pickup(transferId: String, photoUrl: String? = null): Result<Unit> =
        safe("pickup") {
            val body = if (photoUrl.isNullOrBlank()) emptyMap() else mapOf("photo_url" to photoUrl)
            api.pickupTransfer(transferId, body)
        }.map { }

    /** Driver: подтверждает что доставил на объект. in_transit → delivered */
    suspend fun deliver(transferId: String, photoUrl: String? = null): Result<Unit> =
        safe("deliver") {
            val body = if (photoUrl.isNullOrBlank()) emptyMap() else mapOf("photo_url" to photoUrl)
            api.deliverTransfer(transferId, body)
        }.map { }

    suspend fun listIncoming(status: String? = null): Result<List<ToolTransferDto>> =
        safe("listIncoming") { api.listIncoming(status) }

    suspend fun listOutgoing(status: String? = null): Result<List<ToolTransferDto>> =
        safe("listOutgoing") { api.listOutgoing(status) }

    suspend fun listByObject(siteObjectId: String): Result<List<ToolTransferDto>> =
        safe("listByObject") { api.listByObject(siteObjectId) }

    suspend fun getTransfer(transferId: String): Result<ToolTransferDto> =
        safe("getTransfer") { api.getTransfer(transferId) }

    suspend fun getInventory(
        location: String? = null,
        siteObjectId: String? = null,
        kind: String? = null,
    ): Result<List<InventoryItemDto>> =
        safe("getInventory") { api.getInventory(location, siteObjectId, kind) }

    // ─── RETURN FLOW (FIX 2026-05-14 BELSI 2.0.1) ──────────────────

    suspend fun returnRequest(
        transferId: String,
        reason: String? = null,
        photoUrl: String? = null,
    ): Result<Unit> =
        safe("returnRequest") {
            api.returnRequest(transferId, ReturnRequestBody(reason, photoUrl))
        }.map { }

    suspend fun returnAssignDriver(
        transferId: String,
        driverUserId: String,
        routeId: String? = null,
    ): Result<Unit> =
        safe("returnAssignDriver") {
            api.returnAssignDriver(transferId, ReturnAssignDriverBody(driverUserId, routeId))
        }.map { }

    suspend fun returnPickup(
        transferId: String,
        photoUrl: String,
        comment: String? = null,
    ): Result<Unit> =
        safe("returnPickup") {
            api.returnPickup(transferId, ReturnPickupBody(photoUrl, comment))
        }.map { }

    suspend fun returnDeliver(transferId: String, photoUrl: String): Result<Unit> =
        safe("returnDeliver") {
            api.returnDeliver(transferId, ReturnDeliverBody(photoUrl))
        }.map { }

    suspend fun returnAccept(
        transferId: String,
        acceptedQuantity: Int? = null,
        photoUrl: String? = null,
        comment: String? = null,
    ): Result<Unit> =
        safe("returnAccept") {
            api.returnAccept(transferId, ReturnAcceptBody(acceptedQuantity, photoUrl, comment))
        }.map { }

    suspend fun returnReject(transferId: String, reason: String): Result<Unit> =
        safe("returnReject") {
            api.returnReject(transferId, ReturnRejectBody(reason))
        }.map { }

    suspend fun curatorInReturn(): Result<List<ToolTransferDto>> =
        safe("curatorInReturn") { api.curatorInReturn() }

    suspend fun curatorResolveRejection(
        transferId: String,
        action: String,
        comment: String? = null,
    ): Result<Unit> =
        safe("curatorResolveRejection") {
            api.curatorResolveRejection(transferId, CuratorResolveRejectionBody(action, comment))
        }.map { }

    suspend fun curatorCancelReturn(transferId: String, reason: String): Result<Unit> =
        safe("curatorCancelReturn") {
            api.curatorCancelReturn(transferId, ReturnRejectBody(reason))
        }.map { }

    suspend fun listDriversForReturn(): Result<List<DriverPickItemDto>> =
        safe("listDriversForReturn") { api.listDriversForReturn() }

    // ─────────────────────────────────────────────────────────────

    private suspend fun <T> safe(op: String, call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errBody = response.errorBody()?.string()
                val detail = try {
                    if (errBody.isNullOrBlank()) "HTTP ${response.code()}"
                    else (json.parseToJsonElement(errBody).toString())
                } catch (_: Exception) {
                    errBody ?: "HTTP ${response.code()}"
                }
                Result.failure(Exception("$op: $detail"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolTransferRepo", op, e)
            Result.failure(Exception("$op: ${e.message}", e))
        }
    }
}
