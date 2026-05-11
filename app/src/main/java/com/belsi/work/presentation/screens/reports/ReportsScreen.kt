package com.belsi.work.presentation.screens.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.ShiftReportEntry
import com.belsi.work.presentation.theme.belsiColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()
    val selectedHourlyRate by viewModel.selectedHourlyRate.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Отчеты по сменам") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Фильтры
            FilterSection(
                startDate = startDate,
                endDate = endDate,
                selectedHourlyRate = selectedHourlyRate,
                onStartDateChange = { viewModel.setStartDate(it) },
                onEndDateChange = { viewModel.setEndDate(it) },
                onHourlyRateChange = { viewModel.setHourlyRate(it) },
                onLoadClick = { viewModel.loadReport() }
            )

            when (val state = uiState) {
                is ReportsUiState.Initial -> {
                    EmptyState(
                        message = "Выберите период и нажмите 'Сформировать отчет'"
                    )
                }

                is ReportsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is ReportsUiState.Error -> {
                    ErrorState(message = state.message)
                }

                is ReportsUiState.Success,
                is ReportsUiState.GeneratingExcel,
                is ReportsUiState.GeneratingPdf,
                is ReportsUiState.ExcelGenerated,
                is ReportsUiState.PdfGenerated -> {
                    val report = when (state) {
                        is ReportsUiState.Success -> state.report
                        is ReportsUiState.GeneratingExcel -> state.report
                        is ReportsUiState.GeneratingPdf -> state.report
                        is ReportsUiState.ExcelGenerated -> state.report
                        is ReportsUiState.PdfGenerated -> state.report
                        else -> return@Scaffold
                    }

                    // Кнопки экспорта
                    ExportButtons(
                        isGeneratingExcel = state is ReportsUiState.GeneratingExcel,
                        isGeneratingPdf = state is ReportsUiState.GeneratingPdf,
                        excelFile = (state as? ReportsUiState.ExcelGenerated)?.file,
                        pdfFile = (state as? ReportsUiState.PdfGenerated)?.file,
                        onExcelClick = { viewModel.generateExcel() },
                        onPdfClick = { viewModel.generatePdf() },
                        onShareExcel = { file ->
                            viewModel.shareFile(file)
                            viewModel.resetToSuccess()
                        },
                        onSharePdf = { file ->
                            viewModel.shareFile(file)
                            viewModel.resetToSuccess()
                        }
                    )

                    // Итоговая статистика
                    SummaryCard(
                        totalShifts = report.totalShifts,
                        totalWorkHours = report.totalWorkHours,
                        totalAmount = report.totalAmount
                    )

                    // Таблица с данными
                    Text(
                        text = "Детализация (${report.entries.size} записей)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    ReportTable(entries = report.entries)
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    startDate: String,
    endDate: String,
    selectedHourlyRate: Double,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onHourlyRateChange: (Double) -> Unit,
    onLoadClick: () -> Unit
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Период отчета",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Начальная дата
                OutlinedTextField(
                    value = formatDateForDisplay(startDate),
                    onValueChange = {},
                    label = { Text("С даты") },
                    readOnly = true,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showStartDatePicker = true },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                    },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Конечная дата
                OutlinedTextField(
                    value = formatDateForDisplay(endDate),
                    onValueChange = {},
                    label = { Text("По дату") },
                    readOnly = true,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showEndDatePicker = true },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                    },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // Выбор ставки для расчёта расходов
            HourlyRateSelector(
                selectedRate = selectedHourlyRate,
                onRateChange = onHourlyRateChange
            )

            Button(
                onClick = onLoadClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Сформировать отчет")
            }
        }
    }

    // DatePicker для начальной даты
    if (showStartDatePicker) {
        BelsiDatePickerDialog(
            initialDate = startDate,
            onDateSelected = { selectedDate ->
                onStartDateChange(selectedDate)
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    // DatePicker для конечной даты
    if (showEndDatePicker) {
        BelsiDatePickerDialog(
            initialDate = endDate,
            onDateSelected = { selectedDate ->
                onEndDateChange(selectedDate)
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

@Composable
private fun HourlyRateSelector(
    selectedRate: Double,
    onRateChange: (Double) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Ставка для расчёта",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Кнопка 500 рублей/час
            FilterChip(
                selected = selectedRate == 500.0,
                onClick = { onRateChange(500.0) },
                label = { Text("500 ₽/час") },
                modifier = Modifier.weight(1f),
                leadingIcon = if (selectedRate == 500.0) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            // Кнопка 600 рублей/час
            FilterChip(
                selected = selectedRate == 600.0,
                onClick = { onRateChange(600.0) },
                label = { Text("600 ₽/час") },
                modifier = Modifier.weight(1f),
                leadingIcon = if (selectedRate == 600.0) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BelsiDatePickerDialog(
    initialDate: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(initialDate)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        val dateStr = sdf.format(Date(millis))
                        onDateSelected(dateStr)
                    } ?: onDismiss()
                }
            ) {
                Text("Выбрать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun ExportButtons(
    isGeneratingExcel: Boolean,
    isGeneratingPdf: Boolean,
    excelFile: java.io.File?,
    pdfFile: java.io.File?,
    onExcelClick: () -> Unit,
    onPdfClick: () -> Unit,
    onShareExcel: (java.io.File) -> Unit,
    onSharePdf: (java.io.File) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Excel кнопка
        Button(
            onClick = { excelFile?.let(onShareExcel) ?: onExcelClick() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (excelFile != null) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.primary
            ),
            enabled = !isGeneratingExcel && !isGeneratingPdf
        ) {
            if (isGeneratingExcel) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    if (excelFile != null) Icons.Default.Share else Icons.Default.TableChart,
                    contentDescription = null
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (excelFile != null) "Поделиться Excel" else "Excel")
        }

        // PDF кнопка
        Button(
            onClick = { pdfFile?.let(onSharePdf) ?: onPdfClick() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (pdfFile != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            enabled = !isGeneratingExcel && !isGeneratingPdf
        ) {
            if (isGeneratingPdf) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    if (pdfFile != null) Icons.Default.Share else Icons.Default.PictureAsPdf,
                    contentDescription = null
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (pdfFile != null) "Поделиться PDF" else "PDF")
        }
    }
}

@Composable
private fun SummaryCard(
    totalShifts: Int,
    totalWorkHours: Double,
    totalAmount: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                label = "Смен",
                value = totalShifts.toString(),
                icon = Icons.Default.WorkHistory
            )
            SummaryItem(
                label = "Часов",
                value = String.format("%.1f", totalWorkHours),
                icon = Icons.Default.AccessTime
            )
            SummaryItem(
                label = "Сумма",
                value = String.format("%.0f ₽", totalAmount),
                icon = Icons.Default.Payments
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReportTable(entries: List<ShiftReportEntry>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(entries) { entry ->
            ReportEntryCard(entry)
        }
    }
}

@Composable
private fun ReportEntryCard(entry: ShiftReportEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ФИО и дата
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.userFullName ?: entry.userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatDateForDisplay(entry.shiftDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Времена
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimeItem("Общее", entry.formattedTotalTime)
                TimeItem("Работа", entry.formattedWorkTime, MaterialTheme.belsiColors.success)
                TimeItem("Пауза", entry.formattedPauseTime, MaterialTheme.belsiColors.warning)
                TimeItem("Простой", entry.formattedIdleTime, MaterialTheme.colorScheme.error)
            }

            if (entry.idleReason != null) {
                Text(
                    text = "Причина простоя: ${entry.idleReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Divider()

            // Ставка и сумма
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ставка: ${String.format("%.0f", entry.hourlyRate)} ₽/ч",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "За кем: ${entry.assignedTo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = String.format("%.0f ₽", entry.totalAmount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TimeItem(label: String, time: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = time,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Assessment,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatDateForDisplay(isoDate: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val date = inputFormat.parse(isoDate)
        date?.let { outputFormat.format(it) } ?: isoDate
    } catch (e: Exception) {
        isoDate
    }
}
