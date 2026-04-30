package com.belsi.work.presentation.screens.foreman

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.remote.api.ForemanInviteResponse
import com.belsi.work.data.remote.dto.objects.SiteObjectDto
import com.belsi.work.data.remote.dto.team.*
import com.belsi.work.data.models.Task
import com.belsi.work.presentation.components.AppSessionTracker
import com.belsi.work.presentation.components.WorkTimeTracker
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors
import com.belsi.work.presentation.screens.profile.ProfileScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForemanMainScreen(
    navController: NavController,
    viewModel: ForemanViewModel = hiltViewModel()
) {
    val teamMembers by viewModel.teamMembers.collectAsState()
    val invitesState by viewModel.invitesState.collectAsState()
    val teamPhotos by viewModel.teamPhotos.collectAsState()
    val tools by viewModel.tools.collectAsState()
    val createdTasks by viewModel.createdTasks.collectAsState()
    val myTasks by viewModel.myTasks.collectAsState()
    val teamTasks by viewModel.teamTasks.collectAsState()
    val tasksState by viewModel.tasksState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showInviteDialog by viewModel.showInviteDialog.collectAsState()
    val generatedInviteCode by viewModel.generatedInviteCode.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(ForemanTab.TEAM) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    val activeShiftId by viewModel.activeShiftId.collectAsState()
    val foremanShift by viewModel.foremanShift.collectAsState()

    BackHandler { /* Блокируем возврат */ }

    // Обновление фото после успешной загрузки с камеры
    DisposableEffect(navController) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        val photoLiveData = savedStateHandle?.getLiveData<Boolean>("photo_uploaded")
        val photoObserver = androidx.lifecycle.Observer<Boolean> { uploaded ->
            if (uploaded == true) {
                viewModel.refreshPhotos()
                savedStateHandle?.remove<Boolean>("photo_uploaded")
            }
        }
        photoLiveData?.observeForever(photoObserver)
        onDispose {
            photoLiveData?.removeObserver(photoObserver)
        }
    }

    // Ошибки инвайтов
    LaunchedEffect(invitesState.errorMessage) {
        invitesState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Long)
            viewModel.clearInviteError()
        }
    }

    // Успешное создание задачи
    LaunchedEffect(tasksState.createSuccess) {
        if (tasksState.createSuccess) {
            showCreateTaskDialog = false
            val message = if (tasksState.lastBatchCreatedCount > 1) {
                "Создано задач: ${tasksState.lastBatchCreatedCount}"
            } else {
                "Задача создана"
            }
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.resetTaskCreateSuccess()
        }
    }

    val pendingPhotos = teamPhotos.count { it.status == "pending" }
    val issuedTools = tools.count { it.status == "issued" }
    val activeTasks = myTasks.count { it.status in listOf("new", "in_progress") }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            ForemanTab.entries.forEach { tab ->
                item(
                    icon = {
                        when (tab) {
                            ForemanTab.PHOTOS -> BadgedBox(badge = {
                                if (pendingPhotos > 0) Badge { Text("$pendingPhotos") }
                            }) { Icon(tab.icon, contentDescription = null) }
                            ForemanTab.TASKS -> BadgedBox(badge = {
                                if (activeTasks > 0) Badge { Text("$activeTasks") }
                            }) { Icon(tab.icon, contentDescription = null) }
                            ForemanTab.TOOLS -> BadgedBox(badge = {
                                if (issuedTools > 0) Badge { Text("$issuedTools") }
                            }) { Icon(tab.icon, contentDescription = null) }
                            else -> Icon(tab.icon, contentDescription = null)
                        }
                    },
                    label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Belsi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Бригадир", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                WorkTimeTracker(
                                    startTimeMillis = AppSessionTracker.getSessionStartTime(),
                                    compact = true
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadAllData() }) {
                            Icon(Icons.Default.Refresh, "Обновить", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        IconButton(onClick = { navController.navigate(AppRoute.Settings.route) }) {
                            Icon(Icons.Default.Settings, "Настройки", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                when (selectedTab) {
                    ForemanTab.PHOTOS -> {
                        FloatingActionButton(
                            onClick = {
                                viewModel.getOrCreateShiftId { shiftId ->
                                    navController.navigate("camera/$shiftId/0")
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.CameraAlt, "Сделать фото")
                        }
                    }
                    ForemanTab.TASKS -> {
                        FloatingActionButton(
                            onClick = { showCreateTaskDialog = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.Add, "Создать задачу")
                        }
                    }
                    ForemanTab.CHAT -> {
                        FloatingActionButton(
                            onClick = { viewModel.createInvite() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            if (invitesState.isCreating) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Add, "Создать инвайт")
                            }
                        }
                    }
                    else -> {}
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isLoading && teamMembers.isEmpty() && tools.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    when (selectedTab) {
                        ForemanTab.TEAM -> TeamTab(teamMembers, tools, createdTasks, pendingPhotos, navController, foremanShift, viewModel)
                        ForemanTab.PHOTOS -> PhotosTab(teamPhotos, viewModel, navController)
                        ForemanTab.TASKS -> TasksTabWithSubTabs(
                            myTasks = myTasks,
                            teamTasks = teamTasks,
                            teamMembers = teamMembers.map { TeamMember.fromDto(it) },
                            viewModel = viewModel
                        )
                        ForemanTab.TOOLS -> ToolsTab(tools, navController)
                        ForemanTab.CHAT -> com.belsi.work.presentation.screens.messenger.ChatHubScreen(
                            navController = navController
                        )
                        ForemanTab.PROFILE -> ProfileScreen(navController)
                    }
                }
            }
        }
    }

    // Invite Dialog
    if (showInviteDialog && generatedInviteCode != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInviteDialog() },
            title = { Text("Инвайт-код создан") },
            text = {
                Column {
                    Text("Код для приглашения монтажника:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = generatedInviteCode ?: "",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissInviteDialog() }) { Text("OK") }
            }
        )
    }

    // Диалог создания задачи
    if (showCreateTaskDialog) {
        CreateTaskDialogForForeman(
            teamMembers = teamMembers.map { TeamMember.fromDto(it) },
            isCreating = tasksState.isCreating,
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

// ==========================================
// TAB 0: Команда + Дашборд
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamTab(
    teamMembers: List<ForemanTeamMemberDto>,
    tools: List<ForemanToolDto>,
    tasks: List<ForemanTaskDto>,
    pendingPhotos: Int,
    navController: NavController,
    foremanShift: ForemanShiftState,
    viewModel: ForemanViewModel
) {
    val availableObjects by viewModel.availableObjects.collectAsState()
    val currentObjectName by viewModel.currentObjectName.collectAsState()
    var showEndShiftConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Карточка смены бригадира
        item {
            ForemanShiftCard(
                shiftState = foremanShift,
                onEndShift = { showEndShiftConfirm = true },
                onStartShift = { viewModel.startForemanShift() },
                onTakePhoto = {
                    viewModel.getOrCreateShiftId { shiftId ->
                        navController.navigate("camera/$shiftId/0")
                    }
                }
            )
        }

        // Сводка по бригаде за день
        item {
            val activeWorkers = teamMembers.count { it.isWorkingNow }
            val totalPending = teamMembers.sumOf { it.pendingPhotosCount }
            val totalHoursToday = teamMembers.sumOf { it.totalHours }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Сводка за день",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            value = "$activeWorkers/${teamMembers.size}",
                            label = "На смене",
                            icon = Icons.Default.People,
                            color = if (activeWorkers > 0) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            value = "${"%.1f".format(totalHoursToday)}ч",
                            label = "Часов всего",
                            icon = Icons.Default.Schedule,
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            value = "$totalPending",
                            label = "Фото ожидают",
                            icon = Icons.Default.PhotoCamera,
                            color = if (totalPending > 0) MaterialTheme.belsiColors.warning else MaterialTheme.belsiColors.success
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            value = "${tools.size}",
                            label = "Инструментов",
                            icon = Icons.Default.Build,
                            color = MaterialTheme.belsiColors.info
                        )
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            value = "${tasks.count { it.status == "done" }}/${tasks.size}",
                            label = "Задач выполнено",
                            icon = Icons.Default.CheckCircle,
                            color = MaterialTheme.belsiColors.success
                        )
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            value = "$pendingPhotos",
                            label = "На модерации",
                            icon = Icons.Default.Visibility,
                            color = MaterialTheme.belsiColors.warning
                        )
                    }
                }
            }
        }

        // Текущий объект
        if (availableObjects.isNotEmpty()) {
            item {
                var showObjectPicker by remember { mutableStateOf(false) }
                var showCreateObject by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showObjectPicker = true },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentObjectName != null) MaterialTheme.belsiColors.success.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Business, null, Modifier.size(28.dp),
                            tint = if (currentObjectName != null) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (currentObjectName != null) "Текущий объект" else "Выбрать объект",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                currentObjectName ?: "Нажмите для выбора",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (showObjectPicker) {
                    AlertDialog(
                        onDismissRequest = { showObjectPicker = false },
                        title = { Text("Выбор объекта") },
                        text = {
                            Column {
                                availableObjects.forEach { obj ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.changeObject(obj.id)
                                                showObjectPicker = false
                                            },
                                        shape = MaterialTheme.shapes.small,
                                        color = Color.Transparent
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Business, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(obj.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                if (!obj.address.isNullOrBlank()) {
                                                    Text(obj.address, style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { showObjectPicker = false; showCreateObject = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Создать объект")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showObjectPicker = false }) { Text("Закрыть") }
                        }
                    )
                }

                if (showCreateObject) {
                    var newName by remember { mutableStateOf("") }
                    var newAddress by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showCreateObject = false },
                        title = { Text("Новый объект") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = newName, onValueChange = { newName = it },
                                    label = { Text("Название *") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = newAddress, onValueChange = { newAddress = it },
                                    label = { Text("Адрес") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.createObject(newName, newAddress); showCreateObject = false },
                                enabled = newName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) { Text("Создать") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreateObject = false }) { Text("Отмена") }
                        }
                    )
                }
            }
        }

        // Быстрые действия
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { navController.navigate(AppRoute.ToolIssue.route) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Выдать инструмент", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Монтажнику из команды", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Задачи (если есть)
        if (tasks.isNotEmpty()) {
            item {
                Text("Задачи", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            items(tasks.take(3)) { task ->
                TaskCard(task)
            }
        }

        // Заголовок команды + кнопка "Написать всем"
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (teamMembers.isEmpty()) "Команда" else "Команда (${teamMembers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (teamMembers.isNotEmpty()) {
                    val scope = rememberCoroutineScope()
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.getOrCreateGroupThread { threadId ->
                                    navController.navigate(AppRoute.MessengerConversation.createRoute(threadId))
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Chat, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Написать всем", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Список команды или пустое состояние
        if (teamMembers.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.People, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("Нет монтажников", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Создайте инвайт-код чтобы пригласить", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(teamMembers) { member ->
                TeamMemberCard(
                    member = member,
                    onClick = {
                        // Навигация на экран деталей монтажника
                        navController.navigate(AppRoute.InstallerDetail.createRoute(member.memberId()))
                    }
                )
            }
        }
    }

    // Диалог подтверждения завершения смены
    if (showEndShiftConfirm) {
        AlertDialog(
            onDismissRequest = { showEndShiftConfirm = false },
            title = { Text("Завершить смену?") },
            text = {
                Column {
                    Text("Вы уверены, что хотите завершить текущую смену?")
                    if (foremanShift.elapsedSeconds > 0) {
                        Spacer(Modifier.height(8.dp))
                        val hours = foremanShift.elapsedSeconds / 3600
                        val minutes = (foremanShift.elapsedSeconds % 3600) / 60
                        Text(
                            "Продолжительность: ${hours}ч ${minutes}мин",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndShiftConfirm = false
                        viewModel.endForemanShift()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(onClick = { showEndShiftConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ForemanShiftCard(
    shiftState: ForemanShiftState,
    onEndShift: () -> Unit,
    onStartShift: () -> Unit,
    onTakePhoto: () -> Unit
) {
    val hours = shiftState.elapsedSeconds / 3600
    val minutes = (shiftState.elapsedSeconds % 3600) / 60
    val seconds = shiftState.elapsedSeconds % 60
    val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = if (shiftState.isActive) {
                        Brush.horizontalGradient(
                            colors = listOf(MaterialTheme.belsiColors.success, MaterialTheme.belsiColors.success.copy(alpha = 0.8f))
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            )
                        )
                    },
                    shape = MaterialTheme.shapes.large
                )
                .padding(20.dp)
        ) {
            if (shiftState.isActive) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Смена активна",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            timeString,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onTakePhoto,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Фото", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = onEndShift,
                            modifier = Modifier.weight(1f),
                            enabled = !shiftState.isEnding,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
                        ) {
                            if (shiftState.isEnding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Завершить", fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                // Нет активной смены
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Смена не начата",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onStartShift,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Начать смену")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = color)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

@Composable
private fun TaskCard(task: ForemanTaskDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val statusColor = when (task.status) {
                "done" -> MaterialTheme.belsiColors.success
                "in_progress" -> MaterialTheme.belsiColors.warning
                else -> MaterialTheme.colorScheme.primary
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                task.assignedToName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val priorityColor = when (task.priority) {
                "high" -> MaterialTheme.colorScheme.error
                "medium" -> MaterialTheme.belsiColors.warning
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(shape = MaterialTheme.shapes.extraSmall, color = priorityColor.copy(alpha = 0.15f)) {
                Text(
                    text = when (task.priority) { "high" -> "Высокий"; "medium" -> "Средний"; else -> "Низкий" },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = priorityColor
                )
            }
        }
    }
}

@Composable
private fun TeamMemberCard(member: ForemanTeamMemberDto, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (member.displayName() != member.phone) {
                        Text(
                            text = member.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Монтажник",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Статус: работает сейчас или нет
                val statusColor = if (member.isWorkingNow) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor)
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Статистика — показываем только если есть данные
            if (member.totalShifts > 0 || member.pendingPhotosCount > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Смены
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${member.totalShifts}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Смен", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Часы
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f", member.totalHours),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Часов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Фото на проверке
                    if (member.pendingPhotosCount > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${member.pendingPhotosCount}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.belsiColors.warning
                            )
                            Text("На проверке", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Статус
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (member.isWorkingNow) "На смене" else "Не на смене",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (member.isWorkingNow) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Статус", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 1: Фото
// ==========================================

@Composable
private fun PhotosTab(
    photos: List<ForemanPhotoDto>,
    viewModel: ForemanViewModel,
    navController: NavController
) {
    var rejectingPhotoId by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Карточка для своего фото
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.getOrCreateShiftId { shiftId ->
                            navController.navigate("camera/$shiftId/0")
                        }
                    },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Сделать своё фото", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Загрузите фото для отчётности", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Заголовок фото команды
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Фото команды", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val pending = photos.count { it.status == "pending" }
                    if (pending > 0) {
                        Text("$pending ожидают проверки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.belsiColors.warning)
                    }
                }
                IconButton(onClick = { viewModel.refreshPhotos() }) {
                    Icon(Icons.Default.Refresh, "Обновить")
                }
            }
        }

        if (photos.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text("Нет фотографий от команды", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("Фото монтажников появятся здесь", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(photos, key = { it.id }) { photo ->
                PhotoReviewCard(
                    photo = photo,
                    onApprove = { viewModel.approvePhoto(photo.id) },
                    onReject = { rejectingPhotoId = photo.id }
                )
            }
        }
    }

    // Reject dialog
    if (rejectingPhotoId != null) {
        AlertDialog(
            onDismissRequest = { rejectingPhotoId = null; rejectReason = "" },
            title = { Text("Отклонить фото") },
            text = {
                Column {
                    Text("Укажите причину отклонения:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Причина...") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        rejectingPhotoId?.let { viewModel.rejectPhoto(it, rejectReason) }
                        rejectingPhotoId = null
                        rejectReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Отклонить") }
            },
            dismissButton = {
                TextButton(onClick = { rejectingPhotoId = null; rejectReason = "" }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun PhotoReviewCard(
    photo: ForemanPhotoDto,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(photo.installerName.firstOrNull()?.uppercase() ?: "?", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(photo.installerName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        photo.hourLabel?.let {
                            Text("Час: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                PhotoStatusBadge(photo.status)
            }

            Spacer(Modifier.height(8.dp))

            // Photo
            AsyncImage(
                model = photo.photoUrl,
                contentDescription = "Фото",
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.height(4.dp))
            Text(
                formatDateTime(photo.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Actions for pending photos
            if (photo.status == "pending") {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Отклонить", fontSize = 13.sp)
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.belsiColors.success)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Одобрить", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoStatusBadge(status: String) {
    val (text, color) = when (status.lowercase()) {
        "pending" -> "На проверке" to MaterialTheme.belsiColors.warning
        "approved" -> "Одобрено" to MaterialTheme.belsiColors.success
        "rejected" -> "Отклонено" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.outline
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ==========================================
// TAB 2: Инструменты
// ==========================================

@Composable
private fun ToolsTab(
    tools: List<ForemanToolDto>,
    navController: NavController
) {
    var filterStatus by remember { mutableStateOf<String?>(null) }

    val filteredTools = if (filterStatus == null) tools else tools.filter { it.status == filterStatus }
    val availableCount = tools.count { it.status == "available" }
    val issuedCount = tools.count { it.status == "issued" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Text("Инструменты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // Stats
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatMiniCard(Modifier.weight(1f), "${tools.size}", "Всего", Icons.Default.Build, MaterialTheme.colorScheme.primary)
                StatMiniCard(Modifier.weight(1f), "$availableCount", "Доступно", Icons.Default.CheckCircle, MaterialTheme.belsiColors.success)
                StatMiniCard(Modifier.weight(1f), "$issuedCount", "Выдано", Icons.Default.Person, MaterialTheme.belsiColors.warning)
            }
        }

        // Quick action - issue tool
        item {
            Button(
                onClick = { navController.navigate(AppRoute.ToolIssue.route) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Выдать инструмент", fontWeight = FontWeight.SemiBold)
            }
        }

        // Filters
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterStatus == null,
                    onClick = { filterStatus = null },
                    label = { Text("Все (${tools.size})") }
                )
                FilterChip(
                    selected = filterStatus == "available",
                    onClick = { filterStatus = if (filterStatus == "available") null else "available" },
                    label = { Text("Доступно ($availableCount)") }
                )
                FilterChip(
                    selected = filterStatus == "issued",
                    onClick = { filterStatus = if (filterStatus == "issued") null else "issued" },
                    label = { Text("Выдано ($issuedCount)") }
                )
            }
        }

        // Tools list
        if (filteredTools.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Build, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    Text("Нет инструментов", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            items(filteredTools, key = { it.id }) { tool ->
                ToolCard(tool)
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ForemanToolDto) {
    val statusColor = when (tool.status) {
        "available" -> MaterialTheme.belsiColors.success
        "issued" -> MaterialTheme.belsiColors.warning
        "lost" -> MaterialTheme.colorScheme.error
        "repair" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }
    val statusText = when (tool.status) {
        "available" -> "Доступен"
        "issued" -> "Выдан"
        "lost" -> "Утерян"
        "repair" -> "В ремонте"
        else -> tool.status
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tool icon
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(statusColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Build, null, Modifier.size(22.dp), tint = statusColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                tool.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (tool.status == "issued" && tool.issuedToName != null) {
                    Text("Выдан: ${tool.issuedToName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.belsiColors.warning, fontWeight = FontWeight.Medium)
                }
                tool.serialNumber?.let {
                    Text("S/N: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                Text(statusText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ==========================================
// TAB 2: Задачи с подвкладками
// ==========================================

@Composable
private fun TasksTabWithSubTabs(
    myTasks: List<Task>,
    teamTasks: List<Task>,
    teamMembers: List<TeamMember>,
    viewModel: ForemanViewModel
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val tasksState by viewModel.tasksState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Табы
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = {
                    val count = myTasks.count { it.status in listOf("new", "in_progress") }
                    Text(if (count > 0) "Мои ($count)" else "Мои задачи")
                }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = {
                    val count = teamTasks.count { it.status in listOf("new", "in_progress") }
                    Text(if (count > 0) "Для команды ($count)" else "Для команды")
                }
            )
        }

        // Контент
        when (selectedSubTab) {
            0 -> MyTasksTabContent(myTasks, viewModel)
            1 -> TeamTasksTabContent(
                teamTasks = teamTasks,
                teamMembers = teamMembers,
                viewModel = viewModel,
                onCreateTask = { showCreateDialog = true }
            )
        }
    }

    // Диалог создания задачи
    if (showCreateDialog) {
        CreateTaskDialogForForeman(
            teamMembers = teamMembers,
            isCreating = tasksState.isCreating,
            onDismiss = { showCreateDialog = false },
            onCreateTask = { title, description, assignedTo, priority ->
                viewModel.createTask(title, description, assignedTo, priority)
            },
            onCreateBatchTasks = { title, description, assignedToIds, priority ->
                viewModel.createBatchTasks(title, description, assignedToIds, priority)
            }
        )
    }
}

@Composable
private fun TeamTasksTabContent(
    teamTasks: List<Task>,
    teamMembers: List<TeamMember>,
    viewModel: ForemanViewModel,
    onCreateTask: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Заголовок с кнопкой создания
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Задачи для команды", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val activeCount = teamTasks.count { it.status in listOf("new", "in_progress") }
                    if (activeCount > 0) {
                        Text("$activeCount активных", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row {
                    IconButton(onClick = { viewModel.loadTeamTasks() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                    IconButton(onClick = onCreateTask) {
                        Icon(Icons.Default.Add, "Создать задачу", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Статистика
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    value = "${teamTasks.count { it.status == "new" }}",
                    label = "Новых",
                    icon = Icons.Default.FiberNew,
                    color = MaterialTheme.colorScheme.primary
                )
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    value = "${teamTasks.count { it.status == "in_progress" }}",
                    label = "В работе",
                    icon = Icons.Default.PlayArrow,
                    color = MaterialTheme.belsiColors.warning
                )
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    value = "${teamTasks.count { it.status == "done" }}",
                    label = "Выполнено",
                    icon = Icons.Default.CheckCircle,
                    color = MaterialTheme.belsiColors.success
                )
            }
        }

        // Кнопка создания задачи (большая)
        if (teamMembers.isNotEmpty()) {
            item {
                Button(
                    onClick = onCreateTask,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Создать задачу для команды", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (teamTasks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Assignment, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text("Нет созданных задач", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (teamMembers.isEmpty()) "Добавьте монтажников в команду"
                        else "Нажмите + чтобы создать задачу",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Группируем по статусу
            val activeTasks = teamTasks.filter { it.status in listOf("new", "in_progress") }
            val completedTasks = teamTasks.filter { it.status in listOf("done", "cancelled") }

            if (activeTasks.isNotEmpty()) {
                item {
                    Text(
                        "Активные (${activeTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(activeTasks, key = { it.id }) { task ->
                    TeamTaskCard(task)
                }
            }

            if (completedTasks.isNotEmpty()) {
                item {
                    Text(
                        "Завершённые (${completedTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(completedTasks.take(5), key = { it.id }) { task ->
                    TeamTaskCard(task)
                }
            }
        }
    }
}

@Composable
private fun TeamTaskCard(task: Task) {
    val statusColor = when (task.status) {
        "done" -> MaterialTheme.belsiColors.success
        "in_progress" -> MaterialTheme.belsiColors.warning
        "cancelled" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    val priorityColor = when (task.priority) {
        "high" -> MaterialTheme.colorScheme.error
        "normal" -> MaterialTheme.belsiColors.warning
        else -> MaterialTheme.colorScheme.outline
    }
    val statusText = when (task.status) {
        "new" -> "Новая"
        "in_progress" -> "В работе"
        "done" -> "Выполнено"
        "cancelled" -> "Отменена"
        else -> task.status
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!task.description.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(shape = MaterialTheme.shapes.extraSmall, color = priorityColor.copy(alpha = 0.15f)) {
                    Text(
                        text = when (task.priority) { "high" -> "Высокий"; "normal" -> "Обычный"; else -> "Низкий" },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Статус
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Кому назначено
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
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
private fun CreateTaskDialogForForeman(
    teamMembers: List<TeamMember>,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreateTask: (title: String, description: String?, assignedTo: String, priority: String) -> Unit,
    onCreateBatchTasks: (title: String, description: String?, assignedToIds: List<String>, priority: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf<Set<TeamMember>>(emptySet()) }
    var priority by remember { mutableStateOf("normal") }
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

                // Выбор исполнителей
                Column {
                    Text(
                        "Назначить исполнителям",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))

                    // Кнопка выбора
                    OutlinedButton(
                        onClick = { showMemberSelection = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreating && teamMembers.isNotEmpty()
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (selectedMembers.isEmpty()) "Выбрать исполнителей"
                            else "Выбрано: ${selectedMembers.size}"
                        )
                    }

                    // Список выбранных
                    if (selectedMembers.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        selectedMembers.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(member.name, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = { selectedMembers = selectedMembers - member },
                                    modifier = Modifier.size(24.dp),
                                    enabled = !isCreating
                                ) {
                                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                }
                            }
                        }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = priority == "low",
                        onClick = { priority = "low" },
                        label = { Text("Низкий") },
                        enabled = !isCreating,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.belsiColors.success.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.belsiColors.success
                        )
                    )
                    FilterChip(
                        selected = priority == "normal",
                        onClick = { priority = "normal" },
                        label = { Text("Обычный") },
                        enabled = !isCreating,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.belsiColors.warning.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.belsiColors.warning
                        )
                    )
                    FilterChip(
                        selected = priority == "high",
                        onClick = { priority = "high" },
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
                    val ids = selectedMembers.map { it.id }
                    if (ids.size == 1) {
                        onCreateTask(title, description.ifBlank { null }, ids.first(), priority)
                    } else {
                        onCreateBatchTasks(title, description.ifBlank { null }, ids, priority)
                    }
                },
                enabled = title.isNotBlank() && selectedMembers.isNotEmpty() && !isCreating,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text(
                        if (selectedMembers.size > 1) "Создать для ${selectedMembers.size} чел."
                        else "Создать"
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Отмена")
            }
        }
    )

    // Диалог выбора исполнителей
    if (showMemberSelection) {
        MemberSelectionDialogForForeman(
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

@Composable
private fun MemberSelectionDialogForForeman(
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                        Text(if (currentSelection.size == teamMembers.size) "Снять все" else "Выбрать всех")
                    }
                }

                HorizontalDivider()

                // Список
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
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
                                    currentSelection = if (checked) currentSelection + member else currentSelection - member
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(member.name, style = MaterialTheme.typography.bodyLarge)
                                Text(member.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ==========================================
// Мои задачи (от куратора) - контент
// ==========================================

@Composable
private fun MyTasksTabContent(
    tasks: List<Task>,
    viewModel: ForemanViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Заголовок
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Мои задачи", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val activeCount = tasks.count { it.status in listOf("new", "in_progress") }
                    if (activeCount > 0) {
                        Text("$activeCount активных", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { viewModel.refreshTasks() }) {
                    Icon(Icons.Default.Refresh, "Обновить")
                }
            }
        }

        // Статистика
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    value = "${tasks.count { it.status == "new" }}",
                    label = "Новых",
                    icon = Icons.Default.FiberNew,
                    color = MaterialTheme.colorScheme.primary
                )
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    value = "${tasks.count { it.status == "in_progress" }}",
                    label = "В работе",
                    icon = Icons.Default.PlayArrow,
                    color = MaterialTheme.belsiColors.warning
                )
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    value = "${tasks.count { it.status == "done" }}",
                    label = "Выполнено",
                    icon = Icons.Default.CheckCircle,
                    color = MaterialTheme.belsiColors.success
                )
            }
        }

        if (tasks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Assignment, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text("Нет задач", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("Задачи от куратора появятся здесь", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            // Новые задачи
            val newTasks = tasks.filter { it.status == "new" }
            if (newTasks.isNotEmpty()) {
                item {
                    Text("Новые", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(newTasks, key = { it.id }) { task ->
                    MyTaskCard(task, viewModel)
                }
            }

            // В работе
            val inProgressTasks = tasks.filter { it.status == "in_progress" }
            if (inProgressTasks.isNotEmpty()) {
                item {
                    Text("В работе", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(inProgressTasks, key = { it.id }) { task ->
                    MyTaskCard(task, viewModel)
                }
            }

            // Выполненные
            val doneTasks = tasks.filter { it.status == "done" }
            if (doneTasks.isNotEmpty()) {
                item {
                    Text("Выполнено", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(doneTasks.take(5), key = { it.id }) { task ->
                    MyTaskCard(task, viewModel)
                }
            }
        }
    }
}

@Composable
private fun MyTaskCard(task: Task, viewModel: ForemanViewModel) {
    val statusColor = when (task.status) {
        "done" -> MaterialTheme.belsiColors.success
        "in_progress" -> MaterialTheme.belsiColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val priorityColor = when (task.priority) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> MaterialTheme.belsiColors.warning
        else -> MaterialTheme.colorScheme.outline
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(shape = MaterialTheme.shapes.extraSmall, color = priorityColor.copy(alpha = 0.15f)) {
                    Text(
                        text = when (task.priority) { "high" -> "Высокий"; "medium" -> "Средний"; else -> "Низкий" },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor
                    )
                }
            }

            // Description
            if (!task.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions
            if (task.status != "done") {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (task.status == "new") {
                        Button(
                            onClick = { viewModel.updateTaskStatus(task.id, "in_progress") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Начать", fontSize = 13.sp)
                        }
                    }
                    if (task.status == "in_progress") {
                        Button(
                            onClick = { viewModel.updateTaskStatus(task.id, "done") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.belsiColors.success)
                        ) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Выполнено", fontSize = 13.sp)
                        }
                    }
                }
            }

            // Meta info - срок задачи
            if (task.dueAt != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Срок: ${task.dueAt.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==========================================
// TAB 4: Инвайты
// ==========================================

@Composable
private fun InvitesTab(state: InvitesUiState, viewModel: ForemanViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Приглашение в команду", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text("Создайте код для приглашения монтажника", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.createInvite() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isCreating,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (state.isCreating) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Сгенерировать код", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // Section header
        if (state.items.isNotEmpty()) {
            item {
                Text("Активные коды (${state.items.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Invites list
        when {
            state.isLoading && state.items.isEmpty() -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            state.items.isEmpty() -> {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.QrCode, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text("Нет активных кодов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("Создайте код для приглашения", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            else -> {
                items(state.items) { invite ->
                    InviteCard(invite = invite, isCancelling = state.cancellingId == invite.id, onCancelClick = { viewModel.cancelInvite(invite) })
                }
            }
        }
    }
}

@Composable
private fun InviteCard(
    invite: ForemanInviteResponse,
    isCancelling: Boolean,
    onCancelClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isActive = invite.status in listOf("new", "active")
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (isActive) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.outline))
                    Spacer(Modifier.width(8.dp))
                    Text(invite.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(invite.createdAt.take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (invite.installerPhone != null) {
                Spacer(Modifier.height(8.dp))
                Text("Использован: ${invite.installerPhone}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.belsiColors.success, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { clipboardManager.setText(AnnotatedString(invite.code)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Копировать")
                }

                val isActive = invite.status in listOf("new", "active")
                if (isActive) {
                    Button(
                        onClick = onCancelClick,
                        enabled = !isCancelling,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        if (isCancelling) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Отозвать")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Helpers
// ==========================================

private fun formatDateTime(isoString: String): String {
    return try {
        val datePart = isoString.take(10) // yyyy-MM-dd
        val timePart = isoString.substring(11, 16) // HH:mm
        "$datePart $timePart"
    } catch (_: Exception) {
        isoString
    }
}

// ==========================================
// Модель для диалога выбора членов команды в задачах
// ==========================================

/**
 * Представление члена команды для выбора в диалогах
 */
data class TeamMember(
    val id: String,
    val name: String,
    val phone: String,
    val activeShift: Boolean = false
) {
    companion object {
        /**
         * Конвертировать ForemanTeamMemberDto в TeamMember для использования в диалогах
         */
        fun fromDto(dto: ForemanTeamMemberDto): TeamMember {
            return TeamMember(
                id = dto.userId ?: dto.phone,
                name = dto.displayName(),
                phone = dto.phone,
                activeShift = false // TODO: получить статус активной смены из API
            )
        }
    }
}

enum class ForemanTab(
    val title: String,
    val icon: ImageVector
) {
    TEAM("Команда", Icons.Default.People),
    PHOTOS("Фото", Icons.Default.PhotoLibrary),
    TASKS("Задачи", Icons.Default.Assignment),
    TOOLS("Инструм.", Icons.Default.Build),
    CHAT("Чат", Icons.Default.Chat),
    PROFILE("Профиль", Icons.Default.AccountCircle)
}
