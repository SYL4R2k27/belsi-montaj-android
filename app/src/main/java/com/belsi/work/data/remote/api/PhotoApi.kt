package com.belsi.work.data.remote.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import java.util.UUID

interface PhotoApi {
    
    @Multipart
    @POST("shift/hour/photo")
    suspend fun uploadPhoto(
        @Part photo: MultipartBody.Part,
        @Part("shift_id") shiftId: RequestBody,
        @Part("hour_label") hourLabel: RequestBody,
        @Part("latitude") latitude: RequestBody? = null,
        @Part("longitude") longitude: RequestBody? = null,
        @Part("comment") comment: RequestBody? = null,
        @Part("category") category: RequestBody? = null
    ): Response<PhotoUploadResponse>
    
    @GET("photos/{photoId}")
    suspend fun getPhoto(
        @Path("photoId") photoId: UUID
    ): Response<PhotoOutDto>
    
    @GET("photos/shift/{shiftId}")
    suspend fun getPhotosByShift(
        @Path("shiftId") shiftId: UUID
    ): Response<List<PhotoOutDto>>
    
    @PUT("photos/{photoId}")
    suspend fun updatePhoto(
        @Path("photoId") photoId: UUID,
        @Body request: UpdatePhotoRequest
    ): Response<PhotoOutDto>
    
    @DELETE("photos/{photoId}")
    suspend fun deletePhoto(
        @Path("photoId") photoId: UUID
    ): Response<Unit>
    
    @GET("photos/pending")
    suspend fun getPendingPhotos(): Response<List<PhotoOutDto>>

    /**
     * Универсальный ревью фото (для любой роли)
     * POST /photos/{photoId}/review
     * Бэкенд: photo_review.py
     */
    @POST("photos/{photoId}/review")
    suspend fun reviewPhoto(
        @Path("photoId") photoId: String,
        @Body request: PhotoReviewRequest
    ): Response<Unit>

    /**
     * Одобрить фото
     * POST /photos/{photoId}/approve
     */
    @POST("photos/{photoId}/approve")
    suspend fun approvePhoto(
        @Path("photoId") photoId: String
    ): Response<Unit>

    /**
     * Отклонить фото
     * POST /photos/{photoId}/reject
     */
    @POST("photos/{photoId}/reject")
    suspend fun rejectPhoto(
        @Path("photoId") photoId: String,
        @Body request: RejectPhotoRequest
    ): Response<Unit>
}

// Request/Response DTOs
data class PhotoUploadResponse(
    val photoId: UUID,
    val remoteUrl: String,
    val status: String
)

data class UpdatePhotoRequest(
    val comment: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@kotlinx.serialization.Serializable
data class PhotoReviewBody(
    val status: String,  // "approved" or "rejected"
    val comment: String? = null
)

/**
 * FIX(2026-05-25): @Serializable DTO для ответа GET/PUT /photos/{id}
 * (на бэке response_model=PhotoOut, photo_review.py). Раньше PhotoApi возвращал
 * доменную модель ShiftPhoto (enum PhotoStatus + java.time.LocalDateTime + Compose Color),
 * которую kotlinx-serialization сериализовать не может → Retrofit «Unable to create
 * converter for class ShiftPhoto» → экран «Фотоотчёт» падал. Теперь — сериализуемый DTO,
 * маппится в UI прямо во вьюмодели.
 */
@kotlinx.serialization.Serializable
data class PhotoOutDto(
    val id: String,
    @kotlinx.serialization.SerialName("shift_id") val shiftId: String? = null,
    @kotlinx.serialization.SerialName("hour_label") val hourLabel: String? = null,
    val status: String = "",
    val comment: String? = null,
    @kotlinx.serialization.SerialName("photo_url") val photoUrl: String = "",
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    @kotlinx.serialization.SerialName("ai_comment") val aiComment: String? = null,
)
