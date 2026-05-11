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
    val brigade: Brigade? = null,
    val members: List<BrigadeMember> = emptyList(),
    val error: String? = null,
)

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

            val brigadeRes = productionRepo.getMyBrigade()
            val brigade = brigadeRes.getOrNull()

            val members = if (brigade != null) {
                productionRepo.getBrigadeMembers(brigade.id).getOrNull() ?: emptyList()
            } else emptyList()

            _state.value = SeniorWorkerUiState(
                loading = false,
                brigade = brigade,
                members = members,
                error = brigadeRes.exceptionOrNull()?.message,
            )
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

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            val facilities = productionRepo.listFacilities().getOrNull() ?: emptyList()
            val selected = facilities.firstOrNull()

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

    fun createOrderForLowStock(item: InventoryItem) {
        viewModelScope.launch {
            val needed = (item.minStock - item.quantity).coerceAtLeast(item.minStock)
            productionRepo.createOrder(
                facilityId = item.facilityId,
                materialId = item.materialId,
                quantity = needed,
                note = "Авто-заявка (низкий остаток)",
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

            val myTasks = productionRepo.getEngineerTasks(mine = true).getOrNull() ?: emptyList()
            val openTasks = productionRepo.getEngineerTasks(status = "open").getOrNull() ?: emptyList()

            _state.value = EngineerUiState(
                loading = false,
                myTasks = myTasks,
                openTasks = openTasks.filter { it.assignedTo == null },
            )
        }
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
}
