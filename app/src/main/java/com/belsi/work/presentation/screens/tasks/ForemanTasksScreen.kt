package com.belsi.work.presentation.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.belsi.work.data.models.Task
import com.belsi.work.data.models.TaskStatus
import com.belsi.work.data.models.TaskPriority
import com.belsi.work.presentation.screens.foreman.TeamMember
import com.belsi.work.presentation.theme.belsiColors

/**
 * Экран задач для бригадира
 * Позволяет создавать задачи для монтажников и просматривать свои задачи от куратора
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForemanTasksScreen(
    teamMembers: List<TeamMember>,
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadAllTasks()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.createSuccess) {
        if (uiState.createSuccess) {
            showCreateTaskDialog = false
            val message = if (uiState.lastBatchCreatedCount > 1) {
                "Создано задач: ${uiState.lastBatchCreatedCount}"
            } else {
                "Задача создана"
            }
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.resetCreateSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == 1) { // На вкладке "Созданные"
                FloatingActionButton(
                    onClick = { showCreateTaskDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Создать задачу",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Индикатор загрузки
            if (uiState.isLoading || uiState.isLoadingCreated) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Табы
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Мои задачи") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Для команды") }
                )
            }

            when (selectedTab) {
                0 -> MyTasksTab(
                    tasks = uiState.myTasks,
                    updatingTaskId = uiState.updatingTaskId,
                    onStartTask = { viewModel.startTask(it) },
                    onCompleteTask = { viewModel.completeTask(it) }
                )
                1 -> CreatedTasksTab(
                    tasks = uiState.createdTasks
                )
            }
        }
    }

    // Диалог создания задачи
    if (showCreateTaskDialog) {
        CreateTaskDialog(
            teamMembers = teamMembers,
            isCreating = uiState.isCreating,
            onDismiss = { showCreateTaskDialog = false },
            onCreateTask = { title, description, assignedTo, priority ->
                viewModel.createTask(
                    title = title,
                    description = description,
                    assignedTo = assignedTo,
                    priority = priority
                )
            },
            onCreateBatchTasks = { title, description, assignedToIds, priority ->
                viewModel.createBatchTasks(
                    title = title,
                    description = description,
                    assignedToIds = assignedToIds,
                    priority = priority
                )
            }
        )
    }
}

@Composable
private fun MyTasksTab(
    tasks: List<Task>,
    updatingTaskId: String?,
    onStartTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit
) {
    if (tasks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Нет задач",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Задачи от куратора появятся здесь",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tasks) { task ->
                InstallerTaskCard(
                    task = task,
                    isUpdating = updatingTaskId == task.id,
                    onStartTask = { onStartTask(task.id) },
                    onCompleteTask = { onCompleteTask(task.id) }
                )
            }
        }
    }
}

@Composable
private fun CreatedTasksTab(tasks: List<Task>) {
    if (tasks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Assignment,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Нет созданных задач",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Нажмите + чтобы создать задачу для монтажника",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Группируем по статусу
            val activeTasks = tasks.filter {
                it.status == TaskStatus.NEW || it.status == TaskStatus.IN_PROGRESS
            }
            val completedTasks = tasks.filter {
                it.status == TaskStatus.DONE || it.status == TaskStatus.CANCELLED
            }

            if (activeTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Активные (${activeTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(activeTasks) { task ->
                    CreatedTaskCard(task = task)
                }
            }

            if (completedTasks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Завершённые (${completedTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(completedTasks) { task ->
                    CompletedTaskCard(task = task)
                }
            }
        }
    }
}

@Composable
private fun CreatedTaskCard(task: Task) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!task.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                PriorityBadge(priority = task.priority)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = task.status)

                // Показываем кому назначено
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Назначено",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskDialog(
    teamMembers: List<TeamMember>,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreateTask: (title: String, description: String?, assignedTo: String, priority: String) -> Unit,
    onCreateBatchTasks: (title: String, description: String?, assignedToIds: List<String>, priority: String) -> Unit = { _, _, _, _ -> }
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf<Set<TeamMember>>(emptySet()) }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }
    var showMemberSelection by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая задача") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Название задачи
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название задачи") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCreating
                )

                // Описание
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (опционально)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isCreating
                )

                // Выбор исполнителей (множественный)
                Column {
                    Text(
                        "Назначить исполнителям",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Отображение выбранных
                    if (selectedMembers.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedMembers.forEach { member ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        if (!isCreating) {
                                            selectedMembers = selectedMembers - member
                                        }
                                    },
                                    label = { Text(member.name) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Удалить",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    enabled = !isCreating
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Кнопка добавления исполнителей
                    OutlinedButton(
                        onClick = { showMemberSelection = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreating && teamMembers.isNotEmpty()
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (selectedMembers.isEmpty()) "Выбрать исполнителей"
                            else "Добавить ещё (${selectedMembers.size} выбрано)"
                        )
                    }

                    if (teamMembers.isEmpty()) {
                        Text(
                            "Нет монтажников в команде",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Выбор приоритета
                Text(
                    "Приоритет",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriorityChip(
                        label = "Низкий",
                        selected = priority == TaskPriority.LOW,
                        color = MaterialTheme.belsiColors.success,
                        onClick = { priority = TaskPriority.LOW },
                        enabled = !isCreating
                    )
                    PriorityChip(
                        label = "Обычный",
                        selected = priority == TaskPriority.NORMAL,
                        color = MaterialTheme.belsiColors.warning,
                        onClick = { priority = TaskPriority.NORMAL },
                        enabled = !isCreating
                    )
                    PriorityChip(
                        label = "Высокий",
                        selected = priority == TaskPriority.HIGH,
                        color = MaterialTheme.colorScheme.error,
                        onClick = { priority = TaskPriority.HIGH },
                        enabled = !isCreating
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ids = selectedMembers.map { it.id }
                    if (ids.size == 1) {
                        // Одиночное создание
                        onCreateTask(
                            title,
                            description.ifBlank { null },
                            ids.first(),
                            priority
                        )
                    } else {
                        // Batch-создание
                        onCreateBatchTasks(
                            title,
                            description.ifBlank { null },
                            ids,
                            priority
                        )
                    }
                },
                enabled = title.isNotBlank() && selectedMembers.isNotEmpty() && !isCreating,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (selectedMembers.size > 1)
                            "Создать для ${selectedMembers.size} чел."
                        else
                            "Создать"
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Отмена")
            }
        }
    )

    // Диалог выбора исполнителей
    if (showMemberSelection) {
        MemberSelectionDialog(
            teamMembers = teamMembers,
            selectedMembers = selectedMembers,
            onDismiss = { showMemberSelection = false },
            onConfirm = { selected ->
                selectedMembers = selected
                showMemberSelection = false
            }
        )
    }
}

/**
 * Диалог выбора нескольких исполнителей
 */
@Composable
private fun MemberSelectionDialog(
    teamMembers: List<TeamMember>,
    selectedMembers: Set<TeamMember>,
    onDismiss: () -> Unit,
    onConfirm: (Set<TeamMember>) -> Unit
) {
    var currentSelection by remember { mutableStateOf(selectedMembers) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбор исполнителей") },
        text = {
            Column {
                // Кнопка "Выбрать всех"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Выбрано: ${currentSelection.size} из ${teamMembers.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            currentSelection = if (currentSelection.size == teamMembers.size) {
                                emptySet()
                            } else {
                                teamMembers.toSet()
                            }
                        }
                    ) {
                        Text(
                            if (currentSelection.size == teamMembers.size)
                                "Снять все"
                            else
                                "Выбрать всех"
                        )
                    }
                }

                HorizontalDivider()

                // Список монтажников
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(teamMembers) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSelection = if (member in currentSelection) {
                                        currentSelection - member
                                    } else {
                                        currentSelection + member
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = member in currentSelection,
                                onCheckedChange = { checked ->
                                    currentSelection = if (checked) {
                                        currentSelection + member
                                    } else {
                                        currentSelection - member
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = member.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (member.activeShift) {
                                    Text(
                                        text = "На смене",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.belsiColors.success
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentSelection) },
                enabled = currentSelection.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Выбрать (${currentSelection.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color
        )
    )
}
