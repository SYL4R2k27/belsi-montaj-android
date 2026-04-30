package com.belsi.work.data.remote.api

import com.belsi.work.data.remote.dto.objects.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * API для управления строительными объектами
 * Бэкенд: site_objects.py, curator.py
 */
interface ObjectsApi {

    @GET("objects")
    suspend fun getObjects(
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("coordinator_id") coordinatorId: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): Response<List<SiteObjectDto>>

    @POST("objects")
    suspend fun createObject(@Body request: CreateObjectRequest): Response<SiteObjectDto>

    @GET("objects/{id}")
    suspend fun getObjectDetail(@Path("id") id: String): Response<SiteObjectDetailDto>

    @PUT("objects/{id}")
    suspend fun updateObject(@Path("id") id: String, @Body body: Map<String, String>): Response<SiteObjectDto>

    @DELETE("objects/{id}")
    suspend fun archiveObject(@Path("id") id: String): Response<Map<String, String>>

    /** POST /objects/{id}/photos — загрузить фото объекта (multipart) */
    @Multipart
    @POST("objects/{id}/photos")
    suspend fun uploadObjectPhoto(
        @Path("id") id: String,
        @Part photo: MultipartBody.Part
    ): Response<ObjectPhotoUploadResponse>

    /** POST /objects/{id}/files — загрузить файл объекта (multipart) */
    @Multipart
    @POST("objects/{id}/files")
    suspend fun uploadObjectFile(
        @Path("id") id: String,
        @Part file: MultipartBody.Part
    ): Response<ObjectFileUploadResponse>

    @GET("curator/objects")
    suspend fun getCuratorObjects(): Response<List<SiteObjectDto>>

    @GET("curator/objects/{id}")
    suspend fun getCuratorObjectDetail(@Path("id") id: String): Response<SiteObjectDetailDto>

    @POST("curator/objects")
    suspend fun createCuratorObject(@Body request: CreateObjectRequest): Response<SiteObjectDto>

    @PUT("curator/objects/{id}")
    suspend fun updateCuratorObject(@Path("id") id: String, @Body body: Map<String, String>): Response<SiteObjectDto>

    @DELETE("curator/objects/{id}")
    suspend fun archiveCuratorObject(@Path("id") id: String): Response<Map<String, String>>

    @POST("shifts/change-object")
    suspend fun changeShiftObject(@Body request: ChangeObjectRequest): Response<Map<String, String>>
}

@Serializable
data class ObjectPhotoUploadResponse(
    val url: String
)

@Serializable
data class ObjectFileUploadResponse(
    val url: String
)
