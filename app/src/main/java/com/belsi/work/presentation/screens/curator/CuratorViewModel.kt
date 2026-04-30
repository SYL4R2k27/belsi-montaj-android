package com.belsi.work.presentation.screens.curator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.LocalSite
import com.belsi.work.data.local.SiteStore
import com.belsi.work.data.local.TokenManager
import com.belsi.work.data.remote.dto.curator.*
import com.belsi.work.data.repositories.CuratorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Главный ViewModel куратора — загружает dashboard, бригадиров, фото, тикеты.
 * Также управляет локальными объектами через SiteStore (без серверной синхронизации).
 */
@HiltViewModel
class CuratorViewModel @Inject constructor(
    private val curatorRepository: CuratorRepository,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _dashboard = MutableStateFlow(CuratorDashboardDto())
    val dashboard: StateFlow<CuratorDashboardDto> = _dashboard.asStateFlow()

    private val _foremen = MutableStateFlow<List<CuratorForemanDto>>(emptyList())
    val foremen: StateFlow<List<CuratorForemanDto>> = _foremen.asStateFlow()

    // Для вкладки задач - бригадиры с полной информацией
    private val _foremenFull = MutableStateFlow<List<ForemanDto>>(emptyList())
    val foremenFull: StateFlow<List<ForemanDto>> = _foremenFull.asStateFlow()

    // Незакрепленные монтажники
    private val _unassignedInstallers = MutableStateFlow<List<UnassignedInstallerDto>>(emptyList())
    val unassignedInstallers: StateFlow<List<UnassignedInstallerDto>> = _unassignedInstallers.asStateFlow()

    // Все пользователи
    private val _allUsers = MutableStateFlow<List<AllUserDto>>(emptyList())
    val allUsers: StateFlow<List<AllUserDto>> = _allUsers.asStateFlow()

    // Детальная информация о пользователе
    private val _selectedUserDetail = MutableStateFlow<UserDetailDto?>(null)
    val selectedUserDetail: StateFlow<UserDetailDto?> = _selectedUserDetail.asStateFlow()

    private val _photos = MutableStateFlow<List<CuratorPhotoDto>>(emptyList())
    val photos: StateFlow<List<CuratorPhotoDto>> = _photos.asStateFlow()

    private val _tickets = MutableStateFlow<List<CuratorSupportTicketDto>>(emptyList())
    val tickets: StateFlow<List<CuratorSupportTicketDto>> = _tickets.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ========== Локальные объекты (SiteStore, без серверной синхронизации) ==========

    private val _localSites = MutableStateFlow<List<LocalSite>>(emptyList())
    val localSites: StateFlow<List<LocalSite>> = _localSites.asStateFlow()

    private var siteStore: SiteStore? = null

    init {
        initSiteStore()
        loadAllData()
    }

    private fun initSiteStore() {
        viewModelScope.launch {
            val phone = tokenManager.getPhone() ?: return@launch
            val prefs = context.getSharedPreferences("belsi_sites", Context.MODE_PRIVATE)
            siteStore = SiteStore(
                userPhone = phone,
                prefs = prefs,
                repository = null, // куратор не синхронизирует объекты с сервером
                keyPrefix = "curator"
            )
            launch {
                siteStore?.sites?.collect { sites ->
                    _localSites.value = sites
                }
            }
        }
    }

    fun loadAllData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Загружаем параллельно и ЖДЁМ завершения всех запросов
            kotlinx.coroutines.coroutineScope {
                launch {
                    curatorRepository.getDashboard()
                        .onSuccess { _dashboard.value = it }
                        .onFailure { _error.value = it.message }
                }
                launch {
                    curatorRepository.getForemen()
                        .onSuccess { _foremen.value = it }
                        .onFailure { _error.value = it.message }
                }
                launch {
                    curatorRepository.getPhotos()
                        .onSuccess { _photos.value = it }
                        .onFailure { _error.value = it.message }
                }
                launch {
                    curatorRepository.getSupportTickets()
                        .onSuccess { _tickets.value = it }
                        .onFailure { _error.value = it.message }
                }
                launch {
                    curatorRepository.getForemenFull()
                        .onSuccess { _foremenFull.value = it }
                        .onFailure { android.util.Log.e("CuratorViewModel", "Failed to load foremenFull: ${it.message}") }
                }
                launch {
                    curatorRepository.getUnassignedInstallers()
                        .onSuccess { _unassignedInstallers.value = it }
                        .onFailure { android.util.Log.e("CuratorViewModel", "Failed to load unassigned: ${it.message}") }
                }
                launch {
                    curatorRepository.getAllUsers()
                        .onSuccess { _allUsers.value = it }
                        .onFailure { android.util.Log.e("CuratorViewModel", "Failed to load all users: ${it.message}") }
                }
            }

            _isLoading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null

            kotlinx.coroutines.coroutineScope {
                launch {
                    curatorRepository.getDashboard()
                        .onSuccess { _dashboard.value = it }
                        .onFailure { _error.value = it.message }
                }
                launch {
                    curatorRepository.getForemen()
                        .onSuccess { _foremen.value = it }
                        .onFailure { _error.value = it.message }
                }
                launch {
                    curatorRepository.getPhotos()
                        .onSuccess { _photos.value = it }
                        .onFailure { _error.value = it.message }
                }
                launch {
                    curatorRepository.getSupportTickets()
                        .onSuccess { _tickets.value = it }
                        .onFailure { _error.value = it.message }
                }
                launch {
                    curatorRepository.getForemenFull()
                        .onSuccess { _foremenFull.value = it }
                        .onFailure { android.util.Log.e("CuratorViewModel", "Failed to refresh foremenFull: ${it.message}") }
                }
                launch {
                    curatorRepository.getUnassignedInstallers()
                        .onSuccess { _unassignedInstallers.value = it }
                        .onFailure { android.util.Log.e("CuratorViewModel", "Failed to refresh unassigned: ${it.message}") }
                }
                launch {
                    curatorRepository.getAllUsers()
                        .onSuccess { _allUsers.value = it }
                        .onFailure { android.util.Log.e("CuratorViewModel", "Failed to refresh all users: ${it.message}") }
                }
            }

            _isRefreshing.value = false
        }
    }

    fun refreshPhotos() {
        viewModelScope.launch {
            curatorRepository.getPhotos()
                .onSuccess { _photos.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun approvePhoto(photoId: String) {
        viewModelScope.launch {
            curatorRepository.approvePhoto(photoId)
                .onSuccess {
                    // Удаляем из списка и обновляем dashboard
                    _photos.value = _photos.value.filterNot { it.id == photoId }
                    curatorRepository.getDashboard().onSuccess { _dashboard.value = it }
                }
                .onFailure { _error.value = it.message ?: "Не удалось одобрить фото" }
        }
    }

    fun rejectPhoto(photoId: String, reason: String) {
        viewModelScope.launch {
            curatorRepository.rejectPhoto(photoId, reason)
                .onSuccess {
                    _photos.value = _photos.value.filterNot { it.id == photoId }
                    curatorRepository.getDashboard().onSuccess { _dashboard.value = it }
                }
                .onFailure { _error.value = it.message ?: "Не удалось отклонить фото" }
        }
    }

    // ========== SiteStore CRUD ==========

    fun addLocalSite(name: String, address: String) {
        siteStore?.addSite(name, address)
    }

    fun selectLocalSite(id: String) {
        siteStore?.selectSite(id)
    }

    fun updateLocalSite(
        id: String,
        name: String? = null,
        address: String? = null,
        measurements: Map<String, String>? = null,
        comments: String? = null,
        status: String? = null
    ) {
        siteStore?.updateSite(id, name, address, measurements, comments, status)
    }

    fun deleteLocalSite(id: String) {
        siteStore?.deleteSite(id)
    }

    fun clearError() {
        _error.value = null
    }

    fun loadUserDetail(userId: String) {
        viewModelScope.launch {
            curatorRepository.getUserDetail(userId)
                .onSuccess { _selectedUserDetail.value = it }
                .onFailure { _error.value = it.message ?: "Не удалось загрузить данные пользователя" }
        }
    }

    fun clearSelectedUser() {
        _selectedUserDetail.value = null
    }
}
