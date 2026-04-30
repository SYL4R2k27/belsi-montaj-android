package com.belsi.work.presentation.screens.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.models.Tool
import java.io.File

/**
 * Экран выдачи инструмента
 * Используется бригадиром и монтажником (для самовыдачи)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolIssueScreen(
    navController: NavController,
    installerId: String? = null, // Если null - показываем выбор монтажника
    viewModel: ToolIssueViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showToolSelector by remember { mutableStateOf(false) }
    var showUserPicker by remember { mutableStateOf(false) }
    var photoFile by remember { mutableStateOf<File?>(null) }

    // Установить installerId если передан (самовыдача)
    LaunchedEffect(installerId) {
        if (!installerId.isNullOrBlank()) {
            viewModel.setInstallerId(installerId)
        }
    }

    // Отслеживание успешной выдачи
    var wasIssuing by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isIssuing, uiState.error) {
        // Если была выдача (isIssuing был true), но теперь false и нет ошибки - значит успешно
        if (wasIssuing && !uiState.isIssuing && uiState.error == null) {
            navController.popBackStack()
        }
        wasIssuing = uiState.isIssuing
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
                title = { Text("Выдать инструмент") },
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
            // Выбор инструментов (множественный выбор)
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showToolSelector = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Инструменты",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (uiState.selectedCount == 0)
                                    "Выберите инструменты"
                                else
                                    "Выбрано: ${uiState.selectedCount}/40",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (uiState.selectedCount > 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (uiState.selectedCount > 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                }
            }

            // Монтажник (если не самовыдача)
            // Для installer не показываем поле - ID автоматически заполняется
            if (!uiState.isSelfIssue && installerId.isNullOrBlank()) {
                item {
                    // Для foreman/curator - кнопка выбора из списка
                    if (uiState.canPickUser) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showUserPicker = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (uiState.installerId.isBlank())
                                                "Выберите монтажника"
                                            else
                                                "Монтажник выбран",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (uiState.installerId.isBlank())
                                                FontWeight.Normal
                                            else
                                                FontWeight.Medium
                                        )
                                        if (uiState.installerId.isNotBlank()) {
                                            Text(
                                                text = "ID: ${uiState.installerId.take(8)}...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Fallback для ручного ввода (если команда не загрузилась)
                        OutlinedTextField(
                            value = uiState.installerId,
                            onValueChange = { viewModel.setInstallerId(it) },
                            label = { Text("ID монтажника") },
                            placeholder = { Text("Введите ID монтажника") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        )
                    }
                }
            } else if (uiState.isSelfIssue) {
                // Показываем информацию о самовыдаче
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Самовыдача: инструмент будет выдан вам",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Комментарий (опционально)
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
                    placeholder = { Text("Напр: Новый, в отличном состоянии") }
                )
            }

            // Фото (опционально)
            item {
                Text(
                    text = "Фото при выдаче (опционально)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val file = File(context.cacheDir, "tool_issue_${System.currentTimeMillis()}.jpg")
                        photoFile = file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        cameraLauncher.launch(uri)
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
                                contentDescription = "Фото инструмента",
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Нажмите, чтобы сделать фото",
                                style = MaterialTheme.typography.bodyLarge
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

            // Кнопка выдать
            item {
                Button(
                    onClick = {
                        viewModel.issueTool(context, photoFile)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.canIssue
                ) {
                    if (uiState.isIssuing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выдаем...")
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.selectedCount > 1)
                                "Выдать ${uiState.selectedCount} инструментов"
                            else
                                "Выдать инструмент"
                        )
                    }
                }
            }
        }

        // Диалог выбора инструментов (множественный выбор)
        if (showToolSelector) {
            ToolSelectorDialog(
                tools = uiState.availableTools,
                selectedTools = uiState.selectedTools,
                isLoading = uiState.isLoadingTools,
                onToggleSelect = { tool ->
                    viewModel.toggleToolSelection(tool)
                },
                onDone = { showToolSelector = false },
                onDismiss = { showToolSelector = false },
                onRetry = { viewModel.loadTools() }
            )
        }

        // Диалог выбора пользователя (для foreman/curator)
        if (showUserPicker) {
            // Конвертируем ForemanTeamMemberDto в TeamMemberDto для UserPickerDialog
            val teamMemberDtos = uiState.teamMembers.mapNotNull { member ->
                member.userId?.let { id ->
                    com.belsi.work.data.remote.dto.team.TeamMemberDto(
                        id = id,
                        fullName = member.displayName(),
                        phone = member.phone,
                        isActive = true
                    )
                }
            }

            com.belsi.work.presentation.components.UserPickerDialog(
                users = teamMemberDtos,
                isLoading = uiState.isLoadingTeam,
                onUserSelected = { user ->
                    viewModel.setInstallerId(user.id)
                    showUserPicker = false
                },
                onDismiss = { showUserPicker = false }
            )
        }
    }
}

/**
 * Диалог выбора инструментов (множественный выбор)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSelectorDialog(
    tools: List<Tool>,
    selectedTools: List<Tool>,
    isLoading: Boolean,
    onToggleSelect: (Tool) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val selectedCount = selectedTools.size
    val maxTools = 40

    AlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Заголовок с счетчиком
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Выберите инструменты",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Счетчик выбранных
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (selectedCount > 0)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "$selectedCount/$maxTools",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedCount > 0)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    tools.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Нет доступных инструментов",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = onRetry) {
                                Text("Обновить")
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tools) { tool ->
                                val isSelected = selectedTools.any { it.id == tool.id }

                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onToggleSelect(tool) },
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Чекбокс
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { onToggleSelect(tool) }
                                        )

                                        Icon(
                                            imageVector = Icons.Default.Build,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            tint = if (isSelected)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tool.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            if (!tool.serialNumber.isNullOrBlank()) {
                                                Text(
                                                    text = "S/N: ${tool.serialNumber}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = onDone,
                        modifier = Modifier.weight(1f),
                        enabled = selectedCount > 0
                    ) {
                        Text("Готово ($selectedCount)")
                    }
                }
            }
        }
    }
}
