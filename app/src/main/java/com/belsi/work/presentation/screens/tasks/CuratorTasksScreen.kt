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
import com.belsi.work.data.remote.dto.curator.AllUserDto
import com.belsi.work.data.remote.dto.curator.ForemanDto
import com.belsi.work.data.remote.dto.curator.UnassignedInstallerDto
import com.belsi.work.presentation.theme.belsiColors

/**
 * Модель пользователя для выбора при создании задачи
 */
data class AssignableUser(
    val id: String,
    val name: String,
    val phone: String,
    val role: String // "foreman", "installer" или "coordinator"
)

/**
 * Экран задач для куратора
 * Позволяет создавать задачи для бригадиров и монтажников
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorTasksScreen(
    foremen: List<ForemanDto>,
    unassignedInstallers: List<UnassignedInstallerDto>,
    coordinators: List<AllUserDto> = emptyList(),
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val error by viewModel.error.collectAsState()

    var showCreateTaskDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Собираем список всех пользователей для назначения задач
    val assignableUsers = remember(foremen, unassignedInstallers, coordinators) {
        val users = mutableListOf<AssignableUser>()

        // Добавляем координаторов
        coordinators.forEach { coord ->
            users.add(AssignableUser(
                id = coord.id,
                name = coord.displayName,
                phone = coord.phone,
                role = "coordinator"
            ))
        }

        // Добавляем бригадиров
        foremen.forEach { foreman ->
            users.add(AssignableUser(
                id = foreman.id,
                name = foreman.displayName,
                phone = foreman.phone,
                role = "foreman"
            ))

            // Добавляем монтажников из команд бригадиров
            foreman.installers.forEach { installer ->
                users.add(AssignableUser(
                    id = installer.id,
                    name = installer.displayName,
                    phone = installer.phone,
                    role = "installer"
                ))
            }
        }

        // Добавляем незакрепленных монтажников
        unassignedInstallers.forEach { installer ->
            users.add(AssignableUser(
                id = installer.id,
                name = installer.displayName,
                phone = installer.phone,
                role = "installer"
            ))
        }

        users
    }

    LaunchedEffect(Unit) {
        viewModel.loadCreatedTasks()
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
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Индикатор загрузки
            if (uiState.isLoadingCreated) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Статистика
            CuratorTasksStatistics(
                tasks = uiState.createdTasks,
                modifier = Modifier.padding(16.dp)
            )

            // Список задач
            if (uiState.createdTasks.isEmpty() && !uiState.isLoadingCreated) {
                EmptyCreatedTasksContent()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Группируем по статусу
                    val activeTasks = uiState.createdTasks.filter {
                        it.status == TaskStatus.NEW || it.status == TaskStatus.IN_PROGRESS
                    }
                    val completedTasks = uiState.createdTasks.filter {
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
                            CuratorTaskCard(
                                task = task,
                                assignableUsers = assignableUsers
                            )
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
    }

    // Диалог создания задачи
    if (showCreateTaskDialog) {
        CuratorCreateTaskDialog(
            assignableUsers = assignableUsers,
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
private fun CuratorTasksStatistics(
    tasks: List<Task>,
    modifier: Modifier = Modifier
) {
    val newCount = tasks.count { it.status == TaskStatus.NEW }
    val inProgressCount = tasks.count { it.status == TaskStatus.IN_PROGRESS }
    val doneCount = tasks.count { it.status == TaskStatus.DONE }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "Новые",
            value = "$newCount",
            color = MaterialTheme.belsiColors.info,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "В работе",
            value = "$inProgressCount",
            color = MaterialTheme.belsiColors.warning,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Выполнены",
            value = "$doneCount",
            color = MaterialTheme.belsiColors.success,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyCreatedTasksContent() {
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
            "Нажмите + чтобы создать задачу\nдля бригадира или монтажника",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CuratorTaskCard(
    task: Task,
    assignableUsers: List<AssignableUser>
) {
    val assignedUser = assignableUsers.find { it.id == task.assignedTo }

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
                assignedUser?.let { user ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (user.role) {
                                "foreman" -> Icons.Default.SupervisorAccount
                                "coordinator" -> Icons.Default.Engineering
                                else -> Icons.Default.Person
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when (user.role) {
                                "foreman" -> MaterialTheme.colorScheme.primary
                                "coordinator" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorCreateTaskDialog(
    assignableUsers: List<AssignableUser>,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreateTask: (title: String, description: String?, assignedTo: String, priority: String) -> Unit,
    onCreateBatchTasks: (title: String, description: String?, assignedToIds: List<String>, priority: String) -> Unit = { _, _, _, _ -> }
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedUsers by remember { mutableStateOf<Set<AssignableUser>>(emptySet()) }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }
    var showUserSelection by remember { mutableStateOf(false) }
    var filterRole by remember { mutableStateOf<String?>(null) } // null = все, "foreman", "installer"

    val filteredUsers = remember(assignableUsers, filterRole) {
        if (filterRole == null) {
            assignableUsers
        } else {
            assignableUsers.filter { it.role == filterRole }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая задача") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    maxLines = 3,
                    enabled = !isCreating
                )

                // Фильтр по роли
                Text(
                    "Назначить исполнителям",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterRole == null,
                        onClick = { filterRole = null },
                        label = { Text("Все") },
                        enabled = !isCreating
                    )
                    FilterChip(
                        selected = filterRole == "foreman",
                        onClick = { filterRole = "foreman" },
                        label = { Text("Бригадиры") },
                        enabled = !isCreating,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    FilterChip(
                        selected = filterRole == "coordinator",
                        onClick = { filterRole = "coordinator" },
                        label = { Text("Координаторы") },
                        enabled = !isCreating,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = com.belsi.work.presentation.theme.Violet500.copy(alpha = 0.2f),
                            selectedLabelColor = com.belsi.work.presentation.theme.Violet500
                        )
                    )
                    FilterChip(
                        selected = filterRole == "installer",
                        onClick = { filterRole = "installer" },
                        label = { Text("Монтажники") },
                        enabled = !isCreating
                    )
                }

                // Отображение выбранных пользователей
                if (selectedUsers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedUsers.forEach { user ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    if (!isCreating) {
                                        selectedUsers = selectedUsers - user
                                    }
                                },
                                label = { Text(user.name) },
                                avatar = {
                                    Icon(
                                        imageVector = when (user.role) {
                                            "foreman" -> Icons.Default.SupervisorAccount
                                            "coordinator" -> Icons.Default.Engineering
                                            else -> Icons.Default.Person
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
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
                }

                // Кнопка выбора исполнителей
                OutlinedButton(
                    onClick = { showUserSelection = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreating && assignableUsers.isNotEmpty()
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (selectedUsers.isEmpty()) "Выбрать исполнителей"
                        else "Изменить выбор (${selectedUsers.size})"
                    )
                }

                if (assignableUsers.isEmpty()) {
                    Text(
                        "Нет доступных пользователей",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
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
                    FilterChip(
                        selected = priority == TaskPriority.LOW,
                        onClick = { priority = TaskPriority.LOW },
                        label = { Text("Низкий") },
                        enabled = !isCreating,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.belsiColors.success.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.belsiColors.success
                        )
                    )
                    FilterChip(
                        selected = priority == TaskPriority.NORMAL,
                        onClick = { priority = TaskPriority.NORMAL },
                        label = { Text("Обычный") },
                        enabled = !isCreating,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.belsiColors.warning.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.belsiColors.warning
                        )
                    )
                    FilterChip(
                        selected = priority == TaskPriority.HIGH,
                        onClick = { priority = TaskPriority.HIGH },
                        label = { Text("Высокий") },
                        enabled = !isCreating,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ids = selectedUsers.map { it.id }
                    if (ids.size == 1) {
                        onCreateTask(
                            title,
                            description.ifBlank { null },
                            ids.first(),
                            priority
                        )
                    } else {
                        onCreateBatchTasks(
                            title,
                            description.ifBlank { null },
                            ids,
                            priority
                        )
                    }
                },
                enabled = title.isNotBlank() && selectedUsers.isNotEmpty() && !isCreating,
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
                        if (selectedUsers.size > 1)
                            "Создать для ${selectedUsers.size} чел."
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
    if (showUserSelection) {
        CuratorUserSelectionDialog(
            users = filteredUsers,
            selectedUsers = selectedUsers,
            filterRole = filterRole,
            onFilterRoleChange = { filterRole = it },
            onDismiss = { showUserSelection = false },
            onConfirm = { selected ->
                selectedUsers = selected
                showUserSelection = false
            }
        )
    }
}

/**
 * Диалог выбора нескольких исполнителей для куратора
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CuratorUserSelectionDialog(
    users: List<AssignableUser>,
    selectedUsers: Set<AssignableUser>,
    filterRole: String?,
    onFilterRoleChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Set<AssignableUser>) -> Unit
) {
    var currentSelection by remember { mutableStateOf(selectedUsers) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбор исполнителей") },
        text = {
            Column {
                // Фильтр по роли
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterRole == null,
                        onClick = { onFilterRoleChange(null) },
                        label = { Text("Все") }
                    )
                    FilterChip(
                        selected = filterRole == "foreman",
                        onClick = { onFilterRoleChange("foreman") },
                        label = { Text("Бригадиры") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    )
                    FilterChip(
                        selected = filterRole == "coordinator",
                        onClick = { onFilterRoleChange("coordinator") },
                        label = { Text("Координаторы") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = com.belsi.work.presentation.theme.Violet500.copy(alpha = 0.2f)
                        )
                    )
                    FilterChip(
                        selected = filterRole == "installer",
                        onClick = { onFilterRoleChange("installer") },
                        label = { Text("Монтажники") }
                    )
                }

                // Кнопки выбора
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Выбрано: ${currentSelection.size} из ${users.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            currentSelection = if (currentSelection.containsAll(users)) {
                                currentSelection - users.toSet()
                            } else {
                                currentSelection + users.toSet()
                            }
                        }
                    ) {
                        Text(
                            if (currentSelection.containsAll(users))
                                "Снять все"
                            else
                                "Выбрать всех"
                        )
                    }
                }

                HorizontalDivider()

                // Список пользователей
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(users) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSelection = if (user in currentSelection) {
                                        currentSelection - user
                                    } else {
                                        currentSelection + user
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = user in currentSelection,
                                onCheckedChange = { checked ->
                                    currentSelection = if (checked) {
                                        currentSelection + user
                                    } else {
                                        currentSelection - user
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = when (user.role) {
                                    "foreman" -> Icons.Default.SupervisorAccount
                                    "coordinator" -> Icons.Default.Engineering
                                    else -> Icons.Default.Person
                                },
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = when (user.role) {
                                    "foreman" -> MaterialTheme.colorScheme.primary
                                    "coordinator" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = getRoleLabel(user.role),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (user.role) {
                                        "foreman" -> MaterialTheme.colorScheme.primary
                                        "coordinator" -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
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

private fun getRoleLabel(role: String): String {
    return when (role) {
        "foreman" -> "Бригадир"
        "coordinator" -> "Координатор"
        "installer" -> "Монтажник"
        else -> role
    }
}
