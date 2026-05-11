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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.EngineerTask

/**
 * FIX(2026-05-06): EngineerMainScreen — подключён к API.
 * Задачи через GET /production/engineer/tasks.
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmberPrimary.copy(alpha = 0.1f))
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Стат-карточки
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniStat("Мои задачи", "${state.myTasks.size}", AmberPrimary)
                MiniStat("Свободные", "${state.openTasks.size}", Color(0xFF10B981))
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
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (tab == 0) "🛠" else "🔍", fontSize = 56.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (tab == 0) "Нет назначенных задач" else "Нет свободных задач",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
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
    val (icon, color) = when (p) {
        "urgent" -> "🔥" to Color(0xFFEF4444)
        "high" -> "⚡" to Color(0xFFF59E0B)
        "low" -> "·" to Color.Gray
        else -> "•" to Color(0xFF6366F1)
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = 11.sp)
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (text, color) = when (status) {
        "open" -> "Открыта" to Color(0xFFFBBF24)
        "in_progress" -> "В работе" to Color(0xFF6366F1)
        "done" -> "Готова" to Color(0xFF10B981)
        "cancelled" -> "Отмена" to Color.Gray
        else -> status to Color.Gray
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RowScope.MiniStat(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
