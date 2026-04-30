package com.belsi.work.data.remote.api

import com.belsi.work.data.models.Task
import com.belsi.work.data.remote.dto.curator.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * API куратора — все эндпоинты серверного /curator/
 * Сервер: api.belsi.ru
 * Бэкенд: curator.py, photo_review.py, photos_feed.py
 */
interface CuratorApi {

    /** GET /curator/foremen → List<CuratorForemanDto> (массив!) */
    @GET("curator/foremen")
    suspend fun getForemen(): Response<List<CuratorForemanDto>>

    /** GET /curator/dashboard → CuratorDashboardDto (один объект) */
    @GET("curator/dashboard")
    suspend fun getDashboard(): Response<CuratorDashboardDto>

    /** GET /curator/photos → {"photos": [...]} */
    @GET("curator/photos")
    suspend fun getPhotos(
        @Query("site_object_id") siteObjectId: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 200
    ): Response<CuratorPhotosResponse>

    /** GET /curator/photos/latest → последние фото (photos_feed.py) */
    @GET("curator/photos/latest")
    suspend fun getLatestPhotos(): Response<CuratorPhotosResponse>

    /** POST /photos/{photoId}/approve */
    @POST("photos/{photoId}/approve")
    suspend fun approvePhoto(@Path("photoId") photoId: String): Response<Unit>

    /** POST /photos/{photoId}/reject */
    @POST("photos/{photoId}/reject")
    suspend fun rejectPhoto(
        @Path("photoId") photoId: String,
        @Body request: RejectPhotoRequest
    ): Response<Unit>

    /** POST /photos/{photoId}/review (photo_review.py) */
    @POST("photos/{photoId}/review")
    suspend fun reviewPhoto(
        @Path("photoId") photoId: String,
        @Body request: PhotoReviewRequest
    ): Response<Unit>

    /** POST /curator/photos/batch-review — массовое одобрение/отклонение */
    @POST("curator/photos/batch-review")
    suspend fun batchReviewPhotos(
        @Body request: BatchReviewRequest
    ): Response<BatchReviewResponse>

    /** GET /curator/support → List<CuratorSupportTicketDto> (массив!) */
    @GET("curator/support")
    suspend fun getSupportTickets(): Response<List<CuratorSupportTicketDto>>

    /** GET /curator/tools/transactions → пагинированный */
    @GET("curator/tools/transactions")
    suspend fun getToolTransactions(
        @Query("status") status: String? = null,
        @Query("installer_id") installerId: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<CuratorToolTransactionsResponse>

    /** POST /curator/tools/issue — выдать инструмент (как куратор) */
    @POST("curator/tools/issue")
    suspend fun curatorIssueTool(
        @Body request: CuratorIssueToolRequest
    ): Response<Unit>

    /** GET /curator/foremen → List<ForemanDto> (бригадиры с полной инфой и монтажниками) */
    @GET("curator/foremen")
    suspend fun getForemenFull(): Response<List<ForemanDto>>

    /** GET /curator/installers/unassigned → List<UnassignedInstallerDto> */
    @GET("curator/installers/unassigned")
    suspend fun getUnassignedInstallers(): Response<List<UnassignedInstallerDto>>

    /** GET /curator/users/all → List<AllUserDto> (все пользователи) */
    @GET("curator/users/all")
    suspend fun getAllUsers(
        @Query("role") role: String? = null,
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0
    ): Response<List<AllUserDto>>

    /** GET /curator/users/{userId} → UserDetailDto (детальная информация) */
    @GET("curator/users/{userId}")
    suspend fun getUserDetail(
        @Path("userId") userId: String
    ): Response<UserDetailDto>

    /** POST /curator/users/{userId}/role → смена роли пользователя */
    @POST("curator/users/{userId}/role")
    suspend fun changeUserRole(
        @Path("userId") userId: String,
        @Body request: ChangeRoleRequest
    ): Response<ChangeRoleResponse>

    /** DELETE /curator/users/{userId} — удалить пользователя (каскадно) */
    @DELETE("curator/users/{userId}")
    suspend fun deleteUser(
        @Path("userId") userId: String
    ): Response<Unit>

    /** POST /curator/tasks — создать задачу (как куратор) */
    @POST("curator/tasks")
    suspend fun createTask(
        @Body request: CreateTaskRequest
    ): Response<Task>

    /** DELETE /curator/tasks/{taskId} — удалить задачу */
    @DELETE("curator/tasks/{taskId}")
    suspend fun deleteTask(
        @Path("taskId") taskId: String
    ): Response<Unit>

    /** DELETE /curator/shifts/{shiftId} — удалить смену */
    @DELETE("curator/shifts/{shiftId}")
    suspend fun deleteShift(
        @Path("shiftId") shiftId: String
    ): Response<Unit>

    /** GET /curator/users/{userId}/reports — отчёты пользователя */
    @GET("curator/users/{userId}/reports")
    suspend fun getUserReports(
        @Path("userId") userId: String
    ): Response<List<CuratorReportDto>>

    /** PUT /curator/users/{userId}/reports/{reportId}/feedback — обратная связь по отчёту */
    @PUT("curator/users/{userId}/reports/{reportId}/feedback")
    suspend fun setReportFeedback(
        @Path("userId") userId: String,
        @Path("reportId") reportId: String,
        @Body request: ReportFeedbackRequest
    ): Response<Unit>

    /** POST /curator/screenshots — загрузить скриншот */
    @Multipart
    @POST("curator/screenshots")
    suspend fun uploadScreenshot(
        @Part screenshot: MultipartBody.Part
    ): Response<ScreenshotUploadResponse>

    /** GET /curator/users/{userId}/role-history — история ролей */
    @GET("curator/users/{userId}/role-history")
    suspend fun getUserRoleHistory(
        @Path("userId") userId: String
    ): Response<RoleHistoryResponse>

    /** GET /curator/analytics — аналитика по дням */
    @GET("curator/analytics")
    suspend fun getAnalytics(
        @Query("period") period: String = "week"
    ): Response<AnalyticsResponse>

    /** GET /curator/ai-settings — настройки автоодобрения */
    @GET("curator/ai-settings")
    suspend fun getAiSettings(): Response<AiSettingsResponse>

    /** PUT /curator/ai-settings — обновить порог автоодобрения */
    @PUT("curator/ai-settings")
    suspend fun updateAiSettings(
        @Body request: AiSettingsRequest
    ): Response<Unit>

    /** GET /curator/ai-dashboard — AI аналитика по фото */
    @GET("curator/ai-dashboard")
    suspend fun getAiDashboard(
        @Query("period") period: String = "today"
    ): Response<AiDashboardResponse>
}

/**
 * Запрос на массовое одобрение/отклонение фото
 */
@kotlinx.serialization.Serializable
data class BatchReviewRequest(
    @kotlinx.serialization.SerialName("photo_ids")
    val photoIds: List<String>,
    val action: String, // "approve" или "reject"
    val reason: String? = null
)

/**
 * Ответ на массовое одобрение/отклонение фото
 */
@kotlinx.serialization.Serializable
data class BatchReviewResponse(
    val success: Boolean,
    val processed: Int = 0,
    val total: Int = 0
)

/**
 * Запрос на отклонение фото
 */
@kotlinx.serialization.Serializable
data class RejectPhotoRequest(
    val reason: String
)

/**
 * Запрос на смену роли
 */
@kotlinx.serialization.Serializable
data class ChangeRoleRequest(
    val role: String
)

/**
 * Ответ на удаление пользователя
 */
@kotlinx.serialization.Serializable
data class DeleteUserResponse(
    @kotlinx.serialization.SerialName("deleted_user_id")
    val deletedUserId: String,
    @kotlinx.serialization.SerialName("deleted_shifts")
    val deletedShifts: Int = 0,
    @kotlinx.serialization.SerialName("deleted_photos")
    val deletedPhotos: Int = 0,
    @kotlinx.serialization.SerialName("deleted_tasks")
    val deletedTasks: Int = 0,
    @kotlinx.serialization.SerialName("deleted_tickets")
    val deletedTickets: Int = 0,
    @kotlinx.serialization.SerialName("deleted_invites")
    val deletedInvites: Int = 0,
    @kotlinx.serialization.SerialName("deleted_memberships")
    val deletedMemberships: Int = 0,
    @kotlinx.serialization.SerialName("deleted_messages")
    val deletedMessages: Int = 0,
    @kotlinx.serialization.SerialName("deleted_tools")
    val deletedTools: Int = 0
)

/**
 * Ответ на смену роли
 */
@kotlinx.serialization.Serializable
data class ChangeRoleResponse(
    val success: Boolean,
    @kotlinx.serialization.SerialName("user_id")
    val userId: String,
    @kotlinx.serialization.SerialName("old_role")
    val oldRole: String,
    @kotlinx.serialization.SerialName("new_role")
    val newRole: String,
    val message: String? = null
)

/**
 * Запрос на выдачу инструмента куратором
 */
@kotlinx.serialization.Serializable
data class CuratorIssueToolRequest(
    @kotlinx.serialization.SerialName("tool_id")
    val toolId: String,
    @kotlinx.serialization.SerialName("installer_id")
    val installerId: String,
    val comment: String? = null
)

/**
 * Запрос на обратную связь по отчёту
 */
@kotlinx.serialization.Serializable
data class ReportFeedbackRequest(
    val feedback: String,
    val rating: Int? = null
)

/**
 * DTO отчёта куратора
 */
@kotlinx.serialization.Serializable
data class CuratorReportDto(
    val id: String,
    @kotlinx.serialization.SerialName("user_id")
    val userId: String,
    @kotlinx.serialization.SerialName("site_object_id")
    val siteObjectId: String? = null,
    val title: String? = null,
    val content: String? = null,
    @kotlinx.serialization.SerialName("photo_urls")
    val photoUrls: List<String> = emptyList(),
    val feedback: String? = null,
    val rating: Int? = null,
    @kotlinx.serialization.SerialName("created_at")
    val createdAt: String? = null
)

/**
 * Ответ на загрузку скриншота
 */
@kotlinx.serialization.Serializable
data class ScreenshotUploadResponse(
    val url: String
)

/**
 * Ответ с историей ролей
 */
@kotlinx.serialization.Serializable
data class RoleHistoryResponse(
    val history: List<RoleChangeEntry> = emptyList()
)

@kotlinx.serialization.Serializable
data class RoleChangeEntry(
    val id: String,
    @kotlinx.serialization.SerialName("old_role")
    val oldRole: String,
    @kotlinx.serialization.SerialName("new_role")
    val newRole: String,
    @kotlinx.serialization.SerialName("changed_by")
    val changedBy: String? = null,
    @kotlinx.serialization.SerialName("changed_by_name")
    val changedByName: String? = null,
    @kotlinx.serialization.SerialName("changed_at")
    val changedAt: String? = null
)

/**
 * Ответ с аналитикой
 */
@kotlinx.serialization.Serializable
data class AnalyticsResponse(
    val period: String,
    val days: List<AnalyticsDayEntry> = emptyList()
)

@kotlinx.serialization.Serializable
data class AiDashboardResponse(
    @kotlinx.serialization.SerialName("total_analyzed") val totalAnalyzed: Int = 0,
    @kotlinx.serialization.SerialName("auto_approved") val autoApproved: Int = 0,
    @kotlinx.serialization.SerialName("needs_attention") val needsAttention: Int = 0,
    @kotlinx.serialization.SerialName("avg_score") val avgScore: Double = 0.0,
    @kotlinx.serialization.SerialName("category_counts") val categoryCounts: Map<String, Int> = emptyMap(),
    @kotlinx.serialization.SerialName("problem_installers") val problemInstallers: List<ProblemInstaller> = emptyList()
)

@kotlinx.serialization.Serializable
data class ProblemInstaller(
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    val name: String,
    @kotlinx.serialization.SerialName("problem_count") val problemCount: Int
)

@kotlinx.serialization.Serializable
data class AiSettingsResponse(
    val threshold: Int = 0
)

@kotlinx.serialization.Serializable
data class AiSettingsRequest(
    val threshold: Int
)

@kotlinx.serialization.Serializable
data class AnalyticsDayEntry(
    val date: String,
    val photos: Int = 0,
    val shifts: Int = 0,
    @kotlinx.serialization.SerialName("work_hours")
    val workHours: Double = 0.0,
    @kotlinx.serialization.SerialName("idle_hours")
    val idleHours: Double = 0.0
)
