package com.belsi.work.presentation.screens.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.ShiftPhotoData
import com.belsi.work.data.repositories.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShiftWithPhotos(
    val shiftId: String,
    val shiftDate: String,
    val shiftStatus: String,
    val photos: List<ShiftPhotoData>
)

@HiltViewModel
class PhotoGalleryViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoGalleryUiState())
    val uiState: StateFlow<PhotoGalleryUiState> = _uiState.asStateFlow()

    init {
        loadAllPhotos()
    }

    /**
     * Загрузить все фото со всех смен
     */
    fun loadAllPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Загружаем историю смен
                shiftRepository.getShiftHistory(page = 1, limit = 100)
                    .onSuccess { shifts ->
                        // Для каждой смены загружаем фото
                        val shiftsWithPhotos = mutableListOf<ShiftWithPhotos>()

                        shifts.forEach { shift ->
                            try {
                                shiftRepository.getShiftPhotos(shift.id)
                                    .onSuccess { photos ->
                                        if (photos.isNotEmpty()) {
                                            shiftsWithPhotos.add(
                                                ShiftWithPhotos(
                                                    shiftId = shift.id,
                                                    shiftDate = shift.startAt,
                                                    shiftStatus = shift.status,
                                                    photos = photos
                                                )
                                            )
                                        }
                                    }
                            } catch (e: Exception) {
                                android.util.Log.e("PhotoGalleryViewModel", "Error loading photos for shift ${shift.id}", e)
                            }
                        }

                        // Сортируем по дате (новые сверху)
                        val sorted = shiftsWithPhotos.sortedByDescending { it.shiftDate }

                        _uiState.value = _uiState.value.copy(
                            shifts = sorted,
                            isLoading = false
                        )
                    }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Ошибка загрузки фотографий"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Не удалось загрузить фотографии: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class PhotoGalleryUiState(
    val shifts: List<ShiftWithPhotos> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
