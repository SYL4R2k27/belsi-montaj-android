package com.belsi.work.data.remote.api

import com.belsi.work.data.remote.dto.coordinator.*
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

/**
 * API координатора — все эндпоинты серверного /coordinator/
 * Бэкенд: coordinator.py
 */
interface CoordinatorApi {

    /** GET /coordinator/dashboard → дашборд координатора */
    @GET("coordinator/dashboard")
    suspend fun getDashboard(): Response<CoordinatorDashboardDto>

    /** GET /coordinator/photos → фото для проверки */
    @GET("coordinator/photos")
    suspend fun getPhotos(): Response<CoordinatorPhotosResponse>

    /** POST /coordinator/photos/{id}/approve */
    @POST("coordinator/photos/{photoId}/approve")
    suspend fun approvePhoto(@Path("photoId") photoId: String): Response<Unit>

    /** POST /coordinator/photos/{id}/reject */
    @POST("coordinator/photos/{photoId}/reject")
    suspend fun rejectPhoto(
        @Path("photoId") photoId: String,
        @Body request: CoordinatorRejectPhotoRequest
    ): Response<Unit>

    /** GET /coordinator/tasks → задачи координатора */
    @GET("coordinator/tasks")
    suspend fun getTasks(): Response<CoordinatorTasksResponse>

    /** POST /coordinator/tasks → создать задачу */
    @POST("coordinator/tasks")
    suspend fun createTask(
        @Body request: CreateCoordinatorTaskRequest
    ): Response<CoordinatorTaskDto>

    /** GET /coordinator/team → команда координатора */
    @GET("coordinator/team")
    suspend fun getTeam(): Response<CoordinatorTeamResponse>

    /** GET /coordinator/reports → отчёты координатора */
    @GET("coordinator/reports")
    suspend fun getReports(): Response<CoordinatorReportsResponse>

    /** POST /coordinator/reports → создать отчёт */
    @POST("coordinator/reports")
    suspend fun createReport(
        @Body request: CreateReportRequest
    ): Response<CoordinatorReportDto>

    /** GET /coordinator/reports/{id} → детали отчёта */
    @GET("coordinator/reports/{reportId}")
    suspend fun getReport(
        @Path("reportId") reportId: String
    ): Response<CoordinatorReportDto>

    /** PUT /coordinator/reports/{id} → обновить отчёт */
    @PUT("coordinator/reports/{reportId}")
    suspend fun updateReport(
        @Path("reportId") reportId: String,
        @Body request: UpdateReportRequest
    ): Response<CoordinatorReportDto>

    /** GET /coordinator/site → информация об объекте координатора */
    @GET("coordinator/site")
    suspend fun getSite(): Response<CoordinatorSiteResponse>

    /** PUT /coordinator/site → обновить объект координатора */
    @PUT("coordinator/site")
    suspend fun updateSite(
        @Body request: UpdateSiteRequest
    ): Response<CoordinatorSiteDto>
}

// Запрос на отклонение фото координатором (только здесь используется)
@Serializable
data class CoordinatorRejectPhotoRequest(
    val reason: String
)
