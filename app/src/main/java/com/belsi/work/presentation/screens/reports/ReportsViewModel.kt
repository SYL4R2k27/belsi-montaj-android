package com.belsi.work.presentation.screens.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.ShiftReport
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
    private val reportRepository: ReportRepository
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

    // Выбранная ставка для расчёта (500 или 600 рублей/час)
    private val _selectedHourlyRate = MutableStateFlow(500.0)
    val selectedHourlyRate: StateFlow<Double> = _selectedHourlyRate.asStateFlow()

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
                _uiState.value = ReportsUiState.Success(report)
            }.onFailure { error ->
                _uiState.value = ReportsUiState.Error(error.message ?: "Ошибка загрузки отчета")
            }
        }
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
        // Пересчитать отчёт с новой ставкой
        recalculateReportWithRate(rate)
    }

    /**
     * Пересчитать отчёт с новой почасовой ставкой
     */
    private fun recalculateReportWithRate(newRate: Double) {
        val currentState = _uiState.value
        val report = when (currentState) {
            is ReportsUiState.Success -> currentState.report
            is ReportsUiState.ExcelGenerated -> currentState.report
            is ReportsUiState.PdfGenerated -> currentState.report
            else -> return
        }

        // Пересчитываем каждую запись с новой ставкой
        val recalculatedEntries = report.entries.map { entry ->
            val newAmount = entry.workHours * newRate
            entry.copy(
                hourlyRate = newRate,
                totalAmount = newAmount
            )
        }

        val newTotalAmount = recalculatedEntries.sumOf { it.totalAmount }

        val recalculatedReport = report.copy(
            entries = recalculatedEntries,
            totalAmount = newTotalAmount
        )

        _uiState.value = ReportsUiState.Success(recalculatedReport)
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
