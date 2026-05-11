package com.belsi.work.data.remote.api

import com.belsi.work.data.models.Task
import com.belsi.work.data.models.ToolTransaction
import com.belsi.work.data.remote.dto.team.*
import retrofit2.Response
import retrofit2.http.*

/**
 * API бригадира - команда, фото, инструменты, задачи
 * Бэкенд: foreman.py, foreman_team.py
 *
 * Реальные endpoints сервера:
 * - GET /foreman/team → ForemanTeamResponse (items: List)
 * - DELETE /foreman/team/{id} → удалить из команды
 * - GET /foreman/photos → List<ForemanPhotoDto> (прямой массив)
 * - GET /foreman/photos/latest → List<ForemanPhotoDto>
 * - POST /foreman/photos/{id}/review → фото-ревью
 * - GET /foreman/tools → List<ForemanToolDto>
 * - GET /foreman/tools/history → List<ForemanToolTransactionDto>
 * - POST /foreman/tools/issue → выдать инструмент
 * - POST /foreman/tools/return → вернуть инструмент
 * - POST /foreman/reminders/photo → напоминание
 * - POST /foreman/tasks → создать задачу
 * - GET /tasks/created → List<ForemanTaskDto>
 */
interface TeamApi {

    /**
     * Получить список команды (монтажников)
     * GET /foreman/team → {"items": [...]}
     */
    @GET("foreman/team")
    suspend fun getTeamMembers(): Response<ForemanTeamResponse>

    /**
     * Удалить монтажника из команды
     * DELETE /foreman/team/{installerId}
     * Бэкенд: foreman.py
     */
    @DELETE("foreman/team/{installerId}")
    suspend fun removeTeamMember(
        @Path("installerId") installerId: String
    ): Response<Unit>

    /**
     * Получить фотографии на проверку
     * GET /foreman/photos → {"photos": [...]}
     */
    @GET("foreman/photos")
    suspend fun getTeamPhotos(): Response<ForemanPhotosResponse>

    /**
     * Получить последние фото команды
     * GET /foreman/photos/latest → {"photos": [...]}
     */
    @GET("foreman/photos/latest")
    suspend fun getLatestPhotos(): Response<ForemanPhotosResponse>

    /**
     * Проверить фото (одобрить/отклонить)
     * POST /foreman/photos/{photoId}/review
     */
    @POST("foreman/photos/{photoId}/review")
    suspend fun reviewPhoto(
        @Path("photoId") photoId: String,
        @Body request: PhotoReviewRequest
    ): Response<Unit>

    /**
     * Получить детали монтажника из своей команды
     * GET /foreman/team/{installerId}
     */
    @GET("foreman/team/{installerId}")
    suspend fun getTeamMemberDetail(
        @Path("installerId") installerId: String
    ): Response<InstallerDetailResponse>

    /**
     * Получить инструменты бригадира
     * GET /foreman/tools → [] прямой массив ToolOut
     */
    @GET("foreman/tools")
    suspend fun getForemanTools(): Response<List<ForemanToolDto>>

    /**
     * Получить историю движения инструментов
     * GET /foreman/tools/history → [] прямой массив
     */
    @GET("foreman/tools/history")
    suspend fun getToolsHistory(): Response<List<ForemanToolTransactionDto>>

    /**
     * Выдать инструмент монтажнику (как бригадир)
     * POST /foreman/tools/issue
     * Бэкенд: foreman.py
     */
    @POST("foreman/tools/issue")
    suspend fun foremanIssueTool(
        @Body request: ForemanIssueToolRequest
    ): Response<ToolTransaction>

    /**
     * Вернуть инструмент (как бригадир)
     * POST /foreman/tools/return
     * Бэкенд: foreman.py
     */
    @POST("foreman/tools/return")
    suspend fun foremanReturnTool(
        @Body request: ForemanReturnToolRequest
    ): Response<ToolTransaction>

    /**
     * Напомнить монтажнику о фото
     * POST /foreman/reminders/photo
     */
    @POST("foreman/reminders/photo")
    suspend fun sendPhotoReminder(
        @Body request: PhotoReminderRequest
    ): Response<PhotoReminderResponse>

    /**
     * Создать задачу (как бригадир)
     * POST /foreman/tasks
     * Бэкенд: foreman.py
     */
    @POST("foreman/tasks")
    suspend fun createForemanTask(
        @Body request: CreateTaskRequest
    ): Response<Task>

    /**
     * Получить задачи, созданные бригадиром
     * GET /tasks/created → [] прямой массив
     */
    @GET("tasks/created")
    suspend fun getCreatedTasks(): Response<List<ForemanTaskDto>>

    /**
     * Создать/получить групповой чат со всей бригадой
     * POST /foreman/team/group-thread
     */
    @POST("foreman/team/group-thread")
    suspend fun getOrCreateGroupThread(): Response<GroupThreadResponse>

    /**
     * Получить статистику простоев монтажника
     * GET /foreman/team/{installerId}/pause-stats
     */
    @GET("foreman/team/{installerId}/pause-stats")
    suspend fun getInstallerPauseStats(
        @Path("installerId") installerId: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<PauseStatsResponse>

    /**
     * Перевести монтажника на другой объект
     * POST /foreman/team/{installerId}/reassign
     */
    @POST("foreman/team/{installerId}/reassign")
    suspend fun reassignInstaller(
        @Path("installerId") installerId: String,
        @Body request: ReassignRequest
    ): Response<ReassignResponse>
}

/**
 * Запрос на проверку фото
 */
@kotlinx.serialization.Serializable
data class PhotoReviewRequest(
    val status: String,  // "approved" or "rejected"
    val comment: String? = null
)

/**
 * Запрос на напоминание о фото
 */
@kotlinx.serialization.Serializable
data class PhotoReminderRequest(
    @kotlinx.serialization.SerialName("installer_id")
    val installerId: String,
    val message: String? = null
)

/**
 * Ответ на напоминание
 */
@kotlinx.serialization.Serializable
data class PhotoReminderResponse(
    val success: Boolean,
    val message: String
)

/**
 * Запрос на выдачу инструмента бригадиром
 */
@kotlinx.serialization.Serializable
data class ForemanIssueToolRequest(
    @kotlinx.serialization.SerialName("tool_id")
    val toolId: String,
    @kotlinx.serialization.SerialName("installer_id")
    val installerId: String,
    val comment: String? = null,
    @kotlinx.serialization.SerialName("photo_url")
    val photoUrl: String? = null
)

/**
 * Запрос на возврат инструмента бригадиром
 */
@kotlinx.serialization.Serializable
data class ForemanReturnToolRequest(
    @kotlinx.serialization.SerialName("transaction_id")
    val transactionId: String,
    @kotlinx.serialization.SerialName("return_condition")
    val returnCondition: String = "good",  // "good", "damaged", "broken"
    @kotlinx.serialization.SerialName("return_comment")
    val returnComment: String? = null,
    @kotlinx.serialization.SerialName("return_photo_url")
    val returnPhotoUrl: String? = null
)

/**
 * Ответ на создание/получение группового треда
 */
@kotlinx.serialization.Serializable
data class GroupThreadResponse(
    @kotlinx.serialization.SerialName("thread_id")
    val threadId: String
)

/**
 * Ответ со статистикой простоев
 */
@kotlinx.serialization.Serializable
data class PauseStatsResponse(
    @kotlinx.serialization.SerialName("installer_id")
    val installerId: String,
    @kotlinx.serialization.SerialName("installer_name")
    val installerName: String? = null,
    @kotlinx.serialization.SerialName("total_shifts")
    val totalShifts: Int = 0,
    @kotlinx.serialization.SerialName("total_pause_seconds")
    val totalPauseSeconds: Long = 0,
    @kotlinx.serialization.SerialName("total_idle_seconds")
    val totalIdleSeconds: Long = 0,
    @kotlinx.serialization.SerialName("total_work_seconds")
    val totalWorkSeconds: Long = 0,
    @kotlinx.serialization.SerialName("avg_pause_per_shift")
    val avgPausePerShift: Double = 0.0,
    @kotlinx.serialization.SerialName("avg_idle_per_shift")
    val avgIdlePerShift: Double = 0.0,
    @kotlinx.serialization.SerialName("idle_percentage")
    val idlePercentage: Double = 0.0
)

/**
 * Запрос на перевод монтажника
 */
@kotlinx.serialization.Serializable
data class ReassignRequest(
    @kotlinx.serialization.SerialName("site_object_id")
    val siteObjectId: String
)

/**
 * Ответ на перевод монтажника
 */
@kotlinx.serialization.Serializable
data class ReassignResponse(
    val success: Boolean,
    @kotlinx.serialization.SerialName("shift_id")
    val shiftId: String? = null,
    @kotlinx.serialization.SerialName("site_object_id")
    val siteObjectId: String? = null,
    @kotlinx.serialization.SerialName("site_name")
    val siteName: String? = null
)
