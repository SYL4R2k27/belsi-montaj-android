package com.belsi.work.presentation.screens.siteobject

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.ActiveRoleManager
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.repositories.ObjectTree
import com.belsi.work.data.repositories.ObjectV3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ObjectUiState(
    val loading: Boolean = true,
    val tree: ObjectTree? = null,
    val error: String? = null,
    val selectedTab: Int = 1, // по умолчанию «Этажи»
)

/**
 * ViewModel универсального ObjectScreen (модель v3).
 * objectId берётся из nav-аргумента через SavedStateHandle.
 */
@HiltViewModel
class ObjectViewModel @Inject constructor(
    private val repo: ObjectV3Repository,
    activeRoleManager: ActiveRoleManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val objectId: String = savedStateHandle.get<String>("objectId") ?: ""

    /** Активная роль — для гейта куратор/координатор «Принять кабинет → начислить» (Ф2). */
    val activeRole: StateFlow<UserRole?> =
        activeRoleManager.activeRole.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ui = MutableStateFlow(ObjectUiState())
    val ui: StateFlow<ObjectUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repo.getObjectTree(objectId, allowDemo = true)
                .onSuccess { tree -> _ui.update { it.copy(loading = false, tree = tree) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message ?: "Ошибка") } }
        }
    }

    fun selectTab(index: Int) {
        _ui.update { it.copy(selectedTab = index) }
    }

    /** Установить статус кабинета (любая роль) + перезагрузить дерево. */
    fun setCabinetStatus(cabinetId: String, status: String, comment: String? = null) {
        viewModelScope.launch {
            repo.setCabinetStatus(cabinetId, status, comment).onSuccess { load() }
        }
    }
}
