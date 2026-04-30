package com.belsi.work.presentation.screens.curator.userdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.api.RoleChangeEntry
import com.belsi.work.data.remote.dto.coordinator.CoordinatorReportDto
import com.belsi.work.data.remote.dto.curator.CuratorPhotoDto
import com.belsi.work.data.remote.dto.curator.UserDetailDto
import com.belsi.work.data.repositories.CuratorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CuratorUserDetailUiState(
    val isLoading: Boolean = false,
    val userDetail: UserDetailDto? = null,
    val error: String? = null,
    val showTaskDialog: Boolean = false,
    val taskCreated: Boolean = false,
    val showRoleDialog: Boolean = false,
    val roleChangeSuccess: String? = null,
    val showDeleteDialog: Boolean = false,
    val userDeleted: Boolean = false,
    val deleteMessage: String? = null,
    val shiftPhotos: List<CuratorPhotoDto> = emptyList(),
    val isLoadingPhotos: Boolean = false,
    val coordinatorReports: List<CoordinatorReportDto> = emptyList(),
    val isLoadingReports: Boolean = false,
    val roleHistory: List<RoleChangeEntry> = emptyList(),
    val isLoadingRoleHistory: Boolean = false
)

@HiltViewModel
class CuratorUserDetailViewModel @Inject constructor(
    private val curatorRepository: CuratorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CuratorUserDetailUiState())
    val uiState: StateFlow<CuratorUserDetailUiState> = _uiState.asStateFlow()

    fun loadUserDetail(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            curatorRepository.getUserDetail(userId).fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userDetail = detail
                        )
                    }
                    // Автоматически загружаем фото текущей смены
                    if (detail.isOnShift && detail.currentShiftId != null) {
                        loadShiftPhotos(userId, detail.currentShiftId)
                    } else {
                        _uiState.update { it.copy(shiftPhotos = emptyList()) }
                    }
                    // Загружаем отчёты координатора
                    if (detail.role == "coordinator") {
                        loadCoordinatorReports(userId)
                    }
                    // Загружаем историю ролей
                    loadRoleHistory(userId)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Ошибка загрузки"
                        )
                    }
                }
            )
        }
    }

    private fun loadShiftPhotos(userId: String, shiftId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPhotos = true) }

            curatorRepository.getPhotos().fold(
                onSuccess = { allPhotos ->
                    // Фильтруем фото по shiftId
                    val shiftPhotos = allPhotos.filter { it.shiftId == shiftId }
                    _uiState.update {
                        it.copy(
                            isLoadingPhotos = false,
                            shiftPhotos = shiftPhotos
                        )
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("CuratorUserDetail", "Ошибка загрузки фото смены", e)
                    _uiState.update {
                        it.copy(
                            isLoadingPhotos = false,
                            shiftPhotos = emptyList()
                        )
                    }
                }
            )
        }
    }

    private fun loadCoordinatorReports(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingReports = true) }

            curatorRepository.getUserReports(userId).fold(
                onSuccess = { reports ->
                    // Маппим CuratorReportDto → CoordinatorReportDto для совместимости с UI
                    val mapped = reports.map { r ->
                        CoordinatorReportDto(
                            id = r.id,
                            reportDate = r.createdAt,
                            content = r.content ?: "",
                            status = "submitted",
                            photoUrls = r.photoUrls,
                            curatorFeedback = r.feedback,
                            createdAt = r.createdAt
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingReports = false,
                            coordinatorReports = mapped
                        )
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("CuratorUserDetail", "Ошибка загрузки отчётов", e)
                    _uiState.update {
                        it.copy(
                            isLoadingReports = false,
                            coordinatorReports = emptyList()
                        )
                    }
                }
            )
        }
    }

    private fun loadRoleHistory(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRoleHistory = true) }
            curatorRepository.getUserRoleHistory(userId).fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            isLoadingRoleHistory = false,
                            roleHistory = response.history
                        )
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("CuratorUserDetail", "Ошибка загрузки истории ролей", e)
                    _uiState.update {
                        it.copy(isLoadingRoleHistory = false, roleHistory = emptyList())
                    }
                }
            )
        }
    }

    fun addReportFeedback(userId: String, reportId: String, feedback: String) {
        viewModelScope.launch {
            curatorRepository.setReportFeedback(userId, reportId, feedback, null).fold(
                onSuccess = {
                    // Перезагружаем отчёты чтобы обновить статус
                    loadCoordinatorReports(userId)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message ?: "Ошибка отправки обратной связи") }
                }
            )
        }
    }

    fun showCreateTaskDialog() {
        _uiState.update { it.copy(showTaskDialog = true) }
    }

    fun hideCreateTaskDialog() {
        _uiState.update { it.copy(showTaskDialog = false) }
    }

    fun createTask(userId: String, title: String, description: String, priority: String) {
        viewModelScope.launch {
            android.util.Log.d("CuratorUserDetail", "Creating task: $title for user $userId")
            _uiState.update {
                it.copy(
                    showTaskDialog = false,
                    taskCreated = true
                )
            }
        }
    }

    fun showRoleDialog() {
        _uiState.update { it.copy(showRoleDialog = true) }
    }

    fun hideRoleDialog() {
        _uiState.update { it.copy(showRoleDialog = false) }
    }

    fun changeRole(userId: String, newRole: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(showRoleDialog = false, isLoading = true) }

            curatorRepository.changeUserRole(userId, newRole).fold(
                onSuccess = { response ->
                    val roleLabel = when (newRole) {
                        "foreman" -> "Бригадир"
                        "installer" -> "Монтажник"
                        "coordinator" -> "Координатор"
                        "curator" -> "Куратор"
                        else -> newRole
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            roleChangeSuccess = "Роль изменена на: $roleLabel"
                        )
                    }
                    // Reload user detail to reflect the change
                    loadUserDetail(userId)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Ошибка смены роли"
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearRoleChangeSuccess() {
        _uiState.update { it.copy(roleChangeSuccess = null) }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteDialog = false, isLoading = true) }

            curatorRepository.deleteUser(userId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userDeleted = true,
                            deleteMessage = "Пользователь удалён."
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Ошибка удаления"
                        )
                    }
                }
            )
        }
    }
}
