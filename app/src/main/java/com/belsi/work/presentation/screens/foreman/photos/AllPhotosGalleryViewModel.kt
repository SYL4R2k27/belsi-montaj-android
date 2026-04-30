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
import javax.inject.Inject

/**
 * ViewModel для галереи всех фотографий команды бригадира
 * Использует ForemanPhotoDto из реальных серверных ответов
 */
@HiltViewModel
class AllPhotosGalleryViewModel @Inject constructor(
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<ForemanPhotoDto>>(emptyList())
    val photos: StateFlow<List<ForemanPhotoDto>> = _photos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            teamRepository.getTeamPhotos()
                .onSuccess { photosList ->
                    android.util.Log.d("AllPhotosGallery", "Loaded ${photosList.size} photos")
                    _photos.value = photosList.sortedByDescending { it.createdAt }
                }
                .onFailure { error ->
                    android.util.Log.e("AllPhotosGallery", "Failed to load photos", error)
                    _errorMessage.value = error.message ?: "Ошибка загрузки фотографий"
                }

            _isLoading.value = false
        }
    }

    /**
     * Группировка фотографий по hourLabel
     */
    fun getPhotosByHour(): Map<String, List<ForemanPhotoDto>> {
        return _photos.value
            .filter { !it.hourLabel.isNullOrEmpty() }
            .groupBy { it.hourLabel ?: "Без времени" }
            .toSortedMap(compareBy { extractHourNumber(it) })
    }

    private fun extractHourNumber(hourLabel: String): Int {
        return hourLabel.filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    fun retryLoading() {
        loadPhotos()
    }
}
