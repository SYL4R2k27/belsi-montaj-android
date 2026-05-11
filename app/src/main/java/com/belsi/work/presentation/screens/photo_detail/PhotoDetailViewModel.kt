package com.belsi.work.presentation.screens.photo_detail

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

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PhotoDetailUiState>(PhotoDetailUiState.Loading)
    val uiState: StateFlow<PhotoDetailUiState> = _uiState.asStateFlow()

    /**
     * Загрузить данные фото по URL (который передается как photoId)
     * Поскольку photoUrl передается из ShiftScreen, мы используем его напрямую
     */
    fun loadPhoto(photoUrl: String) {
        viewModelScope.launch {
            _uiState.value = PhotoDetailUiState.Loading

            // Декодируем URL если он был закодирован
            val decodedUrl = try {
                java.net.URLDecoder.decode(photoUrl, "UTF-8")
            } catch (e: Exception) {
                photoUrl
            }

            // Поскольку у нас нет отдельного API для получения деталей фото по URL,
            // создаем простую модель с переданным URL
            _uiState.value = PhotoDetailUiState.Success(
                PhotoDetailData(
                    photoUrl = decodedUrl,
                    status = "uploaded",
                    hourLabel = null,
                    comment = null,
                    createdAt = null
                )
            )
        }
    }
}

sealed class PhotoDetailUiState {
    object Loading : PhotoDetailUiState()
    data class Success(val photo: PhotoDetailData) : PhotoDetailUiState()
    data class Error(val message: String) : PhotoDetailUiState()
}

data class PhotoDetailData(
    val photoUrl: String,
    val status: String,
    val hourLabel: String?,
    val comment: String?,
    val createdAt: String?
)
