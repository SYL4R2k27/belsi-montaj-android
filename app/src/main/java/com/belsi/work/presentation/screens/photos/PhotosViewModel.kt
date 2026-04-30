package com.belsi.work.presentation.screens.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.ShiftPhoto
import com.belsi.work.data.repositories.PhotoRepository
import com.belsi.work.data.repositories.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<ShiftPhoto>>(emptyList())
    val photos: StateFlow<List<ShiftPhoto>> = _photos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Сначала синхронизируем фото с сервера, если есть активная смена
            android.util.Log.d("PhotosViewModel", "Loading photos...")
            shiftRepository.getActiveShift()
                .onSuccess { activeShift ->
                    if (activeShift != null) {
                        android.util.Log.d("PhotosViewModel", "Active shift found: ${activeShift.id}, syncing photos from server...")
                        photoRepository.syncPhotosFromServer(activeShift.id)
                            .onSuccess {
                                android.util.Log.d("PhotosViewModel", "Photos synced successfully")
                            }
                            .onFailure { e ->
                                android.util.Log.e("PhotosViewModel", "Failed to sync photos from server", e)
                            }
                    } else {
                        android.util.Log.d("PhotosViewModel", "No active shift found")
                    }
                }
                .onFailure { e ->
                    android.util.Log.e("PhotosViewModel", "Failed to get active shift", e)
                }

            // Загружаем фото из локальной базы (включая синхронизированные)
            photoRepository.getAllPhotos()
                .onSuccess { photoList ->
                    android.util.Log.d("PhotosViewModel", "Loaded ${photoList.size} photos from repository")
                    _photos.value = photoList.sortedByDescending { it.createdAt }
                }
                .onFailure { e ->
                    android.util.Log.e("PhotosViewModel", "Failed to load photos", e)
                    _error.value = e.message ?: "Не удалось загрузить фотографии"
                }

            _isLoading.value = false
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            photoRepository.deletePhoto(photoId)
                .onSuccess {
                    loadPhotos()
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Не удалось удалить фото"
                }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
