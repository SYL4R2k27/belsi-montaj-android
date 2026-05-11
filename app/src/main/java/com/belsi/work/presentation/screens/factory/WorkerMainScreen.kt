package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.repositories.BatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-05): Главный экран Работника на производстве.
 * Brandbook: смена + 3 кнопки (перекур/обед/простой) + камера + задачи + автоген отчёт.
 * Привязан к одной фабрике (Углич), переключатель в шапке.
 */
/**
 * FIX(2026-05-05): VM для WorkerMainScreen — реальные вызовы /shift/break/start|end.
 * При 404 от сервера ошибка глотается, UI продолжает на локальном state.
 * После деплоя backend — реальные нажатия начнут писать в audit shifts.
 */
@HiltViewModel
class WorkerShiftViewModel @Inject constructor(
    private val repo: BatchRepository,
) : ViewModel() {

    fun startSmoke() = viewModelScope.launch { repo.startSmokeBreak() }
    fun startLunch() = viewModelScope.launch { repo.startLunchBreak() }
    fun endBreak() = viewModelScope.launch { repo.endBreak() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerMainScreen(
    navController: NavController,
    viewModel: WorkerShiftViewModel = hiltViewModel(),
) {
    val mock = FactoryMockData

    // Состояние смены — local UI state. Backend audit идёт через VM.
    var shiftActive by remember { mutableStateOf(true) }
    var currentBreak by remember { mutableStateOf<BreakType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Работник", fontWeight = FontWeight.Bold)
                    Text(mock.FACILITY_NAME, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                actions = {
                    IconButton(onClick = { /* facility switch */ }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Сменить фабрику")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AmberPrimary.copy(alpha = 0.1f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Большая карточка статуса смены ───
            ShiftStatusCard(
                active = shiftActive,
                currentBreak = currentBreak,
                onStartShift = { shiftActive = true },
                onFinishShift = {
                    shiftActive = false
                    currentBreak = null
                }
            )

            // ─── 3 кнопки: Перекур · Обед · Простой ───
            if (shiftActive && currentBreak == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BreakButton(
                        modifier = Modifier.weight(1f),
                        label = "Перекур", emoji = "☕", color = Color(0xFFFBBF24),
                        onClick = {
                            currentBreak = BreakType.SMOKE
                            viewModel.startSmoke()  // FIX(2026-05-05): real /shift/break/start
                        }
                    )
                    BreakButton(
                        modifier = Modifier.weight(1f),
                        label = "Обед", emoji = "🍱", color = Color(0xFFF59E0B),
                        onClick = {
                            currentBreak = BreakType.LUNCH
                            viewModel.startLunch()  // FIX(2026-05-05): real /shift/break/start
                        }
                    )
                    BreakButton(
                        modifier = Modifier.weight(1f),
                        label = "Простой", emoji = "⚠️", color = Color(0xFFF43F5E),
                        onClick = { navController.navigate("factory/idle/reasons") }
                    )
                }
            } else if (currentBreak != null) {
                ActiveBreakCard(currentBreak!!) {
                    currentBreak = null
                    viewModel.endBreak()  // FIX(2026-05-05): real /shift/break/end
                }
            }

            // ─── Камера ───
            ActionCard(
                title = "Сделать фото",
                subtitle = "Обязателен комментарий",
                icon = Icons.Default.PhotoCamera,
                onClick = { /* open camera */ }
            )

            // ─── Задачи ───
            SectionHeader("Мои задачи", count = mock.workerTasks.count { !it.done })
            mock.workerTasks.forEach { TaskRow(it) }

            // ─── Кнопка отчёта смены ───
            // FIX(2026-05-10): убрана навигация на factory/shift/report/{shiftId}
            // потому что роута нет в NavGraph → IllegalArgumentException и crash.
            // Пока этот экран не реализован — направляем на ShiftHistory
            // (история всех смен, включая текущую).
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    navController.navigate(
                        com.belsi.work.presentation.navigation.AppRoute.ShiftHistory.route
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("История смен", fontWeight = FontWeight.Medium)
            }
        }
    }
}

private enum class BreakType(val label: String, val emoji: String) {
    SMOKE("Перекур", "☕"),
    LUNCH("Обед", "🍱"),
}

@Composable
private fun ShiftStatusCard(
    active: Boolean,
    currentBreak: BreakType?,
    onStartShift: () -> Unit,
    onFinishShift: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (active) AmberPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (active) "📍 Смена идёт" else "Смена не начата",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            if (active) {
                Text(
                    "08:23:14",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    StatPill("Работа 7ч 12мин", Color(0xFF10B981))
                    Spacer(Modifier.width(6.dp))
                    StatPill("Перекур 14мин", Color(0xFFFBBF24))
                    Spacer(Modifier.width(6.dp))
                    StatPill("Обед 30мин", Color(0xFFF59E0B))
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onFinishShift,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Закрыть смену") }
            } else {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onStartShift,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) { Text("Старт смены", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun BreakButton(
    modifier: Modifier = Modifier,
    label: String, emoji: String, color: Color,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@Composable
private fun ActiveBreakCard(breakType: BreakType, onEnd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(breakType.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${breakType.label} идёт", fontWeight = FontWeight.Bold)
                Text("00:08:24", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onEnd) { Text("Вернуться") }
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AmberPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(AmberPrimary, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("$count", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TaskRow(task: FactoryMockData.WorkerTask) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (task.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (task.done) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Medium)
                Text("${task.from} · ${task.priority}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

internal val AmberPrimary = Color(0xFFD97706)
