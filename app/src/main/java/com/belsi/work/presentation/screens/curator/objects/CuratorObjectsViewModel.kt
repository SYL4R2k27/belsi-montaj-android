package com.belsi.work.presentation.screens.curator.objects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.Batch
import com.belsi.work.data.remote.api.BatchApi
import com.belsi.work.data.remote.dto.brand.TimelineEventDto
import com.belsi.work.data.remote.dto.objects.*
import com.belsi.work.data.repositories.BrandCoreRepository
import com.belsi.work.data.repositories.ObjectsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CuratorObjectsViewModel @Inject constructor(
    private val objectsRepository: ObjectsRepository,
    private val brandRepo: BrandCoreRepository,
    private val batchApi: BatchApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CuratorObjectsUiState())
    val uiState: StateFlow<CuratorObjectsUiState> = _uiState.asStateFlow()

    init {
        loadObjects()
    }

    fun loadObjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            objectsRepository.getCuratorObjects()
                .onSuccess { objects ->
                    _uiState.value = _uiState.value.copy(
                        objects = objects,
                        isLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "Ошибка загрузки"
                    )
                }
        }
    }

    fun loadObjectDetail(objectId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingDetail = true,
                timeline = emptyList(),
                timelineError = null,
            )
            objectsRepository.getCuratorObjectDetail(objectId)
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        selectedDetail = detail,
                        isLoadingDetail = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoadingDetail = false,
                        errorMessage = it.message ?: "Ошибка загрузки деталей"
                    )
                }
            // FIX(2026-05-11) BELSI 2.0.0 build7: история объекта прямо в Инфо-табе.
            // Параллельно подгружаем timeline из brand_core /objects/{id}/timeline
            // (UNION ALL по shifts/photos/audit/deliveries/route_points/batches/tasks/tickets).
            _uiState.value = _uiState.value.copy(isLoadingTimeline = true)
            brandRepo.objectTimeline(objectId, limit = 80)
                .onSuccess { events ->
                    _uiState.value = _uiState.value.copy(
                        timeline = events,
                        isLoadingTimeline = false,
                        timelineError = null,
                    )
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingTimeline = false,
                        timelineError = err.message ?: "История временно недоступна",
                    )
                }

            // FIX(2026-05-12) build18 P2: подгружаем партии объекта (для нового таба Партии).
            _uiState.value = _uiState.value.copy(isLoadingBatches = true)
            try {
                val resp = batchApi.listBatches(targetObjectId = objectId, limit = 100)
                if (resp.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        batches = resp.body() ?: emptyList(),
                        isLoadingBatches = false,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoadingBatches = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingBatches = false)
            }
        }
    }

    fun createObject(name: String, address: String?, description: String?) {
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Введите название объекта")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            objectsRepository.createCuratorObject(
                CreateObjectRequest(
                    name = name.trim(),
                    address = address?.trim()?.ifBlank { null },
                    description = description?.trim()?.ifBlank { null }
                )
            )
                .onSuccess { newObj ->
                    _uiState.value = _uiState.value.copy(
                        objects = listOf(newObj) + _uiState.value.objects,
                        isProcessing = false,
                        showCreateDialog = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = it.message ?: "Ошибка создания"
                    )
                }
        }
    }

    fun archiveObject(objectId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            objectsRepository.archiveCuratorObject(objectId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        objects = _uiState.value.objects.filterNot { it.id == objectId },
                        isProcessing = false,
                        selectedDetail = null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = it.message
                    )
                }
        }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun clearDetail() {
        _uiState.value = _uiState.value.copy(selectedDetail = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun refresh() {
        loadObjects()
    }
}

data class CuratorObjectsUiState(
    val objects: List<SiteObjectDto> = emptyList(),
    val selectedDetail: SiteObjectDetailDto? = null,
    val isLoading: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val showCreateDialog: Boolean = false,
    // FIX(2026-05-11) build7: история объекта в InfoTab
    val timeline: List<TimelineEventDto> = emptyList(),
    val isLoadingTimeline: Boolean = false,
    val timelineError: String? = null,
    // FIX(2026-05-12) build18 P2: партии объекта для нового таба «Партии».
    val batches: List<Batch> = emptyList(),
    val isLoadingBatches: Boolean = false,
)
