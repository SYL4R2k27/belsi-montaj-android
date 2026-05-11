package com.belsi.work.presentation.screens.foreman.installerdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.api.PauseStatsResponse
import com.belsi.work.data.remote.api.ReassignResponse
import com.belsi.work.data.remote.dto.team.InstallerDetailResponse
import com.belsi.work.data.repositories.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstallerDetailUiState(
    val isLoading: Boolean = false,
    val detail: InstallerDetailResponse? = null,
    val error: String? = null,
    val pauseStats: PauseStatsResponse? = null,
    val isLoadingPauseStats: Boolean = false,
    val reassignSuccess: String? = null,
    val isReassigning: Boolean = false
)

@HiltViewModel
class InstallerDetailViewModel @Inject constructor(
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallerDetailUiState())
    val uiState: StateFlow<InstallerDetailUiState> = _uiState.asStateFlow()

    fun loadInstallerDetail(installerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            teamRepository.getTeamMemberDetail(installerId).fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(isLoading = false, detail = detail)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки данных")
                    }
                }
            )
        }
    }

    fun retry(installerId: String) {
        loadInstallerDetail(installerId)
    }

    fun loadPauseStats(installerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPauseStats = true) }
            teamRepository.getInstallerPauseStats(installerId).fold(
                onSuccess = { stats ->
                    _uiState.update { it.copy(isLoadingPauseStats = false, pauseStats = stats) }
                },
                onFailure = { e ->
                    android.util.Log.e("InstallerDetailVM", "Ошибка загрузки статистики простоев", e)
                    _uiState.update { it.copy(isLoadingPauseStats = false) }
                }
            )
        }
    }

    fun reassignInstaller(installerId: String, siteObjectId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReassigning = true) }
            teamRepository.reassignInstaller(installerId, siteObjectId).fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            isReassigning = false,
                            reassignSuccess = "Монтажник переведён на объект"
                        )
                    }
                    loadInstallerDetail(installerId)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isReassigning = false,
                            error = e.message ?: "Ошибка перевода"
                        )
                    }
                }
            )
        }
    }

    fun clearReassignSuccess() {
        _uiState.update { it.copy(reassignSuccess = null) }
    }
}
