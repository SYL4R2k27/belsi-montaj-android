package com.belsi.work.presentation.screens.curator.userdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.belsi.work.data.remote.api.RoleChangeEntry
import com.belsi.work.data.remote.dto.coordinator.CoordinatorReportDto
import com.belsi.work.data.remote.dto.curator.CuratorPhotoDto
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.curator.TeamMemberDto
import com.belsi.work.data.remote.dto.curator.UserDetailDto
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorUserDetailScreen(
    navController: NavController,
    userId: String,
    viewModel: CuratorUserDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userId) {
        viewModel.loadUserDetail(userId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.roleChangeSuccess) {
        uiState.roleChangeSuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearRoleChangeSuccess()
        }
    }

    // После удаления — показать сообщение и вернуться назад
    LaunchedEffect(uiState.userDeleted) {
        if (uiState.userDeleted) {
            snackbarHostState.showSnackbar(uiState.deleteMessage ?: "Пользователь удалён")
            navController.popBackStack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.userDetail?.displayName ?: "Загрузка...")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (uiState.userDetail != null) {
                FloatingActionButton(
                    onClick = { viewModel.showCreateTaskDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "Поставить задачу", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                uiState.userDetail != null -> {
                    UserDetailContent(
                        user = uiState.userDetail!!,
                        shiftPhotos = uiState.shiftPhotos,
                        isLoadingPhotos = uiState.isLoadingPhotos,
                        coordinatorReports = uiState.coordinatorReports,
                        isLoadingReports = uiState.isLoadingReports,
                        roleHistory = uiState.roleHistory,
                        isLoadingRoleHistory = uiState.isLoadingRoleHistory,
                        onTeamMemberClick = { memberId ->
                            navController.navigate(AppRoute.CuratorUserDetail.createRoute(memberId))
                        },
                        onChangeRoleClick = { viewModel.showRoleDialog() },
                        onDeleteClick = { viewModel.showDeleteDialog() },
                        onReportFeedback = { reportId, feedback ->
                            viewModel.addReportFeedback(userId, reportId, feedback)
                        }
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Пользователь не найден",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }

    // Диалог создания задачи
    if (uiState.showTaskDialog) {
        CreateTaskDialog(
            onDismiss = { viewModel.hideCreateTaskDialog() },
            onCreate = { title, description, priority ->
                viewModel.createTask(userId, title, description, priority)
            }
        )
    }

    // Диалог смены роли
    if (uiState.showRoleDialog) {
        ChangeRoleDialog(
            currentRole = uiState.userDetail?.role ?: "",
            onDismiss = { viewModel.hideRoleDialog() },
            onConfirm = { newRole ->
                viewModel.changeRole(userId, newRole)
            }
        )
    }

    // Диалог подтверждения удаления
    if (uiState.showDeleteDialog) {
        val userName = uiState.userDetail?.displayName ?: "Пользователь"
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text("Удалить пользователя?", textAlign = TextAlign.Center)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Вы уверены, что хотите удалить $userName?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Будут удалены все данные: смены, фото, задачи, тикеты, приглашения и сообщения. Это действие нельзя отменить.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteUser(userId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun UserDetailContent(
    user: UserDetailDto,
    shiftPhotos: List<CuratorPhotoDto>,
    isLoadingPhotos: Boolean,
    coordinatorReports: List<CoordinatorReportDto>,
    isLoadingReports: Boolean,
    roleHistory: List<RoleChangeEntry>,
    isLoadingRoleHistory: Boolean,
    onTeamMemberClick: (String) -> Unit,
    onChangeRoleClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onReportFeedback: (reportId: String, feedback: String) -> Unit
) {
    val isCoordinator = user.role == "coordinator"
    val roleLabel = when {
        isCoordinator -> "Координатор"
        user.isForeman -> "Бригадир"
        user.isInstaller && user.foremanId != null -> "Монтажник"
        user.isInstaller -> "Монтажник (без бригадира)"
        else -> user.role
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Основная информация
        item {
            UserHeaderCard(
                name = user.displayName,
                phone = user.phone,
                role = roleLabel,
                isActive = user.isOnShift,
                foremanName = if (user.isInstaller) user.foremanName else null,
                warningBadge = user.isInstaller && user.foremanId == null
            )
        }

        // Кнопки: смена роли + удаление
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeRoleClick,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Изменить роль")
                }

                OutlinedButton(
                    onClick = onDeleteClick,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Карточка текущей смены
        if (user.isOnShift && user.currentShiftStartAt != null) {
            item {
                ActiveShiftCard(
                    startAt = user.currentShiftStartAt,
                    elapsedHours = user.currentShiftElapsedHours,
                    photosCount = user.currentShiftPhotosCount,
                    shiftStatus = user.shiftStatus,
                    isPaused = user.isPaused,
                    isIdle = user.isIdle,
                    pauseReason = user.currentPauseReason,
                    pauseSeconds = user.currentShiftPauseSeconds,
                    idleSeconds = user.currentShiftIdleSeconds,
                    totalPauseDuration = user.totalPauseDuration,
                    totalIdleDuration = user.totalIdleDuration
                )
            }
        }

        // Фото текущей смены
        if (user.isOnShift && user.currentShiftId != null) {
            item {
                Text(
                    "Фото текущей смены (${shiftPhotos.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isLoadingPhotos) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else if (shiftPhotos.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Нет фото за текущую смену",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Сетка фото 3 колонки (через chunked, т.к. внутри LazyColumn)
                items(shiftPhotos.chunked(3)) { rowPhotos ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPhotos.forEach { photo ->
                            ShiftPhotoThumbnail(
                                photo = photo,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Заполняем пустые слоты для ровной сетки
                        repeat(3 - rowPhotos.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Статистика
        item {
            Text(
                "Статистика",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Ряд 1: Смены и часы
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMiniCard(
                    title = "Всего смен",
                    value = "${user.totalShifts}",
                    icon = Icons.Default.Schedule,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    title = "Часов",
                    value = "%.1f".format(user.totalHours),
                    icon = Icons.Default.AccessTime,
                    color = MaterialTheme.belsiColors.success,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Ряд 2: Фото (ожидание + одобрено)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMiniCard(
                    title = "Фото на проверке",
                    value = "${user.pendingPhotosCount}",
                    icon = Icons.Default.CameraAlt,
                    color = MaterialTheme.belsiColors.warning,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    title = "Одобрено",
                    value = "${user.approvedPhotosCount}",
                    icon = Icons.Default.CheckCircle,
                    color = MaterialTheme.belsiColors.success,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Ряд 3: Отклонено + статус
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMiniCard(
                    title = "Отклонено",
                    value = "${user.rejectedPhotosCount}",
                    icon = Icons.Default.Cancel,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    title = "Статус",
                    value = when {
                        user.isIdle -> "Простой"
                        user.isPaused -> "Пауза"
                        user.isOnShift -> "На смене"
                        else -> "Не на смене"
                    },
                    icon = when {
                        user.isIdle -> Icons.Default.Warning
                        user.isPaused -> Icons.Default.Pause
                        user.isOnShift -> Icons.Default.CheckCircle
                        else -> Icons.Default.Cancel
                    },
                    color = when {
                        user.isIdle -> MaterialTheme.colorScheme.error
                        user.isPaused -> MaterialTheme.belsiColors.warning
                        user.isOnShift -> MaterialTheme.belsiColors.success
                        else -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Ряд 4: Задачи
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMiniCard(
                    title = "Активных задач",
                    value = "${user.activeTasksCount}",
                    icon = Icons.Default.Assignment,
                    color = MaterialTheme.belsiColors.info,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    title = "Выполнено задач",
                    value = "${user.completedTasksCount}",
                    icon = Icons.Default.TaskAlt,
                    color = MaterialTheme.belsiColors.info,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Координатор-специфика: объект и отчёты
        if (isCoordinator) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Объект",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (user.siteName != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Business,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    user.siteName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            user.siteAddress?.let { addr ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    addr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val siteStatusLabel = when (user.siteStatus) {
                                "active" -> "Активный"
                                "paused" -> "На паузе"
                                "completed" -> "Завершён"
                                else -> user.siteStatus ?: "—"
                            }
                            val siteStatusColor = when (user.siteStatus) {
                                "active" -> MaterialTheme.belsiColors.success
                                "paused" -> MaterialTheme.belsiColors.warning
                                "completed" -> MaterialTheme.belsiColors.info
                                else -> MaterialTheme.colorScheme.outline
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = siteStatusColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    siteStatusLabel,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = siteStatusColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Объект не назначен",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Замеры объекта
            if (user.siteMeasurements.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Замеры",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            user.siteMeasurements.entries.forEachIndexed { index, (key, value) ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        key,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Комментарии к объекту
            if (!user.siteComments.isNullOrBlank()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Комментарии к объекту",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                user.siteComments,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Отчёты координатора — полный список
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Отчёты (${coordinatorReports.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isLoadingReports) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else if (coordinatorReports.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Нет отчётов",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(coordinatorReports) { report ->
                    CoordinatorReportCard(
                        report = report,
                        onFeedback = { feedback ->
                            onReportFeedback(report.id, feedback)
                        }
                    )
                }
            }
        }

        // Бригадир-специфика: команда
        if (user.isForeman && user.hasTeam) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Команда (${user.teamMembers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(user.teamMembers) { member ->
                TeamMemberCard(
                    member = member,
                    onClick = { onTeamMemberClick(member.id) }
                )
            }
        } else if (user.isForeman) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.GroupOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Нет монтажников в команде",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Предупреждение для монтажника без бригадира
        if (user.isInstaller && user.foremanId == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.belsiColors.warning.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.belsiColors.warning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Монтажник не закреплен за бригадиром",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.belsiColors.warning
                            )
                            Text(
                                "Рекомендуется назначить бригадира для контроля",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // История ролей
        if (isLoadingRoleHistory || roleHistory.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "История изменений ролей",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isLoadingRoleHistory) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                items(roleHistory) { entry ->
                    RoleHistoryCard(entry)
                }
            }
        }

        // Последняя активность
        if (user.lastActivityAt != null || user.createdAt != null) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Информация",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (user.lastActivityAt != null) {
                            InfoRow(
                                label = "Последняя активность",
                                value = user.lastActivityAt
                            )
                        }
                        if (user.createdAt != null) {
                            InfoRow(
                                label = "Дата регистрации",
                                value = user.createdAt
                            )
                        }
                        if (user.currentShiftId != null) {
                            InfoRow(
                                label = "Текущая смена",
                                value = user.currentShiftId
                            )
                        }
                    }
                }
            }
        }

        // Задачи placeholder
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Текущие задачи",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Задачи появятся здесь",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Нажмите + чтобы создать задачу",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun UserHeaderCard(
    name: String,
    phone: String,
    role: String,
    isActive: Boolean,
    foremanName: String? = null,
    warningBadge: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Аватар
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = phone,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Бригадир монтажника
            if (foremanName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Бригадир: $foremanName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (warningBadge) MaterialTheme.belsiColors.warning.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = role,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (warningBadge) MaterialTheme.belsiColors.warning else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isActive) MaterialTheme.belsiColors.success.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isActive) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.outline)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isActive) "На смене" else "Не на смене",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatMiniCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamMemberCard(
    member: TeamMemberDto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар со статусом
            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // Индикатор активности
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                if (member.isActiveToday) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = member.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String, priority: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая задача") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Text(
                    "Приоритет",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = priority == "low",
                        onClick = { priority = "low" },
                        label = { Text("Низкий") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.belsiColors.success.copy(alpha = 0.2f)
                        )
                    )
                    FilterChip(
                        selected = priority == "medium",
                        onClick = { priority = "medium" },
                        label = { Text("Средний") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.belsiColors.warning.copy(alpha = 0.2f)
                        )
                    )
                    FilterChip(
                        selected = priority == "high",
                        onClick = { priority = "high" },
                        label = { Text("Высокий") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && description.isNotBlank()) {
                        onCreate(title, description, priority)
                    }
                },
                enabled = title.isNotBlank() && description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun ShiftPhotoThumbnail(
    photo: CuratorPhotoDto,
    modifier: Modifier = Modifier
) {
    val statusColor = when (photo.status) {
        "approved" -> MaterialTheme.belsiColors.success
        "rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.belsiColors.warning // pending
    }
    val statusIcon = when (photo.status) {
        "approved" -> Icons.Default.CheckCircle
        "rejected" -> Icons.Default.Cancel
        else -> Icons.Default.Schedule
    }

    Card(
        modifier = modifier.aspectRatio(1f),
        shape = MaterialTheme.shapes.small,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = photo.photoUrl,
                contentDescription = "Фото смены",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Бейдж статуса в правом верхнем углу
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                shape = CircleShape,
                color = statusColor,
                shadowElevation = 2.dp
            ) {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(2.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Категория в левом нижнем углу (если не hourly)
            if (photo.category != null && photo.category != "hourly") {
                val (catLabel, catColor) = when (photo.category) {
                    "problem" -> "!" to MaterialTheme.colorScheme.error
                    "question" -> "?" to MaterialTheme.belsiColors.warning
                    else -> photo.category to MaterialTheme.colorScheme.outline
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = catColor
                ) {
                    Text(
                        catLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveShiftCard(
    startAt: String,
    elapsedHours: Double,
    photosCount: Int,
    shiftStatus: String = "working",
    isPaused: Boolean = false,
    isIdle: Boolean = false,
    pauseReason: String? = null,
    pauseSeconds: Int = 0,
    idleSeconds: Int = 0,
    totalPauseDuration: Double? = null,
    totalIdleDuration: Double? = null
) {
    val hours = elapsedHours.toInt()
    val minutes = ((elapsedHours - hours) * 60).toInt()
    val timeFormatted = if (hours > 0) "${hours}ч ${minutes}мин" else "${minutes}мин"

    val startFormatted = try {
        val datePart = startAt.substring(0, 10)
        val timePart = startAt.substring(11, 16)
        "$timePart ($datePart)"
    } catch (_: Exception) {
        startAt
    }

    // Цвета в зависимости от статуса
    val statusColor = when {
        isIdle -> MaterialTheme.colorScheme.error       // Красный — простой
        isPaused -> MaterialTheme.belsiColors.warning     // Жёлтый — пауза
        else -> MaterialTheme.belsiColors.success         // Зелёный — работает
    }
    val statusLabel = when {
        isIdle -> "Простой"
        isPaused -> "Пауза"
        else -> "Активная смена"
    }
    val statusIcon = when {
        isIdle -> Icons.Default.Warning
        isPaused -> Icons.Default.Pause
        else -> Icons.Default.PlayCircle
    }

    fun formatSeconds(secs: Int): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        return if (h > 0) "${h}ч ${m}мин" else "${m}мин"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = statusColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                // Статус бейдж
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = shiftStatus.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // Причина простоя/паузы
            if ((isPaused || isIdle) && !pauseReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Причина: $pauseReason",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        timeFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        "Длительность",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$photosCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        "Фото",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (pauseSeconds > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatSeconds(pauseSeconds),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.belsiColors.warning
                        )
                        Text(
                            "Пауза",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (idleSeconds > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatSeconds(idleSeconds),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Простой",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Начало: $startFormatted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // === За всё время ===
            if ((totalPauseDuration != null && totalPauseDuration > 0) ||
                (totalIdleDuration != null && totalIdleDuration > 0)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "За всё время",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (totalPauseDuration != null && totalPauseDuration > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val h = totalPauseDuration.toInt() / 60
                            val m = totalPauseDuration.toInt() % 60
                            val fmt = if (h > 0) "${h}ч ${m}мин" else "${m}мин"
                            Text(
                                fmt,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.belsiColors.warning
                            )
                            Text(
                                "Паузы",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (totalIdleDuration != null && totalIdleDuration > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val h = totalIdleDuration.toInt() / 60
                            val m = totalIdleDuration.toInt() % 60
                            val fmt = if (h > 0) "${h}ч ${m}мин" else "${m}мин"
                            Text(
                                fmt,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Простои",
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

@Composable
private fun CoordinatorReportCard(
    report: CoordinatorReportDto,
    onFeedback: (String) -> Unit
) {
    var showFeedbackDialog by remember { mutableStateOf(false) }

    val statusLabel = when (report.status) {
        "submitted" -> "Отправлен"
        "reviewed" -> "Просмотрен"
        else -> report.status
    }
    val statusColor = when (report.status) {
        "submitted" -> MaterialTheme.belsiColors.warning
        "reviewed" -> MaterialTheme.belsiColors.success
        else -> MaterialTheme.colorScheme.outline
    }

    val dateFormatted = try {
        val raw = report.reportDate ?: report.createdAt ?: ""
        if (raw.length >= 10) raw.substring(0, 10) else raw
    } catch (_: Exception) { "" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Шапка: дата + статус
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dateFormatted.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            dateFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Текст отчёта
            Text(
                report.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5
            )

            // Фото отчёта
            if (report.photoUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    report.photoUrls.take(4).forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (report.photoUrls.size > 4) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "+${report.photoUrls.size - 4}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Обратная связь куратора
            if (!report.curatorFeedback.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.belsiColors.success.copy(alpha = 0.08f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Feedback,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.belsiColors.success
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Обратная связь",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.belsiColors.success,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            report.curatorFeedback,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Кнопка обратной связи
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showFeedbackDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    if (report.curatorFeedback.isNullOrBlank()) Icons.Default.RateReview else Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (report.curatorFeedback.isNullOrBlank()) "Дать обратную связь" else "Изменить обратную связь",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }

    // Диалог обратной связи
    if (showFeedbackDialog) {
        ReportFeedbackDialog(
            initialFeedback = report.curatorFeedback ?: "",
            onDismiss = { showFeedbackDialog = false },
            onConfirm = { feedback ->
                onFeedback(feedback)
                showFeedbackDialog = false
            }
        )
    }
}

@Composable
private fun ReportFeedbackDialog(
    initialFeedback: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var feedback by remember { mutableStateOf(initialFeedback) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.RateReview,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Обратная связь") },
        text = {
            OutlinedTextField(
                value = feedback,
                onValueChange = { feedback = it },
                label = { Text("Комментарий к отчёту") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(feedback) },
                enabled = feedback.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Отправить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun RoleHistoryCard(entry: RoleChangeEntry) {
    val oldRoleLabel = roleLabel(entry.oldRole)
    val newRoleLabel = roleLabel(entry.newRole)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        oldRoleLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        " → ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        newRoleLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                entry.changedAt?.let { date ->
                    Text(
                        date.replace("T", " ").take(16),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "installer" -> "Монтажник"
    "foreman" -> "Бригадир"
    "coordinator" -> "Координатор"
    "curator" -> "Куратор"
    else -> role
}

@Composable
private fun ChangeRoleDialog(
    currentRole: String,
    onDismiss: () -> Unit,
    onConfirm: (newRole: String) -> Unit
) {
    val roles = listOf(
        "installer" to "Монтажник",
        "foreman" to "Бригадир",
        "coordinator" to "Координатор",
        "curator" to "Куратор"
    )
    var selectedRole by remember { mutableStateOf(currentRole) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить роль") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Выберите новую роль для пользователя:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                roles.forEach { (roleKey, roleLabel) ->
                    val isSelected = selectedRole == roleKey
                    val isCurrent = currentRole == roleKey

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedRole = roleKey },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedRole = roleKey },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = roleLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isCurrent) {
                                    Text(
                                        text = "Текущая роль",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
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
                onClick = { onConfirm(selectedRole) },
                enabled = selectedRole != currentRole,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Изменить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
