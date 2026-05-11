package com.belsi.work.presentation.screens.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.models.ToolCondition
import com.belsi.work.data.models.ToolTransaction
import java.io.File

/**
 * Экран возврата инструмента
 * Фото при возврате ОБЯЗАТЕЛЬНО
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolReturnScreen(
    navController: NavController,
    transaction: ToolTransaction,
    viewModel: ToolReturnViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var photoFile by remember { mutableStateOf<File?>(null) }
    var showConditionSelector by remember { mutableStateOf(false) }

    // Установить транзакцию
    LaunchedEffect(transaction) {
        viewModel.setTransaction(transaction)
    }

    // Обработка успешного возврата
    LaunchedEffect(uiState.returnedTransaction) {
        if (uiState.returnedTransaction != null) {
            navController.popBackStack()
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile != null) {
            viewModel.setPhotoUri(Uri.fromFile(photoFile))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вернуть инструмент") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Информация об инструменте
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Информация об инструменте",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = transaction.toolId, // TODO: показать имя инструмента
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Выдан: ${formatDate(transaction.issuedAt)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Состояние инструмента
            item {
                Text(
                    text = "Состояние инструмента *",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showConditionSelector = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getConditionIcon(uiState.condition),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = getConditionColor(uiState.condition)
                            )
                            Column {
                                Text(
                                    text = getConditionText(uiState.condition),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = getConditionDescription(uiState.condition),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                }
            }

            // Комментарий
            item {
                OutlinedTextField(
                    value = uiState.comment,
                    onValueChange = { viewModel.setComment(it) },
                    label = { Text("Комментарий (опционально)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    },
                    placeholder = { Text("Напр: Все работает исправно") }
                )
            }

            // Фото (ОБЯЗАТЕЛЬНО)
            item {
                Text(
                    text = "Фото при возврате *",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Обязательно сделайте фото инструмента при возврате",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val file = File(context.cacheDir, "tool_return_${System.currentTimeMillis()}.jpg")
                        photoFile = file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        cameraLauncher.launch(uri)
                    },
                    colors = if (uiState.photoUri == null) {
                        CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                        )
                    } else {
                        CardDefaults.outlinedCardColors()
                    }
                ) {
                    if (uiState.photoUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            AsyncImage(
                                model = uiState.photoUri,
                                contentDescription = "Фото инструмента при возврате",
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { viewModel.setPhotoUri(null); photoFile = null },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Удалить фото",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Нажмите, чтобы сделать фото",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Фото обязательно для возврата инструмента",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Ошибка
            if (uiState.error != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = uiState.error!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Кнопка вернуть
            item {
                Button(
                    onClick = {
                        viewModel.returnTool(context, photoFile)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.canReturn
                ) {
                    if (uiState.isReturning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Возвращаем...")
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Вернуть инструмент")
                    }
                }
            }
        }

        // Диалог выбора состояния
        if (showConditionSelector) {
            ConditionSelectorDialog(
                currentCondition = uiState.condition,
                onSelect = { condition ->
                    viewModel.setCondition(condition)
                    showConditionSelector = false
                },
                onDismiss = { showConditionSelector = false }
            )
        }
    }
}

/**
 * Диалог выбора состояния инструмента
 */
@Composable
fun ConditionSelectorDialog(
    currentCondition: ToolCondition,
    onSelect: (ToolCondition) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Состояние инструмента") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolCondition.values().forEach { condition ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(condition) },
                        colors = if (condition == currentCondition) {
                            CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            CardDefaults.outlinedCardColors()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getConditionIcon(condition),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = getConditionColor(condition)
                            )
                            Column {
                                Text(
                                    text = getConditionText(condition),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = getConditionDescription(condition),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

/**
 * Получить иконку для состояния
 */
@Composable
private fun getConditionIcon(condition: ToolCondition) = when (condition) {
    ToolCondition.GOOD -> Icons.Default.CheckCircle
    ToolCondition.DAMAGED -> Icons.Default.Warning
    ToolCondition.BROKEN -> Icons.Default.Close
}

/**
 * Получить цвет для состояния
 */
@Composable
private fun getConditionColor(condition: ToolCondition) = when (condition) {
    ToolCondition.GOOD -> MaterialTheme.colorScheme.tertiary
    ToolCondition.DAMAGED -> MaterialTheme.colorScheme.secondary
    ToolCondition.BROKEN -> MaterialTheme.colorScheme.error
}

/**
 * Получить текст для состояния
 */
private fun getConditionText(condition: ToolCondition) = when (condition) {
    ToolCondition.GOOD -> "Хорошее"
    ToolCondition.DAMAGED -> "Поврежден"
    ToolCondition.BROKEN -> "Сломан"
}

/**
 * Получить описание для состояния
 */
private fun getConditionDescription(condition: ToolCondition) = when (condition) {
    ToolCondition.GOOD -> "Инструмент в рабочем состоянии"
    ToolCondition.DAMAGED -> "Есть повреждения, но работает"
    ToolCondition.BROKEN -> "Требует ремонта или замены"
}

/**
 * Форматирование даты
 */
private fun formatDate(dateString: String): String {
    return try {
        val dateTime = java.time.LocalDateTime.parse(
            dateString,
            java.time.format.DateTimeFormatter.ISO_DATE_TIME
        )
        dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    } catch (e: Exception) {
        dateString
    }
}
