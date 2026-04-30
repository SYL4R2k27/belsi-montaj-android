package com.belsi.work.presentation.screens.coordinator

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.local.LocalSite
import com.belsi.work.data.remote.dto.coordinator.*
import com.belsi.work.data.models.Task
import com.belsi.work.presentation.components.AppSessionTracker
import com.belsi.work.presentation.components.WorkTimeTracker
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.screens.shift.ShiftUiState
import com.belsi.work.presentation.screens.shift.ShiftViewModel
import com.belsi.work.presentation.theme.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// =====================================================================
// Coordinator Main Screen — 7 tabs
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinatorMainScreen(
    navController: NavController,
    viewModel: CoordinatorViewModel = hiltViewModel(),
    shiftViewModel: ShiftViewModel = hiltViewModel()
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val myTasks by viewModel.myTasks.collectAsState()
    val team by viewModel.team.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val site by viewModel.site.collectAsState()
    val localSites by viewModel.localSites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val shiftState by shiftViewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler { /* Block back to role selection */ }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Belsi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Координатор", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                            WorkTimeTracker(
                                startTimeMillis = AppSessionTracker.getSessionStartTime(),
                                compact = true
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                    IconButton(onClick = { navController.navigate(AppRoute.Settings.route) }) {
                        Icon(Icons.Default.Settings, "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BelsiPrimary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = BelsiPrimary,
                edgePadding = 8.dp
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Объект") }, icon = { Icon(Icons.Default.Business, null, Modifier.size(18.dp)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Фото")
                            if (dashboard.pendingPhotos > 0) {
                                Spacer(Modifier.width(4.dp))
                                Badge(containerColor = com.belsi.work.presentation.theme.Rose500) {
                                    Text("${dashboard.pendingPhotos}", color = Color.White)
                                }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp)) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("Задачи") }, icon = { Icon(Icons.Default.Assignment, null, Modifier.size(18.dp)) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                    text = { Text("Команда") }, icon = { Icon(Icons.Default.Groups, null, Modifier.size(18.dp)) })
                Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 },
                    text = { Text("Отчёты") }, icon = { Icon(Icons.Default.Description, null, Modifier.size(18.dp)) })
                Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 },
                    text = { Text("Чат") }, icon = { Icon(Icons.Default.Chat, null, Modifier.size(18.dp)) })
                Tab(selected = selectedTab == 6, onClick = { selectedTab = 6 },
                    text = { Text("Профиль") }, icon = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) })
            }

            if (isLoading && team.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BelsiPrimary)
                }
            } else {
                when (selectedTab) {
                    0 -> SiteTab(dashboard, site, localSites, shiftState, shiftViewModel, viewModel, navController)
                    1 -> CoordinatorPhotosTab(photos,
                        onApprove = { viewModel.approvePhoto(it) },
                        onReject = { id, reason -> viewModel.rejectPhoto(id, reason) },
                        onRefresh = { viewModel.refreshPhotos() },
                        navController = navController)
                    2 -> CoordinatorTasksTab(tasks, myTasks, team, viewModel)
                    3 -> TeamTab(team)
                    4 -> ReportsTab(reports, viewModel)
                    5 -> com.belsi.work.presentation.screens.messenger.ChatHubScreen(
                        navController = navController
                    )
                    6 -> com.belsi.work.presentation.screens.profile.ProfileScreen(
                        navController = navController
                    )
                }
            }
        }
    }
}

// =====================================================================
// Tab 0: Site / Dashboard
// =====================================================================

@Composable
private fun SiteTab(
    dashboard: CoordinatorDashboardDto,
    site: CoordinatorSiteDto?,
    localSites: List<LocalSite>,
    shiftState: ShiftUiState,
    shiftViewModel: ShiftViewModel,
    viewModel: CoordinatorViewModel,
    navController: NavController
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var showMeasurementsDialog by remember { mutableStateOf(false) }
    var showCommentsDialog by remember { mutableStateOf(false) }
    var showAddSiteDialog by remember { mutableStateOf(false) }
    var siteToEdit by remember { mutableStateOf<LocalSite?>(null) }
    var siteToDelete by remember { mutableStateOf<LocalSite?>(null) }

    // Dialogs
    if (showStatusDialog && site != null) {
        EditSiteStatusDialog(
            currentStatus = site.status,
            onDismiss = { showStatusDialog = false },
            onSave = { newStatus ->
                viewModel.updateSite(status = newStatus)
                showStatusDialog = false
            }
        )
    }

    if (showMeasurementsDialog && site != null) {
        EditMeasurementsDialog(
            currentMeasurements = site.measurements,
            onDismiss = { showMeasurementsDialog = false },
            onSave = { newMeasurements ->
                viewModel.updateSite(measurements = newMeasurements)
                showMeasurementsDialog = false
            }
        )
    }

    if (showCommentsDialog) {
        EditCommentsDialog(
            currentComments = site?.comments ?: "",
            onDismiss = { showCommentsDialog = false },
            onSave = { newComments ->
                viewModel.updateSite(comments = newComments)
                showCommentsDialog = false
            }
        )
    }

    if (showAddSiteDialog) {
        AddLocalSiteDialog(
            onDismiss = { showAddSiteDialog = false },
            onAdd = { name, address ->
                viewModel.addLocalSite(name, address)
                showAddSiteDialog = false
            }
        )
    }

    siteToEdit?.let { editingSite ->
        EditLocalSiteDialog(
            site = editingSite,
            onDismiss = { siteToEdit = null },
            onSave = { name, address, measurements, comments, status ->
                viewModel.updateLocalSite(
                    id = editingSite.id,
                    name = name,
                    address = address,
                    measurements = measurements,
                    comments = comments,
                    status = status
                )
                siteToEdit = null
            }
        )
    }

    siteToDelete?.let { deletingSite ->
        AlertDialog(
            onDismissRequest = { siteToDelete = null },
            title = { Text("Удалить объект?") },
            text = { Text("«${deletingSite.name}» будет удалён из локального списка.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLocalSite(deletingSite.id)
                        siteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = com.belsi.work.presentation.theme.Rose500)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { siteToDelete = null }) { Text("Отмена") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== Мега-блок «Моя смена» =====
        item {
            ShiftMegaBlock(
                shiftState = shiftState,
                dashboard = dashboard,
                onStartShift = { shiftViewModel.startShift() },
                onPauseShift = { shiftViewModel.pauseShift() },
                onResumeShift = { shiftViewModel.resumeShift() },
                onOpenShift = { navController.navigate(AppRoute.Camera.route) }
            )
        }

        // Site info with status edit button
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Объект", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (site != null) {
                    FilledTonalButton(
                        onClick = { showStatusDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Статус", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (site != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Business, null,
                                tint = BelsiPrimary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(site.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        site.address?.let {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(it, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        StatusChip(site.status)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(8.dp))
                            Text("Объект не назначен", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ---- Measurements section ----
        if (site != null) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Замеры", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FilledTonalButton(
                        onClick = { showMeasurementsDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (site.measurements.isEmpty()) "Добавить" else "Изменить",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                if (site.measurements.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            site.measurements.entries.forEachIndexed { index, (key, value) ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
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
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Straighten, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Замеры ещё не добавлены",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---- Comments section ----
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Комментарии", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FilledTonalButton(
                        onClick = { showCommentsDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (site.comments.isNullOrBlank()) "Добавить" else "Изменить",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                if (!site.comments.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                site.comments,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Comment, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Комментарии не добавлены",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Stats
        item {
            Spacer(Modifier.height(4.dp))
            Text("Сегодня", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStatCard("Рабочие", "${dashboard.activeWorkersToday}", Icons.Default.People,
                    com.belsi.work.presentation.theme.Emerald500, Modifier.weight(1f))
                MiniStatCard("Фото", "${dashboard.totalPhotosToday}", Icons.Default.CameraAlt,
                    com.belsi.work.presentation.theme.Sky500, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStatCard("На проверке", "${dashboard.pendingPhotos}", Icons.Default.HourglassEmpty,
                    com.belsi.work.presentation.theme.Amber500, Modifier.weight(1f))
                MiniStatCard("Отчёты", "${dashboard.reportsToday}", Icons.Default.Description,
                    com.belsi.work.presentation.theme.Violet500, Modifier.weight(1f))
            }
        }

        // Team stats
        item {
            Spacer(Modifier.height(4.dp))
            Text("Команда", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStatCard("Бригадиры", "${dashboard.totalForemen}", Icons.Default.SupervisorAccount,
                    BelsiPrimary, Modifier.weight(1f))
                MiniStatCard("Монтажники", "${dashboard.totalInstallers}", Icons.Default.Engineering,
                    Color(0xFF795548), Modifier.weight(1f))
            }
        }

        // Tasks progress
        item {
            if (dashboard.tasksTotal > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Задачи", fontWeight = FontWeight.SemiBold)
                            Text("${dashboard.tasksCompleted}/${dashboard.tasksTotal}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        val progress = if (dashboard.tasksTotal > 0)
                            dashboard.tasksCompleted.toFloat() / dashboard.tasksTotal else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = BelsiPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // ===== Мои объекты (SiteStore) =====
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Мои объекты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FilledTonalButton(
                    onClick = { showAddSiteDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (localSites.isNotEmpty()) {
            items(localSites, key = { "local_${it.id}" }) { localSite ->
                LocalSiteCard(
                    site = localSite,
                    onSelect = { viewModel.selectLocalSite(localSite.id) },
                    onEdit = { siteToEdit = localSite },
                    onDelete = { siteToDelete = localSite }
                )
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Business, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Нет локальных объектов. Нажмите «Добавить» для создания.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Quick actions
        item {
            Spacer(Modifier.height(4.dp))
            Text("Быстрые действия", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard("Сделать фото", Icons.Default.CameraAlt, com.belsi.work.presentation.theme.Sky500, Modifier.weight(1f)) {
                    navController.navigate(AppRoute.Camera.route)
                }
                QuickActionCard("Отчёт", Icons.Default.Description, com.belsi.work.presentation.theme.Violet500, Modifier.weight(1f)) {
                    // Will scroll to reports tab
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// =====================================================================
// Tab 1: Photos
// =====================================================================

@Composable
private fun CoordinatorPhotosTab(
    photos: List<CoordinatorPhotoDto>,
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit,
    onRefresh: () -> Unit,
    navController: NavController
) {
    var selectedCategory by remember { mutableStateOf("all") }

    val filteredPhotos = remember(photos, selectedCategory) {
        if (selectedCategory == "all") photos
        else photos.filter { it.category == selectedCategory }
    }

    val pendingPhotos = filteredPhotos.filter { it.status == "pending" }
    val otherPhotos = filteredPhotos.filter { it.status != "pending" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Category filter
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories = listOf("all" to "Все", "hourly" to "Ежечас", "problem" to "Проблема", "question" to "Вопрос")
                items(categories.size) { index ->
                    val (key, label) = categories[index]
                    FilterChip(
                        selected = selectedCategory == key,
                        onClick = { selectedCategory = key },
                        label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        }

        // Camera button
        item {
            OutlinedButton(
                onClick = { navController.navigate(AppRoute.Camera.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Сделать фото")
            }
        }

        // Pending photos
        if (pendingPhotos.isNotEmpty()) {
            item {
                Text("На проверке (${pendingPhotos.size})", fontWeight = FontWeight.Bold,
                    color = com.belsi.work.presentation.theme.Amber500)
            }
            items(pendingPhotos, key = { it.id }) { photo ->
                PhotoReviewCard(photo, onApprove, onReject)
            }
        }

        // Other photos
        if (otherPhotos.isNotEmpty()) {
            item {
                Text("Все фото (${otherPhotos.size})", fontWeight = FontWeight.Bold)
            }
            items(otherPhotos, key = { it.id }) { photo ->
                PhotoCard(photo)
            }
        }

        if (filteredPhotos.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Нет фото", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PhotoReviewCard(
    photo: CoordinatorPhotoDto,
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit
) {
    var showRejectDialog by remember { mutableStateOf(false) }

    if (showRejectDialog) {
        RejectPhotoDialog(
            photoUserName = photo.userName ?: photo.userPhone ?: "—",
            onDismiss = { showRejectDialog = false },
            onReject = { reason ->
                onReject(photo.id, reason)
                showRejectDialog = false
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Фото
            if (photo.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = photo.photoUrl,
                    contentDescription = "Фото",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(photo.userName ?: photo.userPhone ?: "—",
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${getRoleLabel(photo.userRole)} • ${photo.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val timeText = formatDateTime(photo.timestamp)
                    if (timeText.isNotEmpty()) {
                        Text(
                            timeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                CategoryBadge(photo.category)
            }
            photo.comment?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onApprove(photo.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = com.belsi.work.presentation.theme.Emerald500),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ок")
                }
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Отклонить")
                }
            }
        }
    }
}

@Composable
private fun RejectPhotoDialog(
    photoUserName: String,
    onDismiss: () -> Unit,
    onReject: (String) -> Unit
) {
    val presetReasons = listOf(
        "Нечёткое фото",
        "Неполный объём работ",
        "Неправильный ракурс",
        "Нет необходимых элементов",
        "Другое"
    )
    var selectedReason by remember { mutableStateOf("") }
    var customReason by remember { mutableStateOf("") }

    val finalReason = if (selectedReason == "Другое") customReason else selectedReason

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отклонить фото") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Фото от: $photoUserName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                presetReasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (selectedReason == "Другое") {
                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        label = { Text("Укажите причину") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onReject(finalReason) },
                enabled = finalReason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = com.belsi.work.presentation.theme.Rose500)
            ) { Text("Отклонить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun PhotoCard(photo: CoordinatorPhotoDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Фото
            if (photo.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = photo.photoUrl,
                    contentDescription = "Фото",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(photo.userName ?: photo.userPhone ?: "—",
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatDateTime(photo.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CategoryBadge(photo.category)
                Spacer(Modifier.width(8.dp))
                PhotoStatusBadge(photo.status ?: "pending")
            }
        }
    }
}

// =====================================================================
// Tab 2: Tasks
// =====================================================================

@Composable
private fun CoordinatorTasksTab(
    tasks: List<CoordinatorTaskDto>,
    myTasks: List<Task>,
    team: List<CoordinatorTeamMemberDto>,
    viewModel: CoordinatorViewModel
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateTaskDialog(
            team = team,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, desc, assignedToIds, priority ->
                viewModel.createTask(title, desc, assignedToIds, priority)
                showCreateDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Задачи (${tasks.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FilledTonalButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Создать")
                }
            }
        }

        // My tasks section (назначенные координатору)
        val activeMyTasks = myTasks.filter { it.status in listOf("new", "in_progress") }
        if (activeMyTasks.isNotEmpty()) {
            item {
                Text("Мои задачи (${activeMyTasks.size})",
                    fontWeight = FontWeight.SemiBold, color = Color(0xFF00897B))
            }
            items(activeMyTasks, key = { "my_${it.id}" }) { task ->
                MyTaskCard(task)
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
        }

        // Created tasks — sorted by priority (high first) then deadline (soonest first)
        val priorityOrder = mapOf("high" to 0, "urgent" to 0, "normal" to 1, "medium" to 1, "low" to 2)
        val newTasks = tasks
            .filter { it.status == "new" || it.status == "in_progress" }
            .sortedWith(compareBy<CoordinatorTaskDto> {
                priorityOrder[it.priority] ?: 1
            }.thenBy {
                it.dueAt?.let { d -> try { OffsetDateTime.parse(d) } catch (_: Exception) { null } }
                    ?: OffsetDateTime.MAX
            })
        val doneTasks = tasks.filter { it.status == "done" || it.status == "completed" }

        if (newTasks.isNotEmpty()) {
            item { Text("Активные", fontWeight = FontWeight.SemiBold, color = com.belsi.work.presentation.theme.Sky500) }
            items(newTasks, key = { it.id }) { task ->
                EnhancedTaskCard(task)
            }
        }

        if (doneTasks.isNotEmpty()) {
            item { Text("Выполненные", fontWeight = FontWeight.SemiBold, color = com.belsi.work.presentation.theme.Emerald500) }
            items(doneTasks, key = { it.id }) { task ->
                TaskCard(task)
            }
        }

        if (tasks.isEmpty() && myTasks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Нет задач", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MyTaskCard(task: Task) {
    val statusColor = when (task.status) {
        "new" -> com.belsi.work.presentation.theme.Sky500
        "in_progress" -> com.belsi.work.presentation.theme.Amber500
        "done", "completed" -> com.belsi.work.presentation.theme.Emerald500
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF00897B).copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Мне", style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00897B), fontWeight = FontWeight.Medium)
                    Text(task.status, style = MaterialTheme.typography.bodySmall, color = statusColor)
                }
            }
            if (task.priority in listOf("high", "urgent")) {
                Icon(Icons.Default.PriorityHigh, null, tint = com.belsi.work.presentation.theme.Rose500, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun EnhancedTaskCard(task: CoordinatorTaskDto) {
    val priorityColor = when (task.priority) {
        "high", "urgent" -> MaterialTheme.colorScheme.error
        "low" -> com.belsi.work.presentation.theme.Emerald500
        else -> com.belsi.work.presentation.theme.Amber500 // normal
    }
    val statusColor = when (task.status) {
        "new" -> com.belsi.work.presentation.theme.Sky500
        "in_progress" -> com.belsi.work.presentation.theme.Amber500
        else -> MaterialTheme.colorScheme.outline
    }

    // Deadline computation — colors resolved outside try/catch (composable context)
    val errorColor = MaterialTheme.colorScheme.error
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val warningDeadline = com.belsi.work.presentation.theme.Amber500
    val today = LocalDate.now()
    val deadlineInfo: Pair<String, Color>? = task.dueAt?.let { dueAt ->
        val dueDate = try {
            OffsetDateTime.parse(dueAt).toLocalDate()
        } catch (_: Exception) {
            try { LocalDate.parse(dueAt.take(10)) } catch (_: Exception) { null }
        }
        dueDate?.let {
            val daysLeft = ChronoUnit.DAYS.between(today, it)
            when {
                daysLeft < 0 -> "Просрочена ${-daysLeft} дн." to errorColor
                daysLeft == 0L -> "Сегодня" to warningDeadline
                daysLeft == 1L -> "Завтра" to warningDeadline
                daysLeft <= 3 -> "$daysLeft дн. осталось" to warningDeadline
                else -> "$daysLeft дн. осталось" to subtleColor
            }
        }
    }
    val isOverdue = deadlineInfo?.first?.startsWith("Просрочена") == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isOverdue) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)) else null
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Priority indicator
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(priorityColor)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        task.assignedName?.let {
                            Text("→ $it", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Priority badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = priorityColor.copy(alpha = 0.1f)
                        ) {
                            Text(
                                when (task.priority) {
                                    "high", "urgent" -> "Высокий"
                                    "low" -> "Низкий"
                                    else -> "Обычный"
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        // Status
                        Text(
                            when (task.status) { "new" -> "Новая"; "in_progress" -> "В работе"; else -> task.status },
                            style = MaterialTheme.typography.bodySmall, color = statusColor
                        )
                    }
                }
            }
            // Deadline row
            deadlineInfo?.let { (text, color) ->
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isOverdue) Icons.Default.Warning else Icons.Default.Schedule,
                        null, modifier = Modifier.size(14.dp), tint = color
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: CoordinatorTaskDto) {
    val statusColor = when (task.status) {
        "new" -> com.belsi.work.presentation.theme.Sky500
        "in_progress" -> com.belsi.work.presentation.theme.Amber500
        "done", "completed" -> com.belsi.work.presentation.theme.Emerald500
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    task.assignedName?.let {
                        Text("→ $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(task.status, style = MaterialTheme.typography.bodySmall, color = statusColor)
                }
            }
            if (task.priority == "high" || task.priority == "urgent") {
                Icon(Icons.Default.PriorityHigh, null, tint = com.belsi.work.presentation.theme.Rose500, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CreateTaskDialog(
    team: List<CoordinatorTeamMemberDto>,
    onDismiss: () -> Unit,
    onCreate: (String, String?, List<String>, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedMemberIds by remember { mutableStateOf(setOf<String>()) }
    var selectedPriority by remember { mutableStateOf("normal") }

    val priorities = listOf(
        "low" to "Низкий",
        "normal" to "Обычный",
        "high" to "Высокий",
        "urgent" to "Срочный"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая задача") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 500.dp),
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
                    maxLines = 3
                )

                // Priority picker
                Text("Приоритет:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    priorities.forEach { (value, label) ->
                        val isSelected = selectedPriority == value
                        val chipColor = when (value) {
                            "urgent" -> com.belsi.work.presentation.theme.Rose500
                            "high" -> com.belsi.work.presentation.theme.Amber500
                            "normal" -> BelsiPrimary
                            else -> MaterialTheme.colorScheme.outline
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPriority = value },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(alpha = 0.15f),
                                selectedLabelColor = chipColor
                            )
                        )
                    }
                }

                // Multi-select team members
                Text(
                    "Исполнители (${selectedMemberIds.size}):",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Column {
                    team.forEach { member ->
                        val isSelected = member.id in selectedMemberIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMemberIds = if (isSelected)
                                        selectedMemberIds - member.id
                                    else
                                        selectedMemberIds + member.id
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedMemberIds = if (checked)
                                        selectedMemberIds + member.id
                                    else
                                        selectedMemberIds - member.id
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(member.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    getRoleLabel(member.role),
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
            Button(
                onClick = {
                    if (title.isNotBlank() && selectedMemberIds.isNotEmpty()) {
                        onCreate(title, description.ifBlank { null }, selectedMemberIds.toList(), selectedPriority)
                    }
                },
                enabled = title.isNotBlank() && selectedMemberIds.isNotEmpty()
            ) {
                Text("Создать (${selectedMemberIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// =====================================================================
// Tab 3: Team
// =====================================================================

@Composable
private fun TeamTab(team: List<CoordinatorTeamMemberDto>) {
    val foremen = team.filter { it.isForeman }
    val installers = team.filter { it.isInstaller }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Команда (${team.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (foremen.isNotEmpty()) {
            item { Text("Бригадиры (${foremen.size})", fontWeight = FontWeight.SemiBold) }
            items(foremen, key = { it.id }) { member ->
                TeamMemberCard(member)
            }
        }

        if (installers.isNotEmpty()) {
            item { Text("Монтажники (${installers.size})", fontWeight = FontWeight.SemiBold) }
            items(installers, key = { it.id }) { member ->
                TeamMemberCard(member)
            }
        }

        if (team.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Команда не найдена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TeamMemberCard(member: CoordinatorTeamMemberDto) {
    val statusColor = when (member.currentShiftStatus) {
        "active" -> com.belsi.work.presentation.theme.Emerald500
        "paused" -> com.belsi.work.presentation.theme.Amber500
        "idle" -> com.belsi.work.presentation.theme.Rose500
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(getRoleLabel(member.role), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (member.isForeman && member.teamSize > 0) {
                        Text("• ${member.teamSize} чел.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (member.photosToday > 0) {
                        Text("• ${member.photosToday} фото", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (member.isActiveToday) {
                if (member.shiftDurationSeconds > 0) {
                    val hours = member.shiftDurationSeconds / 3600
                    val mins = (member.shiftDurationSeconds % 3600) / 60
                    Text("${hours}ч${mins}м", style = MaterialTheme.typography.bodySmall,
                        color = com.belsi.work.presentation.theme.Emerald500, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// =====================================================================
// Tab 4: Reports
// =====================================================================

@Composable
private fun ReportsTab(
    reports: List<CoordinatorReportDto>,
    viewModel: CoordinatorViewModel
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateReportDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { content, photoUris ->
                if (photoUris.isEmpty()) {
                    viewModel.createReport(content)
                } else {
                    viewModel.createReportWithPhotos(content, photoUris)
                }
                showCreateDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Отчёты (${reports.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FilledTonalButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Создать")
                }
            }
        }

        items(reports, key = { it.id }) { report ->
            ReportCard(report)
        }

        if (reports.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(8.dp))
                        Text("Нет отчётов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(report: CoordinatorReportDto) {
    val statusColor = when (report.status) {
        "submitted" -> com.belsi.work.presentation.theme.Sky500
        "reviewed" -> com.belsi.work.presentation.theme.Emerald500
        "draft" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }
    val statusText = when (report.status) {
        "submitted" -> "Отправлен"
        "reviewed" -> "Просмотрен"
        "draft" -> "Черновик"
        else -> report.status
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    report.reportDate?.take(10) ?: "—",
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                report.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (report.photoUrls.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                                    fontWeight = FontWeight.Bold,
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

@Composable
private fun CreateReportDialog(
    onDismiss: () -> Unit,
    onCreate: (String, List<Uri>) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedPhotos = selectedPhotos + uris
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый отчёт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Содержание отчёта") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 10
                )

                // Кнопка прикрепления фото
                OutlinedButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Прикрепить фото (${selectedPhotos.size})")
                }

                // Превью выбранных фото
                if (selectedPhotos.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedPhotos.size) { index ->
                            Box {
                                AsyncImage(
                                    model = selectedPhotos[index],
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                // Кнопка удаления
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(20.dp)
                                        .clickable {
                                            selectedPhotos = selectedPhotos.toMutableList().also {
                                                it.removeAt(index)
                                            }
                                        },
                                    shape = CircleShape,
                                    color = com.belsi.work.presentation.theme.Rose500
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        null,
                                        modifier = Modifier.padding(2.dp),
                                        tint = Color.White
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
                onClick = { if (content.isNotBlank()) onCreate(content, selectedPhotos) },
                enabled = content.isNotBlank()
            ) { Text("Отправить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// =====================================================================
// Site Edit Dialogs
// =====================================================================

@Composable
private fun EditSiteStatusDialog(
    currentStatus: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(currentStatus) }
    val statuses = listOf(
        "active" to "Активный",
        "paused" to "На паузе",
        "completed" to "Завершён"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить статус объекта") },
        text = {
            Column {
                statuses.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStatus = value }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStatus == value,
                            onClick = { selectedStatus = value }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedStatus) },
                enabled = selectedStatus != currentStatus
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun EditMeasurementsDialog(
    currentMeasurements: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    // Mutable list of key-value pairs for editing
    val entries = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll(currentMeasurements.entries.map { it.key to it.value })
        }
    }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Замеры объекта") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Existing entries
                entries.forEachIndexed { index, (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = key,
                            onValueChange = { newName ->
                                entries[index] = newName to entries[index].second
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            label = { Text("Название") }
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newVal ->
                                entries[index] = entries[index].first to newVal
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            label = { Text("Значение") }
                        )
                        IconButton(
                            onClick = { entries.removeAt(index) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, "Удалить",
                                tint = com.belsi.work.presentation.theme.Rose500, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Add new entry
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Добавить замер", style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text("Название") }
                    )
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text("Значение") }
                    )
                    IconButton(
                        onClick = {
                            if (newKey.isNotBlank() && newValue.isNotBlank()) {
                                entries.add(newKey.trim() to newValue.trim())
                                newKey = ""
                                newValue = ""
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        enabled = newKey.isNotBlank() && newValue.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, "Добавить",
                            tint = if (newKey.isNotBlank() && newValue.isNotBlank()) BelsiPrimary
                            else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = entries
                        .filter { it.first.isNotBlank() }
                        .associate { it.first.trim() to it.second.trim() }
                    onSave(result)
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun EditCommentsDialog(
    currentComments: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentComments) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Комментарии к объекту") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Комментарий") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 10
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// =====================================================================
// Shift Mega Block
// =====================================================================

@Composable
private fun ShiftMegaBlock(
    shiftState: ShiftUiState,
    dashboard: CoordinatorDashboardDto,
    onStartShift: () -> Unit,
    onPauseShift: () -> Unit,
    onResumeShift: () -> Unit,
    onOpenShift: () -> Unit
) {
    val (bgColor, statusText) = when (shiftState) {
        is ShiftUiState.Active -> {
            if (shiftState.isPaused) com.belsi.work.presentation.theme.Amber500 to "На паузе"
            else BelsiPrimary to "Активна"
        }
        is ShiftUiState.Loading -> MaterialTheme.colorScheme.surfaceVariant to "Загрузка..."
        is ShiftUiState.NoShift -> MaterialTheme.colorScheme.surfaceVariant to "Не начата"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: icon + title + timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(bgColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Timer, null, tint = bgColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Моя смена", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(bgColor)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(statusText, style = MaterialTheme.typography.bodySmall, color = bgColor)
                        }
                    }
                }

                // Timer display
                if (shiftState is ShiftUiState.Active) {
                    Text(
                        shiftState.formattedTime,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = bgColor
                    )
                }
            }

            // Work/pause time details
            if (shiftState is ShiftUiState.Active) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkOutline, null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(shiftState.formattedNetWorkTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (shiftState.totalPauseSeconds > 0 || shiftState.isPaused) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Pause, null,
                                modifier = Modifier.size(14.dp),
                                tint = com.belsi.work.presentation.theme.Amber500)
                            Spacer(Modifier.width(4.dp))
                            Text(shiftState.formattedTotalPauseTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = com.belsi.work.presentation.theme.Amber500)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            when (shiftState) {
                is ShiftUiState.NoShift -> {
                    Button(
                        onClick = onStartShift,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BelsiPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Начать смену")
                    }
                }
                is ShiftUiState.Active -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (shiftState.isPaused) {
                            Button(
                                onClick = onResumeShift,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = com.belsi.work.presentation.theme.Emerald500),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Продолжить")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onPauseShift,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Pause, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Пауза")
                            }
                        }
                        FilledTonalButton(
                            onClick = onOpenShift,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Открыть")
                        }
                    }
                }
                is ShiftUiState.Loading -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = BelsiPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // Quick links with badges
            if (shiftState is ShiftUiState.Active) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShiftQuickLink(
                        icon = Icons.Default.CameraAlt,
                        label = "Фото",
                        badge = if (dashboard.pendingPhotos > 0) "${dashboard.pendingPhotos}" else null,
                        color = com.belsi.work.presentation.theme.Sky500,
                        modifier = Modifier.weight(1f)
                    )
                    ShiftQuickLink(
                        icon = Icons.Default.Assignment,
                        label = "Задачи",
                        badge = if (dashboard.tasksTotal - dashboard.tasksCompleted > 0)
                            "${dashboard.tasksTotal - dashboard.tasksCompleted}" else null,
                        color = com.belsi.work.presentation.theme.Emerald500,
                        modifier = Modifier.weight(1f)
                    )
                    ShiftQuickLink(
                        icon = Icons.Default.Description,
                        label = "Отчёты",
                        badge = if (dashboard.reportsToday > 0) "${dashboard.reportsToday}" else null,
                        color = com.belsi.work.presentation.theme.Violet500,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShiftQuickLink(
    icon: ImageVector,
    label: String,
    badge: String?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
            if (badge != null) {
                Spacer(Modifier.width(4.dp))
                Badge(containerColor = color) {
                    Text(badge, color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// =====================================================================
// Local Site Components
// =====================================================================

@Composable
private fun LocalSiteCard(
    site: LocalSite,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val bgAlpha = if (site.isActive) 0.06f else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (site.isActive) Modifier.border(
                    width = 2.dp,
                    color = BelsiPrimary,
                    shape = RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .clickable { if (site.isActive) onEdit() else onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (site.isActive) BelsiPrimary.copy(alpha = bgAlpha)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active indicator
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (site.isActive) BelsiPrimary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (site.isActive) Icons.Default.CheckCircle else Icons.Default.Business,
                    null,
                    tint = if (site.isActive) BelsiPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        site.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (site.isActive) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BelsiPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Активный",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = BelsiPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (site.address.isNotBlank()) {
                    Text(
                        site.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (site.measurements.isNotEmpty()) {
                    Text(
                        "${site.measurements.size} замер(ов)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    "Редактировать",
                    tint = BelsiPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (!site.isActive) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        "Удалить",
                        tint = com.belsi.work.presentation.theme.Rose500.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddLocalSiteDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый объект") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название объекта") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onAdd(name.trim(), address.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun EditLocalSiteDialog(
    site: LocalSite,
    onDismiss: () -> Unit,
    onSave: (name: String, address: String, measurements: Map<String, String>, comments: String, status: String) -> Unit
) {
    var name by remember { mutableStateOf(site.name) }
    var address by remember { mutableStateOf(site.address) }
    var comments by remember { mutableStateOf(site.comments) }
    var selectedStatus by remember { mutableStateOf(site.status) }
    val measurements = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll(site.measurements.entries.map { it.key to it.value })
        }
    }
    var newMeasKey by remember { mutableStateOf("") }
    var newMeasVal by remember { mutableStateOf("") }

    val statuses = listOf(
        "active" to "Активный",
        "paused" to "На паузе",
        "completed" to "Завершён"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, null, tint = BelsiPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Редактировать объект")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Название
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Адрес
                item {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Адрес") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Статус
                item {
                    Text("Статус", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        statuses.forEach { (value, label) ->
                            val statusColor = when (value) {
                                "active" -> com.belsi.work.presentation.theme.Emerald500
                                "paused" -> com.belsi.work.presentation.theme.Amber500
                                "completed" -> com.belsi.work.presentation.theme.Sky500
                                else -> MaterialTheme.colorScheme.outline
                            }
                            FilterChip(
                                selected = selectedStatus == value,
                                onClick = { selectedStatus = value },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = statusColor.copy(alpha = 0.15f),
                                    selectedLabelColor = statusColor
                                )
                            )
                        }
                    }
                }

                // Замеры
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Замеры (${measurements.size})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }

                items(measurements.size) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = measurements[index].first,
                            onValueChange = { newKey ->
                                measurements[index] = newKey to measurements[index].second
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            label = { Text("Назв.") }
                        )
                        OutlinedTextField(
                            value = measurements[index].second,
                            onValueChange = { newVal ->
                                measurements[index] = measurements[index].first to newVal
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            label = { Text("Знач.") }
                        )
                        IconButton(
                            onClick = { measurements.removeAt(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, "Удалить",
                                tint = com.belsi.work.presentation.theme.Rose500, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Добавить замер
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = newMeasKey,
                            onValueChange = { newMeasKey = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            placeholder = { Text("Название") }
                        )
                        OutlinedTextField(
                            value = newMeasVal,
                            onValueChange = { newMeasVal = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            placeholder = { Text("Значение") }
                        )
                        IconButton(
                            onClick = {
                                if (newMeasKey.isNotBlank() && newMeasVal.isNotBlank()) {
                                    measurements.add(newMeasKey.trim() to newMeasVal.trim())
                                    newMeasKey = ""
                                    newMeasVal = ""
                                }
                            },
                            modifier = Modifier.size(32.dp),
                            enabled = newMeasKey.isNotBlank() && newMeasVal.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, "Добавить",
                                tint = if (newMeasKey.isNotBlank() && newMeasVal.isNotBlank()) BelsiPrimary
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Комментарии
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedTextField(
                        value = comments,
                        onValueChange = { comments = it },
                        label = { Text("Комментарии") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val measMap = measurements
                        .filter { it.first.isNotBlank() }
                        .associate { it.first.trim() to it.second.trim() }
                    onSave(name.trim(), address.trim(), measMap, comments, selectedStatus)
                },
                enabled = name.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// =====================================================================
// Shared Components
// =====================================================================

@Composable
private fun MiniStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "active" -> "Активный" to com.belsi.work.presentation.theme.Emerald500
        "paused" -> "На паузе" to com.belsi.work.presentation.theme.Amber500
        "completed" -> "Завершён" to com.belsi.work.presentation.theme.Sky500
        else -> status to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CategoryBadge(category: String) {
    if (category == "hourly") return
    val (label, color) = when (category) {
        "problem" -> "Проблема" to com.belsi.work.presentation.theme.Rose500
        "question" -> "Вопрос" to com.belsi.work.presentation.theme.Amber500
        else -> category to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PhotoStatusBadge(status: String) {
    val (label, color) = when (status) {
        "approved" -> "✓" to com.belsi.work.presentation.theme.Emerald500
        "rejected" -> "✗" to com.belsi.work.presentation.theme.Rose500
        "pending" -> "⏳" to com.belsi.work.presentation.theme.Amber500
        else -> "?" to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.1f)
    ) {
        Text(label, modifier = Modifier.padding(4.dp),
            style = MaterialTheme.typography.bodySmall, color = color)
    }
}

private fun getRoleLabel(role: String): String = when (role) {
    "installer" -> "Монтажник"
    "foreman" -> "Бригадир"
    "coordinator" -> "Координатор"
    "curator" -> "Куратор"
    else -> role
}

private fun formatDateTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        val dt = OffsetDateTime.parse(isoString)
        val now = OffsetDateTime.now()
        val formatter = when {
            dt.toLocalDate() == now.toLocalDate() -> DateTimeFormatter.ofPattern("'Сегодня,' HH:mm")
            dt.toLocalDate() == now.toLocalDate().minusDays(1) -> DateTimeFormatter.ofPattern("'Вчера,' HH:mm")
            else -> DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))
        }
        dt.format(formatter)
    } catch (e: Exception) {
        isoString.take(16).replace("T", " ")
    }
}
