package com.belsi.work.presentation.screens.curator.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.api.AnalyticsDayEntry
import com.belsi.work.data.repositories.CuratorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CuratorAnalyticsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val period: String = "week",
    val days: List<AnalyticsDayEntry> = emptyList()
)

@HiltViewModel
class CuratorAnalyticsViewModel @Inject constructor(
    private val curatorRepository: CuratorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CuratorAnalyticsUiState())
    val uiState: StateFlow<CuratorAnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics("week")
    }

    fun loadAnalytics(period: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, period = period) }

            curatorRepository.getAnalytics(period).fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            days = response.days
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Ошибка загрузки аналитики"
                        )
                    }
                }
            )
        }
    }

    fun switchPeriod(period: String) {
        if (period != _uiState.value.period) {
            loadAnalytics(period)
        }
    }
}
