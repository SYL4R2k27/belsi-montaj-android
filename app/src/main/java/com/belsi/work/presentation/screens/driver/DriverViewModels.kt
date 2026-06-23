package com.belsi.work.presentation.screens.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.driver.DriverDashboardDto
import com.belsi.work.data.remote.dto.driver.DriverFleetItemDto
import com.belsi.work.data.remote.dto.driver.DeliveryRequestOutDto
import com.belsi.work.data.remote.dto.driver.LogistDashboardDto
import com.belsi.work.data.remote.dto.driver.RouteOutDto
import com.belsi.work.data.remote.dto.driver.RoutePointOutDto
import com.belsi.work.data.remote.dto.tool_transfer.ToolTransferDto
import com.belsi.work.data.repositories.DriverRepository
import com.belsi.work.data.repositories.LogistRepository
import com.belsi.work.data.repositories.ToolTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-11) BELSI 2.0.0: ViewModels для driver + logistician.
 *
 * Раньше эти экраны работали полностью на DriverMockData (object). Теперь все
 * данные подтягиваются с backend через DriverRepository/LogistRepository.
 * MockData остался только как fallback для preview / role-switcher demo
 * (когда у юзера нет реально назначенных маршрутов).
 */

// ═══════════ DRIVER ═══════════

data class DriverHomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val dashboard: DriverDashboardDto? = null,
    val activeRoutes: List<RouteOutDto> = emptyList(),
    val history: List<RouteOutDto> = emptyList(),
    // FIX(2026-05-18) BELSI 2.0.1 build7: backend двух-стадийный flow
    // (supplier dispatch → driver pickup → deliver → receiver accept).
    // dispatched: водитель ещё не забрал у комплектатора
    // in_transit: уже забрал, везёт на объект
    val incomingTransfers: List<ToolTransferDto> = emptyList(),
    val mutatingTransferId: String? = null,
)

@HiltViewModel
class DriverHomeViewModel @Inject constructor(
    private val repo: DriverRepository,
    private val transferRepo: ToolTransferRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DriverHomeUiState())
    val state: StateFlow<DriverHomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val dash = repo.dashboard().getOrNull()
            val today = repo.routesToday().getOrNull().orEmpty()
            val hist = repo.history(7).getOrNull().orEmpty()
            // FIX(2026-05-18): pull incoming tool_transfers (статусы dispatched / in_transit).
            // Endpoint /tools/transfers/incoming для driver роли возвращает transfers
            // где driver_user_id = current user.id.
            val transfers = transferRepo.listIncoming().getOrNull().orEmpty()
                .filter { it.status in listOf("dispatched", "in_transit") }

            _state.update {
                it.copy(
                    isLoading = false,
                    dashboard = dash,
                    activeRoutes = today,
                    history = hist,
                    incomingTransfers = transfers,
                )
            }
        }
    }

    /** dispatched → in_transit. Водитель забрал у комплектатора. */
    fun pickupTransfer(transferId: String) {
        viewModelScope.launch {
            _state.update { it.copy(mutatingTransferId = transferId) }
            transferRepo.pickup(transferId).fold(
                onSuccess = { load() },
                onFailure = { e -> _state.update { it.copy(mutatingTransferId = null, error = e.message) } }
            )
        }
    }

    /** in_transit → delivered. Водитель доставил на объект. */
    fun deliverTransfer(transferId: String) {
        viewModelScope.launch {
            _state.update { it.copy(mutatingTransferId = transferId) }
            transferRepo.deliver(transferId).fold(
                onSuccess = { load() },
                onFailure = { e -> _state.update { it.copy(mutatingTransferId = null, error = e.message) } }
            )
        }
    }
}


data class DriverRouteUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val route: RouteOutDto? = null,
    val isMutating: Boolean = false,
)

@HiltViewModel
class DriverRouteViewModel @Inject constructor(
    private val repo: DriverRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DriverRouteUiState())
    val state: StateFlow<DriverRouteUiState> = _state.asStateFlow()

    fun load(routeId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repo.routeDetail(routeId).fold(
                onSuccess = { r -> _state.update { it.copy(isLoading = false, route = r) } },
                onFailure = { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
            )
        }
    }

    // FIX(2026-05-11) BELSI 2.0.0 build11: multipart с фото + GPS координатами
    fun arrived(
        pointId: String,
        photoFile: java.io.File? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracyMeters: Float? = null,
        notes: String? = null,
    ) = mutate { r ->
        repo.pointArrived(r.id, pointId, photoFile, latitude, longitude, accuracyMeters, notes)
    }

    fun delivered(
        pointId: String,
        photoFile: java.io.File? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracyMeters: Float? = null,
        notes: String? = null,
    ) = mutate { r ->
        repo.pointDelivered(r.id, pointId, photoFile, latitude, longitude, accuracyMeters, notes)
    }

    fun departure(
        pointId: String,
        photoFile: java.io.File? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracyMeters: Float? = null,
        notes: String? = null,
    ) {
        val route = _state.value.route ?: return
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true) }
            repo.pointDeparture(route.id, pointId, photoFile, latitude, longitude, accuracyMeters, notes)
                .onSuccess { load(route.id) }
                .onFailure { e -> _state.update { it.copy(isMutating = false, error = e.message) } }
        }
    }

    fun skip(pointId: String, reason: String) = mutate { r -> repo.pointSkip(r.id, pointId, reason) }
    fun startRoute() = mutateRoute { repo.startRoute(it.id) }
    fun completeRoute() = mutateRoute { repo.completeRoute(it.id) }

    private fun mutate(action: suspend (RouteOutDto) -> Result<RoutePointOutDto>) {
        val route = _state.value.route ?: return
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true) }
            action(route).onSuccess {
                // После успеха — перезагружаем целый маршрут (counters обновятся)
                load(route.id)
            }.onFailure { e ->
                _state.update { it.copy(isMutating = false, error = e.message) }
            }
        }
    }

    private fun mutateRoute(action: suspend (RouteOutDto) -> Result<RouteOutDto>) {
        val route = _state.value.route ?: return
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true) }
            action(route).fold(
                onSuccess = { r -> _state.update { it.copy(isMutating = false, route = r) } },
                onFailure = { e -> _state.update { it.copy(isMutating = false, error = e.message) } }
            )
        }
    }
}

// ═══════════ LOGISTICIAN ═══════════

data class LogistHomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val dashboard: LogistDashboardDto? = null,
    val activeRoutes: List<RouteOutDto> = emptyList(),
    val pendingRequests: List<DeliveryRequestOutDto> = emptyList(),
    val drivers: List<DriverFleetItemDto> = emptyList(),
)

@HiltViewModel
class LogistHomeViewModel @Inject constructor(
    private val repo: LogistRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LogistHomeUiState())
    val state: StateFlow<LogistHomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val dash = repo.dashboard().getOrNull()
            val routes = repo.listRoutes(status = "active").getOrNull().orEmpty() +
                         repo.listRoutes(status = "planned").getOrNull().orEmpty()
            val reqs = repo.listRequests("pending").getOrNull().orEmpty()
            val drivers = repo.listDrivers().getOrNull().orEmpty()

            _state.update {
                it.copy(
                    isLoading = false,
                    dashboard = dash,
                    activeRoutes = routes,
                    pendingRequests = reqs,
                    drivers = drivers,
                )
            }
        }
    }

    fun cancelRoute(routeId: String) {
        viewModelScope.launch {
            repo.cancelRoute(routeId).onSuccess { load() }
        }
    }
}
