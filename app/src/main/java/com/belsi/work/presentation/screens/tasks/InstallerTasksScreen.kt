package com.belsi.work.presentation.screens.tasks

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.belsi.work.data.models.Task
import com.belsi.work.data.models.TaskStatus
import com.belsi.work.data.models.TaskPriority
import com.belsi.work.presentation.theme.belsiColors
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Экран задач для монтажника
 * Показывает назначенные задачи с возможностью их выполнения
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerTasksScreen(
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadMyTasks()
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

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Все задачи", "Сегодня")

    // Фильтрация задач на сегодня
    val today = remember { LocalDate.now() }
    val todayTasks = remember(uiState.myTasks, today) {
        uiState.myTasks.filter { task ->
            task.dueAt?.let { dueAt ->
                try {
                    val taskDate = OffsetDateTime.parse(dueAt).toLocalDate()
                    taskDate == today
                } catch (e: Exception) {
                    try {
                        val taskDate = LocalDate.parse(dueAt.take(10))
                        taskDate == today
                    } catch (e2: Exception) {
                        false
                    }
                }
            } ?: false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Мои задачи") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Индикатор загрузки
            if (uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Табы: Все задачи / Сегодня
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title)
                                if (index == 1 && todayTasks.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text("${todayTasks.size}")
                                    }
                                }
                            }
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> AllTasksTab(uiState, viewModel)
                1 -> TodayChecklistTab(todayTasks, uiState, viewModel)
            }
        }
    }
}

@Composable
private fun AllTasksTab(uiState: TasksUiState, viewModel: TasksViewModel) {
    Column {
        // Статистика задач
        TasksStatisticsRow(
            tasks = uiState.myTasks,
            modifier = Modifier.padding(16.dp)
        )

        // Список задач
        if (uiState.myTasks.isEmpty() && !uiState.isLoading) {
            EmptyTasksContent()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val activeTasks = uiState.myTasks.filter {
                    it.status == TaskStatus.NEW || it.status == TaskStatus.IN_PROGRESS
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
                        InstallerTaskCard(
                            task = task,
                            isUpdating = uiState.updatingTaskId == task.id,
                            onStartTask = { viewModel.startTask(task.id) },
                            onCompleteTask = { viewModel.completeTask(task.id) }
                        )
                    }
                }

                val completedTasks = uiState.myTasks.filter {
                    it.status == TaskStatus.DONE || it.status == TaskStatus.CANCELLED
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

@Composable
private fun TodayChecklistTab(
    todayTasks: List<Task>,
    uiState: TasksUiState,
    viewModel: TasksViewModel
) {
    if (todayTasks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.EventAvailable,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.belsiColors.success
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "На сегодня задач нет",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Задачи с дедлайном на сегодня появятся здесь",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val doneCount = todayTasks.count { it.status == TaskStatus.DONE }
        val progress = if (todayTasks.isNotEmpty()) doneCount.toFloat() / todayTasks.size else 0f

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Прогресс бар
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Прогресс на сегодня",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "$doneCount из ${todayTasks.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.belsiColors.success,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }

            // Задачи как чек-лист
            items(todayTasks) { task ->
                val isDone = task.status == TaskStatus.DONE
                val isUpdating = uiState.updatingTaskId == task.id

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDone)
                            MaterialTheme.belsiColors.success.copy(alpha = 0.06f)
                        else
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Checkbox(
                                checked = isDone,
                                onCheckedChange = { checked ->
                                    if (checked && !isDone) {
                                        if (task.status == TaskStatus.NEW) {
                                            viewModel.startTask(task.id)
                                        }
                                        viewModel.completeTask(task.id)
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.belsiColors.success
                                )
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (!isDone) FontWeight.Medium else FontWeight.Normal,
                                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (!task.description.isNullOrBlank()) {
                                Text(
                                    text = task.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        PriorityBadge(
                            priority = task.priority,
                            alpha = if (isDone) 0.5f else 1f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksStatisticsRow(
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
        // Новые
        MiniStatCard(
            title = "Новые",
            value = "$newCount",
            color = MaterialTheme.belsiColors.info,
            modifier = Modifier.weight(1f)
        )

        // В работе
        MiniStatCard(
            title = "В работе",
            value = "$inProgressCount",
            color = MaterialTheme.belsiColors.warning,
            modifier = Modifier.weight(1f)
        )

        // Выполнены
        MiniStatCard(
            title = "Выполнены",
            value = "$doneCount",
            color = MaterialTheme.belsiColors.success,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MiniStatCard(
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
private fun EmptyTasksContent() {
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
            "Задачи от бригадира появятся здесь",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InstallerTaskCard(
    task: Task,
    isUpdating: Boolean,
    onStartTask: () -> Unit,
    onCompleteTask: () -> Unit
) {
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
            // Заголовок и приоритет
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
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Бейдж приоритета
                PriorityBadge(priority = task.priority)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Статус и кнопки действий
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Статус бейдж
                StatusBadge(status = task.status)

                // Кнопки действий
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    when (task.status) {
                        TaskStatus.NEW -> {
                            Button(
                                onClick = onStartTask,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.belsiColors.info
                                ),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Начать")
                            }
                        }
                        TaskStatus.IN_PROGRESS -> {
                            Button(
                                onClick = onCompleteTask,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.belsiColors.success
                                ),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Выполнено")
                            }
                        }
                        else -> {}
                    }
                }
            }

            // Дата выполнения если есть
            task.dueAt?.let { dueAt ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "До: ${formatDueDate(dueAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedTaskCard(task: Task) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка статуса
            Icon(
                imageVector = if (task.status == TaskStatus.DONE)
                    Icons.Default.CheckCircle
                else
                    Icons.Default.Cancel,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (task.status == TaskStatus.DONE)
                    MaterialTheme.belsiColors.success
                else
                    MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (task.status == TaskStatus.DONE) "Выполнено" else "Отменено",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            PriorityBadge(priority = task.priority, alpha = 0.5f)
        }
    }
}

@Composable
fun PriorityBadge(priority: String, alpha: Float = 1f) {
    val (color, text) = when (priority) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.error to "Высокий"
        TaskPriority.LOW -> MaterialTheme.belsiColors.success to "Низкий"
        else -> MaterialTheme.belsiColors.warning to "Обычный"
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.1f * alpha)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = alpha),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StatusBadge(status: String) {
    val (color, text) = when (status) {
        TaskStatus.NEW -> MaterialTheme.belsiColors.info to "Новая"
        TaskStatus.IN_PROGRESS -> MaterialTheme.belsiColors.warning to "В работе"
        TaskStatus.DONE -> MaterialTheme.belsiColors.success to "Выполнено"
        TaskStatus.CANCELLED -> MaterialTheme.colorScheme.outline to "Отменено"
        else -> MaterialTheme.colorScheme.outline to status
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDueDate(dueAt: String): String {
    return try {
        // Простое форматирование ISO даты
        dueAt.take(10)
    } catch (e: Exception) {
        dueAt
    }
}
