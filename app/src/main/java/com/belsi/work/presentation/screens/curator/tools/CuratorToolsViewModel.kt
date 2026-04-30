package com.belsi.work.presentation.screens.curator.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.curator.CuratorToolTransactionDto
import com.belsi.work.data.repositories.CuratorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CuratorToolsViewModel @Inject constructor(
    private val curatorRepository: CuratorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CuratorToolsUiState())
    val uiState: StateFlow<CuratorToolsUiState> = _uiState.asStateFlow()

    init { loadTransactions() }

    fun loadTransactions(status: String? = null, installerId: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            curatorRepository.getToolTransactions(status, installerId)
                .onSuccess { response ->
                    val issued = response.items.filter { it.isActive }
                    val returned = response.items.filter { it.isReturned }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allTransactions = response.items,
                        issuedTransactions = issued,
                        returnedTransactions = returned,
                        totalPages = response.totalPages,
                        currentPage = response.page,
                        totalItems = response.totalItems
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false,
                        error = it.message ?: "Ошибка загрузки транзакций")
                }
        }
    }

    fun filterByStatus(filter: ToolTransactionFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        val statusParam = when (filter) {
            ToolTransactionFilter.ALL -> null
            ToolTransactionFilter.ISSUED -> "issued"
            ToolTransactionFilter.RETURNED -> "returned"
        }
        loadTransactions(status = statusParam, installerId = _uiState.value.selectedInstallerId)
    }

    fun refresh() {
        val statusParam = when (_uiState.value.selectedFilter) {
            ToolTransactionFilter.ALL -> null
            ToolTransactionFilter.ISSUED -> "issued"
            ToolTransactionFilter.RETURNED -> "returned"
        }
        loadTransactions(status = statusParam, installerId = _uiState.value.selectedInstallerId)
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(selectedFilter = ToolTransactionFilter.ALL, selectedInstallerId = null)
        loadTransactions()
    }
}

data class CuratorToolsUiState(
    val isLoading: Boolean = false,
    val allTransactions: List<CuratorToolTransactionDto> = emptyList(),
    val issuedTransactions: List<CuratorToolTransactionDto> = emptyList(),
    val returnedTransactions: List<CuratorToolTransactionDto> = emptyList(),
    val selectedFilter: ToolTransactionFilter = ToolTransactionFilter.ALL,
    val selectedInstallerId: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val error: String? = null
) {
    val isEmpty: Boolean get() = allTransactions.isEmpty() && !isLoading
    val isFiltered: Boolean get() = selectedFilter != ToolTransactionFilter.ALL || selectedInstallerId != null
}

enum class ToolTransactionFilter {
    ALL, ISSUED, RETURNED
}
