package com.belsi.work.data.remote.api

import com.belsi.work.data.remote.dto.tool_kit.*
import retrofit2.Response
import retrofit2.http.*

/**
 * FIX(2026-05-18) BELSI 2.0.1: Retrofit API для tool-kits / Тележка.
 * Backend: /opt/belsi-api/app/tool_kits.py
 */
interface ToolKitApi {

    // ─── Чтение шаблонов (всем authenticated) ────────────────────

    @GET("tool-kits")
    suspend fun listKits(
        @Query("active") active: Boolean? = true,
    ): Response<List<ToolKitDto>>

    @GET("tool-kits/{id}")
    suspend fun getKit(
        @Path("id") kitId: String,
    ): Response<ToolKitDto>

    // ─── Управление шаблонами (curator / coordinator / chief) ─────

    @POST("tool-kits")
    suspend fun createKit(
        @Body body: ToolKitCreateRequest,
    ): Response<ToolKitDto>

    @PATCH("tool-kits/{id}")
    suspend fun patchKit(
        @Path("id") kitId: String,
        @Body body: ToolKitPatchRequest,
    ): Response<ToolKitDto>

    @DELETE("tool-kits/{id}")
    suspend fun deleteKit(
        @Path("id") kitId: String,
    ): Response<Unit>

    @POST("tool-kits/{id}/items")
    suspend fun addItem(
        @Path("id") kitId: String,
        @Body body: ToolKitItemCreateRequest,
    ): Response<ToolKitItemDto>

    @DELETE("tool-kits/{id}/items/{itemId}")
    suspend fun deleteItem(
        @Path("id") kitId: String,
        @Path("itemId") itemId: String,
    ): Response<Unit>

    // ─── Выдача (supplier+) ────────────────────────────────────────

    @POST("supplier/tool-kits/{id}/dispatch")
    suspend fun dispatchKit(
        @Path("id") kitId: String,
        @Body body: KitDispatchRequest,
    ): Response<KitDispatchResponse>

    // ─── Phase 3: tools catalog + dispatch preview ──────────────────

    @GET("tools/catalog")
    suspend fun toolsCatalog(
        @Query("search") search: String? = null,
        @Query("category") category: String? = null,
        @Query("kind") kind: String? = null,
        @Query("limit") limit: Int = 200,
    ): Response<List<ToolCatalogItem>>

    @POST("supplier/tool-kits/{id}/dispatch-preview")
    suspend fun dispatchPreview(
        @Path("id") kitId: String,
        @Body body: KitDispatchRequest,
    ): Response<DispatchPreviewResponse>

    // ─── Bulk операции по kit_batch_id ─────────────────────────────

    @GET("tools/transfers/by-kit-batch/{batchId}")
    suspend fun getBatch(
        @Path("batchId") batchId: String,
    ): Response<BatchViewDto>

    @POST("tools/transfers/by-kit-batch/{batchId}/pickup")
    suspend fun batchPickup(
        @Path("batchId") batchId: String,
    ): Response<BulkOpResponse>

    @POST("tools/transfers/by-kit-batch/{batchId}/deliver")
    suspend fun batchDeliver(
        @Path("batchId") batchId: String,
    ): Response<BulkOpResponse>

    @POST("tools/transfers/by-kit-batch/{batchId}/accept")
    suspend fun batchAccept(
        @Path("batchId") batchId: String,
        @Body body: BatchAcceptRequest,
    ): Response<BulkOpResponse>
}
