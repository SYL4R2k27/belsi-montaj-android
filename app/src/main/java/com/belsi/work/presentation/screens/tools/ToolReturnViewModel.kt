package com.belsi.work.presentation.screens.tools

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.ToolCondition
import com.belsi.work.data.models.ToolTransaction
import com.belsi.work.data.repositories.ToolsRepository
import com.belsi.work.utils.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel для возврата инструмента
 * Фото при возврате ОБЯЗАТЕЛЬНО
 */
@HiltViewModel
class ToolReturnViewModel @Inject constructor(
    private val toolsRepository: ToolsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolReturnUiState())
    val uiState: StateFlow<ToolReturnUiState> = _uiState.asStateFlow()

    /**
     * Установить транзакцию для возврата
     */
    fun setTransaction(transaction: ToolTransaction) {
        _uiState.value = _uiState.value.copy(transaction = transaction)
    }

    /**
     * Установить состояние инструмента
     */
    fun setCondition(condition: ToolCondition) {
        _uiState.value = _uiState.value.copy(condition = condition)
    }

    /**
     * Установить комментарий
     */
    fun setComment(comment: String) {
        _uiState.value = _uiState.value.copy(comment = comment)
    }

    /**
     * Установить URI фото (ОБЯЗАТЕЛЬНО)
     */
    fun setPhotoUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(photoUri = uri)
    }

    /**
     * Вернуть инструмент
     */
    fun returnTool(context: Context, photoFile: File?) {
        val state = _uiState.value

        // Валидация
        if (state.transaction == null) {
            _uiState.value = _uiState.value.copy(error = "Транзакция не найдена")
            return
        }

        if (photoFile == null || !photoFile.exists()) {
            _uiState.value = _uiState.value.copy(error = "Фото при возврате обязательно")
            return
        }

        // Проверка интернета
        if (!NetworkUtils.isNetworkAvailable(context)) {
            _uiState.value = _uiState.value.copy(
                error = "Нет подключения к интернету. Проверьте соединение"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReturning = true, error = null)

            try {
                // Шаг 1: Загрузить фото (ОБЯЗАТЕЛЬНО)
                android.util.Log.d("ToolReturnViewModel", "Uploading return photo...")
                var photoUrl: String? = null

                toolsRepository.uploadToolPhoto(photoFile)
                    .onSuccess { response ->
                        photoUrl = response.photoUrl
                        android.util.Log.d("ToolReturnViewModel", "Return photo uploaded: $photoUrl")
                    }
                    .onFailure { error ->
                        android.util.Log.e("ToolReturnViewModel", "Photo upload failed", error)
                        _uiState.value = _uiState.value.copy(
                            isReturning = false,
                            error = "Ошибка загрузки фото: ${error.message}"
                        )
                        return@launch
                    }

                // Проверка что фото загружено
                if (photoUrl == null) {
                    _uiState.value = _uiState.value.copy(
                        isReturning = false,
                        error = "Не удалось загрузить фото. Повторите попытку"
                    )
                    return@launch
                }

                // Шаг 2: Вернуть инструмент
                android.util.Log.d("ToolReturnViewModel", "Returning tool: ${state.transaction.id}")
                toolsRepository.returnTool(
                    transactionId = state.transaction.id,
                    condition = state.condition,
                    comment = state.comment.ifBlank { null },
                    photoUrl = photoUrl
                )
                    .onSuccess { transaction ->
                        android.util.Log.d("ToolReturnViewModel", "Tool returned successfully: ${transaction.id}")
                        _uiState.value = _uiState.value.copy(
                            isReturning = false,
                            returnedTransaction = transaction,
                            error = null
                        )
                    }
                    .onFailure { error ->
                        android.util.Log.e("ToolReturnViewModel", "Tool return failed", error)
                        _uiState.value = _uiState.value.copy(
                            isReturning = false,
                            error = error.message ?: "Не удалось вернуть инструмент"
                        )
                    }
            } catch (e: Exception) {
                android.util.Log.e("ToolReturnViewModel", "Unexpected error", e)
                _uiState.value = _uiState.value.copy(
                    isReturning = false,
                    error = "Непредвиденная ошибка: ${e.message}"
                )
            }
        }
    }

    /**
     * Сбросить ошибку
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Сбросить состояние
     */
    fun reset() {
        _uiState.value = ToolReturnUiState()
    }
}

/**
 * UI состояние для возврата инструмента
 */
data class ToolReturnUiState(
    val transaction: ToolTransaction? = null,
    val condition: ToolCondition = ToolCondition.GOOD,
    val comment: String = "",
    val photoUri: Uri? = null,
    val isReturning: Boolean = false,
    val returnedTransaction: ToolTransaction? = null,
    val error: String? = null
) {
    val canReturn: Boolean
        get() = transaction != null && photoUri != null && !isReturning
}
