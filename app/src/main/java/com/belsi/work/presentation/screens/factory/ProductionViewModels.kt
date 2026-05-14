package com.belsi.work.presentation.screens.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.*
import com.belsi.work.data.repositories.BatchRepository
import com.belsi.work.data.repositories.ProductionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-06): ViewModels для 4 ролей производства (variant C).
 *
 * Все 4 ViewModel работают по одному паттерну:
 *   - StateFlow с UI state (Loading / Success / Error)
 *   - load() — пуллит данные из ProductionRepository
 *   - На любом экране есть pull-to-refresh
 */

// ─────────────────────────────────────────────────────────────────
// SeniorWorker — Старший работник (бригадир производства)
// ─────────────────────────────────────────────────────────────────

data class SeniorWorkerUiState(
    val loading: Boolean = false,
    // FIX(2026-05-14) BELSI 2.0.1: senior может держать до 15 бригад
    val brigades: List<Brigade> = emptyList(),
    val selectedBrigadeId: String? = null,
    val members: List<BrigadeMember> = emptyList(),
    val error: String? = null,
) {
    /** Текущая выбранная бригада (или первая если selectedBrigadeId не задан). */
    val selectedBrigade: Brigade?
        get() = brigades.firstOrNull { it.id == selectedBrigadeId } ?: brigades.firstOrNull()

    /** Legacy alias для совместимости — первая бригада. */
    @Deprecated("Use selectedBrigade or brigades", ReplaceWith("selectedBrigade"))
    val brigade: Brigade? get() = selectedBrigade
}

@HiltViewModel
class SeniorWorkerViewModel @Inject constructor(
    private val productionRepo: ProductionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeniorWorkerUiState())
    val state: StateFlow<SeniorWorkerUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            val brigadesRes = productionRepo.getMyBrigades()
            val brigades = brigadesRes.getOrNull() ?: emptyList()
            val firstId = brigades.firstOrNull()?.id

            val members = if (firstId != null) {
                productionRepo.getBrigadeMembers(firstId).getOrNull() ?: emptyList()
            } else emptyList()

            _state.value = SeniorWorkerUiState(
                loading = false,
                brigades = brigades,
                selectedBrigadeId = firstId,
                members = members,
                error = brigadesRes.exceptionOrNull()?.message,
            )
        }
    }

    /** Переключение между бригадами (если у senior несколько). */
    fun selectBrigade(brigadeId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedBrigadeId = brigadeId,
                loading = true,
            )
            val members = productionRepo.getBrigadeMembers(brigadeId).getOrNull() ?: emptyList()
            _state.value = _state.value.copy(loading = false, members = members)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// ProductionChief — Начальник производства
// ─────────────────────────────────────────────────────────────────

data class ProductionChiefUiState(
    val loading: Boolean = false,
    val facilities: List<Facility> = emptyList(),
    val selectedFacility: Facility? = null,
    val dashboard: FacilityDashboard? = null,
    val brigades: List<Brigade> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ProductionChiefViewModel @Inject constructor(
    private val productionRepo: ProductionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProductionChiefUiState())
    val state: StateFlow<ProductionChiefUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            val facilities = productionRepo.listFacilities().getOrNull() ?: emptyList()
            val selected = facilities.firstOrNull()

            val dashboard = selected?.let {
                productionRepo.getFacilityDashboard(it.id).getOrNull()
            }
            val brigades = selected?.let {
                productionRepo.listBrigades(it.id).getOrNull() ?: emptyList()
            } ?: emptyList()

            _state.value = ProductionChiefUiState(
                loading = false,
                facilities = facilities,
                selectedFacility = selected,
                dashboard = dashboard,
                brigades = brigades,
            )
        }
    }

    fun selectFacility(facility: Facility) {
        viewModelScope.launch {
            _state.value = _state.value.copy(selectedFacility = facility, loading = true)
            val dashboard = productionRepo.getFacilityDashboard(facility.id).getOrNull()
            val brigades = productionRepo.listBrigades(facility.id).getOrNull() ?: emptyList()
            _state.value = _state.value.copy(
                loading = false,
                dashboard = dashboard,
                brigades = brigades,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Supplier — Снабженец
// ─────────────────────────────────────────────────────────────────

data class SupplierUiState(
    val loading: Boolean = false,
    val facilities: List<Facility> = emptyList(),
    val selectedFacility: Facility? = null,
    val pendingOrders: List<MaterialOrder> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val lowStockCount: Int = 0,
    val error: String? = null,
)

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val productionRepo: ProductionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SupplierUiState())
    val state: StateFlow<SupplierUiState> = _state.asStateFlow()

    init { load() }

    /**
     * Полная перезагрузка — список фабрик + первая по умолчанию.
     * FIX(2026-05-14) BELSI 2.0.1: сохраняем selectedFacility если он есть и валиден.
     */
    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            val facilities = productionRepo.listFacilities().getOrNull() ?: emptyList()
            val prevSelectedId = _state.value.selectedFacility?.id
            val selected = facilities.firstOrNull { it.id == prevSelectedId } ?: facilities.firstOrNull()

            loadForFacility(facilities, selected)
        }
    }

    /**
     * FIX(2026-05-14) BELSI 2.0.1: реактивность на смену фабрики.
     * Раньше SupplierViewModel.init не подписан на изменение selectedFacility.
     * Теперь — явный метод который обновляет orders+inventory без полного перезапроса фабрик.
     */
    fun selectFacility(facility: Facility) {
        viewModelScope.launch {
            _state.value = _state.value.copy(selectedFacility = facility, loading = true)
            loadForFacility(_state.value.facilities, facility)
        }
    }

    private suspend fun loadForFacility(facilities: List<Facility>, selected: Facility?) {
        val orders = productionRepo.listOrders(
            facilityId = selected?.id,
            status = "pending",
            mine = true,
        ).getOrNull() ?: emptyList()

        val inventory = selected?.let {
            productionRepo.getInventory(it.id, onlyLow = false).getOrNull() ?: emptyList()
        } ?: emptyList()

        _state.value = SupplierUiState(
            loading = false,
            facilities = facilities,
            selectedFacility = selected,
            pendingOrders = orders,
            inventory = inventory,
            lowStockCount = inventory.count { it.isLow },
        )
    }

    fun approveOrder(orderId: String) {
        viewModelScope.launch {
            productionRepo.updateOrderStatus(orderId, "ordered").onSuccess { load() }
        }
    }

    fun deliverOrder(orderId: String, quantity: Int) {
        viewModelScope.launch {
            productionRepo.updateOrderStatus(orderId, "delivered", quantity).onSuccess { load() }
        }
    }

    /**
     * FIX(2026-05-14) BELSI 2.0.1: правильная формула «сколько заказать при низком запасе».
     *
     * Раньше: needed = (minStock - quantity).coerceAtLeast(minStock)
     *   Пример: minStock=10, quantity=8 → needed = max(2, 10) = 10 ← но у нас уже 8!
     *   Это слишком много — после поставки будет 18 (180% от minStock).
     *
     * Теперь: формула «довести до целевого буфера». Целевой буфер = minStock × bufferMultiplier
     * (по умолчанию ×2). Заказываем точно столько чтобы после поставки получился буфер.
     *   Пример: minStock=10, quantity=8, multiplier=2.0 → target=20, needed=20-8=12.
     *
     * bufferMultiplier параметризован — если у фабрики есть свой множитель в settings,
     * можно его подменить. По умолчанию ×2.0.
     */
    fun createOrderForLowStock(item: InventoryItem, bufferMultiplier: Double = 2.0) {
        viewModelScope.launch {
            val target = (item.minStock * bufferMultiplier).toInt().coerceAtLeast(item.minStock + 1)
            val needed = (target - item.quantity).coerceAtLeast(1)
            productionRepo.createOrder(
                facilityId = item.facilityId,
                materialId = item.materialId,
                quantity = needed,
                note = "Авто-заявка (низкий остаток, цель ${target} ед.)",
            ).onSuccess { load() }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Engineer — Инженер
// ─────────────────────────────────────────────────────────────────

data class EngineerUiState(
    val loading: Boolean = false,
    val myTasks: List<EngineerTask> = emptyList(),
    val openTasks: List<EngineerTask> = emptyList(),
    // FIX(2026-05-14) BELSI 2.0.1: facility scope + engineers list
    val facilities: List<Facility> = emptyList(),
    val selectedFacility: Facility? = null,
    val engineers: List<EngineerPickItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class EngineerViewModel @Inject constructor(
    private val productionRepo: ProductionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EngineerUiState())
    val state: StateFlow<EngineerUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            // FIX(2026-05-14) BELSI 2.0.1: подгружаем фабрики + инженеров для UI выбора
            val facilities = productionRepo.listFacilities().getOrNull() ?: emptyList()
            val prevSelectedId = _state.value.selectedFacility?.id
            val selected = facilities.firstOrNull { it.id == prevSelectedId } ?: facilities.firstOrNull()
            val engineers = productionRepo.listEngineers(selected?.id).getOrNull() ?: emptyList()

            loadTasksForFacility(facilities, selected, engineers)
        }
    }

    fun selectFacility(facility: Facility?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(selectedFacility = facility, loading = true)
            val engineers = productionRepo.listEngineers(facility?.id).getOrNull() ?: emptyList()
            loadTasksForFacility(_state.value.facilities, facility, engineers)
        }
    }

    private suspend fun loadTasksForFacility(
        facilities: List<Facility>,
        selected: Facility?,
        engineers: List<EngineerPickItem>,
    ) {
        val facilityId = selected?.id
        val myTasks = productionRepo.getEngineerTasks(mine = true, facilityId = facilityId)
            .getOrNull() ?: emptyList()
        val openTasks = productionRepo.getEngineerTasks(status = "open", facilityId = facilityId)
            .getOrNull() ?: emptyList()

        _state.value = EngineerUiState(
            loading = false,
            myTasks = myTasks,
            openTasks = openTasks.filter { it.assignedTo == null },
            facilities = facilities,
            selectedFacility = selected,
            engineers = engineers,
        )
    }

    fun startTask(taskId: String) {
        viewModelScope.launch {
            productionRepo.updateEngineerTaskStatus(taskId, "in_progress").onSuccess { load() }
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            productionRepo.updateEngineerTaskStatus(taskId, "done").onSuccess { load() }
        }
    }

    /** FIX(2026-05-14) BELSI 2.0.1: передача задачи другому инженеру. */
    fun reassignTask(taskId: String, newAssigneeId: String, comment: String? = null) {
        viewModelScope.launch {
            productionRepo.reassignEngineerTask(taskId, newAssigneeId, comment)
                .onSuccess { load() }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
        }
    }
}
