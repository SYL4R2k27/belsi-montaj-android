package com.belsi.work.presentation.screens.curator.objects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.objects.*
import com.belsi.work.data.repositories.ObjectsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CuratorObjectsViewModel @Inject constructor(
    private val objectsRepository: ObjectsRepository
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
            _uiState.value = _uiState.value.copy(isLoadingDetail = true)
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
)
