package com.belsi.work.presentation.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.remote.dto.tool_transfer.InventoryItemDto
import com.belsi.work.data.remote.dto.tool_transfer.ToolTransferDto
import com.belsi.work.data.repositories.ToolTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) build19 Этап3+: универсальный hub для tool-transfer pipeline.
 *
 * 3 таба:
 *   1. Входящие — то что мне везут (или куратор видит всё)
 *   2. Отправленные — то что я отправил (только для supplier/curator/coordinator/chief)
 *   3. Склад — текущий inventory (warehouse | site | in_transit фильтр)
 *
 * FAB «Сформировать передачу» — показывается только если роль может:
 *   supplier / curator / coordinator / production_chief.
 */
data class HubState(
    val incoming: List<ToolTransferDto> = emptyList(),
    val outgoing: List<ToolTransferDto> = emptyList(),
    val inventory: List<InventoryItemDto> = emptyList(),
    val inventoryFilter: String = "all",   // all | warehouse | site | in_transit
    val isLoadingIncoming: Boolean = false,
    val isLoadingOutgoing: Boolean = false,
    val isLoadingInventory: Boolean = false,
    val error: String? = null,
    val canCreate: Boolean = false,
    val canSeeOutgoing: Boolean = false,
)

@HiltViewModel
class ToolTransferHubViewModel @Inject constructor(
    private val repo: ToolTransferRepository,
    private val prefs: PrefsManager,
) : ViewModel() {

    private val _state = MutableStateFlow(HubState())
    val state: StateFlow<HubState> = _state.asStateFlow()

    init {
        val role = prefs.getUser()?.role
        val canCreate = role in listOf(
            UserRole.CURATOR,
            UserRole.COORDINATOR,
            UserRole.SUPPLIER,
            UserRole.PRODUCTION_CHIEF,
        )
        _state.update { it.copy(canCreate = canCreate, canSeeOutgoing = canCreate) }
        loadIncoming()
        if (canCreate) loadOutgoing()
        loadInventory()
    }

    fun loadIncoming() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingIncoming = true, error = null) }
            repo.listIncoming().onSuccess { list ->
                _state.update { it.copy(incoming = list, isLoadingIncoming = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoadingIncoming = false, error = e.message) }
            }
        }
    }

    fun loadOutgoing() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingOutgoing = true, error = null) }
            repo.listOutgoing().onSuccess { list ->
                _state.update { it.copy(outgoing = list, isLoadingOutgoing = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoadingOutgoing = false, error = e.message) }
            }
        }
    }

    fun loadInventory(filter: String = _state.value.inventoryFilter) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingInventory = true, error = null, inventoryFilter = filter) }
            val loc = if (filter == "all") null else filter
            repo.getInventory(location = loc).onSuccess { list ->
                _state.update { it.copy(inventory = list, isLoadingInventory = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoadingInventory = false, error = e.message) }
            }
        }
    }

    fun refreshAll() {
        loadIncoming()
        if (state.value.canSeeOutgoing) loadOutgoing()
        loadInventory()
    }
}
