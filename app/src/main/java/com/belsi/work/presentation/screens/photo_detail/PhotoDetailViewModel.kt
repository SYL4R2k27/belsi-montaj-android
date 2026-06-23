package com.belsi.work.presentation.screens.photo_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.api.PhotoApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * FIX(2026-05-12) build17 P0: переписан со stub'а на реальный API.
 * Раньше: брал переданный photoId/URL и заполнял заглушку без вызова сети.
 * Теперь: вызывает GET /photos/{photoId} через PhotoApi.
 *
 * Поддерживает два режима:
 *  - photoId — UUID фото (новый, рекомендованный)
 *  - photoUrl — прямой URL (legacy, если ID нет — показываем URL без метаданных)
 */
@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    private val photoApi: PhotoApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PhotoDetailUiState>(PhotoDetailUiState.Loading)
    val uiState: StateFlow<PhotoDetailUiState> = _uiState.asStateFlow()

    /**
     * Загрузить фото. photoIdOrUrl может быть либо UUID, либо URL (legacy путь).
     */
    fun loadPhoto(photoIdOrUrl: String) {
        viewModelScope.launch {
            _uiState.value = PhotoDetailUiState.Loading

            val decoded = try {
                java.net.URLDecoder.decode(photoIdOrUrl, "UTF-8")
            } catch (e: Exception) {
                photoIdOrUrl
            }

            // Попробуем как UUID — если получится, тянем через API.
            val asUuid = runCatching { UUID.fromString(decoded) }.getOrNull()
            if (asUuid != null) {
                try {
                    val response = photoApi.getPhoto(asUuid)
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            _uiState.value = PhotoDetailUiState.Success(
                                PhotoDetailData(
                                    photoUrl = body.photoUrl,
                                    status = body.status.lowercase(),
                                    hourLabel = body.hourLabel?.ifBlank { null },
                                    comment = body.comment,
                                    createdAt = body.createdAt,
                                    photoId = body.id,
                                )
                            )
                            return@launch
                        }
                    }
                    _uiState.value = PhotoDetailUiState.Error(
                        "HTTP ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
                    )
                } catch (e: Exception) {
                    _uiState.value = PhotoDetailUiState.Error(e.message ?: "Ошибка загрузки фото")
                }
            } else {
                // Legacy: передан URL напрямую, рисуем без метаданных.
                _uiState.value = PhotoDetailUiState.Success(
                    PhotoDetailData(
                        photoUrl = decoded,
                        status = "uploaded",
                        hourLabel = null,
                        comment = null,
                        createdAt = null,
                        photoId = null,
                    )
                )
            }
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
    val createdAt: String?,
    val photoId: String? = null,
)
