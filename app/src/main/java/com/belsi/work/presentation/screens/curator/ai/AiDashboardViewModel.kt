package com.belsi.work.presentation.screens.curator.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.api.CuratorApi
import com.belsi.work.data.remote.api.ProblemInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiDashboardViewModel @Inject constructor(
    private val curatorApi: CuratorApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiDashboardUiState())
    val uiState: StateFlow<AiDashboardUiState> = _uiState.asStateFlow()

    init { loadDashboard() }

    fun setPeriod(period: String) {
        _uiState.value = _uiState.value.copy(period = period)
        loadDashboard()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = curatorApi.getAiDashboard(_uiState.value.period)
                if (response.isSuccessful) {
                    val body = response.body()!!
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        totalAnalyzed = body.totalAnalyzed,
                        autoApproved = body.autoApproved,
                        needsAttention = body.needsAttention,
                        avgScore = body.avgScore,
                        categoryCounts = body.categoryCounts,
                        problemInstallers = body.problemInstallers
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

data class AiDashboardUiState(
    val period: String = "today",
    val isLoading: Boolean = false,
    val totalAnalyzed: Int = 0,
    val autoApproved: Int = 0,
    val needsAttention: Int = 0,
    val avgScore: Double = 0.0,
    val categoryCounts: Map<String, Int> = emptyMap(),
    val problemInstallers: List<ProblemInstaller> = emptyList()
)
