package com.belsi.work.data.remote.api

import com.belsi.work.data.models.Task
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

/**
 * BELSI API - Task Management
 * Базовый URL: https://api.belsi.ru
 */
interface TasksApi {

    /**
     * Создать новую задачу
     * POST /tasks
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: foreman, curator
     */
    @POST("tasks")
    suspend fun createTask(
        @Body request: CreateTaskRequest
    ): Response<Task>

    /**
     * Получить мои задачи (назначенные мне)
     * GET /tasks/my
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: все роли
     */
    @GET("tasks/my")
    suspend fun getMyTasks(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<Task>>

    /**
     * Получить задачи, созданные мной
     * GET /tasks/created
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: foreman, curator
     */
    @GET("tasks/created")
    suspend fun getCreatedTasks(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<Task>>

    /**
     * Получить детали задачи
     * GET /tasks/{taskId}
     *
     * Требует: Authorization: Bearer <token>
     */
    @GET("tasks/{taskId}")
    suspend fun getTask(
        @Path("taskId") taskId: String
    ): Response<Task>

    /**
     * Обновить статус задачи
     * PATCH /tasks/{taskId}
     *
     * Требует: Authorization: Bearer <token>
     */
    @PATCH("tasks/{taskId}")
    suspend fun updateTask(
        @Path("taskId") taskId: String,
        @Body request: UpdateTaskRequest
    ): Response<Task>
}

// Request DTOs
@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    @SerialName("assigned_to")
    val assignedTo: String,
    val priority: String = "normal",  // "low", "normal", "high"
    @SerialName("due_at")
    val dueAt: String? = null,  // ISO datetime
    val meta: Map<String, String>? = null
)

@Serializable
data class UpdateTaskRequest(
    val status: String  // "new", "in_progress", "done", "cancelled"
)
