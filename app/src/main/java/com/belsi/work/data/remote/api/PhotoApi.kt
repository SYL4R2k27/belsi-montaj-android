package com.belsi.work.data.remote.api

import com.belsi.work.data.models.ShiftPhoto
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
    ): Response<ShiftPhoto>
    
    @GET("photos/shift/{shiftId}")
    suspend fun getPhotosByShift(
        @Path("shiftId") shiftId: UUID
    ): Response<List<ShiftPhoto>>
    
    @PUT("photos/{photoId}")
    suspend fun updatePhoto(
        @Path("photoId") photoId: UUID,
        @Body request: UpdatePhotoRequest
    ): Response<ShiftPhoto>
    
    @DELETE("photos/{photoId}")
    suspend fun deletePhoto(
        @Path("photoId") photoId: UUID
    ): Response<Unit>
    
    @GET("photos/pending")
    suspend fun getPendingPhotos(): Response<List<ShiftPhoto>>

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
