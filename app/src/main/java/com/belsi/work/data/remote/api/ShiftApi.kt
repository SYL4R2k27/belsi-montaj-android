package com.belsi.work.data.remote.api

import com.belsi.work.data.remote.dto.shift.StartShiftResponse
import com.belsi.work.data.remote.dto.shift.UploadHourPhotoResponse
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * BELSI API - Работа со сменами и фото
 * Базовый URL: https://api.belsi.ru
 */
interface ShiftApi {

    /**
     * Начать смену
     * POST /shifts/start
     *
     * Требует: Authorization: Bearer <token>
     * Тело: пустой JSON объект {}
     * Response: { "id": "UUID", "start_at": "2025-12-08T09:15:00+03:00", "status": "active" }
     */
    @POST("shifts/start")
    suspend fun startShift(@Body request: StartShiftRequest = StartShiftRequest()): Response<StartShiftResponse>

    /**
     * Загрузить почасовое фото
     * POST /shift/hour/photo
     *
     * Требует: Authorization: Bearer <token>
     * Content-Type: multipart/form-data
     *
     * Поля:
     * - shift_id: UUID смены
     * - hour_label: строка (например "2025-12-08T10:00:00+03:00")
     * - photo: бинарный JPEG/PNG
     * - comment: опциональный комментарий к фото
     */
    @Multipart
    @POST("shift/hour/photo")
    suspend fun uploadHourPhoto(
        @Part("shift_id") shiftId: RequestBody,
        @Part("hour_label") hourLabel: RequestBody,
        @Part photo: MultipartBody.Part,
        @Part("comment") comment: RequestBody? = null,
        @Part("category") category: RequestBody? = null
    ): Response<UploadHourPhotoResponse>

    /**
     * Завершить смену
     * POST /shifts/finish
     *
     * Требует: Authorization: Bearer <token>
     * Тело: { "shift_id": "UUID активной смены" }
     * Response: { "id": "UUID", "start_at": "...", "finish_at": "...", "duration_hours": 8.5, "status": "finished" }
     */
    @POST("shifts/finish")
    suspend fun finishShift(@Body request: FinishShiftRequest): Response<FinishShiftResponse>

    /**
     * Получить список смен (история)
     * GET /shifts
     *
     * Реальный ответ сервера: { "items": [...] }
     */
    @GET("shifts")
    suspend fun getShiftHistory(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ShiftHistoryResponse>

    /**
     * Получить детали смены
     * GET /shifts/{id}
     */
    @GET("shifts/{id}")
    suspend fun getShift(@Path("id") shiftId: String): Response<ShiftDetailResponse>

    /**
     * Получить фотографии смены
     * GET /shifts/{id}/photos
     */
    @GET("shifts/{shift_id}/photos")
    suspend fun getShiftPhotos(@Path("shift_id") shiftId: String): Response<List<ShiftPhotoResponse>>
}

// Response DTOs
// Реальный ответ сервера: { "items": [...] }
@Serializable
data class ShiftHistoryResponse(
    val items: List<ShiftHistoryItem>
)

@Serializable
data class ShiftHistoryItem(
    val id: String,
    val start_at: String,
    val end_at: String? = null,
    val status: String,
    val duration_minutes: Int? = null,
    val earnings: Double? = null,
    val photos_count: Int? = null
)

@Serializable
data class ShiftDetailResponse(
    val id: String,
    val user_id: String,
    val start_at: String,
    val end_at: String? = null,
    val status: String,
    val location: String? = null,
    val notes: String? = null
)

@Serializable
data class ShiftPhotoResponse(
    val id: String,
    val shift_id: String,
    val hour_label: String? = null,
    val status: String,
    val comment: String? = null,
    val photo_url: String,
    val created_at: String
)

// Request body для начала смены
@Serializable
data class StartShiftRequest(
    @kotlinx.serialization.SerialName("site_object_id")
    val siteObjectId: String? = null
)

// Request body для завершения смены
@Serializable
data class FinishShiftRequest(
    @kotlinx.serialization.SerialName("shift_id")
    val shiftId: String
)

// Response для завершения смены
@Serializable
data class FinishShiftResponse(
    val id: String,
    @kotlinx.serialization.SerialName("start_at")
    val startAt: String,
    @kotlinx.serialization.SerialName("finish_at")
    val finishAt: String,
    @kotlinx.serialization.SerialName("duration_hours")
    val durationHours: Double,
    val status: String
)
