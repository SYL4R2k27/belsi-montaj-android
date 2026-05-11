package com.belsi.work.presentation.screens.curator.shiftadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.api.AdminShiftDto
import com.belsi.work.data.remote.api.CuratorApi
import com.belsi.work.data.remote.api.EditShiftRequest
import com.belsi.work.data.remote.api.ReopenShiftRequest
import com.belsi.work.data.remote.api.ShiftAuditEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for ShiftAdminScreen.
 */
data class ShiftAdminState(
    val shifts: List<AdminShiftDto> = emptyList(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val selectedShift: AdminShiftDto? = null,
    val audit: List<ShiftAuditEntry>? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val userId: String? = null,
)

@HiltViewModel
class ShiftAdminViewModel @Inject constructor(
    private val curatorApi: CuratorApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ShiftAdminState())
    val state: StateFlow<ShiftAdminState> = _state.asStateFlow()

    fun load(userId: String) {
        _state.value = _state.value.copy(isLoading = true, error = null, userId = userId)
        viewModelScope.launch {
            try {
                val resp = curatorApi.getUserShiftsAdmin(userId)
                if (resp.isSuccessful) {
                    _state.value = _state.value.copy(
                        shifts = resp.body()?.shifts ?: emptyList(),
                        isLoading = false,
                    )
                } else {
                    _state.value = _state.value.copy(
                        error = "Не удалось загрузить смены (${resp.code()})",
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Ошибка сети", isLoading = false)
            }
        }
    }

    fun selectShift(shift: AdminShiftDto) {
        _state.value = _state.value.copy(selectedShift = shift, audit = null)
    }

    fun dismissSelected() {
        _state.value = _state.value.copy(selectedShift = null, audit = null)
    }

    fun reopen(shiftId: String, reason: String?) {
        _state.value = _state.value.copy(isProcessing = true, error = null)
        viewModelScope.launch {
            try {
                val resp = curatorApi.reopenShift(shiftId, ReopenShiftRequest(reason))
                if (resp.isSuccessful) {
                    _state.value = _state.value.copy(
                        successMessage = "Смена снова активна",
                        isProcessing = false,
                        selectedShift = null,
                    )
                    _state.value.userId?.let { load(it) }
                } else {
                    _state.value = _state.value.copy(
                        error = "Re-open failed (${resp.code()}): ${resp.errorBody()?.string() ?: ""}",
                        isProcessing = false,
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isProcessing = false)
            }
        }
    }

    fun edit(shiftId: String, startIso: String?, finishIso: String?, reason: String?) {
        _state.value = _state.value.copy(isProcessing = true, error = null)
        viewModelScope.launch {
            try {
                val resp = curatorApi.editShift(
                    shiftId,
                    EditShiftRequest(
                        startAt = startIso,
                        finishAt = finishIso,
                        reason = reason,
                    ),
                )
                if (resp.isSuccessful) {
                    _state.value = _state.value.copy(
                        successMessage = "Смена обновлена",
                        isProcessing = false,
                        selectedShift = null,
                    )
                    _state.value.userId?.let { load(it) }
                } else {
                    _state.value = _state.value.copy(
                        error = "Edit failed (${resp.code()}): ${resp.errorBody()?.string() ?: ""}",
                        isProcessing = false,
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isProcessing = false)
            }
        }
    }

    fun loadAudit(shiftId: String) {
        viewModelScope.launch {
            try {
                val resp = curatorApi.getShiftAudit(shiftId)
                if (resp.isSuccessful) {
                    _state.value = _state.value.copy(audit = resp.body()?.entries ?: emptyList())
                } else {
                    _state.value = _state.value.copy(
                        error = "Audit load failed (${resp.code()})",
                        audit = emptyList(),
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, audit = emptyList())
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearSuccess() {
        _state.value = _state.value.copy(successMessage = null)
    }
}
