package com.belsi.work.presentation.screens.curator.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.curator.CuratorSupportTicketDto
import com.belsi.work.data.repositories.CuratorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CuratorSupportViewModel @Inject constructor(
    private val curatorRepository: CuratorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CuratorSupportUiState())
    val uiState: StateFlow<CuratorSupportUiState> = _uiState.asStateFlow()

    init { loadTickets() }

    fun loadTickets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            curatorRepository.getSupportTickets()
                .onSuccess { tickets ->
                    val open = tickets.filter { it.status == "open" }
                    val inProgress = tickets.filter { it.status == "in_progress" }
                    val resolved = tickets.filter { it.status == "resolved" || it.status == "closed" }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allTickets = tickets,
                        openTickets = open,
                        inProgressTickets = inProgress,
                        resolvedTickets = resolved
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false,
                        error = it.message ?: "Ошибка загрузки тикетов")
                }
        }
    }

    fun refresh() { loadTickets() }
}

data class CuratorSupportUiState(
    val isLoading: Boolean = false,
    val allTickets: List<CuratorSupportTicketDto> = emptyList(),
    val openTickets: List<CuratorSupportTicketDto> = emptyList(),
    val inProgressTickets: List<CuratorSupportTicketDto> = emptyList(),
    val resolvedTickets: List<CuratorSupportTicketDto> = emptyList(),
    val error: String? = null
) {
    val isEmpty: Boolean get() = allTickets.isEmpty() && !isLoading
}
