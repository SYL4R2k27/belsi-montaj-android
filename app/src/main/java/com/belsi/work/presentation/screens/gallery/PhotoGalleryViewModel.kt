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

/**
 * FIX(2026-05-12) build18 P2: переписан с N+1 на bulk-endpoint.
 * Раньше: для каждой смены отдельно `getShiftPhotos(shift.id)` — до 100 запросов параллельно.
 * Теперь: один GET /shifts/photos/all → группировка по shift_id на клиенте.
 */
@HiltViewModel
class PhotoGalleryViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoGalleryUiState())
    val uiState: StateFlow<PhotoGalleryUiState> = _uiState.asStateFlow()

    init {
        loadAllPhotos()
    }

    /**
     * Загрузить все фото со всех смен одним запросом.
     */
    fun loadAllPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // 1) Параллельно тянем historyShifts (для дат и статусов) и photos (через bulk).
            val historyResult = shiftRepository.getShiftHistory(page = 1, limit = 200)
            val photosResult = shiftRepository.getAllUserPhotos(userId = null, limit = 500)

            historyResult.onSuccess { shifts ->
                photosResult.onSuccess { allPhotos ->
                    // Группируем фото по shift_id
                    val photosByShift = allPhotos.groupBy { it.shiftId }
                    // Соединяем shifts с их фото
                    val shiftsWithPhotos = shifts
                        .mapNotNull { shift ->
                            val photos = photosByShift[shift.id].orEmpty()
                            if (photos.isEmpty()) null
                            else ShiftWithPhotos(
                                shiftId = shift.id,
                                shiftDate = shift.startAt,
                                shiftStatus = shift.status,
                                photos = photos,
                            )
                        }
                        .sortedByDescending { it.shiftDate }

                    _uiState.value = _uiState.value.copy(
                        shifts = shiftsWithPhotos,
                        isLoading = false,
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Не удалось загрузить фотографии",
                    )
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Не удалось загрузить историю смен",
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
    val errorMessage: String? = null,
)
