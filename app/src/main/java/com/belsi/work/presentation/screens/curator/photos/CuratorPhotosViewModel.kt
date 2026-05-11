package com.belsi.work.presentation.screens.curator.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.curator.CuratorPhotoDto
import com.belsi.work.data.repositories.CuratorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CuratorPhotosViewModel @Inject constructor(
    private val curatorRepository: CuratorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CuratorPhotosUiState())
    val uiState: StateFlow<CuratorPhotosUiState> = _uiState.asStateFlow()

    init { loadPhotos() }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val statusParam = when (_uiState.value.selectedStatus) {
                CuratorPhotoStatusFilter.ALL -> "all"
                CuratorPhotoStatusFilter.PENDING -> null // default on backend
                CuratorPhotoStatusFilter.APPROVED -> "approved"
                CuratorPhotoStatusFilter.REJECTED -> "rejected"
            }
            curatorRepository.getPhotos(status = statusParam)
                .onSuccess { photos ->
                    // Сортировка: проблемные фото (низкий AI score) первыми
                    val sorted = photos.sortedBy { it.aiScore ?: 100 }
                    _uiState.value = _uiState.value.copy(photos = sorted, isLoading = false)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message ?: "Ошибка загрузки") }
        }
    }

    fun approvePhoto(photoId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            curatorRepository.approvePhoto(photoId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        photos = _uiState.value.photos.filterNot { it.id == photoId }, isProcessing = false)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isProcessing = false, errorMessage = it.message) }
        }
    }

    fun rejectPhoto(photoId: String, reason: String) {
        if (reason.isBlank()) { _uiState.value = _uiState.value.copy(errorMessage = "Введите причину"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            curatorRepository.rejectPhoto(photoId, reason)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        photos = _uiState.value.photos.filterNot { it.id == photoId },
                        isProcessing = false, showRejectDialog = false, selectedPhotoId = null, rejectReason = "")
                }
                .onFailure { _uiState.value = _uiState.value.copy(isProcessing = false, errorMessage = it.message) }
        }
    }

    // ========== Batch selection mode ==========

    fun enterSelectionMode(photoId: String) {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = true,
            selectedPhotoIds = setOf(photoId)
        )
    }

    fun exitSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = false,
            selectedPhotoIds = emptySet()
        )
    }

    fun togglePhotoSelection(photoId: String) {
        val current = _uiState.value.selectedPhotoIds.toMutableSet()
        if (current.contains(photoId)) {
            current.remove(photoId)
            if (current.isEmpty()) {
                exitSelectionMode()
                return
            }
        } else {
            current.add(photoId)
        }
        _uiState.value = _uiState.value.copy(selectedPhotoIds = current)
    }

    fun selectAll() {
        val allIds = getFilteredPhotos()
            .filter { it.status == "pending" || it.status == null }
            .map { it.id }
            .toSet()
        _uiState.value = _uiState.value.copy(selectedPhotoIds = allIds)
    }

    fun batchApprove() {
        val ids = _uiState.value.selectedPhotoIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            curatorRepository.batchReviewPhotos(ids, "approve")
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        photos = _uiState.value.photos.filterNot { it.id in ids },
                        isProcessing = false,
                        isSelectionMode = false,
                        selectedPhotoIds = emptySet()
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = it.message ?: "Ошибка массового одобрения"
                    )
                }
        }
    }

    fun batchReject(reason: String) {
        if (reason.isBlank()) { _uiState.value = _uiState.value.copy(errorMessage = "Введите причину"); return }
        val ids = _uiState.value.selectedPhotoIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            curatorRepository.batchReviewPhotos(ids, "reject", reason)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        photos = _uiState.value.photos.filterNot { it.id in ids },
                        isProcessing = false,
                        isSelectionMode = false,
                        selectedPhotoIds = emptySet(),
                        showRejectDialog = false,
                        rejectReason = ""
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = it.message ?: "Ошибка массового отклонения"
                    )
                }
        }
    }

    // ========== Filters ==========

    fun filterByCategory(category: CuratorPhotoCategoryFilter) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun filterByStatus(status: CuratorPhotoStatusFilter) {
        _uiState.value = _uiState.value.copy(selectedStatus = status, isSelectionMode = false, selectedPhotoIds = emptySet())
        loadPhotos()
    }

    fun getFilteredPhotos(): List<CuratorPhotoDto> {
        val categoryFilter = _uiState.value.selectedCategory
        return when (categoryFilter) {
            CuratorPhotoCategoryFilter.ALL -> _uiState.value.photos
            CuratorPhotoCategoryFilter.HOURLY -> _uiState.value.photos.filter { it.category == "hourly" }
            CuratorPhotoCategoryFilter.PROBLEM -> _uiState.value.photos.filter { it.category == "problem" }
            CuratorPhotoCategoryFilter.QUESTION -> _uiState.value.photos.filter { it.category == "question" }
            CuratorPhotoCategoryFilter.AI_PROBLEM -> _uiState.value.photos.filter { (it.aiScore ?: 100) < 60 }
        }
    }

    fun showRejectDialog(photoId: String) { _uiState.value = _uiState.value.copy(showRejectDialog = true, selectedPhotoId = photoId) }
    fun showBatchRejectDialog() { _uiState.value = _uiState.value.copy(showRejectDialog = true, selectedPhotoId = null) }
    fun hideRejectDialog() { _uiState.value = _uiState.value.copy(showRejectDialog = false, selectedPhotoId = null, rejectReason = "") }
    fun updateRejectReason(reason: String) { _uiState.value = _uiState.value.copy(rejectReason = reason) }
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun refresh() { loadPhotos() }
}

data class CuratorPhotosUiState(
    val photos: List<CuratorPhotoDto> = emptyList(),
    val selectedCategory: CuratorPhotoCategoryFilter = CuratorPhotoCategoryFilter.ALL,
    val selectedStatus: CuratorPhotoStatusFilter = CuratorPhotoStatusFilter.PENDING,
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val showRejectDialog: Boolean = false,
    val selectedPhotoId: String? = null,
    val rejectReason: String = "",
    // Batch selection
    val isSelectionMode: Boolean = false,
    val selectedPhotoIds: Set<String> = emptySet()
)

enum class CuratorPhotoCategoryFilter {
    ALL, HOURLY, PROBLEM, QUESTION, AI_PROBLEM
}

enum class CuratorPhotoStatusFilter {
    ALL, PENDING, APPROVED, REJECTED
}
