package com.belsi.work.presentation.screens.foreman.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.team.ForemanPhotoDto
import com.belsi.work.data.repositories.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ForemanPhotosViewModel @Inject constructor(
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForemanPhotosUiState())
    val uiState: StateFlow<ForemanPhotosUiState> = _uiState.asStateFlow()

    init {
        loadTeamPhotos()
    }

    fun loadTeamPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            teamRepository.getTeamPhotos()
                .onSuccess { photos ->
                    val groupedByTime = groupPhotosByTimeSlot(photos)
                    _uiState.value = _uiState.value.copy(
                        photos = photos,
                        groupedPhotos = groupedByTime,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Ошибка загрузки фото"
                    )
                }
        }
    }

    private fun groupPhotosByTimeSlot(photos: List<ForemanPhotoDto>): Map<String, List<ForemanPhotoDto>> {
        return photos.groupBy { photo ->
            try {
                val dateTime = OffsetDateTime.parse(photo.createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                val hour = dateTime.hour
                val nextHour = (hour + 1) % 24
                String.format("%02d:00-%02d:00", hour, nextHour)
            } catch (e: Exception) {
                photo.hourLabel ?: "Время неизвестно"
            }
        }.toSortedMap()
    }

    fun filterByStatus(status: PhotoFilterStatus) {
        _uiState.value = _uiState.value.copy(selectedFilter = status)
    }

    fun filterByCategory(category: PhotoCategoryFilter) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun getFilteredPhotos(): List<ForemanPhotoDto> {
        val statusFilter = _uiState.value.selectedFilter
        val categoryFilter = _uiState.value.selectedCategory
        var photos = _uiState.value.photos

        // Фильтр по статусу
        photos = when (statusFilter) {
            PhotoFilterStatus.ALL -> photos
            PhotoFilterStatus.APPROVED -> photos.filter { it.status == "approved" }
            PhotoFilterStatus.REJECTED -> photos.filter { it.status == "rejected" }
            PhotoFilterStatus.PENDING -> photos.filter { it.status in listOf("uploaded", "pending") }
        }

        // Фильтр по категории
        photos = when (categoryFilter) {
            PhotoCategoryFilter.ALL -> photos
            PhotoCategoryFilter.HOURLY -> photos.filter { it.category == "hourly" }
            PhotoCategoryFilter.PROBLEM -> photos.filter { it.category == "problem" }
            PhotoCategoryFilter.QUESTION -> photos.filter { it.category == "question" }
        }

        return photos
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun refresh() {
        loadTeamPhotos()
    }

    fun approvePhoto(photoId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)

            teamRepository.approvePhoto(photoId)
                .onSuccess {
                    val updatedPhotos = _uiState.value.photos.map { photo ->
                        if (photo.id == photoId) photo.copy(status = "approved") else photo
                    }
                    _uiState.value = _uiState.value.copy(photos = updatedPhotos, isProcessing = false)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "Ошибка одобрения фото"
                    )
                }
        }
    }

    fun rejectPhoto(photoId: String, reason: String) {
        if (reason.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Введите причину отклонения")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)

            teamRepository.rejectPhoto(photoId, reason)
                .onSuccess {
                    val updatedPhotos = _uiState.value.photos.map { photo ->
                        if (photo.id == photoId) photo.copy(status = "rejected") else photo
                    }
                    _uiState.value = _uiState.value.copy(
                        photos = updatedPhotos,
                        isProcessing = false,
                        showRejectDialog = false,
                        selectedPhotoId = null,
                        rejectReason = ""
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "Ошибка отклонения фото"
                    )
                }
        }
    }

    fun showRejectDialog(photoId: String) {
        _uiState.value = _uiState.value.copy(showRejectDialog = true, selectedPhotoId = photoId)
    }

    fun hideRejectDialog() {
        _uiState.value = _uiState.value.copy(showRejectDialog = false, selectedPhotoId = null, rejectReason = "")
    }

    fun updateRejectReason(reason: String) {
        _uiState.value = _uiState.value.copy(rejectReason = reason)
    }
}

data class ForemanPhotosUiState(
    val photos: List<ForemanPhotoDto> = emptyList(),
    val groupedPhotos: Map<String, List<ForemanPhotoDto>> = emptyMap(),
    val selectedFilter: PhotoFilterStatus = PhotoFilterStatus.ALL,
    val selectedCategory: PhotoCategoryFilter = PhotoCategoryFilter.ALL,
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val showRejectDialog: Boolean = false,
    val selectedPhotoId: String? = null,
    val rejectReason: String = ""
)

enum class PhotoFilterStatus {
    ALL,
    APPROVED,
    REJECTED,
    PENDING
}

enum class PhotoCategoryFilter {
    ALL,
    HOURLY,
    PROBLEM,
    QUESTION
}
