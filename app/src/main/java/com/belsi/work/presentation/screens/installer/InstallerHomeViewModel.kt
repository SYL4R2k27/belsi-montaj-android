package com.belsi.work.presentation.screens.installer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.repositories.AuthRepository
import com.belsi.work.data.repositories.ObjectTree
import com.belsi.work.data.repositories.ObjectV3Repository
import com.belsi.work.presentation.theme.StageStatus
import com.belsi.work.presentation.theme.windowProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v3-данные объекта для главного экрана монтажника (структура этажей/окон/этапов).
 * Реальные данные через [ObjectV3Repository] (allowDemo=false по умолчанию — НИКАКИХ фейков).
 * Пока backend /v3 не задеплоен — tree будет пустым, секции просто не рендерятся.
 */
@HiltViewModel
class InstallerHomeViewModel @Inject constructor(
    private val repo: ObjectV3Repository,
    private val authRepository: AuthRepository,
    private val prefs: PrefsManager,
) : ViewModel() {

    private val _tree = MutableStateFlow<ObjectTree?>(null)
    val tree: StateFlow<ObjectTree?> = _tree.asStateFlow()

    /** Реальный список кабинетов текущего объекта для пикера «куда отнести фото». */
    private val _cabinets = MutableStateFlow<List<CabinetOption>>(emptyList())
    val cabinets: StateFlow<List<CabinetOption>> = _cabinets.asStateFlow()

    /** «Сейчас работаю» — текущий кабинет (по факту выбора/фото). */
    private val _currentCabinet = MutableStateFlow(prefs.getCurrentCabinetLabel())
    val currentCabinet: StateFlow<String?> = _currentCabinet.asStateFlow()

    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    private var lastObjectId: String? = null

    fun loadObject(objectId: String?) {
        if (objectId.isNullOrBlank() || objectId == lastObjectId) return
        lastObjectId = objectId
        viewModelScope.launch {
            repo.getObjectTree(objectId).onSuccess { tree ->
                _tree.value = tree
                // Камере нужен текущий объект для каскада «Куда отнести фото?».
                prefs.setCurrentObject(objectId)
                _cabinets.value = tree.floors.flatMap { f ->
                    f.zones.flatMap { z ->
                        z.cabinets.map { c ->
                            CabinetOption(
                                id = c.cabinet.id,
                                cabinetNumber = c.cabinet.cabinetNumber,
                                zoneCode = z.zone.zoneCode,
                                floorNumber = f.floor.floorNumber,
                                status = c.cabinet.status,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Установить «текущий кабинет» (по факту выбора при фото). Минимум нажатий:
     * либо выбор из реального списка (есть id → отметим «начат»), либо ручной ввод (только label).
     */
    fun setCurrentCabinet(cabinetId: String?, label: String) {
        prefs.setCurrentCabinet(cabinetId, label)
        _currentCabinet.value = label
        if (cabinetId != null) {
            viewModelScope.launch {
                repo.setCabinetStatus(cabinetId, "in_progress").onSuccess {
                    lastObjectId?.let { /* перезагрузим, чтобы цвета/прогресс обновились */ }
                }
                lastObjectId?.let { reloadTree(it) }
            }
        }
    }

    private suspend fun reloadTree(objectId: String) {
        repo.getObjectTree(objectId).onSuccess { _tree.value = it }
    }

    /** Полный logout (как в ProfileViewModel) → событие для навигации на экран входа. */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _logoutEvent.emit(Unit)
        }
    }
}

/** Опция кабинета для пикера «Сейчас работаю» (реальный список объекта). */
data class CabinetOption(
    val id: String,
    val cabinetNumber: String,
    val zoneCode: String,
    val floorNumber: Int,
    val status: String = "not_started",
) {
    val label: String get() = "Каб $cabinetNumber · ${floorNumber}эт · $zoneCode"
}

/** Сводка прогресса объекта по КАБИНЕТАМ (для карточки «Прогресс объекта»). */
data class ObjectProgress(
    val totalCabinets: Int,
    val doneCabinets: Int,
    val fraction: Float,
) {
    val hasData: Boolean get() = totalCabinets > 0
}

fun ObjectTree?.toProgress(): ObjectProgress {
    if (this == null) return ObjectProgress(0, 0, 0f)
    val cabinets = floors.flatMap { it.zones }.flatMap { it.cabinets }
    if (cabinets.isEmpty()) return ObjectProgress(0, 0, 0f)
    fun windowApproved(w: com.belsi.work.data.remote.dto.object_v3.WindowDto): Boolean =
        w.statusKarkas == "approved" && w.statusPodokonnik == "approved" && w.statusEkran == "approved"
    // Кабинет «готов» = все его окна полностью приняты (каркас+подоконник+экран).
    val done = cabinets.count { c -> c.windows.isNotEmpty() && c.windows.all { windowApproved(it) } }
    // Средний прогресс по кабинетам (среднее от средних по окнам кабинета).
    val avg = cabinets.map { c ->
        if (c.windows.isEmpty()) 0f
        else c.windows.map {
            windowProgress(
                StageStatus.fromDb(it.statusKarkas),
                StageStatus.fromDb(it.statusPodokonnik),
                StageStatus.fromDb(it.statusEkran),
            )
        }.average().toFloat()
    }.average().toFloat()
    return ObjectProgress(cabinets.size, done, avg)
}
