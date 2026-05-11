package com.belsi.work.presentation.screens.installer.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для запроса инструмента у бригадира
 * Создаёт task для foreman с просьбой выдать инструмент
 */
@HiltViewModel
class RequestToolViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestToolUiState())
    val uiState: StateFlow<RequestToolUiState> = _uiState.asStateFlow()

    /**
     * Обновить название инструмента
     */
    fun updateToolName(name: String) {
        _uiState.value = _uiState.value.copy(
            toolName = name,
            errorMessage = null
        )
    }

    /**
     * Обновить комментарий
     */
    fun updateComment(comment: String) {
        _uiState.value = _uiState.value.copy(
            comment = comment,
            errorMessage = null
        )
    }

    /**
     * Отправить запрос на инструмент
     * Создаёт task для foreman (задача уйдёт бригадиру автоматически)
     */
    fun requestTool(foremanId: String) {
        val toolName = _uiState.value.toolName.trim()
        val comment = _uiState.value.comment.trim()

        if (toolName.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Укажите название инструмента"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val title = "Запрос инструмента: $toolName"
            val description = if (comment.isNotEmpty()) {
                "Прошу выдать инструмент: $toolName\n\nКомментарий: $comment"
            } else {
                "Прошу выдать инструмент: $toolName"
            }

            // Добавляем meta-данные, чтобы foreman понимал, что это запрос инструмента
            val meta = mapOf(
                "type" to "tool_request",
                "tool_name" to toolName
            )

            taskRepository.createTask(
                title = title,
                description = description,
                assignedTo = foremanId,
                priority = "medium",
                dueAt = null,
                meta = meta
            )
                .onSuccess { task ->
                    android.util.Log.d("RequestToolVM", "Tool request task created: ${task.id}")

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true,
                        successMessage = "Запрос отправлен бригадиру"
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("RequestToolVM", "Failed to create tool request", e)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Ошибка: ${e.message ?: "Не удалось отправить запрос"}"
                    )
                }
        }
    }

    /**
     * Сброс состояния после успеха
     */
    fun resetSuccess() {
        _uiState.value = RequestToolUiState()
    }
}

data class RequestToolUiState(
    val toolName: String = "",
    val comment: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)
