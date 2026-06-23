package com.belsi.work.presentation.screens.shift

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.database.dao.PhotoDao
import com.belsi.work.data.local.database.dao.ShiftDao
import com.belsi.work.data.offline.OfflineQueuedException
import com.belsi.work.data.remote.dto.objects.SiteObjectDto
import com.belsi.work.data.repositories.BatchRepository
import com.belsi.work.data.repositories.ObjectsRepository
import com.belsi.work.data.repositories.PauseRepository
import com.belsi.work.data.repositories.ShiftData
import com.belsi.work.data.repositories.ShiftRepository
import com.belsi.work.data.repositories.UserRepository
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
    private val userRepository: UserRepository,
    private val shiftDao: ShiftDao,
    private val photoDao: PhotoDao,
    // FIX(2026-05-11) BELSI 2.0.0 build9: подгружаем причины простоя с бэкенда
    private val batchRepository: BatchRepository,
) : ViewModel() {

    /**
     * FIX(2026-05-11) build9: список причин простоя для монтажника (domain=installation).
     * Источник правды — таблица shift_idle_reason_catalog. При недоступности API
     * IdleReasonDialog использует hardcoded fallback (см. Composable).
     */
    private val _idleReasons = MutableStateFlow<List<String>>(emptyList())
    val idleReasons: StateFlow<List<String>> = _idleReasons.asStateFlow()

    init {
        viewModelScope.launch {
            batchRepository.getIdleReasons(domain = "installation")
                .onSuccess { list -> _idleReasons.value = list.map { it.label } }
                .onFailure { android.util.Log.w("ShiftViewModel", "getIdleReasons failed: ${it.message}") }
        }
    }

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
    /**
     * FIX(2026-05-22) P1-3: гард от двойного тапа «Завершить смену».
     * _uiState=Loading ставилось ВНУТРИ async launch → быстрый второй тап успевал
     * зайти повторно и запустить второй finish-флоу. Ставим флаг СИНХРОННО до launch.
     */
    private var isEndingShift = false
    /**
     * FIX(2026-05-22) P1-4: процесс-живущий IO-скоуп для записи в Room на teardown.
     * Раньше onCleared делал runBlocking{Room} на main-треде → блок UI +
     * SupportSQLiteLock FileLockInterruptionException (write прерывался при отмене).
     */
    private val teardownScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private var currentShiftId: String? = null

    // Объекты
    private val _availableObjects = MutableStateFlow<List<SiteObjectDto>>(emptyList())
    val availableObjects: StateFlow<List<SiteObjectDto>> = _availableObjects.asStateFlow()

    private val _selectedObjectId = MutableStateFlow<String?>(null)
    val selectedObjectId: StateFlow<String?> = _selectedObjectId.asStateFlow()

    private val _currentObjectName = MutableStateFlow<String?>(null)
    val currentObjectName: StateFlow<String?> = _currentObjectName.asStateFlow()

    // FIX(2026-05-01): pull-to-refresh state — пользователь свайпом вниз
    // принудительно подтягивает свежие данные с сервера (на случай рассинхрона
    // таймера или зависшего состояния).
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        initializePhotoSlots()
        checkActiveShift()
        loadObjects()
        // FIX(2026-05-01): подтягиваем sticky-binding `current_site_object_id`
        // с сервера при старте VM. Это восстанавливает выбранный объект после
        // minimize/kill приложения. Если активной смены нет — объект всё равно
        // остаётся выбранным в UI, без необходимости его перевыбирать.
        restoreCurrentObjectFromServer()
    }

    /**
     * FIX(2026-05-01): восстановление выбранного объекта из /user/me.
     * Sticky-binding на пользователе, переживает minimize/kill app.
     */
    private fun restoreCurrentObjectFromServer() {
        viewModelScope.launch {
            userRepository.getProfile()
                .onSuccess { user ->
                    val current = user.currentSiteObjectId?.toString()
                    if (current != null && _selectedObjectId.value == null) {
                        _selectedObjectId.value = current
                        // Имя подтягиваем когда availableObjects загрузится (см. ниже)
                        _currentObjectName.value =
                            _availableObjects.value.firstOrNull { it.id == current }?.name
                        android.util.Log.d(
                            "ShiftViewModel",
                            "Restored sticky site_object_id from server: $current"
                        )
                    }
                }
                .onFailure {
                    android.util.Log.w(
                        "ShiftViewModel",
                        "Failed to fetch /user/me for sticky object: ${it.message}"
                    )
                }
        }
    }

    private fun loadObjects() {
        viewModelScope.launch {
            objectsRepository.getObjects(status = "active")
                .onSuccess { list ->
                    _availableObjects.value = list
                    // Если объект уже выбран (например, восстановлен из /user/me),
                    // но имя ещё не подставлено (objects грузились параллельно) —
                    // подставим теперь.
                    val sel = _selectedObjectId.value
                    if (sel != null && _currentObjectName.value == null) {
                        _currentObjectName.value = list.firstOrNull { it.id == sel }?.name
                    }
                }
                .onFailure { android.util.Log.e("ShiftViewModel", "Failed to load objects: ${it.message}") }
        }
    }

    /**
     * Выбор объекта пользователем (до старта смены или вне смены).
     * FIX(2026-05-01): объект персиститься на сервере (sticky-binding на user),
     * чтобы переживать minimize/kill приложения.
     * fire-and-forget: при ошибке сети — UI остаётся, retry на следующем выборе.
     */
    fun selectObject(objectId: String?) {
        _selectedObjectId.value = objectId
        _currentObjectName.value = _availableObjects.value.firstOrNull { it.id == objectId }?.name

        // Persist на сервер
        viewModelScope.launch {
            val uuid = objectId?.let {
                try { java.util.UUID.fromString(it) } catch (e: Exception) { null }
            }
            userRepository.setCurrentObject(uuid)
                .onFailure {
                    android.util.Log.w(
                        "ShiftViewModel",
                        "Failed to persist current_site_object_id=$objectId: ${it.message}"
                    )
                    // Не показываем ошибку юзеру — UI уже отреагировал.
                    // При следующем выборе/refresh попробуем снова.
                }
        }
    }

    fun changeObject(newObjectId: String) {
        viewModelScope.launch {
            objectsRepository.changeShiftObject(newObjectId)
                .onSuccess {
                    _selectedObjectId.value = newObjectId
                    _currentObjectName.value = _availableObjects.value.firstOrNull { it.id == newObjectId }?.name
                    // FIX(2026-05-01): синхронизируем sticky-binding на user,
                    // чтобы после minimize/kill приложения объект остался выбранным.
                    val uuid = try { java.util.UUID.fromString(newObjectId) } catch (e: Exception) { null }
                    if (uuid != null) {
                        userRepository.setCurrentObject(uuid)
                    }
                }
                .onFailure { _error.value = it.message ?: "Ошибка смены объекта" }
        }
    }

    /**
     * Публичный метод для принудительной проверки активной смены.
     * - Используется после создания смены в CameraViewModel (quiet=false → Loading)
     * - Используется на ON_RESUME из ShiftScreen (quiet=true → silent)
     * - Используется в periodic sync каждые 60 сек (quiet=true)
     */
    fun checkActiveShiftPublic(quiet: Boolean = false) {
        checkActiveShift(quiet = quiet)
    }

    /**
     * FIX(2026-05-01): pull-to-refresh — свайп вниз → форсированный re-sync.
     * Применяется когда таймер отображает некорректное время или другое
     * состояние выглядит зависшим.
     *
     * 1. Включает isRefreshing (показывает spinner)
     * 2. Подтягивает с сервера: shift state, паузу/простой, фото, объекты
     * 3. Минимум 600 мс для UX (чтобы spinner не моргал)
     * 4. Выключает isRefreshing
     */
    fun forceRefresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                // 1) Активная смена + пауза/простой
                checkActiveShift(quiet = true)
                // 2) Объекты (на случай если куратор добавил/изменил)
                loadObjects()
                // 3) Фото текущей смены
                currentShiftId?.let { loadShiftPhotos(it) }
                // 4) Минимум 600 мс
                val elapsed = System.currentTimeMillis() - started
                if (elapsed < 600L) delay(600L - elapsed)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Проверка активной смены при запуске приложения
     * Если смена была начата и приложение было закрыто, восстанавливаем состояние
     * ВКЛЮЧАЯ состояние паузы/простоя с сервера
     */
    private fun checkActiveShift(quiet: Boolean = false) {
        viewModelScope.launch {
            android.util.Log.d("ShiftViewModel", "Checking for active shift (quiet=$quiet)...")
            // FIX(2026-05-01) BUG#3: Loading state ставим ТОЛЬКО на cold-start.
            // Периодический sync (каждые 60 сек) НЕ должен мигать "Loading"
            // и затирать накопленные значения elapsedSeconds в активной смене.
            if (!quiet) _uiState.value = ShiftUiState.Loading

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

                        // FIX(2026-05-01) BUG#3 cont: при quiet sync сохраняем накопленные
                        // pause/idle/totals из текущего state, чтобы не сбросить таймер.
                        val prev = _uiState.value as? ShiftUiState.Active

                        var state = ShiftUiState.Active(
                            shiftId = activeShift.id,
                            startTime = startTimeMillis,
                            elapsedSeconds = elapsedSeconds,
                            isPaused = prev?.isPaused ?: false,
                            pauseStartTime = prev?.pauseStartTime,
                            pauseSeconds = prev?.pauseSeconds ?: 0L,
                            totalPauseSeconds = prev?.totalPauseSeconds ?: 0L,
                            isIdle = prev?.isIdle ?: false,
                            idleStartTime = prev?.idleStartTime,
                            idleSeconds = prev?.idleSeconds ?: 0L,
                            totalIdleSeconds = prev?.totalIdleSeconds ?: 0L,
                            idleReason = prev?.idleReason,
                        )

                        // Серверная правда о паузе/простое (всегда уточняем)
                        state = restorePauseState(state)

                        _uiState.value = state

                        // Слоты и таймер — на cold-start или если смена сменилась
                        if (!quiet || prev == null || prev.shiftId != activeShift.id) {
                            initializePhotoSlotsFromStartTime(startTimeMillis)
                            startTimer()
                            if (state.isPaused) startPauseTimer()
                            if (state.isIdle) startIdleTimer()
                            loadShiftPhotos(activeShift.id)
                        }
                    } else {
                        // FIX(2026-05-01) BUG#2+#5: сервер говорит — нет активной смены.
                        // Останавливаем таймер, чистим Room (чтобы restoreFromRoom
                        // потом не вернул призрак на 190 часов), сбрасываем currentShiftId.
                        android.util.Log.d("ShiftViewModel", "No active shift on server — clearing local state")
                        stopTimer()
                        stopPauseTimer()
                        stopIdleTimer()
                        currentShiftId = null
                        try {
                            // Любая локальная "active" запись — устаревшая, переводим в finished
                            val zombie = shiftDao.getActiveShift()
                            if (zombie != null) {
                                android.util.Log.w("ShiftViewModel", "Marking zombie Room shift ${zombie.id} as finished")
                                shiftDao.updateShiftStatus(zombie.id, "finished")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("ShiftViewModel", "Failed to clean zombie Room shift", e)
                        }
                        ShiftWidgetUpdater.clearWidgetState(application)
                        _uiState.value = ShiftUiState.NoShift
                    }
                }
                .onFailure { e ->
                    android.util.Log.e("ShiftViewModel", "Failed to check active shift", e)
                    // FIX(2026-05-01) BUG#5: при periodic sync (quiet) НЕ откатываемся в Room.
                    // Иначе при кратковременной потере сети мы могли бы задним числом
                    // подменить актуальный state на устаревший Room snapshot.
                    if (!quiet) {
                        restoreFromRoom()
                    }
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

                // FIX(2026-05-01) BUG#5: SAFETY GUARD против "190 часов".
                // Если локальной смене больше 16 часов — это почти 100% призрак
                // (server её закрыл, а Room не обновился). Не восстанавливаем,
                // помечаем finished и показываем NoShift.
                val ageHours = (System.currentTimeMillis() - startTimeMillis) / 3_600_000.0
                if (ageHours > 16) {
                    android.util.Log.w("ShiftViewModel",
                        "Refusing to restore stale Room shift (age=${ageHours}h > 16h). Marking finished.")
                    try {
                        shiftDao.updateShiftStatus(localShift.id, "finished")
                    } catch (_: Exception) { }
                    currentShiftId = null
                    _uiState.value = ShiftUiState.NoShift
                    return
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
        // FIX(2026-05-13) BELSI 2.0.1: race-condition guard.
        // Пока идёт user-action (pauseShift/resumeShift/startIdle/endIdle),
        // НЕ перетираем state — иначе 60-sec polling может ответить раньше POST
        // и self-heal перетрёт optimistic isPaused=true в false до того как
        // POST commit-нулся в БД (баг с Tecno 15:49:38 — POST и GET в одной секунде).
        if (isActionInProgress) {
            android.util.Log.d("ShiftViewModel",
                "restorePauseState: skipping — user action in progress (race-guard)")
            return state
        }
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

                    // Классификация по reason:
                    //   null/"" → обычная Пауза (pause_seconds)
                    //   "break:*" → Обед/Перекур — это семейство ПАУЗЫ (lunch/break_seconds)
                    //   прочее непустое → Простой (idle_seconds)
                    val reason = pause.reason
                    val isBreak = reason != null && reason.startsWith("break:")
                    val isIdle = !reason.isNullOrBlank() && !isBreak

                    if (isIdle) {
                        android.util.Log.d("ShiftViewModel", "Restored IDLE from server: reason=$reason, elapsed=${pauseElapsed}s")
                        state.copy(
                            isIdle = true,
                            idleStartTime = pauseStartMillis,
                            idleSeconds = pauseElapsed,
                            idleReason = reason
                        )
                    } else {
                        android.util.Log.d("ShiftViewModel", "Restored PAUSE/BREAK from server: reason=$reason, elapsed=${pauseElapsed}s")
                        state.copy(
                            isPaused = true,
                            pauseStartTime = pauseStartMillis,
                            pauseSeconds = pauseElapsed,
                            pauseReason = if (isBreak) reason else null
                        )
                    }
                } else {
                    // FIX(2026-05-06) HOTFIX 1.2.6 + 1.3.0: сервер — единственный источник истины.
                    // Если у нас в state застряло isPaused=true / isIdle=true (после
                    // потерянного ответа на endPause/endIdle и rollback по 400
                    // "No active pause"), а сервер отвечает on_pause=false — СБРАСЫВАЕМ.
                    // Без этого state наследуется из prev на каждом ON_RESUME / 60s sync
                    // и пользователь застревает в «вечной паузе» (баг Курешова, Красавин).
                    android.util.Log.d("ShiftViewModel",
                        "No active pause/idle on server — clearing local pause/idle state " +
                        "(was isPaused=${state.isPaused}, isIdle=${state.isIdle})")
                    val cleared = state.copy(
                        isPaused = false,
                        isIdle = false,
                        pauseStartTime = null,
                        idleStartTime = null,
                        pauseSeconds = 0,
                        idleSeconds = 0,
                        idleReason = null,
                        pauseReason = null,
                    )
                    loadPauseTotals(cleared)
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
                        val r = p.reason
                        // break:* (Обед/Перекур) считаем в паузу; прочее непустое — простой.
                        if (!r.isNullOrBlank() && !r.startsWith("break:")) {
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

            // FIX(2026-05-01): УБРАЛИ обязательность объекта при старте смены.
            // Раньше блокировали startShift без siteObjectId — это ломало рабочий
            // процесс монтажников (Денисов А., +7XXXXXXXXXX: смена не стартовала).
            // Теперь объект опционален; если не выбран — смена идёт без привязки,
            // куратор может прицепить объект потом.

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

            // FIX(2026-05-01): объект опционален (см. startShift())

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
        // P1-3: дедупликация двойного тапа. Флаг ставим СИНХРОННО до launch,
        // т.к. _uiState=Loading выставляется уже внутри корутины (асинхронно).
        if (isEndingShift) {
            android.util.Log.w("ShiftViewModel", "endShift ignored — finish already in progress")
            return
        }
        isEndingShift = true

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

                    // FIX(2026-05-01) BUG#1: после успешного finish ОБЯЗАТЕЛЬНО
                    // помечаем смену в Room как finished, иначе при следующем
                    // cold-start restoreFromRoom() поднимет её заново
                    // (это и был корень бага «таймер 190h после закрытия»).
                    try {
                        shiftDao.updateShiftStatus(currentState.shiftId, "finished")
                        shiftDao.updateShiftEndTime(
                            currentState.shiftId,
                            java.time.OffsetDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("ShiftViewModel", "Failed to mark Room shift finished", e)
                    }

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

            // P1-3: снимаем гард в обоих исходах (успех/ошибка), чтобы повторная
            // попытка была возможна после реального завершения флоу.
            isEndingShift = false
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
    /** reason: null = обычная пауза; "break:lunch" = Обед; "break:smoke" = Перекур. */
    fun pauseShift(reason: String? = null) {
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
            pauseSeconds = 0,
            pauseReason = reason
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

                pauseRepository.startPause(reason)
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
                            // FIX(2026-05-13) BELSI 2.0.1: race-condition fix.
                            // Явно ставим isPaused=true в onSuccess — если параллельный
                            // 60-sec polling успел перетереть optimistic isPaused в false
                            // (ответ GET pause/current пришёл раньше нашего POST commit-а),
                            // здесь финально восстанавливаем правильный state.
                            _uiState.value = currentActiveState.copy(
                                isPaused = true,
                                isIdle = false,
                                pauseStartTime = pauseStartMillis,
                                idleStartTime = null,
                                idleReason = null,
                                pauseReason = reason,
                            )
                            // Перезапуск таймера паузы — на случай если был остановлен self-heal
                            if (pauseTimerJob == null || pauseTimerJob?.isActive != true) {
                                startPauseTimer()
                            }
                        }
                    }
                    .onFailure { e ->
                        if (e is OfflineQueuedException) {
                            // FIX(2026-05-11) BELSI 2.0.0 build8: оффлайн-пауза — действие
                            // уже лежит в pending_actions, PendingSyncWorker отправит когда
                            // появится сеть. UI оставляем в "на паузе" — не откатываем,
                            // юзер видит мягкое сообщение, таймер продолжает идти локально.
                            android.util.Log.i("ShiftViewModel", "Pause queued offline — keeping optimistic UI")
                            setErrorWithAutoClear("📤 Пауза будет отправлена когда появится сеть")
                        } else {
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
                            // FIX(2026-05-13) BELSI 2.0.1: race-condition fix.
                            // Явно сбрасываем isPaused — параллельный polling мог
                            // ответить раньше POST commit-а и оставить isPaused=true.
                            _uiState.value = currentActiveState.copy(
                                isPaused = false,
                                pauseStartTime = null,
                                pauseSeconds = 0,
                                totalPauseSeconds = currentState.totalPauseSeconds + serverDuration
                            )
                            stopPauseTimer()
                        }
                    }
                    .onFailure { e ->
                        if (e is OfflineQueuedException) {
                            // FIX(2026-05-11) build8: оффлайн-возобновление — оставляем UI
                            // в "снято с паузы", action в очереди, сервер досчитает.
                            android.util.Log.i("ShiftViewModel", "Resume queued offline — keeping optimistic UI")
                            setErrorWithAutoClear("📤 Снятие паузы будет отправлено когда появится сеть")
                        } else {
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
                        if (e is OfflineQueuedException) {
                            // FIX(2026-05-11) build8: оффлайн-простой — переводим UI в idle
                            // локально (как если бы сервер ответил OK), action в очереди.
                            android.util.Log.i("ShiftViewModel", "Idle queued offline — applying optimistic UI")
                            val now = System.currentTimeMillis()
                            val st = _uiState.value
                            if (st is ShiftUiState.Active) {
                                _uiState.value = st.copy(
                                    isIdle = true,
                                    idleStartTime = now,
                                    idleSeconds = 0,
                                    idleReason = idleReason,
                                )
                                startIdleTimer()
                            }
                            setErrorWithAutoClear("📤 Простой «$idleReason» будет отправлен когда появится сеть")
                        } else {
                            android.util.Log.e("ShiftViewModel", "Failed to start idle on server", e)
                            setErrorWithAutoClear("Ошибка запуска простоя: ${e.message}")
                        }
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
                        if (e is OfflineQueuedException) {
                            // FIX(2026-05-11) build8: оффлайн-выход из простоя — гасим idle
                            // в UI, action в очереди, серверная длительность досчитается при синке.
                            android.util.Log.i("ShiftViewModel", "EndIdle queued offline — applying optimistic UI")
                            stopIdleTimer()
                            val st = _uiState.value
                            if (st is ShiftUiState.Active) {
                                _uiState.value = st.copy(
                                    isIdle = false,
                                    idleStartTime = null,
                                    idleSeconds = 0,
                                    totalIdleSeconds = currentState.totalIdleSeconds + currentState.idleSeconds,
                                    idleReason = null,
                                )
                            }
                            setErrorWithAutoClear("📤 Снятие простоя будет отправлено когда появится сеть")
                        } else {
                            android.util.Log.e("ShiftViewModel", "Failed to end idle on server", e)
                            setErrorWithAutoClear("Ошибка снятия простоя: ${e.message}")
                        }
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
            // P1-4: НЕ блокируем main-тред (бывший runBlocking → SupportSQLiteLock crash).
            // Таймер и так persист'ит состояние каждые 10с (см. startTimer), а elapsed
            // на restore пересчитывается из startTime — так что fire-and-forget здесь
            // безопасен и не теряет данные. teardownScope живёт независимо от ViewModel.
            teardownScope.launch {
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
        val idleReason: String? = null, // Причина простоя
        val pauseReason: String? = null // null = обычная пауза, "break:lunch" = Обед, "break:smoke" = Перекур
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
                // FIX(2026-05-22) P1-5 + бизнес-правило #3 (РЕШЕНО): простой ОПЛАЧИВАЕТСЯ.
                // Поэтому idle БОЛЬШЕ НЕ вычитается из оплачиваемого времени.
                // Оплачиваемое = общее − паузы (вкл. обед/перекур, всё неоплачиваемое).
                // idleSeconds/totalIdleSeconds по-прежнему показываются отдельно (дисциплина),
                // но в чистое рабочее время не входят как вычет.
                val net = elapsedSeconds - totalPauseSeconds - pauseSeconds
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
