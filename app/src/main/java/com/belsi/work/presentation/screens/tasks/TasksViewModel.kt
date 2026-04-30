package com.belsi.work.presentation.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.Task
import com.belsi.work.data.models.TaskStatus
import com.belsi.work.data.models.TaskPriority
import com.belsi.work.data.repositories.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadMyTasks()
    }

    /**
     * Загрузить мои задачи (назначенные мне)
     */
    fun loadMyTasks(status: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _error.value = null

            taskRepository.getMyTasks(status)
                .onSuccess { tasks ->
                    android.util.Log.d("TasksViewModel", "Loaded ${tasks.size} tasks")
                    _uiState.value = _uiState.value.copy(
                        myTasks = tasks,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("TasksViewModel", "Failed to load tasks", e)
                    _error.value = e.message ?: "Ошибка загрузки задач"
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    /**
     * Загрузить созданные мной задачи (для бригадира/куратора)
     */
    fun loadCreatedTasks(status: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCreated = true)
            _error.value = null

            taskRepository.getCreatedTasks(status)
                .onSuccess { tasks ->
                    android.util.Log.d("TasksViewModel", "Loaded ${tasks.size} created tasks")
                    _uiState.value = _uiState.value.copy(
                        createdTasks = tasks,
                        isLoadingCreated = false
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("TasksViewModel", "Failed to load created tasks", e)
                    _error.value = e.message ?: "Ошибка загрузки созданных задач"
                    _uiState.value = _uiState.value.copy(isLoadingCreated = false)
                }
        }
    }

    /**
     * Загрузить все задачи (для бригадира/куратора)
     */
    fun loadAllTasks() {
        loadMyTasks()
        loadCreatedTasks()
    }

    /**
     * Создать новую задачу
     */
    fun createTask(
        title: String,
        description: String?,
        assignedTo: String,
        priority: String = TaskPriority.NORMAL,
        dueAt: String? = null,
        meta: Map<String, String>? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            _error.value = null

            taskRepository.createTask(
                title = title,
                description = description,
                assignedTo = assignedTo,
                priority = priority,
                dueAt = dueAt,
                meta = meta
            )
                .onSuccess { task ->
                    android.util.Log.d("TasksViewModel", "Task created: ${task.id}")
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createdTasks = listOf(task) + _uiState.value.createdTasks,
                        createSuccess = true
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("TasksViewModel", "Failed to create task", e)
                    _error.value = e.message ?: "Ошибка создания задачи"
                    _uiState.value = _uiState.value.copy(isCreating = false)
                }
        }
    }

    /**
     * Создать задачи для нескольких пользователей (batch)
     */
    fun createBatchTasks(
        title: String,
        description: String?,
        assignedToIds: List<String>,
        priority: String = TaskPriority.NORMAL,
        dueAt: String? = null,
        meta: Map<String, String>? = null
    ) {
        if (assignedToIds.isEmpty()) {
            _error.value = "Выберите хотя бы одного исполнителя"
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            _error.value = null

            taskRepository.createBatchTasks(
                title = title,
                description = description,
                assignedToIds = assignedToIds,
                priority = priority,
                dueAt = dueAt,
                meta = meta
            )
                .onSuccess { result ->
                    android.util.Log.d("TasksViewModel", "Batch tasks created: ${result.createdCount}")
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createdTasks = result.tasks + _uiState.value.createdTasks,
                        createSuccess = true,
                        lastBatchCreatedCount = result.createdCount
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("TasksViewModel", "Failed to create batch tasks", e)
                    _error.value = e.message ?: "Ошибка создания задач"
                    _uiState.value = _uiState.value.copy(isCreating = false)
                }
        }
    }

    /**
     * Начать выполнение задачи
     */
    fun startTask(taskId: String) {
        updateTaskStatus(taskId, TaskStatus.IN_PROGRESS)
    }

    /**
     * Завершить задачу
     */
    fun completeTask(taskId: String) {
        updateTaskStatus(taskId, TaskStatus.DONE)
    }

    /**
     * Отменить задачу
     */
    fun cancelTask(taskId: String) {
        updateTaskStatus(taskId, TaskStatus.CANCELLED)
    }

    private fun updateTaskStatus(taskId: String, status: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(updatingTaskId = taskId)
            _error.value = null

            taskRepository.updateTaskStatus(taskId, status)
                .onSuccess { updatedTask ->
                    android.util.Log.d("TasksViewModel", "Task status updated: ${updatedTask.status}")

                    // Обновляем задачу в списке
                    val updatedMyTasks = _uiState.value.myTasks.map {
                        if (it.id == taskId) updatedTask else it
                    }
                    val updatedCreatedTasks = _uiState.value.createdTasks.map {
                        if (it.id == taskId) updatedTask else it
                    }

                    _uiState.value = _uiState.value.copy(
                        myTasks = updatedMyTasks,
                        createdTasks = updatedCreatedTasks,
                        updatingTaskId = null
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("TasksViewModel", "Failed to update task status", e)
                    _error.value = e.message ?: "Ошибка обновления статуса"
                    _uiState.value = _uiState.value.copy(updatingTaskId = null)
                }
        }
    }

    fun resetCreateSuccess() {
        _uiState.value = _uiState.value.copy(createSuccess = false)
    }

    fun clearError() {
        _error.value = null
    }

    fun refresh() {
        loadAllTasks()
    }
}

data class TasksUiState(
    val myTasks: List<Task> = emptyList(),
    val createdTasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingCreated: Boolean = false,
    val isCreating: Boolean = false,
    val updatingTaskId: String? = null,
    val createSuccess: Boolean = false,
    val lastBatchCreatedCount: Int = 0
)
