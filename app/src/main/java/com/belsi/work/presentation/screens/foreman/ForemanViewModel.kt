package com.belsi.work.presentation.screens.foreman

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.api.ForemanInviteResponse
import com.belsi.work.data.remote.dto.objects.CreateObjectRequest
import com.belsi.work.data.remote.dto.objects.SiteObjectDto
import com.belsi.work.data.remote.dto.team.*
import com.belsi.work.data.repositories.InviteRepository
import com.belsi.work.data.repositories.ObjectsRepository
import com.belsi.work.data.repositories.ShiftRepository
import com.belsi.work.data.repositories.TaskRepository
import com.belsi.work.data.repositories.TeamRepository
import com.belsi.work.data.models.Task
import com.belsi.work.data.models.TaskPriority
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ForemanShiftState(
    val isActive: Boolean = false,
    val shiftId: String? = null,
    val startAt: String? = null,
    val elapsedSeconds: Long = 0,
    val isEnding: Boolean = false
)

data class InvitesUiState(
    val items: List<ForemanInviteResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val cancellingId: String? = null,
    val errorMessage: String? = null
)

data class TasksUiState(
    val isCreating: Boolean = false,
    val createSuccess: Boolean = false,
    val lastBatchCreatedCount: Int = 0
)

@HiltViewModel
class ForemanViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val inviteRepository: InviteRepository,
    private val taskRepository: TaskRepository,
    private val shiftRepository: ShiftRepository,
    private val objectsRepository: ObjectsRepository
) : ViewModel() {

    // Состояние смены бригадира
    private val _foremanShift = MutableStateFlow(ForemanShiftState())
    val foremanShift: StateFlow<ForemanShiftState> = _foremanShift.asStateFlow()

    // ID активной смены бригадира
    private val _activeShiftId = MutableStateFlow<String?>(null)
    val activeShiftId: StateFlow<String?> = _activeShiftId.asStateFlow()

    private var shiftTimerJob: Job? = null

    // Команда
    private val _teamMembers = MutableStateFlow<List<ForemanTeamMemberDto>>(emptyList())
    val teamMembers: StateFlow<List<ForemanTeamMemberDto>> = _teamMembers.asStateFlow()

    // Инвайты
    private val _invitesState = MutableStateFlow(InvitesUiState())
    val invitesState: StateFlow<InvitesUiState> = _invitesState.asStateFlow()

    // Фото
    private val _teamPhotos = MutableStateFlow<List<ForemanPhotoDto>>(emptyList())
    val teamPhotos: StateFlow<List<ForemanPhotoDto>> = _teamPhotos.asStateFlow()

    // Инструменты
    private val _tools = MutableStateFlow<List<ForemanToolDto>>(emptyList())
    val tools: StateFlow<List<ForemanToolDto>> = _tools.asStateFlow()

    private val _toolsHistory = MutableStateFlow<List<ForemanToolTransactionDto>>(emptyList())
    val toolsHistory: StateFlow<List<ForemanToolTransactionDto>> = _toolsHistory.asStateFlow()

    // Задачи созданные бригадиром
    private val _createdTasks = MutableStateFlow<List<ForemanTaskDto>>(emptyList())
    val createdTasks: StateFlow<List<ForemanTaskDto>> = _createdTasks.asStateFlow()

    // Задачи назначенные бригадиру (от куратора)
    private val _myTasks = MutableStateFlow<List<Task>>(emptyList())
    val myTasks: StateFlow<List<Task>> = _myTasks.asStateFlow()

    // Задачи созданные бригадиром (как Task для совместимости с UI)
    private val _teamTasks = MutableStateFlow<List<Task>>(emptyList())
    val teamTasks: StateFlow<List<Task>> = _teamTasks.asStateFlow()

    // Состояние создания задач
    private val _tasksState = MutableStateFlow(TasksUiState())
    val tasksState: StateFlow<TasksUiState> = _tasksState.asStateFlow()

    // Общее
    private val _showInviteDialog = MutableStateFlow(false)
    val showInviteDialog: StateFlow<Boolean> = _showInviteDialog.asStateFlow()

    private val _generatedInviteCode = MutableStateFlow<String?>(null)
    val generatedInviteCode: StateFlow<String?> = _generatedInviteCode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Объекты
    private val _availableObjects = MutableStateFlow<List<SiteObjectDto>>(emptyList())
    val availableObjects: StateFlow<List<SiteObjectDto>> = _availableObjects.asStateFlow()

    private val _currentObjectName = MutableStateFlow<String?>(null)
    val currentObjectName: StateFlow<String?> = _currentObjectName.asStateFlow()

    init {
        loadAllData()
        checkOrCreateForemanShift()
        loadObjects()
    }

    private fun loadObjects() {
        viewModelScope.launch {
            objectsRepository.getObjects(status = "active")
                .onSuccess { _availableObjects.value = it }
                .onFailure { android.util.Log.e("ForemanVM", "Failed to load objects: ${it.message}") }
        }
    }

    fun changeObject(newObjectId: String) {
        viewModelScope.launch {
            objectsRepository.changeShiftObject(newObjectId)
                .onSuccess {
                    _currentObjectName.value = _availableObjects.value.firstOrNull { it.id == newObjectId }?.name
                }
                .onFailure { _error.value = it.message ?: "Ошибка смены объекта" }
        }
    }

    fun createObject(name: String, address: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            objectsRepository.createObject(CreateObjectRequest(name = name.trim(), address = address?.trim()?.ifBlank { null }))
                .onSuccess { newObj ->
                    _availableObjects.value = listOf(newObj) + _availableObjects.value
                }
                .onFailure { _error.value = it.message ?: "Ошибка создания объекта" }
        }
    }

    /**
     * Проверяет наличие активной смены у бригадира.
     * Если смены нет - автоматически создаёт её при входе в приложение.
     */
    private fun checkOrCreateForemanShift() {
        viewModelScope.launch {
            android.util.Log.d("ForemanVM", "Checking for active foreman shift...")

            shiftRepository.getActiveShift()
                .onSuccess { activeShift ->
                    if (activeShift != null) {
                        android.util.Log.d("ForemanVM", "Found active shift: ${activeShift.id}")
                        _activeShiftId.value = activeShift.id
                        _foremanShift.value = ForemanShiftState(
                            isActive = true,
                            shiftId = activeShift.id,
                            startAt = activeShift.startAt
                        )
                        startShiftTimer(activeShift.startAt)
                    } else {
                        android.util.Log.d("ForemanVM", "No active shift, creating new one...")
                        startForemanShift()
                    }
                }
                .onFailure { e ->
                    android.util.Log.e("ForemanVM", "Failed to check active shift: ${e.message}")
                    startForemanShift()
                }
        }
    }

    /**
     * Запустить новую смену бригадира
     */
    fun startForemanShift() {
        viewModelScope.launch {
            shiftRepository.startShift()
                .onSuccess { shiftData ->
                    android.util.Log.d("ForemanVM", "Foreman shift created: ${shiftData.id}")
                    _activeShiftId.value = shiftData.id
                    _foremanShift.value = ForemanShiftState(
                        isActive = true,
                        shiftId = shiftData.id,
                        startAt = shiftData.startAt
                    )
                    startShiftTimer(shiftData.startAt)
                }
                .onFailure { e ->
                    android.util.Log.e("ForemanVM", "Failed to create foreman shift: ${e.message}")
                    _error.value = "Не удалось создать смену: ${e.message}"
                }
        }
    }

    /**
     * Завершить смену бригадира
     */
    fun endForemanShift() {
        val shiftId = _activeShiftId.value ?: return
        viewModelScope.launch {
            _foremanShift.value = _foremanShift.value.copy(isEnding = true)
            shiftRepository.endShift(shiftId)
                .onSuccess {
                    android.util.Log.d("ForemanVM", "Foreman shift ended: $shiftId")
                    shiftTimerJob?.cancel()
                    _activeShiftId.value = null
                    _foremanShift.value = ForemanShiftState(isActive = false)
                }
                .onFailure { e ->
                    android.util.Log.e("ForemanVM", "Failed to end shift: ${e.message}")
                    _error.value = "Не удалось завершить смену: ${e.message}"
                    _foremanShift.value = _foremanShift.value.copy(isEnding = false)
                }
        }
    }

    /**
     * Запустить таймер обновления прошедшего времени
     */
    private fun startShiftTimer(startAtIso: String) {
        shiftTimerJob?.cancel()
        shiftTimerJob = viewModelScope.launch {
            val startEpoch = try {
                ZonedDateTime.parse(startAtIso, DateTimeFormatter.ISO_DATE_TIME)
                    .toInstant().epochSecond
            } catch (_: Exception) {
                try {
                    Instant.parse(startAtIso).epochSecond
                } catch (_: Exception) {
                    System.currentTimeMillis() / 1000
                }
            }
            while (true) {
                val now = System.currentTimeMillis() / 1000
                val elapsed = now - startEpoch
                _foremanShift.value = _foremanShift.value.copy(elapsedSeconds = elapsed)
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        shiftTimerJob?.cancel()
    }

    /**
     * Получить ID активной смены для загрузки фото
     * Если смены нет - создаёт новую и возвращает её ID
     */
    fun getOrCreateShiftId(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val currentShiftId = _activeShiftId.value
            if (currentShiftId != null) {
                onResult(currentShiftId)
                return@launch
            }

            // Попробуем получить или создать смену
            shiftRepository.getActiveShift()
                .onSuccess { activeShift ->
                    if (activeShift != null) {
                        _activeShiftId.value = activeShift.id
                        onResult(activeShift.id)
                    } else {
                        // Создаём новую смену
                        shiftRepository.startShift()
                            .onSuccess { newShift ->
                                _activeShiftId.value = newShift.id
                                onResult(newShift.id)
                            }
                            .onFailure { e ->
                                _error.value = "Не удалось создать смену: ${e.message}"
                            }
                    }
                }
                .onFailure { e ->
                    _error.value = "Ошибка получения смены: ${e.message}"
                }
        }
    }

    /**
     * Параллельная загрузка всех данных
     */
    fun loadAllData() {
        viewModelScope.launch {
            _isLoading.value = true

            // Запускаем все запросы параллельно
            val teamDeferred = async { teamRepository.getTeamMembers() }
            val photosDeferred = async { teamRepository.getTeamPhotos() }
            val toolsDeferred = async { teamRepository.getForemanTools() }
            val createdTasksDeferred = async { teamRepository.getCreatedTasks() }
            val myTasksDeferred = async { taskRepository.getMyTasks() }
            val teamTasksDeferred = async { taskRepository.getCreatedTasks() }
            val invitesDeferred = async { inviteRepository.getForemanInvites() }

            // Команда
            teamDeferred.await()
                .onSuccess { _teamMembers.value = it }
                .onFailure { android.util.Log.e("ForemanVM", "Team error: ${it.message}") }

            // Фото
            photosDeferred.await()
                .onSuccess { _teamPhotos.value = it }
                .onFailure { android.util.Log.e("ForemanVM", "Photos error: ${it.message}") }

            // Инструменты
            toolsDeferred.await()
                .onSuccess { _tools.value = it }
                .onFailure { android.util.Log.e("ForemanVM", "Tools error: ${it.message}") }

            // Задачи созданные бригадиром
            createdTasksDeferred.await()
                .onSuccess { _createdTasks.value = it }
                .onFailure { android.util.Log.e("ForemanVM", "Created tasks error: ${it.message}") }

            // Задачи назначенные бригадиру
            myTasksDeferred.await()
                .onSuccess { _myTasks.value = it }
                .onFailure { android.util.Log.e("ForemanVM", "My tasks error: ${it.message}") }

            // Задачи созданные бригадиром для команды
            teamTasksDeferred.await()
                .onSuccess { _teamTasks.value = it }
                .onFailure { android.util.Log.e("ForemanVM", "Team tasks error: ${it.message}") }

            // Инвайты
            invitesDeferred.await()
                .onSuccess {
                    _invitesState.value = _invitesState.value.copy(items = it, isLoading = false)
                }
                .onFailure {
                    _invitesState.value = _invitesState.value.copy(
                        isLoading = false,
                        errorMessage = it.message
                    )
                }

            _isLoading.value = false
        }
    }

    /**
     * Загрузить только инвайты
     */
    fun loadInvites() {
        viewModelScope.launch {
            _invitesState.value = _invitesState.value.copy(isLoading = true, errorMessage = null)

            inviteRepository.getForemanInvites()
                .onSuccess { invites ->
                    _invitesState.value = _invitesState.value.copy(
                        items = invites,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _invitesState.value = _invitesState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Не удалось загрузить инвайты"
                    )
                }
        }
    }

    fun refreshPhotos() {
        viewModelScope.launch {
            teamRepository.getTeamPhotos()
                .onSuccess { _teamPhotos.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun refreshTools() {
        viewModelScope.launch {
            teamRepository.getForemanTools()
                .onSuccess { _tools.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    /**
     * Одобрить фото
     */
    fun approvePhoto(photoId: String) {
        viewModelScope.launch {
            teamRepository.approvePhoto(photoId)
                .onSuccess {
                    // Убираем фото из списка
                    _teamPhotos.value = _teamPhotos.value.filter { it.id != photoId }
                }
                .onFailure { _error.value = it.message }
        }
    }

    /**
     * Отклонить фото
     */
    fun rejectPhoto(photoId: String, reason: String) {
        viewModelScope.launch {
            teamRepository.rejectPhoto(photoId, reason)
                .onSuccess {
                    _teamPhotos.value = _teamPhotos.value.filter { it.id != photoId }
                }
                .onFailure { _error.value = it.message }
        }
    }

    /**
     * Создать инвайт
     */
    fun createInvite() {
        if (_invitesState.value.isCreating) return

        viewModelScope.launch {
            _invitesState.value = _invitesState.value.copy(isCreating = true, errorMessage = null)

            try {
                inviteRepository.createInvite()
                    .onSuccess { newInvite ->
                        _generatedInviteCode.value = newInvite.code
                        _showInviteDialog.value = true
                        loadInvites()
                    }
                    .onFailure { e ->
                        _invitesState.value = _invitesState.value.copy(
                            errorMessage = e.message ?: "Не удалось создать инвайт"
                        )
                    }
            } finally {
                _invitesState.value = _invitesState.value.copy(isCreating = false)
            }
        }
    }

    /**
     * Отменить инвайт
     */
    fun cancelInvite(invite: ForemanInviteResponse) {
        if (_invitesState.value.cancellingId != null) return

        viewModelScope.launch {
            _invitesState.value = _invitesState.value.copy(
                cancellingId = invite.id,
                errorMessage = null
            )

            try {
                inviteRepository.cancelInvite(invite.code)
                    .onSuccess { loadInvites() }
                    .onFailure { e ->
                        _invitesState.value = _invitesState.value.copy(
                            errorMessage = e.message ?: "Не удалось отменить инвайт"
                        )
                    }
            } finally {
                _invitesState.value = _invitesState.value.copy(cancellingId = null)
            }
        }
    }

    fun dismissInviteDialog() {
        _showInviteDialog.value = false
    }

    fun clearError() {
        _error.value = null
    }

    fun clearInviteError() {
        _invitesState.value = _invitesState.value.copy(errorMessage = null)
    }

    /**
     * Обновить статус задачи
     */
    fun updateTaskStatus(taskId: String, newStatus: String) {
        viewModelScope.launch {
            taskRepository.updateTaskStatus(taskId, newStatus)
                .onSuccess { updatedTask ->
                    // Обновляем задачу в списке
                    _myTasks.value = _myTasks.value.map {
                        if (it.id == taskId) updatedTask else it
                    }
                }
                .onFailure { _error.value = it.message }
        }
    }

    /**
     * Обновить список задач
     */
    fun refreshTasks() {
        viewModelScope.launch {
            taskRepository.getMyTasks()
                .onSuccess { _myTasks.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    /**
     * Загрузить задачи созданные бригадиром для команды
     */
    fun loadTeamTasks() {
        viewModelScope.launch {
            taskRepository.getCreatedTasks()
                .onSuccess { _teamTasks.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    /**
     * Создать задачу для члена команды
     */
    fun createTask(
        title: String,
        description: String?,
        assignedTo: String,
        priority: String = TaskPriority.NORMAL
    ) {
        viewModelScope.launch {
            _tasksState.value = _tasksState.value.copy(isCreating = true)
            _error.value = null

            taskRepository.createTask(
                title = title,
                description = description,
                assignedTo = assignedTo,
                priority = priority,
                dueAt = null,
                meta = null
            )
                .onSuccess { task ->
                    android.util.Log.d("ForemanVM", "Task created: ${task.id}")
                    _teamTasks.value = listOf(task) + _teamTasks.value
                    _tasksState.value = _tasksState.value.copy(
                        isCreating = false,
                        createSuccess = true,
                        lastBatchCreatedCount = 1
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("ForemanVM", "Failed to create task", e)
                    _error.value = e.message ?: "Ошибка создания задачи"
                    _tasksState.value = _tasksState.value.copy(isCreating = false)
                }
        }
    }

    /**
     * Создать задачи для нескольких членов команды (batch)
     */
    fun createBatchTasks(
        title: String,
        description: String?,
        assignedToIds: List<String>,
        priority: String = TaskPriority.NORMAL
    ) {
        if (assignedToIds.isEmpty()) {
            _error.value = "Выберите хотя бы одного исполнителя"
            return
        }

        viewModelScope.launch {
            _tasksState.value = _tasksState.value.copy(isCreating = true)
            _error.value = null

            taskRepository.createBatchTasks(
                title = title,
                description = description,
                assignedToIds = assignedToIds,
                priority = priority,
                dueAt = null,
                meta = null
            )
                .onSuccess { result ->
                    android.util.Log.d("ForemanVM", "Batch tasks created: ${result.createdCount}")
                    _teamTasks.value = result.tasks + _teamTasks.value
                    _tasksState.value = _tasksState.value.copy(
                        isCreating = false,
                        createSuccess = true,
                        lastBatchCreatedCount = result.createdCount
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("ForemanVM", "Failed to create batch tasks", e)
                    _error.value = e.message ?: "Ошибка создания задач"
                    _tasksState.value = _tasksState.value.copy(isCreating = false)
                }
        }
    }

    /**
     * Сбросить флаг успешного создания задачи
     */
    fun resetTaskCreateSuccess() {
        _tasksState.value = _tasksState.value.copy(createSuccess = false)
    }

    /**
     * Обновить данные команды (pull-to-refresh)
     */
    fun refreshTeam() {
        android.util.Log.d("ForemanViewModel", "Refreshing team data...")
        loadTeamData()
    }

    /**
     * Загрузить данные команды (члены команды и инвайты)
     */
    private fun loadTeamData() {
        viewModelScope.launch {
            _isLoading.value = true

            // Загружаем команду и инвайты параллельно
            val teamDeferred = async { teamRepository.getTeamMembers() }
            val invitesDeferred = async { inviteRepository.getForemanInvites() }

            // Команда
            teamDeferred.await()
                .onSuccess { _teamMembers.value = it }
                .onFailure { android.util.Log.e("ForemanVM", "Team error: ${it.message}") }

            // Инвайты
            invitesDeferred.await()
                .onSuccess {
                    _invitesState.value = _invitesState.value.copy(items = it, isLoading = false)
                }
                .onFailure {
                    _invitesState.value = _invitesState.value.copy(
                        isLoading = false,
                        errorMessage = it.message
                    )
                }

            _isLoading.value = false
        }
    }

    // ========== 3.10 — Групповой чат бригады ==========

    fun getOrCreateGroupThread(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            teamRepository.getOrCreateGroupThread()
                .onSuccess { threadId -> onSuccess(threadId) }
                .onFailure { _error.value = it.message ?: "Ошибка создания группового чата" }
        }
    }
}
