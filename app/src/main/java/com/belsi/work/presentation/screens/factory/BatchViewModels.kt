package com.belsi.work.presentation.screens.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.Batch
import com.belsi.work.data.models.BatchCreateRequest
import com.belsi.work.data.models.BatchHistoryItem
import com.belsi.work.data.models.BatchStatus
import com.belsi.work.data.repositories.BatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-05): ViewModels для Pipeline партии.
 *
 * Все три ViewModel используют BatchRepository (Result-обёртки).
 * При отсутствии сервера (404 на эндпоинт) — возвращают пустой список,
 * UI показывает empty-state. Это позволяет тестировать UI на 1.2.5
 * сервере без падений.
 */

data class BatchListUiState(
    val batches: List<Batch> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val filter: BatchStatus? = null,
)

@HiltViewModel
class BatchListViewModel @Inject constructor(
    private val repo: BatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BatchListUiState())
    val state: StateFlow<BatchListUiState> = _state.asStateFlow()

    init { load() }

    fun load(status: BatchStatus? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, filter = status)
            repo.listBatches(status = status)
                .onSuccess { _state.value = _state.value.copy(batches = it, isLoading = false) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun setFilter(status: BatchStatus?) {
        if (_state.value.filter == status) return
        load(status)
    }

    fun changeStatus(batchId: String, to: BatchStatus, comment: String? = null) {
        viewModelScope.launch {
            repo.changeStatus(batchId, to, comment).onSuccess { load(_state.value.filter) }
        }
    }
}


data class BatchDetailUiState(
    val batch: Batch? = null,
    val history: List<BatchHistoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BatchDetailViewModel @Inject constructor(
    private val repo: BatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BatchDetailUiState())
    val state: StateFlow<BatchDetailUiState> = _state.asStateFlow()

    fun load(batchId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val batchResult = repo.getBatch(batchId)
            val historyResult = repo.getHistory(batchId)
            _state.value = _state.value.copy(
                batch = batchResult.getOrNull(),
                history = historyResult.getOrNull().orEmpty(),
                isLoading = false,
                error = batchResult.exceptionOrNull()?.message,
            )
        }
    }

    fun changeStatus(to: BatchStatus, comment: String? = null) {
        val current = _state.value.batch ?: return
        viewModelScope.launch {
            repo.changeStatus(current.id.toString(), to, comment).onSuccess {
                load(current.id.toString())
            }
        }
    }
}


data class BatchCreateUiState(
    val isSubmitting: Boolean = false,
    val createdId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class BatchCreateViewModel @Inject constructor(
    private val repo: BatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BatchCreateUiState())
    val state: StateFlow<BatchCreateUiState> = _state.asStateFlow()

    fun submit(request: BatchCreateRequest) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            repo.createBatch(request)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        createdId = it.id.toString(),
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        error = it.message,
                    )
                }
        }
    }

    fun reset() { _state.value = BatchCreateUiState() }
}
