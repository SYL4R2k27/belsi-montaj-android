package com.belsi.work.presentation.screens.curator.support

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.curator.CuratorSupportTicketDto
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorSupportScreen(
    navController: NavController,
    viewModel: CuratorSupportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf(SupportFilter.ALL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тикеты поддержки") },
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
                    Icon(Icons.Default.Support, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Нет тикетов", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Тикеты от пользователей появятся здесь", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Статистика
                    item {
                        Card(Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Статистика тикетов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    MiniStat("Всего", uiState.allTickets.size.toString())
                                    MiniStat("Открыто", uiState.openTickets.size.toString(), MaterialTheme.belsiColors.info)
                                    MiniStat("В работе", uiState.inProgressTickets.size.toString(), MaterialTheme.belsiColors.warning)
                                    MiniStat("Решено", uiState.resolvedTickets.size.toString(), MaterialTheme.belsiColors.success)
                                }
                            }
                        }
                    }

                    // Фильтры
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = selectedFilter == SupportFilter.ALL,
                                onClick = { selectedFilter = SupportFilter.ALL }, label = { Text("Все") })
                            FilterChip(selected = selectedFilter == SupportFilter.OPEN,
                                onClick = { selectedFilter = SupportFilter.OPEN }, label = { Text("Открыто") })
                            FilterChip(selected = selectedFilter == SupportFilter.IN_PROGRESS,
                                onClick = { selectedFilter = SupportFilter.IN_PROGRESS }, label = { Text("В работе") })
                            FilterChip(selected = selectedFilter == SupportFilter.RESOLVED,
                                onClick = { selectedFilter = SupportFilter.RESOLVED }, label = { Text("Решено") })
                        }
                    }

                    val displayList = when (selectedFilter) {
                        SupportFilter.OPEN -> uiState.openTickets
                        SupportFilter.IN_PROGRESS -> uiState.inProgressTickets
                        SupportFilter.RESOLVED -> uiState.resolvedTickets
                        else -> uiState.allTickets
                    }

                    items(displayList, key = { it.id }) { ticket ->
                        TicketCard(ticket) {
                            navController.navigate(AppRoute.CuratorChatConversation.createRoute(ticket.id, ticket.userPhone))
                        }
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
private fun TicketCard(ticket: CuratorSupportTicketDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Заголовок
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ticket.userName ?: "Пользователь", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(ticket.userPhone ?: "", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(ticket.status)
            }

            // Последнее сообщение
            if (!ticket.lastMessageSnippet.isNullOrBlank()) {
                Text(ticket.lastMessageSnippet, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Категория, непрочитанные, дата
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ticket.category != null) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                            Text(getCategoryText(ticket.category),
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (ticket.unreadCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text("${ticket.unreadCount} новых", color = MaterialTheme.colorScheme.onError,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDate(ticket.createdAt), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (text, color) = when (status) {
        "open" -> "Открыт" to MaterialTheme.belsiColors.info
        "in_progress" -> "В работе" to MaterialTheme.belsiColors.warning
        "resolved" -> "Решён" to MaterialTheme.belsiColors.success
        "closed" -> "Закрыт" to MaterialTheme.colorScheme.outline
        else -> status to MaterialTheme.colorScheme.outline
    }
    Surface(shape = MaterialTheme.shapes.medium, color = color) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

private fun getCategoryText(category: String) = when (category) {
    "general" -> "Общий"
    "technical" -> "Технический"
    "payment" -> "Оплата"
    "tools" -> "Инструменты"
    else -> category
}

private fun formatDate(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        val dt = OffsetDateTime.parse(isoString)
        val now = OffsetDateTime.now()
        val formatter = when {
            dt.toLocalDate() == now.toLocalDate() -> DateTimeFormatter.ofPattern("HH:mm")
            dt.toLocalDate() == now.toLocalDate().minusDays(1) -> DateTimeFormatter.ofPattern("'Вчера'")
            else -> DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
        }
        dt.format(formatter)
    } catch (_: Exception) { isoString.take(10) }
}

enum class SupportFilter {
    ALL, OPEN, IN_PROGRESS, RESOLVED
}
