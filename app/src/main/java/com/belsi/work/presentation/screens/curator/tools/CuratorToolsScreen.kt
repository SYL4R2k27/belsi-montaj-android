package com.belsi.work.presentation.screens.curator.tools

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.curator.CuratorToolTransactionDto
import com.belsi.work.presentation.theme.belsiColors
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorToolsScreen(
    navController: NavController,
    viewModel: CuratorToolsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление инструментами") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            } else if (uiState.error != null) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(uiState.error!!, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) { Text("Повторить") }
                }
            } else if (uiState.isEmpty) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Build, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Транзакции отсутствуют", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Статистика
                    item {
                        Card(Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                MiniStat("Всего", uiState.totalItems.toString())
                                MiniStat("Выдано", uiState.issuedTransactions.size.toString(), MaterialTheme.colorScheme.primary)
                                MiniStat("Возвращено", uiState.returnedTransactions.size.toString(), MaterialTheme.belsiColors.success)
                            }
                        }
                    }

                    // Фильтр чипы
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = uiState.selectedFilter == ToolTransactionFilter.ALL,
                                onClick = { viewModel.filterByStatus(ToolTransactionFilter.ALL) },
                                label = { Text("Все") })
                            FilterChip(selected = uiState.selectedFilter == ToolTransactionFilter.ISSUED,
                                onClick = { viewModel.filterByStatus(ToolTransactionFilter.ISSUED) },
                                label = { Text("Выдано") })
                            FilterChip(selected = uiState.selectedFilter == ToolTransactionFilter.RETURNED,
                                onClick = { viewModel.filterByStatus(ToolTransactionFilter.RETURNED) },
                                label = { Text("Возвращено") })
                        }
                    }

                    val displayList = when (uiState.selectedFilter) {
                        ToolTransactionFilter.ISSUED -> uiState.issuedTransactions
                        ToolTransactionFilter.RETURNED -> uiState.returnedTransactions
                        else -> uiState.allTransactions
                    }

                    items(displayList, key = { it.id }) { transaction ->
                        TransactionCard(transaction)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TransactionCard(t: CuratorToolTransactionDto) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Инструмент", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusChip(t.status)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Tag, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ID: ${t.toolId.take(8)}...", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Person, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Монтажник: ${t.installerId.take(8)}...", style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.DateRange, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Выдан: ${formatDate(t.issuedAt)}", style = MaterialTheme.typography.bodySmall)
            }

            if (t.returnedAt != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.belsiColors.success)
                    Text("Возвращен: ${formatDate(t.returnedAt)}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (t.returnCondition != null) {
                Text("Состояние: ${getConditionText(t.returnCondition)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (text, color) = when (status) {
        "issued" -> "Выдано" to MaterialTheme.colorScheme.primary
        "returned" -> "Возвращено" to MaterialTheme.belsiColors.success
        else -> status to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

private fun formatDate(dateString: String?): String {
    if (dateString.isNullOrBlank()) return "—"
    return try {
        val dt = try { OffsetDateTime.parse(dateString) } catch (_: Exception) { null }
        if (dt != null) {
            dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        } else {
            val ldt = LocalDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME)
            ldt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        }
    } catch (_: Exception) { dateString.take(16) }
}

private fun getConditionText(condition: String) = when (condition.uppercase()) {
    "GOOD" -> "Хорошее"
    "DAMAGED" -> "Поврежден"
    "BROKEN" -> "Сломан"
    else -> condition
}
