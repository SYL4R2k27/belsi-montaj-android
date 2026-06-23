package com.belsi.work.presentation.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.ToolTransaction
import com.belsi.work.data.repositories.ToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для списка моих инструментов (монтажник)
 */
@HiltViewModel
class ToolsListViewModel @Inject constructor(
    private val toolsRepository: ToolsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsListUiState())
    val uiState: StateFlow<ToolsListUiState> = _uiState.asStateFlow()

    init {
        loadMyTools()
    }

    /**
     * Загрузить мои инструменты
     */
    fun loadMyTools() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            toolsRepository.getMyTools()
                .onSuccess { transactions ->
                    android.util.Log.d("ToolsListViewModel", "Loaded ${transactions.size} tools")

                    // Разделяем активные и возвращенные
                    val active = transactions.filter { it.isActive }
                    val returned = transactions.filter { it.isReturned }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        activeTools = active,
                        returnedTools = returned,
                        error = null
                    )
                }
                .onFailure { error ->
                    android.util.Log.e("ToolsListViewModel", "Failed to load tools", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Не удалось загрузить инструменты"
                    )
                }
        }
    }

    /**
     * Обновить список (pull-to-refresh)
     */
    fun refresh() {
        loadMyTools()
    }
}

/**
 * UI состояние для списка инструментов
 */
data class ToolsListUiState(
    val isLoading: Boolean = false,
    val activeTools: List<ToolTransaction> = emptyList(),
    val returnedTools: List<ToolTransaction> = emptyList(),
    val error: String? = null
) {
    val hasActiveTools: Boolean
        get() = activeTools.isNotEmpty()

    val hasReturnedTools: Boolean
        get() = returnedTools.isNotEmpty()

    val isEmpty: Boolean
        get() = !hasActiveTools && !hasReturnedTools && !isLoading
}
