package com.belsi.work.presentation.screens.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.models.ToolTransaction
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.presentation.navigation.AppRoute
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Экран "Мои инструменты" для монтажника
 * Показывает активные и возвращенные инструменты
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsListScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ToolsListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    val user = prefsManager.getUser()

    Scaffold(
        modifier = modifier,  // Применяем modifier от MainScreen
        topBar = {
            TopAppBar(
                title = { Text("Мои инструменты") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            // Show "Request Tool" button if installer has a foreman
            if (user?.foremanId != null) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(AppRoute.RequestTool.createRoute(user.foremanId.toString()))
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Запросить инструмент")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.error != null) {
                ErrorMessage(
                    message = uiState.error!!,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.isEmpty) {
                EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    hasForemanId = user?.foremanId != null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Активные инструменты
                    if (uiState.hasActiveTools) {
                        item {
                            Text(
                                text = "Активные (${uiState.activeTools.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(uiState.activeTools) { transaction ->
                            ActiveToolCard(transaction = transaction)
                        }
                    }

                    // Возвращенные инструменты
                    if (uiState.hasReturnedTools) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Возвращенные (${uiState.returnedTools.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(uiState.returnedTools) { transaction ->
                            ReturnedToolCard(transaction = transaction)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка активного инструмента
 */
@Composable
fun ActiveToolCard(transaction: ToolTransaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Иконка или фото
            if (transaction.issuePhotoUrl != null) {
                AsyncImage(
                    model = transaction.issuePhotoUrl,
                    contentDescription = "Фото инструмента",
                    modifier = Modifier.size(60.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Информация
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = transaction.toolId, // TODO: показать имя инструмента когда будет полная модель
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Выдан: ${formatDate(transaction.issuedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Комментарий при выдаче
                if (!transaction.issueComment.isNullOrBlank()) {
                    Text(
                        text = transaction.issueComment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Статус
                Chip(text = "В работе", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Карточка возвращенного инструмента
 */
@Composable
fun ReturnedToolCard(transaction: ToolTransaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Иконка с галочкой
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )

            // Информация
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = transaction.toolId, // TODO: показать имя инструмента
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Выдан: ${formatDate(transaction.issuedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (transaction.returnedAt != null) {
                    Text(
                        text = "Возвращен: ${formatDate(transaction.returnedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Состояние при возврате
                if (transaction.returnCondition != null) {
                    Chip(
                        text = getConditionText(transaction.returnCondition.name),
                        color = getConditionColor(transaction.returnCondition.name)
                    )
                }
            }
        }
    }
}

/**
 * Чип для статусов и состояний
 */
@Composable
fun Chip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Сообщение об ошибке
 */
@Composable
fun ErrorMessage(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )

        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

/**
 * Пустое состояние
 */
@Composable
fun EmptyState(modifier: Modifier = Modifier, hasForemanId: Boolean = false) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Text(
            text = "У вас пока нет инструментов",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = if (hasForemanId) {
                "Нажмите кнопку + чтобы запросить инструмент у бригадира"
            } else {
                "Сначала присоединитесь к команде бригадира в разделе Профиль"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * Форматирование даты
 */
private fun formatDate(dateString: String): String {
    return try {
        val dateTime = LocalDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME)
        dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    } catch (e: Exception) {
        dateString
    }
}

/**
 * Текст для состояния инструмента
 */
private fun getConditionText(condition: String): String {
    return when (condition) {
        "GOOD" -> "Хорошее"
        "DAMAGED" -> "Поврежден"
        "BROKEN" -> "Сломан"
        else -> condition
    }
}

/**
 * Цвет для состояния инструмента
 */
@Composable
private fun getConditionColor(condition: String): androidx.compose.ui.graphics.Color {
    return when (condition) {
        "GOOD" -> MaterialTheme.colorScheme.tertiary
        "DAMAGED" -> MaterialTheme.colorScheme.secondary
        "BROKEN" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
