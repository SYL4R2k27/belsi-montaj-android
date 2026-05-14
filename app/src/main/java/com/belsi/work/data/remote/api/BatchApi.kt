package com.belsi.work.data.remote.api

import com.belsi.work.data.models.Batch
import com.belsi.work.data.models.BatchCreateRequest
import com.belsi.work.data.models.BatchHistoryItem
import com.belsi.work.data.models.BatchStatusChangeRequest
import com.belsi.work.data.models.IdleReason
import com.belsi.work.data.models.IncomingBatchDto
import retrofit2.Response
import retrofit2.http.*

/**
 * FIX(2026-05-05): API Pipeline партии (BELSI.Команда).
 * Соответствует серверному production_batches.py.
 *
 * Сервер сейчас на проде НЕ имеет этих эндпоинтов — они появятся после деплоя
 * feature/driver-integration. Клиент готов к подключению заранее.
 */
interface BatchApi {

    /** GET /production/batches — список с фильтрами. */
    @GET("production/batches")
    suspend fun listBatches(
        @Query("status") status: String? = null,
        @Query("facility_id") facilityId: String? = null,
        @Query("target_object_id") targetObjectId: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): Response<List<Batch>>

    /** POST /production/batches — создать (Начальник производства). */
    @POST("production/batches")
    suspend fun createBatch(@Body request: BatchCreateRequest): Response<Batch>

    /** GET /production/batches/{id} — детали. */
    @GET("production/batches/{batchId}")
    suspend fun getBatch(@Path("batchId") batchId: String): Response<Batch>

    /** POST /production/batches/{id}/status — смена статуса. */
    @POST("production/batches/{batchId}/status")
    suspend fun changeStatus(
        @Path("batchId") batchId: String,
        @Body request: BatchStatusChangeRequest,
    ): Response<Batch>

    /** GET /production/batches/{id}/history — audit log. */
    @GET("production/batches/{batchId}/history")
    suspend fun getHistory(@Path("batchId") batchId: String): Response<List<BatchHistoryItem>>

    // FIX(2026-05-12) BELSI 2.0.0 build15: Foreman/Coordinator endpoints
    @GET("production/batches/incoming")
    suspend fun getIncomingBatches(): Response<List<IncomingBatchDto>>

    /** Бригадир/координатор отмечает партию принятой (in_route → delivered). */
    @POST("production/batches/{batchId}/receive")
    suspend fun receiveBatch(@Path("batchId") batchId: String): Response<Map<String, String>>

    /** Закрытие монтажа (delivered → installed). */
    @POST("production/batches/{batchId}/install")
    suspend fun installBatch(@Path("batchId") batchId: String): Response<Map<String, String>>

    // ─── Перерывы ───
    @POST("shift/break/start")
    suspend fun startBreak(@Body request: BreakStartRequest): Response<Unit>

    @POST("shift/break/end")
    suspend fun endBreak(): Response<Unit>

    /** GET /shift/idle-reasons?domain=... — список причин по домену. */
    @GET("shift/idle-reasons")
    suspend fun getIdleReasons(
        @Query("domain") domain: String? = null,
    ): Response<List<IdleReason>>
}

@kotlinx.serialization.Serializable
data class BreakStartRequest(val type: String) // "smoke" | "lunch"
