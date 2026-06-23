package com.belsi.work.presentation.screens.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.ShiftReport
import com.belsi.work.data.remote.api.UserApi
import com.belsi.work.data.repositories.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reportRepository: ReportRepository,
    private val userApi: UserApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportsUiState>(ReportsUiState.Initial)
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Фильтры
    private val _startDate = MutableStateFlow(getDefaultStartDate())
    val startDate: StateFlow<String> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow(getDefaultEndDate())
    val endDate: StateFlow<String> = _endDate.asStateFlow()

    // FIX(2026-05-12) build18 P2: ставка из API /user/me/rate.
    // Раньше хардкод 500 — теперь реальный hourly_rate пользователя.
    private val _selectedHourlyRate = MutableStateFlow(500.0)
    val selectedHourlyRate: StateFlow<Double> = _selectedHourlyRate.asStateFlow()

    private val _isRateFromServer = MutableStateFlow(false)
    val isRateFromServer: StateFlow<Boolean> = _isRateFromServer.asStateFlow()

    // FIX(2026-06-02) Report v2: фильтр ролей. По умолчанию — только монтажный домен,
    // чтобы куратор/координатор/тестовые юзера НЕ попадали в таблицу (на фото они засоряли отчёт).
    private val _roleFilter = MutableStateFlow(MONTAGE_ROLES)
    val roleFilter: StateFlow<Set<String>> = _roleFilter.asStateFlow()

    // Полные неотфильтрованные данные сервера — сохраняем, чтобы переключение фильтра
    // не требовало повторного запроса.
    private var rawReport: com.belsi.work.data.models.ShiftReport? = null

    init {
        loadServerRate()
    }

    private fun loadServerRate() {
        viewModelScope.launch {
            try {
                val resp = userApi.getMyRate()
                if (resp.isSuccessful) {
                    resp.body()?.let { rate ->
                        _selectedHourlyRate.value = rate.hourlyRate
                        _isRateFromServer.value = !rate.isDefault
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ReportsVM", "loadServerRate failed: ${e.message}")
            }
        }
    }

    /**
     * Загрузить отчет с сервера
     */
    fun loadReport() {
        viewModelScope.launch {
            _uiState.value = ReportsUiState.Loading

            reportRepository.getShiftReport(
                startDate = _startDate.value,
                endDate = _endDate.value
            ).onSuccess { report ->
                rawReport = report
                // FIX(2026-06-02) Report v2: применяем role-фильтр + автоматически
                // умножаем на текущую ставку (если бэк отдал hourly_rate=0 — амоунт был 0₽).
                _uiState.value = ReportsUiState.Success(
                    applyFilterAndRate(report, _roleFilter.value, _selectedHourlyRate.value)
                )
            }.onFailure { error ->
                _uiState.value = ReportsUiState.Error(error.message ?: "Ошибка загрузки отчета")
            }
        }
    }

    /**
     * Переключение фильтра по ролям (без повторного запроса к серверу).
     */
    fun setRoleFilter(roles: Set<String>) {
        _roleFilter.value = roles
        rawReport?.let { raw ->
            _uiState.value = ReportsUiState.Success(
                applyFilterAndRate(raw, roles, _selectedHourlyRate.value)
            )
        }
    }

    /**
     * Фильтрует entries по userRole + пересчитывает amount по ставке. Итоги — заново.
     */
    private fun applyFilterAndRate(
        raw: com.belsi.work.data.models.ShiftReport,
        roles: Set<String>,
        rate: Double,
    ): com.belsi.work.data.models.ShiftReport {
        val filtered = raw.entries
            .filter { roles.isEmpty() || it.userRole.lowercase() in roles }
            .map { e -> e.copy(hourlyRate = rate, totalAmount = e.workHours * rate) }
        val totalWork = filtered.sumOf { it.workSeconds } / 3600.0
        val totalAmount = filtered.sumOf { it.totalAmount }
        return raw.copy(
            entries = filtered,
            totalShifts = filtered.size,
            totalWorkHours = totalWork,
            totalAmount = totalAmount,
        )
    }

    /**
     * Сгенерировать Excel отчет
     */
    fun generateExcel() {
        val currentState = _uiState.value
        if (currentState !is ReportsUiState.Success) {
            showSnackbar("Сначала загрузите данные отчета")
            return
        }

        viewModelScope.launch {
            _uiState.value = ReportsUiState.GeneratingExcel(currentState.report)

            reportRepository.generateExcelReport(currentState.report)
                .onSuccess { file ->
                    _uiState.value = ReportsUiState.ExcelGenerated(currentState.report, file)
                    showSnackbar("Excel файл создан")
                }
                .onFailure { error ->
                    _uiState.value = ReportsUiState.Success(currentState.report)
                    showSnackbar("Ошибка создания Excel: ${error.message}")
                }
        }
    }

    /**
     * Сгенерировать PDF отчет
     */
    fun generatePdf() {
        val currentState = _uiState.value
        if (currentState !is ReportsUiState.Success) {
            showSnackbar("Сначала загрузите данные отчета")
            return
        }

        viewModelScope.launch {
            _uiState.value = ReportsUiState.GeneratingPdf(currentState.report)

            reportRepository.generatePdfReport(currentState.report)
                .onSuccess { file ->
                    _uiState.value = ReportsUiState.PdfGenerated(currentState.report, file)
                    showSnackbar("PDF файл создан")
                }
                .onFailure { error ->
                    _uiState.value = ReportsUiState.Success(currentState.report)
                    showSnackbar("Ошибка создания PDF: ${error.message}")
                }
        }
    }

    /**
     * Поделиться файлом
     */
    fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = when {
                    file.extension == "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    file.extension == "pdf" -> "application/pdf"
                    else -> "*/*"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Поделиться отчетом").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            showSnackbar("Ошибка отправки: ${e.message}")
        }
    }

    /**
     * Установить начальную дату
     */
    fun setStartDate(date: String) {
        _startDate.value = date
    }

    /**
     * Установить конечную дату
     */
    fun setEndDate(date: String) {
        _endDate.value = date
    }

    /**
     * Установить ставку для расчёта расходов
     */
    fun setHourlyRate(rate: Double) {
        _selectedHourlyRate.value = rate
        // FIX(2026-06-02) пересчёт ставки идёт через applyFilterAndRate (общий пайплайн).
        rawReport?.let { raw ->
            _uiState.value = ReportsUiState.Success(
                applyFilterAndRate(raw, _roleFilter.value, rate)
            )
        }
    }

    /**
     * Сбросить состояние после генерации файла
     */
    fun resetToSuccess() {
        val currentState = _uiState.value
        if (currentState is ReportsUiState.ExcelGenerated || currentState is ReportsUiState.PdfGenerated) {
            val report = when (currentState) {
                is ReportsUiState.ExcelGenerated -> currentState.report
                is ReportsUiState.PdfGenerated -> currentState.report
                else -> return
            }
            _uiState.value = ReportsUiState.Success(report)
        }
    }

    private fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun getDefaultStartDate(): String {
        // Начало текущего месяца
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    private fun getDefaultEndDate(): String {
        // Сегодня
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}

sealed class ReportsUiState {
    object Initial : ReportsUiState()
    object Loading : ReportsUiState()
    data class Success(val report: ShiftReport) : ReportsUiState()
    data class GeneratingExcel(val report: ShiftReport) : ReportsUiState()
    data class GeneratingPdf(val report: ShiftReport) : ReportsUiState()
    data class ExcelGenerated(val report: ShiftReport, val file: File) : ReportsUiState()
    data class PdfGenerated(val report: ShiftReport, val file: File) : ReportsUiState()
    data class Error(val message: String) : ReportsUiState()
}

// FIX(2026-06-02) Report v2: дефолтный фильтр — только монтажный домен.
val MONTAGE_ROLES: Set<String> = setOf("installer", "foreman", "senior_worker", "worker")
val ALL_ROLES: Set<String> = emptySet() // пустое = «не фильтровать» — все роли
