package com.belsi.work.presentation.screens.toolkits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.tool_kit.*
import com.belsi.work.data.repositories.ToolKitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-18) BELSI 2.0.1: ViewModels для tool-kits / Тележка.
 * Все 4 экрана (List / Detail / Dispatch / Batch) в одном файле — оптимизация
 * как у ToolTransferViewModels.kt.
 */

// ═══════════════════════════════════════════════════════════════════
// 1. List screen — список kit-шаблонов
// ═══════════════════════════════════════════════════════════════════

data class ToolKitListUiState(
    val kits: List<ToolKitDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ToolKitListViewModel @Inject constructor(
    private val repo: ToolKitRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ToolKitListUiState())
    val state: StateFlow<ToolKitListUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.listKits(active = true)
                .onSuccess { kits -> _state.update { it.copy(loading = false, kits = kits) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 2. Detail screen — детали kit'а (60 items с группировкой по секциям)
// ═══════════════════════════════════════════════════════════════════

data class ToolKitDetailUiState(
    val kit: ToolKitDto? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ToolKitDetailViewModel @Inject constructor(
    private val repo: ToolKitRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val kitId: String = checkNotNull(savedState["kitId"])

    private val _state = MutableStateFlow(ToolKitDetailUiState())
    val state: StateFlow<ToolKitDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.getKit(kitId)
                .onSuccess { kit -> _state.update { it.copy(loading = false, kit = kit) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    /** Delete (soft) — для curator/coordinator. */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            repo.deleteKit(kitId)
                .onSuccess { onDone() }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    /** Patch — обновить header (название, описание, размер команды, active). */
    fun patch(name: String?, description: String?, teamSize: Int?, active: Boolean?) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            repo.patchKit(kitId, ToolKitPatchRequest(name, description, teamSize, active))
                .onSuccess { kit -> _state.update { it.copy(loading = false, kit = kit) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    /** Add item — для items editor. */
    fun addItem(req: ToolKitItemCreateRequest, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.addItem(kitId, req)
                .onSuccess { onDone(); refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    /** Delete item. */
    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            repo.deleteItem(kitId, itemId)
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 3. Dispatch screen — supplier выдаёт kit
// ═══════════════════════════════════════════════════════════════════

data class ToolKitDispatchUiState(
    val kit: ToolKitDto? = null,
    val loading: Boolean = false,
    val dispatching: Boolean = false,
    val error: String? = null,
    val success: KitDispatchResponse? = null,

    // Form fields
    val toSiteObjectId: String? = null,
    val siteObjectLabel: String? = null,
    val driverUserId: String? = null,
    val driverLabel: String? = null,
    val teamCount: Int = 1,
    val excludedItemIds: Set<String> = emptySet(),
    val quantityOverrides: Map<String, Int> = emptyMap(),

    // Phase 3: inventory pre-check warnings (auto-refresh при изменении формы)
    val preview: DispatchPreviewResponse? = null,
    val previewLoading: Boolean = false,
)

@HiltViewModel
class ToolKitDispatchViewModel @Inject constructor(
    private val repo: ToolKitRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val kitId: String = checkNotNull(savedState["kitId"])

    private val _state = MutableStateFlow(ToolKitDispatchUiState())
    val state: StateFlow<ToolKitDispatchUiState> = _state.asStateFlow()

    init { loadKit() }

    private fun loadKit() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.getKit(kitId)
                .onSuccess { kit ->
                    _state.update {
                        it.copy(
                            loading = false,
                            kit = kit,
                            teamCount = kit.teamSize.coerceIn(1, 20),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun setSiteObject(id: String, label: String) {
        _state.update { it.copy(toSiteObjectId = id, siteObjectLabel = label) }
        refreshPreview()
    }
    fun setDriver(id: String, label: String) {
        _state.update { it.copy(driverUserId = id, driverLabel = label) }
        // preview не зависит от driver, но всё равно рефрешим (не дорого)
    }
    fun setTeamCount(n: Int) {
        _state.update { it.copy(teamCount = n.coerceIn(1, 20)) }
        refreshPreview()
    }

    fun toggleItemExcluded(itemId: String) {
        _state.update { s ->
            val ex = s.excludedItemIds.toMutableSet()
            if (itemId in ex) ex.remove(itemId) else ex.add(itemId)
            s.copy(excludedItemIds = ex)
        }
        refreshPreview()
    }

    fun setQuantityOverride(itemId: String, qty: Int?) {
        _state.update { s ->
            val map = s.quantityOverrides.toMutableMap()
            if (qty == null) map.remove(itemId) else map[itemId] = qty.coerceAtLeast(1)
            s.copy(quantityOverrides = map)
        }
        refreshPreview()
    }

    private var previewJob: kotlinx.coroutines.Job? = null
    /** Pre-check naличия инструментов — debounced 400ms. */
    private fun refreshPreview() {
        val s = _state.value
        val site = s.toSiteObjectId ?: return
        val driver = s.driverUserId ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            _state.update { it.copy(previewLoading = true) }
            repo.dispatchPreview(kitId, KitDispatchRequest(
                toSiteObjectId = site,
                driverUserId = driver,
                teamCount = s.teamCount,
                excludedItemIds = s.excludedItemIds.toList().takeIf { it.isNotEmpty() },
                quantityOverrides = s.quantityOverrides.takeIf { it.isNotEmpty() },
            ))
                .onSuccess { p -> _state.update { it.copy(previewLoading = false, preview = p) } }
                .onFailure { _ -> _state.update { it.copy(previewLoading = false) } }
        }
    }

    fun dispatch() {
        val s = _state.value
        val site = s.toSiteObjectId
        val driver = s.driverUserId
        if (site.isNullOrBlank() || driver.isNullOrBlank()) {
            _state.update { it.copy(error = "Заполните объект и водителя") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(dispatching = true, error = null) }
            repo.dispatchKit(
                kitId = kitId,
                body = KitDispatchRequest(
                    toSiteObjectId = site,
                    driverUserId = driver,
                    teamCount = s.teamCount,
                    excludedItemIds = s.excludedItemIds.toList().takeIf { it.isNotEmpty() },
                    quantityOverrides = s.quantityOverrides.takeIf { it.isNotEmpty() },
                ),
            )
                .onSuccess { resp -> _state.update { it.copy(dispatching = false, success = resp) } }
                .onFailure { e -> _state.update { it.copy(dispatching = false, error = e.message) } }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}

// ═══════════════════════════════════════════════════════════════════
// 4. Batch screen — driver/receiver видит kit_batch
// ═══════════════════════════════════════════════════════════════════

data class ToolKitBatchUiState(
    val batch: BatchViewDto? = null,
    val loading: Boolean = false,
    val processing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    // Partial-accept режим: bridge selectedItemIds для accept выборки.
    val partialAcceptMode: Boolean = false,
    val selectedAcceptIds: Set<String> = emptySet(),
    // Photo URL (если сделали снимок перед действием)
    val pendingPhotoUrl: String? = null,
)

@HiltViewModel
class ToolKitBatchViewModel @Inject constructor(
    private val repo: ToolKitRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val batchId: String = checkNotNull(savedState["batchId"])

    private val _state = MutableStateFlow(ToolKitBatchUiState())
    val state: StateFlow<ToolKitBatchUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.getBatch(batchId)
                .onSuccess { b -> _state.update { it.copy(loading = false, batch = b) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun pickup() = bulkOp { repo.batchPickup(batchId) }
    fun deliver() = bulkOp { repo.batchDeliver(batchId) }
    fun acceptAll() = bulkOp {
        repo.batchAccept(batchId, photoUrl = _state.value.pendingPhotoUrl)
    }
    fun acceptSelected() = bulkOp {
        val ids = _state.value.selectedAcceptIds.toList()
        if (ids.isEmpty()) Result.failure(IllegalStateException("Не выбрано ни одной позиции"))
        else repo.batchAccept(
            batchId,
            acceptedItemIds = ids,
            photoUrl = _state.value.pendingPhotoUrl,
        )
    }

    fun togglePartialMode() {
        _state.update {
            it.copy(
                partialAcceptMode = !it.partialAcceptMode,
                selectedAcceptIds = if (!it.partialAcceptMode) emptySet() else it.selectedAcceptIds,
            )
        }
    }

    fun toggleSelected(itemId: String) {
        _state.update { s ->
            val sel = s.selectedAcceptIds.toMutableSet()
            if (itemId in sel) sel.remove(itemId) else sel.add(itemId)
            s.copy(selectedAcceptIds = sel)
        }
    }

    fun selectAllDelivered() {
        val ids = _state.value.batch?.sections.orEmpty()
            .values.flatten()
            .filter { it.status == "delivered" }
            .map { it.id }
            .toSet()
        _state.update { it.copy(selectedAcceptIds = ids) }
    }

    fun clearSelection() { _state.update { it.copy(selectedAcceptIds = emptySet()) } }

    fun setPendingPhoto(url: String?) { _state.update { it.copy(pendingPhotoUrl = url) } }

    private fun bulkOp(call: suspend () -> Result<BulkOpResponse>) {
        viewModelScope.launch {
            _state.update { it.copy(processing = true, error = null) }
            call()
                .onSuccess { r ->
                    _state.update {
                        it.copy(
                            processing = false,
                            message = "${r.updated} → ${r.status}",
                            pendingPhotoUrl = null,
                            partialAcceptMode = false,
                            selectedAcceptIds = emptySet(),
                        )
                    }
                    refresh()
                }
                .onFailure { e -> _state.update { it.copy(processing = false, error = e.message) } }
        }
    }

    fun clearMessage() { _state.update { it.copy(message = null, error = null) } }
}

// ═══════════════════════════════════════════════════════════════════
// 5. Create kit screen (curator+) — простой ViewModel
// ═══════════════════════════════════════════════════════════════════

data class ToolKitCreateUiState(
    val code: String = "",
    val name: String = "",
    val description: String = "",
    val teamSize: Int = 2,
    val creating: Boolean = false,
    val createdKitId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ToolKitCreateViewModel @Inject constructor(
    private val repo: ToolKitRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ToolKitCreateUiState())
    val state: StateFlow<ToolKitCreateUiState> = _state.asStateFlow()

    fun setCode(c: String) { _state.update { it.copy(code = c) } }
    fun setName(n: String) { _state.update { it.copy(name = n) } }
    fun setDescription(d: String) { _state.update { it.copy(description = d) } }
    fun setTeamSize(t: Int) { _state.update { it.copy(teamSize = t.coerceIn(1, 20)) } }

    fun create() {
        val s = _state.value
        if (s.code.isBlank() || s.name.isBlank()) {
            _state.update { it.copy(error = "Заполните code и name") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            repo.createKit(ToolKitCreateRequest(
                code = s.code.trim(),
                name = s.name.trim(),
                description = s.description.trim().ifBlank { null },
                teamSize = s.teamSize,
            ))
                .onSuccess { kit -> _state.update { it.copy(creating = false, createdKitId = kit.id) } }
                .onFailure { e -> _state.update { it.copy(creating = false, error = e.message) } }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
