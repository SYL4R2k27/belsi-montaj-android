package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.EngineerTask
import com.belsi.work.presentation.components.role.RoleEmptyState
import com.belsi.work.presentation.components.role.RoleStatCard
import com.belsi.work.presentation.components.role.RoleStatusPill
import com.belsi.work.presentation.components.role.Severity
import com.belsi.work.presentation.components.role.colors

/**
 * EngineerMainScreen — дашборд инженера производства.
 *
 * FIX(2026-05-12) build19 hotfix: единая дизайн-система (RoleStatCard / Pill / Severity).
 * Удалены 8 hardcoded Color(0xFF...).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineerMainScreen(
    navController: NavController,
    viewModel: EngineerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инженер", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Стат-карточки
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoleStatCard("Мои задачи", "${state.myTasks.size}", Severity.PRIMARY, modifier = Modifier.weight(1f))
                RoleStatCard("Свободные",  "${state.openTasks.size}", Severity.SUCCESS, modifier = Modifier.weight(1f))
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }) {
                    Text("Мои задачи", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = tab == 1, onClick = { tab = 1 }) {
                    Text("Свободные", modifier = Modifier.padding(12.dp))
                }
            }

            if (state.loading && state.myTasks.isEmpty() && state.openTasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val tasks = if (tab == 0) state.myTasks else state.openTasks
            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RoleEmptyState(
                        emoji = if (tab == 0) "🛠" else "🔍",
                        title = if (tab == 0) "Нет назначенных задач" else "Нет свободных задач",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tasks) { t ->
                        EngineerTaskCard(t, isMine = tab == 0, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineerTaskCard(task: EngineerTask, isMine: Boolean, viewModel: EngineerViewModel) {
    val (successFg, _) = Severity.SUCCESS.colors()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriorityBadge(task.priority)
                Spacer(Modifier.width(6.dp))
                Text(task.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusBadge(task.status)
            }
            if (task.description != null) {
                Spacer(Modifier.height(4.dp))
                Text(task.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.batchTitle != null) {
                Spacer(Modifier.height(2.dp))
                Text("Партия: ${task.batchTitle}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.facilityName != null) {
                Text("📍 ${task.facilityName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (task.status) {
                    "open" -> {
                        Button(
                            onClick = { viewModel.startTask(task.id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Взять")
                        }
                    }
                    "in_progress" -> {
                        Button(
                            onClick = { viewModel.completeTask(task.id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = successFg),
                        ) {
                            Text("Готово")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityBadge(p: String) {
    val (icon, severity) = when (p) {
        "urgent" -> "🔥" to Severity.ERROR
        "high"   -> "⚡" to Severity.WARNING
        "low"    -> "·" to Severity.NEUTRAL
        else     -> "•" to Severity.PRIMARY
    }
    val (fg, _) = severity.colors()
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(fg.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = 11.sp)
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (text, severity) = when (status) {
        "open"        -> "Открыта"  to Severity.WARNING
        "in_progress" -> "В работе" to Severity.PRIMARY
        "done"        -> "Готова"   to Severity.SUCCESS
        "cancelled"   -> "Отмена"   to Severity.NEUTRAL
        else          -> status      to Severity.NEUTRAL
    }
    RoleStatusPill(text = text, severity = severity)
}
