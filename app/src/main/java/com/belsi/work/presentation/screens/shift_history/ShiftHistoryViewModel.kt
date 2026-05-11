package com.belsi.work.presentation.screens.shift_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.ShiftHistoryData
import com.belsi.work.data.repositories.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ShiftHistoryViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val _shifts = MutableStateFlow<List<ShiftHistoryData>>(emptyList())
    val shifts: StateFlow<List<ShiftHistoryData>> = _shifts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadShifts()
    }

    private fun loadShifts() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            shiftRepository.getShiftHistory(page = 1, limit = 50)
                .onSuccess { shifts ->
                    _shifts.value = shifts
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка загрузки истории"
                }

            _isLoading.value = false
        }
    }

    fun refresh() {
        loadShifts()
    }

    fun clearError() {
        _error.value = null
    }

    fun formatDuration(minutes: Int?): String {
        if (minutes == null) return "—"
        val hours = minutes / 60
        val mins = minutes % 60
        return "${hours}ч ${mins}м"
    }

    fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    fun formatEarnings(earnings: Double?): String {
        return if (earnings != null) "%.2f ₽".format(earnings) else "—"
    }
}
