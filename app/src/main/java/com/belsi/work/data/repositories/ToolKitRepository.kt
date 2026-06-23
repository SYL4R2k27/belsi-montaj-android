package com.belsi.work.data.repositories

import com.belsi.work.data.remote.api.ToolKitApi
import com.belsi.work.data.remote.dto.tool_kit.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX(2026-05-18) BELSI 2.0.1: Repository для tool-kits (Тележка).
 * Тонкая обёртка над ToolKitApi с обработкой ошибок.
 */
@Singleton
class ToolKitRepository @Inject constructor(
    private val api: ToolKitApi,
) {

    private inline fun <T> wrap(block: () -> retrofit2.Response<T>): Result<T> = try {
        val resp = block()
        if (resp.isSuccessful) {
            resp.body()?.let { Result.success(it) }
                ?: Result.failure(Exception("Empty response body"))
        } else {
            Result.failure(Exception("HTTP ${resp.code()}: ${resp.errorBody()?.string() ?: "no body"}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ── Чтение ──

    suspend fun listKits(active: Boolean? = true): Result<List<ToolKitDto>> = wrap {
        api.listKits(active)
    }

    suspend fun getKit(kitId: String): Result<ToolKitDto> = wrap { api.getKit(kitId) }

    // ── Управление шаблонами ──

    suspend fun createKit(body: ToolKitCreateRequest): Result<ToolKitDto> = wrap {
        api.createKit(body)
    }

    suspend fun patchKit(kitId: String, body: ToolKitPatchRequest): Result<ToolKitDto> = wrap {
        api.patchKit(kitId, body)
    }

    suspend fun deleteKit(kitId: String): Result<Unit> = wrap { api.deleteKit(kitId) }

    suspend fun addItem(kitId: String, body: ToolKitItemCreateRequest): Result<ToolKitItemDto> = wrap {
        api.addItem(kitId, body)
    }

    suspend fun deleteItem(kitId: String, itemId: String): Result<Unit> = wrap {
        api.deleteItem(kitId, itemId)
    }

    // ── Выдача ──

    suspend fun dispatchKit(kitId: String, body: KitDispatchRequest): Result<KitDispatchResponse> = wrap {
        api.dispatchKit(kitId, body)
    }

    // ── Phase 3: tools catalog + dispatch preview ──

    suspend fun toolsCatalog(
        search: String? = null,
        category: String? = null,
        kind: String? = null,
    ): Result<List<ToolCatalogItem>> = wrap { api.toolsCatalog(search, category, kind) }

    suspend fun dispatchPreview(
        kitId: String,
        body: KitDispatchRequest,
    ): Result<DispatchPreviewResponse> = wrap { api.dispatchPreview(kitId, body) }

    // ── Bulk операции ──

    suspend fun getBatch(batchId: String): Result<BatchViewDto> = wrap { api.getBatch(batchId) }

    suspend fun batchPickup(batchId: String): Result<BulkOpResponse> = wrap { api.batchPickup(batchId) }
    suspend fun batchDeliver(batchId: String): Result<BulkOpResponse> = wrap { api.batchDeliver(batchId) }
    suspend fun batchAccept(
        batchId: String,
        acceptedItemIds: List<String>? = null,
        photoUrl: String? = null,
        comment: String? = null,
    ): Result<BulkOpResponse> = wrap {
        api.batchAccept(batchId, BatchAcceptRequest(acceptedItemIds, photoUrl, comment))
    }
}
