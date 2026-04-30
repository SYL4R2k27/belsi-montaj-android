package com.belsi.work.presentation.screens.shift

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.database.dao.PhotoDao
import com.belsi.work.data.local.database.dao.ShiftDao
import com.belsi.work.data.remote.dto.objects.SiteObjectDto
import com.belsi.work.data.repositories.ObjectsRepository
import com.belsi.work.data.repositories.PauseRepository
import com.belsi.work.data.repositories.ShiftData
import com.belsi.work.data.repositories.ShiftRepository
import com.belsi.work.presentation.widget.ShiftWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import java.util.Calendar

@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val application: Application,
    private val shiftRepository: ShiftRepository,
    private val objectsRepository: ObjectsRepository,
    private val pauseRepository: PauseRepository,
    private val shiftDao: ShiftDao,
    private val photoDao: PhotoDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShiftUiState>(ShiftUiState.NoShift)
    val uiState: StateFlow<ShiftUiState> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _photoSlots = MutableStateFlow<List<PhotoSlot>>(emptyList())
    val photoSlots: StateFlow<List<PhotoSlot>> = _photoSlots.asStateFlow()

    /** Количество фото в очереди на загрузку (для бэйджа) */
    val pendingPhotoCount: StateFlow<Int> = photoDao.observePendingPhotoCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var timerJob: Job? = null
    private var pauseTimerJob: Job? = null
    private var idleTimerJob: Job? = null

    /** Блокировка одновременного нажатия паузы и простоя */
    private var isActionInProgress = false
    /** Дедупликация быстрых нажатий pause/resume (2 секунды) */
    private var lastActionTimestamp = 0L

    private var currentShiftId: String? = null

    // Объекты
    private val _availableObjects = MutableStateFlow<List<SiteObjectDto>>(emptyList())
    val availableObjects: StateFlow<List<SiteObjectDto>> = _availableObjects.asStateFlow()

    private val _selectedObjectId = MutableStateFlow<String?>(null)
    val selectedObjectId: StateFlow<String?> = _selectedObjectId.asStateFlow()

    private val _currentObjectName = MutableStateFlow<String?>(null)
    val currentObjectName: StateFlow<String?> = _currentObjectName.asStateFlow()

    init {
        initializePhotoSlots()
        checkActiveShift()
        loadObjects()
    }

    private fun loadObjects() {
        viewModelScope.launch {
            objectsRepository.getObjects(status = "active")
                .onSuccess { _availableObjects.value = it }
                .onFailure { android.util.Log.e("ShiftViewModel", "Failed to load objects: ${it.message}") }
        }
    }

    fun selectObject(objectId: String?) {
        _selectedObjectId.value = objectId
        _currentObjectName.value = _availableObjects.value.firstOrNull { it.id == objectId }?.name
    }

    fun changeObject(newObjectId: String) {
        viewModelScope.launch {
            objectsRepository.changeShiftObject(newObjectId)
                .onSuccess {
                    _selectedObjectId.value = newObjectId
                    _currentObjectName.value = _availableObjects.value.firstOrNull { it.id == newObjectId }?.name
                }
                .onFailure { _error.value = it.message ?: "Ошибка смены объекта" }
        }
    }

    /**
     * Публичный метод для принудительной проверки активной смены
     * Используется после создания смены в CameraViewModel
     */
    fun checkActiveShiftPublic() {
        checkActiveShift()
    }

    /**
     * Проверка активной смены при запуске приложения
     * Если смена была начата и приложение было закрыто, восстанавливаем состояние
     * ВКЛЮЧАЯ состояние паузы/простоя с сервера
     */
    private fun checkActiveShift() {
        viewModelScope.launch {
            android.util.Log.d("ShiftViewModel", "Checking for active shift...")
            _uiState.value = ShiftUiState.Loading

            shiftRepository.getActiveShift()
                .onSuccess { activeShift ->
                    if (activeShift != null) {
                        android.util.Log.d("ShiftViewModel", "Found active shift: ${activeShift.id}, started at: ${activeShift.startAt}")

                        currentShiftId = activeShift.id

                        // Парсим время начала смены
                        val startTimeMillis = try {
                            java.time.OffsetDateTime.parse(activeShift.startAt)
                                .toInstant()
                                .toEpochMilli()
                        } catch (e: Exception) {
                            android.util.Log.e("ShiftViewModel", "Failed to parse start time", e)
                            System.currentTimeMillis()
                        }

                        val elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000

                        // Базовое состояние смены
                        var state = ShiftUiState.Active(
                            shiftId = activeShift.id,
                            startTime = startTimeMillis,
                            elapsedSeconds = elapsedSeconds
                        )

                        // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: проверяем текущую паузу/простой на сервере
                        state = restorePauseState(state)

                        _uiState.value = state

                        // Инициализируем слоты фото
                        initializePhotoSlotsFromStartTime(startTimeMillis)
                        startTimer()

                        // Запускаем таймер паузы/простоя если нужно
                        if (state.isPaused) {
                            startPauseTimer()
                        }
                        if (state.isIdle) {
                            startIdleTimer()
                        }

                        // Загружаем существующие фото
                        loadShiftPhotos(activeShift.id)
                    } else {
                        android.util.Log.d("ShiftViewModel", "No active shift found")
                        _uiState.value = ShiftUiState.NoShift
                    }
                }
                .onFailure { e ->
                    android.util.Log.e("ShiftViewModel", "Failed to check active shift", e)
                    // Офлайн-фоллбэк: пробуем восстановить из Room
                    restoreFromRoom()
                }
        }
    }

    /**
     * Офлайн-восстановление: если сервер недоступен, восстанавливаем состояние смены из Room.
     * Таймер продолжает считать с сохранёнными значениями.
     */
    private suspend fun restoreFromRoom() {
        try {
            val localShift = shiftDao.getActiveShift()
            if (localShift != null && localShift.status == "active") {
                android.util.Log.d("ShiftViewModel", "Restoring shift from Room: ${localShift.id}")

                currentShiftId = localShift.id

                val startTimeMillis = if (localShift.startTimeMillis > 0) {
                    localShift.startTimeMillis
                } else {
                    try {
                        java.time.OffsetDateTime.parse(localShift.startAt)
                            .toInstant()
                            .toEpochMilli()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                }

                val now = System.currentTimeMillis()
                val elapsedSeconds = if (localShift.elapsedSeconds > 0) {
                    // Используем сохранённое значение + время с последнего сохранения
                    val timeSinceLastSave = (now - localShift.lastSyncAt) / 1000
                    localShift.elapsedSeconds + timeSinceLastSave
                } else {
                    (now - startTimeMillis) / 1000
                }

                val state = ShiftUiState.Active(
                    shiftId = localShift.id,
                    startTime = startTimeMillis,
                    elapsedSeconds = elapsedSeconds,
                    isPaused = localShift.isPaused,
                    isIdle = localShift.isIdle,
                    pauseSeconds = localShift.pauseSeconds,
                    idleSeconds = localShift.idleSeconds,
                    totalPauseSeconds = localShift.totalPauseSeconds,
                    totalIdleSeconds = localShift.totalIdleSeconds,
                    pauseStartTime = localShift.pauseStartTime,
                    idleStartTime = localShift.idleStartTime,
                    idleReason = localShift.idleReason
                )

                _uiState.value = state
                initializePhotoSlotsFromStartTime(startTimeMillis)
                startTimer()

                if (state.isPaused) startPauseTimer()
                if (state.isIdle) startIdleTimer()

                android.util.Log.d("ShiftViewModel", "Shift restored from Room: elapsed=${elapsedSeconds}s")
            } else {
                android.util.Log.d("ShiftViewModel", "No active shift in Room")
                _error.value = null
                _uiState.value = ShiftUiState.NoShift
                stopTimer()
            }
        } catch (e: Exception) {
            android.util.Log.e("ShiftViewModel", "Failed to restore from Room", e)
            _error.value = null
            _uiState.value = ShiftUiState.NoShift
            stopTimer()
        }
    }

    /**
     * Восстановление состояния паузы/простоя с сервера
     * Вызывается при checkActiveShift, чтобы после перезапуска приложения
     * пауза/простой продолжали идти
     */
    private suspend fun restorePauseState(state: ShiftUiState.Active): ShiftUiState.Active {
        return try {
            val result = pauseRepository.getCurrentPause()
            result.getOrNull()?.let { currentPause ->
                if (currentPause.onPause && currentPause.pause != null) {
                    val pause = currentPause.pause
                    val pauseStartMillis = try {
                        java.time.OffsetDateTime.parse(pause.startedAt)
                            .toInstant()
                            .toEpochMilli()
                    } catch (e: Exception) {
                        android.util.Log.e("ShiftViewModel", "Failed to parse pause start time", e)
                        System.currentTimeMillis()
                    }

                    val pauseElapsed = (System.currentTimeMillis() - pauseStartMillis) / 1000

                    // Определяем: пауза или простой
                    // Если reason не пустой — это простой (idle)
                    val isIdle = !pause.reason.isNullOrBlank()

                    if (isIdle) {
                        android.util.Log.d("ShiftViewModel", "Restored IDLE from server: reason=${pause.reason}, elapsed=${pauseElapsed}s")
                        state.copy(
                            isIdle = true,
                            idleStartTime = pauseStartMillis,
                            idleSeconds = pauseElapsed,
                            idleReason = pause.reason
                        )
                    } else {
                        android.util.Log.d("ShiftViewModel", "Restored PAUSE from server: elapsed=${pauseElapsed}s")
                        state.copy(
                            isPaused = true,
                            pauseStartTime = pauseStartMillis,
                            pauseSeconds = pauseElapsed
                        )
                    }
                } else {
                    android.util.Log.d("ShiftViewModel", "No active pause/idle on server")
                    // Загружаем историю пауз для подсчёта totalPauseSeconds и totalIdleSeconds
                    loadPauseTotals(state)
                }
            } ?: run {
                android.util.Log.w("ShiftViewModel", "getCurrentPause returned null result")
                state
            }
        } catch (e: Exception) {
            android.util.Log.e("ShiftViewModel", "Failed to restore pause state", e)
            state
        }
    }

    /**
     * Загружаем общее время пауз и простоев из истории пауз смены
     */
    private suspend fun loadPauseTotals(state: ShiftUiState.Active): ShiftUiState.Active {
        return try {
            val result = pauseRepository.getShiftPauses(state.shiftId)
            result.getOrNull()?.let { pauses ->
                var totalPause = 0L
                var totalIdle = 0L
                for (p in pauses) {
                    if (p.endedAt != null && p.durationSeconds != null) {
                        if (!p.reason.isNullOrBlank()) {
                            totalIdle += p.durationSeconds
                        } else {
                            totalPause += p.durationSeconds
                        }
                    }
                }
                android.util.Log.d("ShiftViewModel", "Loaded pause totals: pause=${totalPause}s, idle=${totalIdle}s")
                state.copy(
                    totalPauseSeconds = totalPause,
                    totalIdleSeconds = totalIdle
                )
            } ?: state
        } catch (e: Exception) {
            android.util.Log.e("ShiftViewModel", "Failed to load pause totals", e)
            state
        }
    }

    fun startShift() {
        viewModelScope.launch {
            _error.value = null

            // FIX(2026-04-30): требуем выбор объекта перед стартом смены —
            // иначе фото и часы не привязаны к объекту, и аналитика пустая.
            if (_selectedObjectId.value.isNullOrBlank()) {
                _error.value = "Выберите объект перед началом смены"
                return@launch
            }

            // Offline-First: сразу запускаем смену локально
            val currentTime = System.currentTimeMillis()
            val tempShiftId = "local-${System.currentTimeMillis()}"
            currentShiftId = tempShiftId
            _uiState.value = ShiftUiState.Active(
                shiftId = tempShiftId,
                startTime = currentTime,
                elapsedSeconds = 0
            )
            initializePhotoSlotsFromStartTime(currentTime)
            startTimer()

            // Сохраняем смену в Room для офлайн-восстановления
            try {
                val now = java.time.OffsetDateTime.now()
                val startAtIso = now.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                shiftDao.insertShift(
                    com.belsi.work.data.local.database.entities.ShiftEntity(
                        id = tempShiftId,
                        userId = "",
                        startAt = startAtIso,
                        status = "active",
                        syncStatus = "pending",
                        startTimeMillis = currentTime
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w("ShiftViewModel", "Failed to save shift to Room", e)
            }

            // Обновляем виджет
            ShiftWidgetUpdater.updateWidgetState(
                context = application,
                isRunning = true,
                startTimeMs = currentTime,
                shiftId = tempShiftId
            )

            // Параллельно синхронизируем с сервером
            shiftRepository.startShift(siteObjectId = _selectedObjectId.value)
                .onSuccess { shiftData ->
                    // Обновляем ID смены с серверного
                    val oldId = tempShiftId
                    currentShiftId = shiftData.id
                    val currentState = _uiState.value
                    if (currentState is ShiftUiState.Active) {
                        _uiState.value = currentState.copy(shiftId = shiftData.id)
                    }
                    // Обновляем запись в Room: удаляем локальную, вставляем с серверным ID
                    try {
                        shiftDao.deleteShift(oldId)
                        shiftDao.insertShift(
                            com.belsi.work.data.local.database.entities.ShiftEntity(
                                id = shiftData.id,
                                userId = "",
                                startAt = shiftData.startAt,
                                status = "active",
                                syncStatus = "synced",
                                startTimeMillis = currentTime
                            )
                        )
                    } catch (_: Exception) { }
                    loadShiftPhotos(shiftData.id)
                }
                .onFailure { e ->
                    android.util.Log.w("ShiftViewModel", "Смена запущена локально, сервер недоступен: ${e.message}")
                    // Смена продолжает работать локально — покажем предупреждение
                    setErrorWithAutoClear("Смена запущена офлайн. Синхронизация при восстановлении связи.")
                }
        }
    }

    /**
     * Начать смену после того как первое фото было загружено
     * Используется для workflow: сначала фото, потом смена
     */
    fun startShiftAfterFirstPhoto() {
        viewModelScope.launch {
            _uiState.value = ShiftUiState.Loading
            _error.value = null

            // FIX(2026-04-30): требуем выбор объекта перед стартом смены
            if (_selectedObjectId.value.isNullOrBlank()) {
                _error.value = "Выберите объект перед началом смены"
                _uiState.value = ShiftUiState.NoShift
                return@launch
            }

            android.util.Log.d("ShiftViewModel", "Starting shift after first photo uploaded")

            shiftRepository.startShift(siteObjectId = _selectedObjectId.value)
                .onSuccess { shiftData ->
                    android.util.Log.d("ShiftViewModel", "Shift started successfully: ${shiftData.id}")
                    currentShiftId = shiftData.id
                    val currentTime = System.currentTimeMillis()
                    _uiState.value = ShiftUiState.Active(
                        shiftId = shiftData.id,
                        startTime = currentTime,
                        elapsedSeconds = 0
                    )
                    // Инициализируем слоты с текущим временем начала смены
                    initializePhotoSlotsFromStartTime(currentTime)
                    startTimer()

                    // Обновляем виджет
                    ShiftWidgetUpdater.updateWidgetState(
                        context = application,
                        isRunning = true,
                        startTimeMs = currentTime,
                        shiftId = shiftData.id
                    )

                    // Загрузить фото (включая только что загруженное)
                    loadShiftPhotos(shiftData.id)
                }
                .onFailure { e ->
                    android.util.Log.e("ShiftViewModel", "Failed to start shift after photo", e)
                    _error.value = e.message ?: "Не удалось начать смену"
                    _uiState.value = ShiftUiState.NoShift
                }
        }
    }

    fun endShift() {
        val currentState = _uiState.value
        if (currentState !is ShiftUiState.Active) {
            android.util.Log.w("ShiftViewModel", "endShift called but state is not Active: $currentState")
            return
        }

        viewModelScope.launch {
            _uiState.value = ShiftUiState.Loading
            _error.value = null

            android.util.Log.d("ShiftViewModel", "Attempting to end shift, current state: $currentState")

            // Если есть активная пауза/простой — завершаем перед окончанием смены
            if (currentState.isPaused) {
                android.util.Log.d("ShiftViewModel", "Ending active pause before finishing shift")
                pauseRepository.endPause()
            }
            if (currentState.isIdle) {
                android.util.Log.d("ShiftViewModel", "Ending active idle before finishing shift")
                pauseRepository.endIdle()
            }

            // Пробуем завершить смену с retry логикой
            val result = retryEndShift(currentState.shiftId, maxRetries = 3)

            result
                .onSuccess { finishedShift ->
                    android.util.Log.d("ShiftViewModel", "Shift ended successfully: status=${finishedShift.status}")

                    // Дополнительная проверка - верифицируем, что смена действительно завершена
                    viewModelScope.launch {
                        delay(500) // Даём серверу время обновить статус
                        shiftRepository.getActiveShift()
                            .onSuccess { activeShift ->
                                if (activeShift != null && activeShift.id == currentShiftId) {
                                    android.util.Log.w("ShiftViewModel", "WARNING: Shift still active on server after ending!")
                                    setErrorWithAutoClear("Смена завершена, но сервер ещё показывает её активной. Подождите немного")
                                } else {
                                    android.util.Log.d("ShiftViewModel", "Verified: No active shift on server")
                                }
                            }
                    }

                    stopTimer()
                    stopPauseTimer()
                    stopIdleTimer()
                    _uiState.value = ShiftUiState.NoShift
                    currentShiftId = null
                    // Сбросить слоты
                    initializePhotoSlots()

                    // Обновляем виджет
                    ShiftWidgetUpdater.clearWidgetState(application)
                }
                .onFailure { e ->
                    android.util.Log.e("ShiftViewModel", "Failed to end shift after all retries", e)
                    _error.value = e.message ?: "Не удалось завершить смену. Попробуйте ещё раз"
                    _uiState.value = currentState
                }
        }
    }

    /**
     * Попытка завершить смену с retry логикой
     */
    private suspend fun retryEndShift(shiftId: String, maxRetries: Int): Result<ShiftData> {
        var lastError: Throwable? = null

        repeat(maxRetries) { attempt ->
            android.util.Log.d("ShiftViewModel", "Ending shift attempt ${attempt + 1}/$maxRetries, shiftId: $shiftId")

            // Сначала проверяем, есть ли активная смена на сервере
            val activeShiftResult = shiftRepository.getActiveShift()

            val actualShiftId = activeShiftResult.getOrNull()?.id ?: shiftId
            android.util.Log.d("ShiftViewModel", "Using shiftId from server: $actualShiftId")

            // Пытаемся завершить смену
            val result = shiftRepository.endShift(actualShiftId)

            if (result.isSuccess) {
                android.util.Log.d("ShiftViewModel", "Shift ended successfully on attempt ${attempt + 1}")
                return result
            }

            lastError = result.exceptionOrNull()
            android.util.Log.w("ShiftViewModel", "Attempt ${attempt + 1} failed: ${lastError?.message}")

            // Ждём перед следующей попыткой (кроме последней)
            if (attempt < maxRetries - 1) {
                delay(1000L * (attempt + 1)) // Экспоненциальная задержка: 1s, 2s, 3s
            }
        }

        return Result.failure(lastError ?: Exception("Failed to end shift after $maxRetries attempts"))
    }

    /**
     * Начать паузу — Optimistic UI: сначала обновляем UI, потом отправляем на сервер.
     * При ошибке — откатываем.
     */
    fun pauseShift() {
        val currentState = _uiState.value
        if (currentState !is ShiftUiState.Active || currentState.isPaused || currentState.isIdle) return
        if (isActionInProgress) return
        val now = System.currentTimeMillis()
        if (now - lastActionTimestamp < 2000) return
        lastActionTimestamp = now
        isActionInProgress = true

        // Optimistic: обновляем UI мгновенно
        val optimisticPauseStart = System.currentTimeMillis()
        _uiState.value = currentState.copy(
            isPaused = true,
            pauseStartTime = optimisticPauseStart,
            pauseSeconds = 0
        )
        startPauseTimer()

        // Обновляем виджет
        ShiftWidgetUpdater.updateWidgetState(
            context = application,
            isRunning = true,
            isPaused = true,
            startTimeMs = currentState.startTime,
            shiftId = currentState.shiftId
        )

        viewModelScope.launch {
            try {
                android.util.Log.d("ShiftViewModel", "Starting pause on server...")

                pauseRepository.startPause()
                    .onSuccess { pauseResponse ->
                        android.util.Log.d("ShiftViewModel", "Pause started on server: ${pauseResponse.id}")

                        // Уточняем время паузы с сервера
                        val pauseStartMillis = try {
                            java.time.OffsetDateTime.parse(pauseResponse.startedAt)
                                .toInstant()
                                .toEpochMilli()
                        } catch (e: Exception) {
                            optimisticPauseStart
                        }

                        val currentActiveState = _uiState.value
                        if (currentActiveState is ShiftUiState.Active) {
                            _uiState.value = currentActiveState.copy(
                                pauseStartTime = pauseStartMillis
                            )
                        }
                    }
                    .onFailure { e ->
                        android.util.Log.e("ShiftViewModel", "Failed to start pause on server, rolling back", e)
                        // Откат оптимистичного обновления
                        stopPauseTimer()
                        val rollbackState = _uiState.value
                        if (rollbackState is ShiftUiState.Active) {
                            _uiState.value = rollbackState.copy(
                                isPaused = false,
                                pauseStartTime = null,
                                pauseSeconds = 0
                            )
                        }
                        setErrorWithAutoClear("Ошибка запуска паузы: ${e.message}")
                    }
            } finally {
                isActionInProgress = false
            }
        }
    }

    /**
     * Снять паузу — Optimistic UI: сначала обновляем UI, потом отправляем на сервер.
     * При ошибке — откатываем.
     */
    fun resumeShift() {
        val currentState = _uiState.value
        if (currentState !is ShiftUiState.Active || !currentState.isPaused) return
        if (isActionInProgress) return
        val now = System.currentTimeMillis()
        if (now - lastActionTimestamp < 2000) return
        lastActionTimestamp = now
        isActionInProgress = true

        // Сохраняем для отката
        val savedPauseStartTime = currentState.pauseStartTime
        val savedPauseSeconds = currentState.pauseSeconds
        val estimatedDuration = if (savedPauseStartTime != null) {
            (System.currentTimeMillis() - savedPauseStartTime) / 1000
        } else {
            savedPauseSeconds
        }

        // Optimistic: обновляем UI мгновенно
        stopPauseTimer()
        _uiState.value = currentState.copy(
            isPaused = false,
            pauseStartTime = null,
            pauseSeconds = 0,
            totalPauseSeconds = currentState.totalPauseSeconds + estimatedDuration
        )

        // Обновляем виджет
        ShiftWidgetUpdater.updateWidgetState(
            context = application,
            isRunning = true,
            isPaused = false,
            startTimeMs = currentState.startTime,
            shiftId = currentState.shiftId
        )

        viewModelScope.launch {
            try {
                android.util.Log.d("ShiftViewModel", "Ending pause on server...")

                pauseRepository.endPause()
                    .onSuccess { pauseResponse ->
                        android.util.Log.d("ShiftViewModel", "Pause ended on server, duration: ${pauseResponse.durationSeconds}s")

                        // Уточняем длительность с сервера
                        val serverDuration = pauseResponse.durationSeconds?.toLong() ?: estimatedDuration
                        val currentActiveState = _uiState.value
                        if (currentActiveState is ShiftUiState.Active) {
                            _uiState.value = currentActiveState.copy(
                                totalPauseSeconds = currentState.totalPauseSeconds + serverDuration
                            )
                        }
                    }
                    .onFailure { e ->
                        android.util.Log.e("ShiftViewModel", "Failed to end pause on server, rolling back", e)
                        // Откат оптимистичного обновления
                        val rollbackState = _uiState.value
                        if (rollbackState is ShiftUiState.Active) {
                            _uiState.value = rollbackState.copy(
                                isPaused = true,
                                pauseStartTime = savedPauseStartTime,
                                pauseSeconds = savedPauseSeconds,
                                totalPauseSeconds = currentState.totalPauseSeconds
                            )
                            startPauseTimer()
                        }
                        setErrorWithAutoClear("Ошибка снятия паузы: ${e.message}")
                    }
            } finally {
                isActionInProgress = false
            }
        }
    }

    /**
     * Начать простой — отправляет запрос на СЕРВЕР
     */
    fun startIdle(reason: String = "") {
        val currentState = _uiState.value
        if (currentState !is ShiftUiState.Active || currentState.isIdle || currentState.isPaused) return
        if (isActionInProgress) return
        isActionInProgress = true

        val idleReason = reason.ifBlank { "Простой" }

        viewModelScope.launch {
            try {
                android.util.Log.d("ShiftViewModel", "Starting idle on server, reason: $idleReason")

                pauseRepository.startIdle(idleReason)
                    .onSuccess { pauseResponse ->
                        android.util.Log.d("ShiftViewModel", "Idle started on server: ${pauseResponse.id}")

                        val idleStartMillis = try {
                            java.time.OffsetDateTime.parse(pauseResponse.startedAt)
                                .toInstant()
                                .toEpochMilli()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }

                        val currentActiveState = _uiState.value
                        if (currentActiveState is ShiftUiState.Active) {
                            _uiState.value = currentActiveState.copy(
                                isIdle = true,
                                idleStartTime = idleStartMillis,
                                idleSeconds = 0,
                                idleReason = idleReason
                            )
                            startIdleTimer()
                        }
                    }
                    .onFailure { e ->
                        android.util.Log.e("ShiftViewModel", "Failed to start idle on server", e)
                        setErrorWithAutoClear("Ошибка запуска простоя: ${e.message}")
                    }
            } finally {
                isActionInProgress = false
            }
        }
    }

    /**
     * Завершить простой — отправляет запрос на СЕРВЕР
     */
    fun resumeFromIdle() {
        val currentState = _uiState.value
        if (currentState !is ShiftUiState.Active || !currentState.isIdle) return
        if (isActionInProgress) return
        isActionInProgress = true

        viewModelScope.launch {
            try {
                android.util.Log.d("ShiftViewModel", "Ending idle on server...")

                pauseRepository.endIdle()
                    .onSuccess { pauseResponse ->
                        android.util.Log.d("ShiftViewModel", "Idle ended on server, duration: ${pauseResponse.durationSeconds}s")

                        stopIdleTimer()

                        val serverDuration = pauseResponse.durationSeconds?.toLong() ?: currentState.idleSeconds
                        val newTotalIdleTime = currentState.totalIdleSeconds + serverDuration

                        val currentActiveState = _uiState.value
                        if (currentActiveState is ShiftUiState.Active) {
                            _uiState.value = currentActiveState.copy(
                                isIdle = false,
                                idleStartTime = null,
                                idleSeconds = 0,
                                totalIdleSeconds = newTotalIdleTime,
                                idleReason = null
                            )
                        }
                    }
                    .onFailure { e ->
                        android.util.Log.e("ShiftViewModel", "Failed to end idle on server", e)
                        setErrorWithAutoClear("Ошибка снятия простоя: ${e.message}")
                    }
            } finally {
                isActionInProgress = false
            }
        }
    }

    /** Счётчик тиков для периодического сохранения таймера в Room */
    private var timerTickCount = 0

    /**
     * Единый таймер обновления — обновляет elapsedSeconds, pauseSeconds и idleSeconds
     * атомарно в одном .copy() вызове, чтобы избежать гонки между таймерами.
     * Каждые 10 секунд сохраняет состояние таймера в Room для персистентности.
     */
    private fun startTimer() {
        timerJob?.cancel()
        timerTickCount = 0
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1.seconds)
                val currentState = _uiState.value
                if (currentState is ShiftUiState.Active) {
                    val now = System.currentTimeMillis()
                    // Общее время смены ВСЕГДА идет
                    val totalElapsed = (now - currentState.startTime) / 1000

                    // Время паузы (если на паузе)
                    val newPauseSeconds = if (currentState.isPaused && currentState.pauseStartTime != null) {
                        maxOf(0L, (now - currentState.pauseStartTime) / 1000)
                    } else {
                        currentState.pauseSeconds
                    }

                    // Время простоя (если на простое)
                    val newIdleSeconds = if (currentState.isIdle && currentState.idleStartTime != null) {
                        maxOf(0L, (now - currentState.idleStartTime) / 1000)
                    } else {
                        currentState.idleSeconds
                    }

                    // Атомарное обновление — только если значения изменились
                    if (totalElapsed != currentState.elapsedSeconds ||
                        newPauseSeconds != currentState.pauseSeconds ||
                        newIdleSeconds != currentState.idleSeconds) {
                        _uiState.value = currentState.copy(
                            elapsedSeconds = totalElapsed,
                            pauseSeconds = newPauseSeconds,
                            idleSeconds = newIdleSeconds
                        )
                    }

                    // Каждые 10 секунд сохраняем состояние таймера в Room
                    timerTickCount++
                    if (timerTickCount >= 10) {
                        timerTickCount = 0
                        try {
                            shiftDao.saveTimerState(
                                shiftId = currentState.shiftId,
                                elapsed = totalElapsed,
                                pause = newPauseSeconds,
                                idle = newIdleSeconds,
                                totalPause = currentState.totalPauseSeconds,
                                totalIdle = currentState.totalIdleSeconds,
                                isPaused = currentState.isPaused,
                                isIdle = currentState.isIdle
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("ShiftViewModel", "Failed to persist timer state", e)
                        }
                    }
                }
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
    }

    private fun startPauseTimer() {
        // Больше не нужен отдельный таймер — единый startTimer() обновляет всё
        // Оставляем метод для обратной совместимости
    }

    fun stopPauseTimer() {
        pauseTimerJob?.cancel()
    }

    private fun startIdleTimer() {
        // Больше не нужен отдельный таймер — единый startTimer() обновляет всё
        // Оставляем метод для обратной совместимости
    }

    fun stopIdleTimer() {
        idleTimerJob?.cancel()
    }

    fun clearError() {
        _error.value = null
    }

    /** Автоочистка ошибки через 5 секунд — чтобы не оставалась навсегда */
    private fun setErrorWithAutoClear(message: String) {
        _error.value = message
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            if (_error.value == message) {
                _error.value = null
            }
        }
    }

    private fun initializePhotoSlots() {
        // Используется при завершении смены - сбрасываем к пустым слотам
        _photoSlots.value = emptyList()
    }

    /**
     * Инициализация слотов на основе времени начала смены
     * Смена длится 8 часов, фото каждый час
     */
    private fun initializePhotoSlotsFromStartTime(startTimeMillis: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startTimeMillis
        val startHour = calendar.get(Calendar.HOUR_OF_DAY)

        val slots = (0..7).map { index ->
            val slotHour = (startHour + index) % 24  // Учитываем переход через полночь
            PhotoSlot(
                index = index,
                timeLabel = String.format("%02d:00", slotHour),
                status = PhotoSlotStatus.EMPTY  // Все слоты доступны с начала смены
            )
        }
        _photoSlots.value = slots
    }

    private fun determineSlotStatus(hour: Int): PhotoSlotStatus {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        return when {
            currentHour < hour -> PhotoSlotStatus.LOCKED
            else -> PhotoSlotStatus.EMPTY
        }
    }

    fun updatePhotoSlotStatus(
        slotIndex: Int,
        status: PhotoSlotStatus,
        photoUrl: String? = null,
        rejectionReason: String? = null
    ) {
        val updatedSlots = _photoSlots.value.toMutableList()
        updatedSlots[slotIndex] = updatedSlots[slotIndex].copy(
            status = status,
            photoUrl = photoUrl,
            rejectionReason = rejectionReason
        )
        _photoSlots.value = updatedSlots
    }

    /**
     * Публичный метод для обновления фото текущей смены
     * Вызывается после успешной загрузки нового фото
     */
    fun refreshShiftPhotos() {
        val shiftId = currentShiftId ?: return
        android.util.Log.d("ShiftViewModel", "refreshShiftPhotos called for shift: $shiftId")
        loadShiftPhotos(shiftId)
    }

    private fun loadShiftPhotos(shiftId: String) {
        viewModelScope.launch {
            android.util.Log.d("ShiftViewModel", "loadShiftPhotos called for shift: $shiftId")
            shiftRepository.getShiftPhotos(shiftId)
                .onSuccess { photos ->
                    android.util.Log.d("ShiftViewModel", "Loaded ${photos.size} photos from server")

                    // Получаем текущие слоты
                    val currentSlots = _photoSlots.value

                    // Обновить статусы слотов на основе загруженных фото
                    photos.forEach { photo ->
                        val hourLabel = photo.hourLabel ?: return@forEach

                        // hourLabel может быть "15:00" или ISO 8601 "2026-03-10T15:00:00+03:00"
                        // Извлекаем HH:mm для матчинга со слотами
                        val normalizedLabel = try {
                            if (hourLabel.contains("T")) {
                                val dt = java.time.OffsetDateTime.parse(hourLabel)
                                String.format("%02d:00", dt.hour)
                            } else {
                                hourLabel
                            }
                        } catch (_: Exception) { hourLabel }
                        val slotIndex = currentSlots.indexOfFirst { it.timeLabel == normalizedLabel }

                        if (slotIndex != -1) {
                            val status = when (photo.status.lowercase()) {
                                "approved" -> PhotoSlotStatus.UPLOADED  // Одобрено бригадиром/куратором
                                "uploaded", "pending_review" -> PhotoSlotStatus.PENDING  // На модерации
                                "rejected" -> PhotoSlotStatus.REJECTED  // Отклонено
                                else -> PhotoSlotStatus.EMPTY
                            }
                            android.util.Log.d("ShiftViewModel", "Updating slot $slotIndex ($hourLabel): status=$status, url=${photo.photoUrl}")
                            updatePhotoSlotStatus(
                                slotIndex = slotIndex,
                                status = status,
                                photoUrl = photo.photoUrl,
                                rejectionReason = if (status == PhotoSlotStatus.REJECTED) photo.comment else null
                            )
                        } else {
                            android.util.Log.w("ShiftViewModel", "No slot found for hour label: $hourLabel")
                        }
                    }
                }
                .onFailure { e ->
                    android.util.Log.e("ShiftViewModel", "Failed to load shift photos", e)
                    // Игнорируем ошибку загрузки фото - не критично
                }
        }
    }

    // Алиас для обратной совместимости
    fun refreshPhotos() {
        refreshShiftPhotos()
    }

    override fun onCleared() {
        super.onCleared()
        // Сохраняем состояние таймера перед уничтожением ViewModel
        val currentState = _uiState.value
        if (currentState is ShiftUiState.Active) {
            kotlinx.coroutines.runBlocking {
                try {
                    shiftDao.saveTimerState(
                        shiftId = currentState.shiftId,
                        elapsed = currentState.elapsedSeconds,
                        pause = currentState.pauseSeconds,
                        idle = currentState.idleSeconds,
                        totalPause = currentState.totalPauseSeconds,
                        totalIdle = currentState.totalIdleSeconds,
                        isPaused = currentState.isPaused,
                        isIdle = currentState.isIdle
                    )
                } catch (_: Exception) { }
            }
        }
        stopTimer()
        stopPauseTimer()
        stopIdleTimer()
    }
}

sealed class ShiftUiState {
    object NoShift : ShiftUiState()
    object Loading : ShiftUiState()
    data class Active(
        val shiftId: String,
        val startTime: Long,
        val elapsedSeconds: Long, // Общее время смены (всегда идет)
        val isPaused: Boolean = false,
        val pauseStartTime: Long? = null,
        val pauseSeconds: Long = 0,
        val totalPauseSeconds: Long = 0,
        val isIdle: Boolean = false,
        val idleStartTime: Long? = null,
        val idleSeconds: Long = 0,
        val totalIdleSeconds: Long = 0,
        val idleReason: String? = null // Причина простоя
    ) : ShiftUiState() {
        val formattedTime: String
            get() {
                val hours = elapsedSeconds / 3600
                val minutes = (elapsedSeconds % 3600) / 60
                val seconds = elapsedSeconds % 60
                return String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }

        val formattedPauseTime: String
            get() {
                val hours = pauseSeconds / 3600
                val minutes = (pauseSeconds % 3600) / 60
                val seconds = pauseSeconds % 60
                return String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }

        val formattedTotalPauseTime: String
            get() {
                val hours = totalPauseSeconds / 3600
                val minutes = (totalPauseSeconds % 3600) / 60
                val seconds = totalPauseSeconds % 60
                return String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }

        val formattedIdleTime: String
            get() {
                val hours = idleSeconds / 3600
                val minutes = (idleSeconds % 3600) / 60
                val seconds = idleSeconds % 60
                return String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }

        val formattedTotalIdleTime: String
            get() {
                val hours = totalIdleSeconds / 3600
                val minutes = (totalIdleSeconds % 3600) / 60
                val seconds = totalIdleSeconds % 60
                return String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }

        val netWorkTime: Long
            get() {
                // Чистое рабочее время = общее время - общие паузы - общий простой
                // Текущие pauseSeconds и idleSeconds тоже вычитаем (они еще не в total)
                val net = elapsedSeconds - totalPauseSeconds - totalIdleSeconds - pauseSeconds - idleSeconds
                return if (net < 0) 0 else net
            }

        val formattedNetWorkTime: String
            get() {
                val net = netWorkTime
                val hours = net / 3600
                val minutes = (net % 3600) / 60
                val seconds = net % 60
                return String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }
    }
}
