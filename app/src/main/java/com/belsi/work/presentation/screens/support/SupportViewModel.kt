package com.belsi.work.presentation.screens.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.SupportTicket
import com.belsi.work.data.repositories.SupportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val supportRepository: SupportRepository
) : ViewModel() {

    private val _tickets = MutableStateFlow<List<SupportTicket>>(emptyList())
    val tickets: StateFlow<List<SupportTicket>> = _tickets.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadTickets()
    }

    private fun loadTickets() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            supportRepository.getTickets(page = 1, limit = 50)
                .onSuccess { ticketsList ->
                    _tickets.value = ticketsList
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка загрузки тикетов"
                }

            _isLoading.value = false
        }
    }

    fun refresh() {
        loadTickets()
    }

    fun clearError() {
        _error.value = null
    }
}
