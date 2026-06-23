package com.belsi.work.presentation.screens.installer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.repositories.ShiftHistoryData
import com.belsi.work.presentation.screens.shift_history.ShiftHistoryViewModel
import com.belsi.work.presentation.theme.belsiColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * История смен монтажника — календарь (мок 1.4).
 * Реальные данные: [ShiftHistoryViewModel] → ShiftRepository.getShiftHistory().
 * Поля, которых нет в данных (счётчики пауз по дню, кудосы), НЕ показываем — без фейка.
 * Рекорд месяца = max длительности (реально вычисляется).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerHistoryScreen(
    navController: NavController,
    viewModel: ShiftHistoryViewModel = hiltViewModel(),
) {
    val shifts by viewModel.shifts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // отображаемый месяц как смещение от текущего (0 = текущий)
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDayKey by remember { mutableStateOf<String?>(null) }

    val cal = remember(monthOffset) {
        Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset); set(Calendar.DAY_OF_MONTH, 1) }
    }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)

    // карта день(yyyy-MM-dd) → смена
    val byDay = remember(shifts) { shifts.associateBy { dayKey(it.startAt) } }
    val monthShifts = remember(shifts, monthOffset) {
        shifts.filter { sameMonth(it.startAt, year, month) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("История смен", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading && shifts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            item {
                // переключатель месяца
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { monthOffset -= 1; selectedDayKey = null }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Предыдущий месяц")
                    }
                    Text("${monthName(month)} $year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { if (monthOffset < 0) { monthOffset += 1; selectedDayKey = null } }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Следующий месяц")
                    }
                }
            }

            item { CalendarGrid(year, month, byDay, onSelect = { selectedDayKey = it }, selectedDayKey = selectedDayKey) }

            item { Legend() }

            // детали выбранного дня
            val sel = selectedDayKey?.let { byDay[it] }
            if (sel != null) {
                item { DayDetailCard(sel, viewModel) }
            }

            item { MonthSummary(monthShifts, viewModel) }
        }
    }
}

@Composable
private fun CalendarGrid(
    year: Int,
    month: Int,
    byDay: Map<String, ShiftHistoryData>,
    selectedDayKey: String?,
    onSelect: (String) -> Unit,
) {
    val belsi = MaterialTheme.belsiColors
    val cal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Mon-first: Calendar.MONDAY=2 … SUNDAY=1 → offset
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
    val lead = ((firstDow + 5) % 7) // кол-во пустых ячеек перед 1-м (Пн=0)

    val today = Calendar.getInstance()
    val isCurMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month
    val todayDom = today.get(Calendar.DAY_OF_MONTH)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        val cells = lead + daysInMonth
        val rows = (cells + 6) / 7
        var dom = 1
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c
                    if (idx < lead || dom > daysInMonth) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val d = dom
                        val key = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, d)
                        val shift = byDay[key]
                        val isToday = isCurMonth && d == todayDom
                        val isSel = key == selectedDayKey
                        val bg = when {
                            isToday -> MaterialTheme.colorScheme.primary
                            shift != null -> belsi.successContainer
                            else -> Color.Transparent
                        }
                        val fg = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            shift != null -> belsi.onSuccessContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .then(if (isSel && !isToday) Modifier.background(belsi.success.copy(alpha = 0.35f)) else Modifier)
                                .clickable(enabled = shift != null) { onSelect(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$d", style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                        }
                        dom++
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem(MaterialTheme.belsiColors.successContainer, "рабочий день")
        LegendItem(MaterialTheme.colorScheme.primary, "сегодня")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.clip(CircleShape).background(color).padding(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayDetailCard(shift: ShiftHistoryData, vm: ShiftHistoryViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(prettyDate(shift.startAt), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                StatusPill(shift.status)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyVal("Старт", timeOf(shift.startAt))
                    KeyVal("Чистое", vm.formatDuration(shift.durationMinutes))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyVal("Финиш", shift.endAt?.let { timeOf(it) } ?: "—")
                    KeyVal("Фото", shift.photosCount?.toString() ?: "—")
                }
            }
        }
    }
}

@Composable
private fun MonthSummary(monthShifts: List<ShiftHistoryData>, vm: ShiftHistoryViewModel) {
    // FIX(2026-05-23): «Дней» = РАЗНЫЕ календарные дни (а не число записей-смен —
    // отсюда было «Дней 32» при нескольких сменах в день).
    val days = monthShifts.map { it.startAt.take(10) }.distinct().size
    val totalMin = monthShifts.sumOf { it.durationMinutes ?: 0 }
    val photos = monthShifts.sumOf { it.photosCount ?: 0 }
    val recordMin = monthShifts.mapNotNull { it.durationMinutes }.maxOrNull()
    val avgMin = if (days > 0) totalMin / days else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.belsiColors.aiContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.BarChart, contentDescription = null, tint = MaterialTheme.belsiColors.onAiContainer, modifier = Modifier.size(13.dp))
                Text("ИТОГО ЗА МЕСЯЦ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.belsiColors.onAiContainer, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyVal("Дней", "$days")
                    KeyVal("Часов", "${totalMin / 60}")
                    KeyVal("Среднее", vm.formatDuration(avgMin))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyVal("Фото", "$photos")
                    KeyVal("Рекорд", recordMin?.let { vm.formatDuration(it) } ?: "—")
                }
            }
        }
    }
}

@Composable
private fun KeyVal(k: String, v: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$k:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusPill(status: String) {
    val belsi = MaterialTheme.belsiColors
    val (bg, fg, label) = when (status.lowercase()) {
        "finished", "completed", "done" -> Triple(belsi.successContainer, belsi.onSuccessContainer, "завершён")
        "active", "in_progress" -> Triple(belsi.warningContainer, belsi.onWarningContainer, "идёт")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, status)
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Bold)
    }
}

// ───────────────────────── date helpers (java.util.Calendar, без java.time) ─────────────────────────

private val ISO_FORMATS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss",
)

private fun parseIso(s: String): Date? {
    for (f in ISO_FORMATS) {
        try {
            return SimpleDateFormat(f, Locale.US).apply { isLenient = true }.parse(s)
        } catch (_: Exception) {
        }
    }
    return null
}

private fun dayKey(startAt: String): String {
    val d = parseIso(startAt) ?: return startAt.take(10)
    val c = Calendar.getInstance().apply { time = d }
    return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

private fun sameMonth(startAt: String, year: Int, month: Int): Boolean {
    val d = parseIso(startAt) ?: return false
    val c = Calendar.getInstance().apply { time = d }
    return c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
}

private fun timeOf(s: String): String {
    val d = parseIso(s) ?: return "—"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
}

private fun prettyDate(s: String): String {
    val d = parseIso(s) ?: return s.take(10)
    return SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(d)
}

private fun monthName(month: Int): String = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)[month.coerceIn(0, 11)]
