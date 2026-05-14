package com.belsi.work.presentation.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.tool_transfer.*
import com.belsi.work.data.repositories.ToolTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) build19 Этап3: ViewModels для tool-transfer экранов.
 *
 * Один файл — 3 ViewModel'а для 3 экранов:
 *  - ToolTransferCreateViewModel (комплектатор)
 *  - ToolTransferIncomingViewModel (приёмщик — список входящих)
 *  - ToolTransferDetailViewModel (детали + actions accept/reject/take/release)
 */

// ─────────────────────────────────────────────────────────────────────
// 1. CREATE — комплектатор формирует передачу
// ─────────────────────────────────────────────────────────────────────

data class CreateState(
    val warehouseInventory: List<InventoryItemDto> = emptyList(),
    val selectedToolIds: Set<String> = emptySet(),
    val quantities: Map<String, Int> = emptyMap(),  // tool_id → qty
    val toSiteObjectId: String? = null,
    val batchId: String? = null,
    val comment: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val createdTransferIds: List<String> = emptyList(),
)

@HiltViewModel
class ToolTransferCreateViewModel @Inject constructor(
    private val repo: ToolTransferRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateState())
    val state: StateFlow<CreateState> = _state.asStateFlow()

    fun setSiteObject(siteObjectId: String?) {
        _state.update { it.copy(toSiteObjectId = siteObjectId) }
    }

    fun setBatchId(batchId: String?) {
        _state.update { it.copy(batchId = batchId) }
    }

    fun setComment(text: String) {
        _state.update { it.copy(comment = text) }
    }

    fun loadWarehouse() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.getInventory(location = "warehouse").onSuccess { list ->
                _state.update { it.copy(warehouseInventory = list, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleToolSelected(toolId: String) {
        _state.update { s ->
            val newSet = s.selectedToolIds.toMutableSet()
            val newQty = s.quantities.toMutableMap()
            if (toolId in newSet) {
                newSet.remove(toolId)
                newQty.remove(toolId)
            } else {
                newSet.add(toolId)
                val avail = s.warehouseInventory.firstOrNull { it.toolId == toolId }?.quantity ?: 1
                newQty[toolId] = minOf(1, avail)
            }
            s.copy(selectedToolIds = newSet, quantities = newQty)
        }
    }

    fun setQuantity(toolId: String, qty: Int) {
        _state.update { s ->
            val avail = s.warehouseInventory.firstOrNull { it.toolId == toolId }?.quantity ?: 1
            s.copy(quantities = s.quantities + (toolId to qty.coerceIn(1, avail)))
        }
    }

    fun submit(onSuccess: (List<String>) -> Unit) {
        val s = _state.value
        val sid = s.toSiteObjectId
        if (sid.isNullOrBlank()) {
            _state.update { it.copy(error = "Не выбран объект-получатель") }
            return
        }
        if (s.selectedToolIds.isEmpty()) {
            _state.update { it.copy(error = "Не выбран ни один инструмент") }
            return
        }
        val items = s.selectedToolIds.map {
            TransferItemIn(toolId = it, quantity = s.quantities[it] ?: 1)
        }
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, error = null) }
            repo.createBulk(BulkTransferRequest(
                toSiteObjectId = sid,
                items = items,
                batchId = s.batchId,
                comment = s.comment.takeIf { it.isNotBlank() },
            )).onSuccess { resp ->
                _state.update { it.copy(isSending = false, createdTransferIds = resp.transferIds) }
                onSuccess(resp.transferIds)
            }.onFailure { e ->
                _state.update { it.copy(isSending = false, error = e.message) }
            }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}

// ─────────────────────────────────────────────────────────────────────
// 2. INCOMING — приёмщик видит что везут
// ─────────────────────────────────────────────────────────────────────

data class IncomingState(
    val transfers: List<ToolTransferDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ToolTransferIncomingViewModel @Inject constructor(
    private val repo: ToolTransferRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(IncomingState())
    val state: StateFlow<IncomingState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.listIncoming().onSuccess { list ->
                _state.update { it.copy(transfers = list, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 3. DETAIL — детали + actions
// ─────────────────────────────────────────────────────────────────────

data class DetailState(
    val transfer: ToolTransferDto? = null,
    val isLoading: Boolean = false,
    val isActing: Boolean = false,
    val error: String? = null,
    val justChanged: Boolean = false,  // флажок для UI snackbar после действия
)

@HiltViewModel
class ToolTransferDetailViewModel @Inject constructor(
    private val repo: ToolTransferRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun load(transferId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.getTransfer(transferId).onSuccess { t ->
                _state.update { it.copy(transfer = t, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun accept(transferId: String, actualQuantity: Int? = null, comment: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null, justChanged = false) }
            repo.accept(transferId, actualQuantity, photoUrl = null, comment = comment)
                .onSuccess {
                    _state.update { it.copy(isActing = false, justChanged = true) }
                    load(transferId)
                }
                .onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun reject(transferId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null, justChanged = false) }
            repo.reject(transferId, reason).onSuccess {
                _state.update { it.copy(isActing = false, justChanged = true) }
                load(transferId)
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun take(transferId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.take(transferId).onSuccess {
                _state.update { it.copy(isActing = false, justChanged = true) }
                load(transferId)
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun release(transferId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.release(transferId).onSuccess {
                _state.update { it.copy(isActing = false, justChanged = true) }
                load(transferId)
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun dispatch(transferId: String, driverUserId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.dispatch(transferId, driverUserId).onSuccess {
                _state.update { it.copy(isActing = false, justChanged = true) }
                load(transferId)
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    // ─── RETURN FLOW (FIX 2026-05-14 BELSI 2.0.1) ──────────────────

    fun returnRequest(transferId: String, reason: String?, photoUrl: String? = null,
                      onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.returnRequest(transferId, reason, photoUrl).onSuccess {
                _state.update { it.copy(isActing = false, justChanged = true) }
                load(transferId)
                onSuccess()
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun returnPickup(transferId: String, photoUrl: String, comment: String? = null,
                     onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.returnPickup(transferId, photoUrl, comment).onSuccess {
                _state.update { it.copy(isActing = false, justChanged = true) }
                load(transferId)
                onSuccess()
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun returnDeliver(transferId: String, photoUrl: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.returnDeliver(transferId, photoUrl).onSuccess {
                _state.update { it.copy(isActing = false, justChanged = true) }
                load(transferId)
                onSuccess()
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun returnAccept(transferId: String, acceptedQuantity: Int? = null,
                     photoUrl: String? = null, comment: String? = null,
                     onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.returnAccept(transferId, acceptedQuantity, photoUrl, comment).onSuccess {
                _state.update { it.copy(isActing = false, justChanged = true) }
                load(transferId)
                onSuccess()
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun returnReject(transferId: String, reason: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.returnReject(transferId, reason).onSuccess {
                _state.update { it.copy(isActing = false, justChanged = true) }
                load(transferId)
                onSuccess()
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun returnAssignDriver(transferId: String, driverUserId: String, routeId: String? = null,
                           onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.returnAssignDriver(transferId, driverUserId, routeId).onSuccess {
                _state.update { it.copy(isActing = false, justChanged = true) }
                load(transferId)
                onSuccess()
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun clearError() { _state.update { it.copy(error = null, justChanged = false) } }
}

// ─────────────────────────────────────────────────────────────────────
// 4. CURATOR RETURNS — overview всех активных возвратов
// FIX(2026-05-14) BELSI 2.0.1
// ─────────────────────────────────────────────────────────────────────

data class CuratorReturnsState(
    val transfers: List<ToolTransferDto> = emptyList(),
    val isLoading: Boolean = false,
    val isActing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CuratorReturnsViewModel @Inject constructor(
    private val repo: ToolTransferRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CuratorReturnsState())
    val state: StateFlow<CuratorReturnsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.curatorInReturn().onSuccess { list ->
                _state.update { it.copy(transfers = list, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun resolveRejection(transferId: String, action: String, comment: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.curatorResolveRejection(transferId, action, comment).onSuccess {
                _state.update { it.copy(isActing = false) }
                load()
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun cancelReturn(transferId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, error = null) }
            repo.curatorCancelReturn(transferId, reason).onSuccess {
                _state.update { it.copy(isActing = false) }
                load()
            }.onFailure { e -> _state.update { it.copy(isActing = false, error = e.message) } }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
