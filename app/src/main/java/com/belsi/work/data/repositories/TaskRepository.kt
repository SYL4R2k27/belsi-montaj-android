package com.belsi.work.data.repositories

import com.belsi.work.data.models.Task
import com.belsi.work.data.remote.api.CreateTaskRequest
import com.belsi.work.data.remote.api.TasksApi
import com.belsi.work.data.remote.api.UpdateTaskRequest
import com.belsi.work.data.remote.error.parseApiError
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы с задачами
 */
interface TaskRepository {
    suspend fun createTask(
        title: String,
        description: String?,
        assignedTo: String,
        priority: String,
        dueAt: String?,
        meta: Map<String, String>?
    ): Result<Task>

    suspend fun getMyTasks(status: String? = null): Result<List<Task>>
    suspend fun getCreatedTasks(status: String? = null): Result<List<Task>>
    suspend fun getTask(taskId: String): Result<Task>
    suspend fun updateTaskStatus(taskId: String, status: String): Result<Task>

    suspend fun createBatchTasks(
        title: String,
        description: String?,
        assignedToIds: List<String>,
        priority: String,
        dueAt: String?,
        meta: Map<String, String>?
    ): Result<BatchTaskResult>
}

data class BatchTaskResult(
    val createdCount: Int,
    val tasks: List<Task>,
    val errors: List<String> = emptyList()
)

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val tasksApi: TasksApi,
    private val json: Json
) : TaskRepository {

    override suspend fun createTask(
        title: String,
        description: String?,
        assignedTo: String,
        priority: String,
        dueAt: String?,
        meta: Map<String, String>?
    ): Result<Task> {
        return try {
            android.util.Log.d("TaskRepository", "Creating task: $title, assigned to: $assignedTo")

            val request = CreateTaskRequest(
                title = title,
                description = description,
                assignedTo = assignedTo,
                priority = priority,
                dueAt = dueAt,
                meta = meta
            )

            val response = tasksApi.createTask(request)

            android.util.Log.d("TaskRepository", "Create task response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val task = response.body()!!
                android.util.Log.d("TaskRepository", "✅ Task created: ${task.id}")
                Result.success(task)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TaskRepository", "Failed to create task: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TaskRepository", "createTask exception", e)
            Result.failure(Exception("Ошибка создания задачи: ${e.message}", e))
        }
    }

    override suspend fun getMyTasks(status: String?): Result<List<Task>> {
        return try {
            android.util.Log.d("TaskRepository", "Loading my tasks, status: $status")

            val response = tasksApi.getMyTasks(status = status)

            android.util.Log.d("TaskRepository", "Get my tasks response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val tasks = response.body()!!
                android.util.Log.d("TaskRepository", "Loaded ${tasks.size} tasks")
                Result.success(tasks)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TaskRepository", "Failed to load my tasks: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TaskRepository", "getMyTasks exception", e)
            Result.failure(Exception("Ошибка загрузки задач: ${e.message}", e))
        }
    }

    override suspend fun getCreatedTasks(status: String?): Result<List<Task>> {
        return try {
            android.util.Log.d("TaskRepository", "Loading created tasks, status: $status")

            val response = tasksApi.getCreatedTasks(status = status)

            android.util.Log.d("TaskRepository", "Get created tasks response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val tasks = response.body()!!
                android.util.Log.d("TaskRepository", "Loaded ${tasks.size} created tasks")
                Result.success(tasks)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TaskRepository", "Failed to load created tasks: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TaskRepository", "getCreatedTasks exception", e)
            Result.failure(Exception("Ошибка загрузки созданных задач: ${e.message}", e))
        }
    }

    override suspend fun getTask(taskId: String): Result<Task> {
        return try {
            android.util.Log.d("TaskRepository", "Loading task: $taskId")

            val response = tasksApi.getTask(taskId)

            android.util.Log.d("TaskRepository", "Get task response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val task = response.body()!!
                android.util.Log.d("TaskRepository", "Loaded task: ${task.title}")
                Result.success(task)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TaskRepository", "Failed to load task: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TaskRepository", "getTask exception", e)
            Result.failure(Exception("Ошибка загрузки задачи: ${e.message}", e))
        }
    }

    override suspend fun updateTaskStatus(taskId: String, status: String): Result<Task> {
        return try {
            android.util.Log.d("TaskRepository", "Updating task $taskId status to: $status")

            val request = UpdateTaskRequest(status = status)
            val response = tasksApi.updateTask(taskId, request)

            android.util.Log.d("TaskRepository", "Update task response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val task = response.body()!!
                android.util.Log.d("TaskRepository", "✅ Task status updated")
                Result.success(task)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TaskRepository", "Failed to update task status: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TaskRepository", "updateTaskStatus exception", e)
            Result.failure(Exception("Ошибка обновления статуса задачи: ${e.message}", e))
        }
    }

    override suspend fun createBatchTasks(
        title: String,
        description: String?,
        assignedToIds: List<String>,
        priority: String,
        dueAt: String?,
        meta: Map<String, String>?
    ): Result<BatchTaskResult> {
        return try {
            android.util.Log.d("TaskRepository", "Creating batch tasks: $title for ${assignedToIds.size} users")

            val createdTasks = mutableListOf<Task>()
            val errors = mutableListOf<String>()

            for (userId in assignedToIds) {
                val request = CreateTaskRequest(
                    title = title,
                    description = description,
                    assignedTo = userId,
                    priority = priority,
                    dueAt = dueAt,
                    meta = meta
                )

                try {
                    val response = tasksApi.createTask(request)
                    if (response.isSuccessful && response.body() != null) {
                        createdTasks.add(response.body()!!)
                    } else {
                        errors.add("Ошибка для пользователя $userId: ${response.code()}")
                    }
                } catch (e: Exception) {
                    errors.add("Ошибка для пользователя $userId: ${e.message}")
                }
            }

            android.util.Log.d("TaskRepository", "Batch tasks created: ${createdTasks.size}, errors: ${errors.size}")

            Result.success(BatchTaskResult(
                createdCount = createdTasks.size,
                tasks = createdTasks,
                errors = errors
            ))
        } catch (e: Exception) {
            android.util.Log.e("TaskRepository", "createBatchTasks exception", e)
            Result.failure(Exception("Ошибка создания задач: ${e.message}", e))
        }
    }
}
