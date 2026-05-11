package com.belsi.work.presentation.screens.coordinator

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.LocalSite
import com.belsi.work.data.local.SiteStore
import com.belsi.work.data.local.TokenManager
import com.belsi.work.data.remote.api.ChatApi
import com.belsi.work.data.remote.dto.coordinator.*
import com.belsi.work.data.repositories.CoordinatorRepository
import com.belsi.work.data.repositories.TaskRepository
import com.belsi.work.data.models.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CoordinatorViewModel @Inject constructor(
    private val repository: CoordinatorRepository,
    private val taskRepository: TaskRepository,
    private val chatApi: ChatApi,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _dashboard = MutableStateFlow(CoordinatorDashboardDto())
    val dashboard: StateFlow<CoordinatorDashboardDto> = _dashboard.asStateFlow()

    private val _photos = MutableStateFlow<List<CoordinatorPhotoDto>>(emptyList())
    val photos: StateFlow<List<CoordinatorPhotoDto>> = _photos.asStateFlow()

    private val _tasks = MutableStateFlow<List<CoordinatorTaskDto>>(emptyList())
    val tasks: StateFlow<List<CoordinatorTaskDto>> = _tasks.asStateFlow()

    private val _myTasks = MutableStateFlow<List<Task>>(emptyList())
    val myTasks: StateFlow<List<Task>> = _myTasks.asStateFlow()

    private val _team = MutableStateFlow<List<CoordinatorTeamMemberDto>>(emptyList())
    val team: StateFlow<List<CoordinatorTeamMemberDto>> = _team.asStateFlow()

    private val _reports = MutableStateFlow<List<CoordinatorReportDto>>(emptyList())
    val reports: StateFlow<List<CoordinatorReportDto>> = _reports.asStateFlow()

    private val _site = MutableStateFlow<CoordinatorSiteDto?>(null)
    val site: StateFlow<CoordinatorSiteDto?> = _site.asStateFlow()

    private val _localSites = MutableStateFlow<List<LocalSite>>(emptyList())
    val localSites: StateFlow<List<LocalSite>> = _localSites.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var siteStore: SiteStore? = null

    /** Флаг: идёт авто-обновление (не показываем полный спиннер) */
    private val _isAutoRefreshing = MutableStateFlow(false)
    val isAutoRefreshing: StateFlow<Boolean> = _isAutoRefreshing.asStateFlow()

    init {
        initSiteStore()
        loadAll()
        // Авто-обновление дашборда каждые 30 секунд
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                _isAutoRefreshing.value = true
                try {
                    loadDashboard()
                    loadPhotos()
                    loadTeam()
                } catch (_: Exception) { /* ignore silent refresh errors */ }
                _isAutoRefreshing.value = false
            }
        }
    }

    private fun initSiteStore() {
        viewModelScope.launch {
            val phone = tokenManager.getPhone() ?: return@launch
            val prefs = context.getSharedPreferences("belsi_sites", Context.MODE_PRIVATE)
            siteStore = SiteStore(phone, prefs, repository)
            // Observe site store changes
            launch {
                siteStore?.sites?.collect { sites ->
                    _localSites.value = sites
                }
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Load all data in parallel
            launch { loadDashboard() }
            launch { loadPhotos() }
            launch { loadTasks() }
            launch { loadMyTasks() }
            launch { loadTeam() }
            launch { loadReports() }
            launch { loadSite() }

            _isLoading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadAll()
            _isRefreshing.value = false
        }
    }

    fun refreshPhotos() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadPhotos()
            _isRefreshing.value = false
        }
    }

    fun approvePhoto(photoId: String) {
        viewModelScope.launch {
            repository.approvePhoto(photoId).onSuccess {
                loadPhotos()
                loadDashboard()
            }.onFailure {
                _error.value = it.message
            }
        }
    }

    fun rejectPhoto(photoId: String, comment: String? = null) {
        viewModelScope.launch {
            repository.rejectPhoto(photoId, comment).onSuccess {
                loadPhotos()
                loadDashboard()
            }.onFailure {
                _error.value = it.message
            }
        }
    }

    fun createReport(content: String, photoUrls: List<String> = emptyList()) {
        viewModelScope.launch {
            val request = CreateReportRequest(content = content, photoUrls = photoUrls)
            repository.createReport(request).onSuccess {
                loadReports()
                loadDashboard()
            }.onFailure {
                _error.value = it.message
            }
        }
    }

    /**
     * Создание отчёта с загрузкой фото из URI.
     * 1. Конвертирует URI → File
     * 2. Загружает каждое фото через ChatApi.uploadChatPhoto()
     * 3. Создаёт отчёт с полученными URL
     */
    fun createReportWithPhotos(content: String, photoUris: List<Uri>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uploadedUrls = mutableListOf<String>()
                for (uri in photoUris) {
                    try {
                        val file = uriToFile(uri)
                        if (file != null) {
                            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                            val photoPart = MultipartBody.Part.createFormData("photo", file.name, requestFile)
                            val response = chatApi.uploadChatPhoto(photoPart)
                            if (response.isSuccessful && response.body() != null) {
                                uploadedUrls.add(response.body()!!.photoUrl)
                            }
                            file.delete() // Удаляем временный файл
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CoordinatorVM", "Ошибка загрузки фото: ${e.message}", e)
                    }
                }

                val request = CreateReportRequest(content = content, photoUrls = uploadedUrls)
                repository.createReport(request).onSuccess {
                    loadReports()
                    loadDashboard()
                }.onFailure {
                    _error.value = it.message
                }
            } catch (e: Exception) {
                _error.value = "Ошибка создания отчёта: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Конвертирует content URI в временный файл
     */
    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("report_photo_", ".jpg", context.cacheDir)
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            android.util.Log.e("CoordinatorVM", "uriToFile error: ${e.message}", e)
            null
        }
    }

    /**
     * Создание задачи с мультивыбором исполнителей.
     * Для каждого исполнителя отправляется отдельный POST (как в iOS).
     * Priority "medium" маппится в "normal" (бэкенд не принимает medium).
     */
    fun createTask(
        title: String,
        description: String?,
        assignedToIds: List<String>,
        priority: String = "normal"
    ) {
        viewModelScope.launch {
            val backendPriority = if (priority == "medium") "normal" else priority
            var successCount = 0
            var lastError: String? = null

            for (userId in assignedToIds) {
                val request = CreateCoordinatorTaskRequest(
                    title = title,
                    description = description,
                    assignedTo = userId,
                    priority = backendPriority
                )
                repository.createTask(request)
                    .onSuccess { successCount++ }
                    .onFailure { lastError = it.message }
            }

            if (successCount > 0) {
                loadTasks()
                loadDashboard()
            }
            if (lastError != null && successCount < assignedToIds.size) {
                _error.value = "Создано $successCount из ${assignedToIds.size} задач"
            }
        }
    }

    /** Обратная совместимость — один исполнитель */
    fun createTask(title: String, description: String?, assignedTo: String, priority: String = "normal") {
        createTask(title, description, listOf(assignedTo), priority)
    }

    fun updateSite(measurements: Map<String, String>? = null, comments: String? = null, status: String? = null) {
        viewModelScope.launch {
            val request = UpdateSiteRequest(measurements = measurements, comments = comments, status = status)
            repository.updateSite(request).onSuccess {
                loadSite()
                loadDashboard()
                // Также обновить активный локальный объект
                val active = siteStore?.activeSite
                if (active != null) {
                    siteStore?.updateSite(active.id, measurements = measurements, comments = comments, status = status)
                }
            }.onFailure {
                _error.value = it.message
            }
        }
    }

    // ========== SiteStore methods ==========

    fun addLocalSite(name: String, address: String) {
        val site = siteStore?.addSite(name, address)
        if (site?.isActive == true) {
            viewModelScope.launch {
                siteStore?.syncActiveToServer()
            }
        }
    }

    fun selectLocalSite(id: String) {
        siteStore?.selectSite(id)
        viewModelScope.launch {
            siteStore?.syncActiveToServer()
        }
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
        val isActive = siteStore?.activeSite?.id == id
        if (isActive) {
            viewModelScope.launch {
                siteStore?.syncActiveToServer()
            }
        }
    }

    fun deleteLocalSite(id: String) {
        siteStore?.deleteSite(id)
    }

    fun syncSitesFromServer() {
        viewModelScope.launch {
            siteStore?.syncFromServer()
        }
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun loadDashboard() {
        repository.getDashboard().onSuccess {
            _dashboard.value = it
        }.onFailure {
            _error.value = it.message
        }
    }

    private suspend fun loadPhotos() {
        repository.getPhotos().onSuccess {
            _photos.value = it
        }
    }

    private suspend fun loadTasks() {
        repository.getTasks().onSuccess {
            _tasks.value = it
        }
    }

    private suspend fun loadMyTasks() {
        taskRepository.getMyTasks().onSuccess {
            _myTasks.value = it
        }
    }

    private suspend fun loadTeam() {
        repository.getTeam().onSuccess {
            _team.value = it
        }
    }

    private suspend fun loadReports() {
        repository.getReports().onSuccess {
            _reports.value = it
        }
    }

    private suspend fun loadSite() {
        repository.getSite().onSuccess {
            _site.value = it
        }
    }
}
