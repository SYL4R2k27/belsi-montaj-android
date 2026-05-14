package com.belsi.work.data.remote.api

import com.belsi.work.data.remote.dto.tool_transfer.*
import retrofit2.Response
import retrofit2.http.*

/**
 * FIX(2026-05-12) build19 Этап3: Retrofit API для tool-transfer pipeline.
 * Backend: /opt/belsi-api/app/tool_transfers.py
 */
interface ToolTransferApi {

    // ─── Комплектатор ────────────────────────────────────────────

    @POST("supplier/tool-transfers/bulk")
    suspend fun createBulkTransfer(
        @Body body: BulkTransferRequest,
    ): Response<BulkCreateResponse>

    @POST("supplier/tool-transfers/{id}/dispatch")
    suspend fun dispatchTransfer(
        @Path("id") transferId: String,
        @Body body: DispatchRequest,
    ): Response<Map<String, String>>

    // ─── Приёмщик ──────────────────────────────────────────────────

    @POST("tools/transfers/{id}/accept")
    suspend fun acceptTransfer(
        @Path("id") transferId: String,
        @Body body: AcceptRequest,
    ): Response<Map<String, Any?>>

    @POST("tools/transfers/{id}/reject")
    suspend fun rejectTransfer(
        @Path("id") transferId: String,
        @Body body: RejectRequest,
    ): Response<Map<String, String>>

    @POST("tools/transfers/{id}/take")
    suspend fun takeTransfer(
        @Path("id") transferId: String,
    ): Response<Map<String, String>>

    @POST("tools/transfers/{id}/release")
    suspend fun releaseTransfer(
        @Path("id") transferId: String,
    ): Response<Map<String, String>>

    // ─── Списки ────────────────────────────────────────────────────

    @GET("tools/transfers/incoming")
    suspend fun listIncoming(
        @Query("status") status: String? = null,
    ): Response<List<ToolTransferDto>>

    @GET("tools/transfers/outgoing")
    suspend fun listOutgoing(
        @Query("status") status: String? = null,
    ): Response<List<ToolTransferDto>>

    @GET("tools/transfers/by-object/{site_object_id}")
    suspend fun listByObject(
        @Path("site_object_id") siteObjectId: String,
    ): Response<List<ToolTransferDto>>

    @GET("tools/transfers/{transfer_id}")
    suspend fun getTransfer(
        @Path("transfer_id") transferId: String,
    ): Response<ToolTransferDto>

    // ─── Inventory ─────────────────────────────────────────────────

    @GET("tools/inventory")
    suspend fun getInventory(
        @Query("location") location: String? = null,         // warehouse | site | in_transit | null=all
        @Query("site_object_id") siteObjectId: String? = null,
        @Query("kind") kind: String? = null,                  // tool | consumable
    ): Response<List<InventoryItemDto>>

    // ─── RETURN FLOW (FIX 2026-05-14 BELSI 2.0.1) ──────────────────

    /** Шаг 1: инициация возврата (любая из 5 ролей с правами). */
    @POST("tools/transfers/{id}/return-request")
    suspend fun returnRequest(
        @Path("id") transferId: String,
        @Body body: ReturnRequestBody,
    ): Response<Map<String, String>>

    /** Шаг 2: supplier/curator назначает водителя на возврат. */
    @POST("supplier/tool-transfers/{id}/return-assign-driver")
    suspend fun returnAssignDriver(
        @Path("id") transferId: String,
        @Body body: ReturnAssignDriverBody,
    ): Response<Map<String, String>>

    /** Шаг 3: водитель забрал инструмент с объекта. */
    @POST("tools/transfers/{id}/return-pickup")
    suspend fun returnPickup(
        @Path("id") transferId: String,
        @Body body: ReturnPickupBody,
    ): Response<Map<String, String>>

    /** Шаг 4: водитель доставил на завод. */
    @POST("tools/transfers/{id}/return-deliver")
    suspend fun returnDeliver(
        @Path("id") transferId: String,
        @Body body: ReturnDeliverBody,
    ): Response<Map<String, String>>

    /** Шаг 5a: комплектатор принял возврат. */
    @POST("supplier/tool-transfers/{id}/return-accept")
    suspend fun returnAccept(
        @Path("id") transferId: String,
        @Body body: ReturnAcceptBody,
    ): Response<Map<String, Any?>>

    /** Шаг 5b: комплектатор отклонил возврат — куратору разбирать. */
    @POST("supplier/tool-transfers/{id}/return-reject")
    suspend fun returnReject(
        @Path("id") transferId: String,
        @Body body: ReturnRejectBody,
    ): Response<Map<String, String>>

    /** Куратор: список всех активных возвратов. */
    @GET("curator/tool-transfers/in-return")
    suspend fun curatorInReturn(): Response<List<ToolTransferDto>>

    /** Куратор: разрешить отклонённый возврат (accept | discard | lost). */
    @POST("curator/tool-transfers/{id}/resolve-rejection")
    suspend fun curatorResolveRejection(
        @Path("id") transferId: String,
        @Body body: CuratorResolveRejectionBody,
    ): Response<Map<String, String>>

    /** Куратор: отменить процесс возврата (откатить в accepted/in_use). */
    @POST("curator/tool-transfers/{id}/cancel-return")
    suspend fun curatorCancelReturn(
        @Path("id") transferId: String,
        @Body body: ReturnRejectBody,    // reason
    ): Response<Map<String, String>>

    /** Список водителей для назначения возврата (supplier/curator). */
    @GET("tools/transfers/drivers")
    suspend fun listDriversForReturn(): Response<List<DriverPickItemDto>>
}
