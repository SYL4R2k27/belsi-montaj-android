package com.belsi.work.presentation.screens.curator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.local.LocalSite
import com.belsi.work.data.remote.dto.curator.*
import com.belsi.work.presentation.components.AiInsightsCard
import com.belsi.work.presentation.components.AppSessionTracker
import com.belsi.work.presentation.components.CuratorInsightsBuilder
import com.belsi.work.presentation.components.WorkTimeTracker
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.screens.curator.objects.CuratorObjectsTab
import com.belsi.work.presentation.screens.curator.photos.CuratorPhotosScreen
import com.belsi.work.presentation.screens.tasks.CuratorTasksScreen
import com.belsi.work.presentation.theme.*
import com.belsi.work.presentation.theme.belsiColors
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.belsi.work.utils.CuratorExportHelper
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// =====================================================================
// Curator Main Screen — production-ready, brand-consistent
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorMainScreen(
    navController: NavController,
    viewModel: CuratorViewModel = hiltViewModel()
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val foremen by viewModel.foremen.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val foremenFull by viewModel.foremenFull.collectAsState()
    val unassignedInstallers by viewModel.unassignedInstallers.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val tickets by viewModel.tickets.collectAsState()
    val localSites by viewModel.localSites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var usersFilter by remember { mutableStateOf("all") }

    val navigateToTab: (Int, String?) -> Unit = { tab, filter ->
        selectedTab = tab
        if (filter != null) usersFilter = filter
    }

    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler { /* Блокируем возврат к выбору роли */ }

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
                            Text("Куратор", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                            WorkTimeTracker(
                                startTimeMillis = AppSessionTracker.getSessionStartTime(),
                                compact = true
                            )
                        }
                    }
                },
                actions = {
                    // FIX(2026-05-05): Бейдж offline-очереди (скрыт когда pending = 0,
                    // т.е. для юзера 1.2.5 без шатдауна — UI визуально не меняется).
                    com.belsi.work.presentation.components.PendingBadge(navController)
                    // FIX(2026-04-30): Material 3 — максимум 2 видимые actions + overflow.
                    // Часто-используемые: Refresh + Overflow ⋮ со всем остальным.
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                    var showOverflow by remember { mutableStateOf(false) }
                    val exportContext = LocalContext.current
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, "Меню")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            // FIX(2026-05-11) BELSI 2.0.0: личный профиль куратора —
                            // первый пункт меню. Решение «overflow вместо 9-го таба» —
                            // фокус куратора на команде, не на себе; своё ФИО, аватар,
                            // смена пароля — действия редкие.
                            DropdownMenuItem(
                                text = { Text("Мой профиль") },
                                onClick = {
                                    showOverflow = false
                                    navController.navigate(AppRoute.Profile.route)
                                },
                                leadingIcon = { Icon(Icons.Default.Person, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("AI Dashboard") },
                                onClick = {
                                    showOverflow = false
                                    navController.navigate(AppRoute.AiDashboard.route)
                                },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }
                            )
                            // FIX(2026-05-10) BELSI 1.3.0: AI-поиск фото
                            DropdownMenuItem(
                                text = { Text("🔍 AI-поиск фото") },
                                onClick = {
                                    showOverflow = false
                                    navController.navigate(AppRoute.AiPhotoSearch.route)
                                },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Аналитика") },
                                onClick = {
                                    showOverflow = false
                                    navController.navigate(AppRoute.CuratorAnalytics.route)
                                },
                                leadingIcon = { Icon(Icons.Default.BarChart, null) }
                            )
                            // FIX(2026-05-05): Pipeline партий — все домены
                            DropdownMenuItem(
                                text = { Text("📦 Партии (все)") },
                                onClick = {
                                    showOverflow = false
                                    navController.navigate(AppRoute.BatchList.route)
                                },
                                leadingIcon = { Icon(Icons.Default.Inventory, null) }
                            )
                            // FIX(2026-05-05): История объекта — timeline по доменам
                            DropdownMenuItem(
                                text = { Text("📜 История объекта (демо)") },
                                onClick = {
                                    showOverflow = false
                                    // В демо передаём заглушку UUID. Реально юзер должен
                                    // выбрать объект из списка → не помещается в overflow.
                                    navController.navigate(AppRoute.ObjectHistory.createRoute("demo"))
                                },
                                leadingIcon = { Icon(Icons.Default.History, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                onClick = {
                                    showOverflow = false
                                    navController.navigate(AppRoute.Settings.route)
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, null) }
                            )
                            // FIX(2026-05-03): debug-доступ к Driver/Logistician sandbox
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("🚛 BELSI.Driver (тест UI)") },
                                onClick = {
                                    showOverflow = false
                                    navController.navigate(AppRoute.DriverPlayground.route)
                                },
                                leadingIcon = { Icon(Icons.Default.LocalShipping, null) }
                            )
                            if (selectedTab == 0) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Экспорт CSV") },
                                    onClick = {
                                        showOverflow = false
                                        CuratorExportHelper.exportDashboardCsv(
                                            exportContext, dashboard, foremen, photos
                                        ).onSuccess { file ->
                                            CuratorExportHelper.shareFile(exportContext, file)
                                        }.onFailure { err ->
                                            Toast.makeText(exportContext, err.message, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Description, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Экспорт PDF") },
                                    onClick = {
                                        showOverflow = false
                                        CuratorExportHelper.exportDashboardPdf(
                                            exportContext, dashboard, foremen, photos
                                        ).onSuccess { file ->
                                            CuratorExportHelper.shareFile(exportContext, file)
                                        }.onFailure { err ->
                                            Toast.makeText(exportContext, err.message, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        // FIX(2026-05-11) BELSI 2.0.0: единый источник правды через ResponsiveUtils.
        // Раньше был magic number 540 прямо здесь — теперь через
        // shouldUseNavigationRail() (тот же порог 540dp но с обоснованием в utils).
        val widthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        val useNavRail = com.belsi.work.presentation.utils.shouldUseNavigationRail()
        LaunchedEffect(widthDp) {
            android.util.Log.d("CuratorMain", "screenWidthDp=$widthDp useNavRail=$useNavRail")
        }

        val tabContent: @Composable () -> Unit = {
            if (isLoading && foremen.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                when (selectedTab) {
                    0 -> DashboardTab(dashboard, foremen, localSites, viewModel, navController, navigateToTab)
                    1 -> ForemenTab(foremen)
                    2 -> UsersTab(allUsers, navController)
                    3 -> CuratorObjectsTab()
                    4 -> CuratorTasksScreen(
                        foremen = foremenFull,
                        unassignedInstallers = unassignedInstallers,
                        coordinators = allUsers.filter { it.role == "coordinator" }
                    )
                    5 -> CuratorPhotosScreen(navController = navController, embedded = true)
                    6 -> TicketsTab(tickets, navController)
                    7 -> com.belsi.work.presentation.screens.messenger.ChatHubScreen(
                        navController = navController
                    )
                    // FIX(2026-05-11) BELSI 2.0.0: профиль куратора вынесен в overflow меню
                    // TopAppBar (⋮ → «Мой профиль»). Чтобы не плодить 9-й таб в управленческой
                    // ленте — у куратора фокус на команде, не на себе.
                }
            }
        }

        if (useNavRail) {
            // === EXPANDED (Z Fold open / tablet): NavigationRail слева ===
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    // FIX(2026-05-11) BELSI 2.0.0: NavigationRail scrollable. У куратора 8
                    // пунктов — на коротких экранах (Z Fold открытом портретно) не влезают.
                    // Material3 сам NavigationRail не прокручивает — оборачиваем содержимое
                    // в Column с verticalScroll. Это рекомендованный Android-паттерн.
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(8.dp))
                        NavigationRailItem(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Dashboard, null) }, label = { Text("Обзор") })
                        NavigationRailItem(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Groups, null) }, label = { Text("Бригады") })
                        NavigationRailItem(selected = selectedTab == 2, onClick = { selectedTab = 2; usersFilter = "all" },
                            icon = { Icon(Icons.Default.People, null) }, label = { Text("Люди") })
                        NavigationRailItem(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                            icon = { Icon(Icons.Default.Business, null) }, label = { Text("Объекты") })
                        NavigationRailItem(selected = selectedTab == 4, onClick = { selectedTab = 4 },
                            icon = { Icon(Icons.Default.Assignment, null) }, label = { Text("Задачи") })
                        NavigationRailItem(selected = selectedTab == 5, onClick = { selectedTab = 5 },
                            icon = {
                                BadgedBox(badge = {
                                    if (dashboard.pendingPhotos > 0) Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("${dashboard.pendingPhotos}", color = MaterialTheme.colorScheme.onError)
                                    }
                                }) { Icon(Icons.Default.CameraAlt, null) }
                            },
                            label = { Text("Фото") })
                        NavigationRailItem(selected = selectedTab == 6, onClick = { selectedTab = 6 },
                            icon = {
                                BadgedBox(badge = {
                                    if (dashboard.openSupportTickets > 0) Badge(containerColor = MaterialTheme.belsiColors.warning) {
                                        Text("${dashboard.openSupportTickets}", color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }) { Icon(Icons.Default.Support, null) }
                            },
                            label = { Text("Тикеты") })
                        NavigationRailItem(selected = selectedTab == 7, onClick = { selectedTab = 7 },
                            icon = { Icon(Icons.Default.Chat, null) }, label = { Text("Чат") })
                        Spacer(Modifier.height(8.dp))
                    }
                }
                // FIX(2026-05-11) BELSI 2.0.0: ограничение max-width содержимого
                // на широких экранах (Tab S Ultra 1232dp). Иначе одна колонка
                // карточек растягивается на 1100dp и теряет читаемость.
                // 1100dp — это разумный максимум для управленческого дашборда
                // куратора (5 stat-карт в ряд + текст-описания).
                // На Z Fold open 884dp — Box получит 884-80(rail)=804dp,
                // что меньше 1100 — обрезания не будет.
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
                        tabContent()
                    }
                }
            }
        } else {
            // === COMPACT (phone, foldable closed): ScrollableTabRow сверху ===
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 8.dp
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        text = { Text("Обзор") }, icon = { Icon(Icons.Default.Dashboard, null, Modifier.size(18.dp)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        text = { Text("Бригады") }, icon = { Icon(Icons.Default.Groups, null, Modifier.size(18.dp)) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2; usersFilter = "all" },
                        text = { Text("Люди") }, icon = { Icon(Icons.Default.People, null, Modifier.size(18.dp)) })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                        text = { Text("Объекты") }, icon = { Icon(Icons.Default.Business, null, Modifier.size(18.dp)) })
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 },
                        text = { Text("Задачи") }, icon = { Icon(Icons.Default.Assignment, null, Modifier.size(18.dp)) })
                    Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 },
                        text = { Text("Фото") },
                        // FIX(2026-04-30): BadgedBox над иконкой, не inline с текстом
                        icon = {
                            BadgedBox(badge = {
                                if (dashboard.pendingPhotos > 0) Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("${dashboard.pendingPhotos}", color = MaterialTheme.colorScheme.onError)
                                }
                            }) { Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp)) }
                        })
                    Tab(selected = selectedTab == 6, onClick = { selectedTab = 6 },
                        text = { Text("Тикеты") },
                        icon = {
                            BadgedBox(badge = {
                                if (dashboard.openSupportTickets > 0) Badge(containerColor = MaterialTheme.belsiColors.warning) {
                                    Text("${dashboard.openSupportTickets}", color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }) { Icon(Icons.Default.Support, null, Modifier.size(18.dp)) }
                        })
                    Tab(selected = selectedTab == 7, onClick = { selectedTab = 7 },
                        text = { Text("Чат") },
                        icon = { Icon(Icons.Default.Chat, null, Modifier.size(18.dp)) })
                }
                tabContent()
            }
        }
    }
}

// =====================================================================
// Tab 0: Dashboard
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTab(
    stats: CuratorDashboardDto,
    foremen: List<CuratorForemanDto>,
    localSites: List<LocalSite>,
    viewModel: CuratorViewModel,
    navController: NavController,
    navigateToTab: (Int, String?) -> Unit
) {
    var showAddSiteDialog by remember { mutableStateOf(false) }
    var siteToEdit by remember { mutableStateOf<LocalSite?>(null) }
    var siteToDelete by remember { mutableStateOf<LocalSite?>(null) }

    if (showAddSiteDialog) {
        CuratorAddSiteDialog(
            onDismiss = { showAddSiteDialog = false },
            onAdd = { name, address ->
                viewModel.addLocalSite(name, address)
                showAddSiteDialog = false
            }
        )
    }

    siteToEdit?.let { editingSite ->
        CuratorEditSiteDialog(
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
            text = { Text("«${deletingSite.name}» будет удалён.") },
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

    // FIX(2026-05-10) BELSI 1.3.0: AI инсайты теперь от настоящего AI (Llama 70B
    // через XeroCode). Если AI недоступен — fallback на клиентские эвристики.
    val aiSummary by viewModel.aiSummary.collectAsState()
    val aiSummaryLoading by viewModel.aiSummaryLoading.collectAsState()

    // Загружаем AI-сводку при первом появлении дашборда
    LaunchedEffect(Unit) {
        viewModel.loadAiSummary()
    }

    val aiInsights = remember(aiSummary, stats, foremen) {
        if (aiSummary != null) {
            // Реальный AI: конвертируем summary + anomalies + recommendations в List<AiInsight>
            CuratorInsightsBuilder.fromAiSummary(aiSummary!!)
        } else {
            // Fallback на эвристики
            CuratorInsightsBuilder.build(stats, foremen)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ⚡ AI инсайты — первое, что видит куратор при входе на дашборд.
        // FIX(2026-04-30): пример из брендбука — фиолетовая карточка с фактами.
        if (aiInsights.isNotEmpty()) {
            item { AiInsightsCard(aiInsights) }
        }

        // Карточки "Сегодня"
        item {
            Text("Сегодня", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashMiniCard("Смен", "${stats.totalShiftsToday}", Icons.Default.Schedule,
                    MaterialTheme.belsiColors.info, Modifier.weight(1f),
                    onClick = { navigateToTab(3, null) })
                DashMiniCard("Активных", "${stats.activeInstallersToday}", Icons.Default.Person,
                    MaterialTheme.belsiColors.success, Modifier.weight(1f),
                    onClick = { navigateToTab(2, "active") })
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashMiniCard("Фото", "${stats.pendingPhotos}", Icons.Default.CameraAlt,
                    MaterialTheme.belsiColors.warning, Modifier.weight(1f),
                    onClick = { navigateToTab(4, null) })
                DashMiniCard("Тикеты", "${stats.openSupportTickets}", Icons.Default.Support,
                    MaterialTheme.colorScheme.error, Modifier.weight(1f),
                    onClick = { navigateToTab(5, null) })
            }
        }

        // Общая статистика
        item {
            Spacer(Modifier.height(4.dp))
            Text("Команда", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            DashStatCard("Координаторы", "${stats.totalCoordinators}",
                "${stats.activeCoordinatorsToday} активны сегодня", Icons.Default.Engineering, Color(0xFF00897B),
                onClick = { navigateToTab(2, "coordinator") })
        }
        item {
            DashStatCard("Бригадиры", "${stats.totalForemen}",
                "${stats.activeForemenToday} активны сегодня", Icons.Default.SupervisorAccount, MaterialTheme.colorScheme.primary,
                onClick = { navigateToTab(2, "foreman") })
        }
        item {
            DashStatCard("Монтажники", "${stats.totalInstallers}",
                "${stats.activeInstallersToday} на смене сегодня", Icons.Default.Engineering, MaterialTheme.colorScheme.tertiary,
                onClick = { navigateToTab(2, "installer") })
        }
        item {
            DashStatCard("Инструменты", "${stats.totalTools}",
                "${stats.toolsIssued} выдано", Icons.Default.Build, MaterialTheme.belsiColors.info,
                onClick = { navController.navigate(AppRoute.CuratorTools.route) })
        }
        item {
            DashStatCard("Эффективность", "%.0f%%".format(stats.averageCompletionPercentage),
                "Средний прогресс выполнения", Icons.Default.TrendingUp, MaterialTheme.belsiColors.success)
        }

        // ===== Объекты (SiteStore) =====
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Объекты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
            items(localSites, key = { "curator_site_${it.id}" }) { localSite ->
                CuratorLocalSiteCard(
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
                    shape = MaterialTheme.shapes.large,
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
                            "Нет объектов. Нажмите «Добавить» для создания.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Быстрые действия
        item {
            Spacer(Modifier.height(4.dp))
            Text("Управление", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            QuickActionCard("Управление инструментами", "Транзакции выдачи и возврата",
                Icons.Default.Build, MaterialTheme.belsiColors.info) {
                navController.navigate(AppRoute.CuratorTools.route)
            }
        }
        item {
            QuickActionCard("Модерация фото", "${stats.pendingPhotos} на проверке",
                Icons.Default.PhotoLibrary, MaterialTheme.belsiColors.warning) {
                navController.navigate(AppRoute.CuratorPhotos.route)
            }
        }
        item {
            QuickActionCard("Тикеты поддержки", "${stats.openSupportTickets} открытых",
                Icons.Default.Support, MaterialTheme.belsiColors.info) {
                navController.navigate(AppRoute.CuratorSupport.route)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DashMiniCard(
    title: String, value: String, icon: ImageVector, color: Color,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, Modifier.size(28.dp), tint = color)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DashStatCard(
    title: String, value: String, subtitle: String, icon: ImageVector, color: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.1f)) {
                Icon(icon, null, Modifier.padding(12.dp).size(36.dp), tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickActionCard(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.15f)) {
                Icon(icon, null, Modifier.padding(12.dp).size(28.dp), tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// =====================================================================
// Tab 1: Foremen
// =====================================================================

@Composable
private fun ForemenTab(foremen: List<CuratorForemanDto>) {
    if (foremen.isEmpty()) {
        EmptyStateView(Icons.Default.Groups, "Нет бригадиров", "Бригадиры появятся после регистрации")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(foremen, key = { it.id }) { foreman ->
                ForemanCard(foreman)
            }
        }
    }
}

@Composable
private fun ForemanCard(foreman: CuratorForemanDto) {
    var expanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                    Icon(Icons.Default.Person, null, Modifier.padding(12.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(foreman.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(foreman.phone, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Раскрытие списка монтажников
                if (foreman.installers.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            "Показать монтажников"
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Статистика бригадира
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(Icons.Default.People, "${foreman.teamSize} чел.")
                InfoChip(Icons.Default.Schedule, "${foreman.totalShiftsToday} смен")
                InfoChip(Icons.Default.Build, "${foreman.toolsCount} инстр.")
                if (foreman.pendingPhotosCount > 0) {
                    InfoChip(Icons.Default.CameraAlt, "${foreman.pendingPhotosCount} фото",
                        chipColor = MaterialTheme.belsiColors.warning)
                }
            }

            // Прогресс
            if (foreman.completionPercentage > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (foreman.completionPercentage / 100f).toFloat() },
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall),
                    color = MaterialTheme.belsiColors.success,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text("%.0f%% выполнено".format(foreman.completionPercentage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Раскрытый список монтажников
            if (expanded && foreman.installers.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Монтажники", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                foreman.installers.forEach { installer ->
                    InstallerRow(installer)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun InstallerRow(installer: CuratorInstallerDto) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Person, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(installer.fullName, style = MaterialTheme.typography.bodyMedium)
            Text(installer.phone, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${installer.totalShifts} смен", style = MaterialTheme.typography.bodySmall)
            Text("%.1f ч".format(installer.totalHours), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, chipColor: Color? = null) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = (chipColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.08f)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = chipColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall,
                color = chipColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// =====================================================================
// Tab 2: Users (All Users)
// =====================================================================

@Composable
private fun UsersTab(
    users: List<AllUserDto>,
    navController: NavController,
    initialFilter: String = "all",
    onFilterChange: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember(initialFilter) { mutableStateOf(initialFilter) }

    val filteredUsers = users.filter { user ->
        val matchesSearch = searchQuery.isEmpty() ||
            user.displayName.contains(searchQuery, ignoreCase = true) ||
            user.phone.contains(searchQuery)

        val matchesFilter = when (selectedFilter) {
            "foreman" -> user.isForeman
            "installer" -> user.isInstaller
            "coordinator" -> user.isCoordinator
            "active" -> user.isActiveToday
            else -> true
        }

        matchesSearch && matchesFilter
    }

    Column(Modifier.fillMaxSize()) {
        // Search and filter
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск по имени или телефону") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                // Filter chips (скроллируемый ряд)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "all",
                        onClick = { selectedFilter = "all"; onFilterChange("all") },
                        label = { Text("Все (${users.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "active",
                        onClick = { selectedFilter = "active"; onFilterChange("active") },
                        label = { Text("Активные (${users.count { it.isActiveToday }})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "coordinator",
                        onClick = { selectedFilter = "coordinator"; onFilterChange("coordinator") },
                        label = { Text("Координаторы (${users.count { it.isCoordinator }})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "foreman",
                        onClick = { selectedFilter = "foreman"; onFilterChange("foreman") },
                        label = { Text("Бригадиры (${users.count { it.isForeman }})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "installer",
                        onClick = { selectedFilter = "installer"; onFilterChange("installer") },
                        label = { Text("Монтажники (${users.count { it.isInstaller }})") }
                    )
                }
            }
        }

        if (filteredUsers.isEmpty()) {
            EmptyStateView(
                Icons.Default.PersonSearch,
                if (users.isEmpty()) "Нет пользователей" else "Ничего не найдено",
                if (users.isEmpty()) "Пользователи появятся после регистрации" else "Попробуйте изменить запрос"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredUsers, key = { it.id }) { user ->
                    UserCard(user) {
                        navController.navigate(AppRoute.CuratorUserDetail.createRoute(user.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: AllUserDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with role indicator
            val avatarColor = when {
                user.isCoordinator -> Color(0xFF00897B)
                user.isForeman -> MaterialTheme.colorScheme.primary
                else -> com.belsi.work.presentation.theme.Violet500
            }
            val avatarIcon = when {
                user.isCoordinator -> Icons.Default.Engineering
                user.isForeman -> Icons.Default.SupervisorAccount
                else -> Icons.Default.Person
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = avatarColor.copy(alpha = 0.1f)
            ) {
                Icon(
                    avatarIcon,
                    null,
                    Modifier.padding(12.dp).size(28.dp),
                    tint = avatarColor
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                // Name
                Text(
                    user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Phone
                Text(
                    user.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                // Status chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Role badge
                    val badgeColor = when {
                        user.isCoordinator -> Color(0xFF00897B)
                        user.isForeman -> MaterialTheme.colorScheme.primary
                        else -> com.belsi.work.presentation.theme.Violet500
                    }
                    val badgeLabel = when {
                        user.isCoordinator -> "Координатор"
                        user.isForeman -> "Бригадир"
                        else -> "Монтажник"
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = badgeColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            badgeLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor
                        )
                    }

                    // Active today indicator
                    if (user.isActiveToday) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.belsiColors.success.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Circle, null, Modifier.size(8.dp), tint = MaterialTheme.belsiColors.success)
                                Spacer(Modifier.width(4.dp))
                                Text("Активен", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.belsiColors.success)
                            }
                        }
                    }

                    // Pending photos
                    if (user.pendingPhotosCount > 0) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.belsiColors.warning.copy(alpha = 0.1f)
                        ) {
                            Text(
                                "${user.pendingPhotosCount} фото",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.belsiColors.warning
                            )
                        }
                    }
                }

                // Foreman name for installers
                if (user.isInstaller && user.foremanName != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Бригадир: ${user.foremanName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Stats column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${user.totalShifts} смен",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "%.1f ч".format(user.totalHours),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =====================================================================
// Tab 3: Photos
// =====================================================================

// =====================================================================
// Tab 3: Support Tickets
// =====================================================================

@Composable
private fun TicketsTab(tickets: List<CuratorSupportTicketDto>, navController: NavController) {
    val openTickets = tickets.filter { it.status == "open" }
    val inProgressTickets = tickets.filter { it.status == "in_progress" }
    val resolvedTickets = tickets.filter { it.status == "resolved" || it.status == "closed" }

    // FIX(2026-05-11) BELSI 2.0.0 (Фаза 3.1): list-detail на expanded.
    // На compact (<840dp) — текущее поведение: тап на тикет → отдельный экран.
    // На expanded — слева список 38%, справа preview выбранного тикета 62%.
    var selectedTicketId by remember { mutableStateOf<String?>(null) }
    val selected = tickets.firstOrNull { it.id == selectedTicketId }

    if (tickets.isEmpty()) {
        EmptyStateView(Icons.Default.Support, "Нет тикетов", "Тикеты от пользователей появятся здесь")
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        if (isExpanded) {
            // Two-pane layout
            Row(Modifier.fillMaxSize()) {
                // ЛЕВО: список
                LazyColumn(
                    modifier = Modifier.weight(0.38f).fillMaxHeight(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TicketCountCard("Откр", openTickets.size, MaterialTheme.belsiColors.info, Modifier.weight(1f))
                            TicketCountCard("В раб", inProgressTickets.size, MaterialTheme.belsiColors.warning, Modifier.weight(1f))
                            TicketCountCard("Реш", resolvedTickets.size, MaterialTheme.belsiColors.success, Modifier.weight(1f))
                        }
                    }
                    items(tickets.take(20), key = { it.id }) { ticket ->
                        TicketCard(
                            ticket = ticket,
                            isSelected = ticket.id == selectedTicketId,
                            onClick = { selectedTicketId = ticket.id },
                        )
                    }
                }
                // Разделитель
                VerticalDivider(modifier = Modifier.fillMaxHeight(),
                    color = MaterialTheme.colorScheme.outlineVariant)
                // ПРАВО: preview выбранного
                Box(modifier = Modifier.weight(0.62f).fillMaxHeight().padding(16.dp)) {
                    if (selected != null) {
                        TicketDetailPane(ticket = selected, navController = navController)
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.Support,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Выберите тикет слева",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            // COMPACT: текущая логика, без preview-pane
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TicketCountCard("Открыто", openTickets.size, MaterialTheme.belsiColors.info, Modifier.weight(1f))
                        TicketCountCard("В работе", inProgressTickets.size, MaterialTheme.belsiColors.warning, Modifier.weight(1f))
                        TicketCountCard("Решено", resolvedTickets.size, MaterialTheme.belsiColors.success, Modifier.weight(1f))
                    }
                }
                item {
                    Button(
                        onClick = { navController.navigate(AppRoute.CuratorSupport.route) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Все тикеты")
                    }
                }
                item {
                    Text("Последние тикеты", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
                items(tickets.take(10), key = { it.id }) { ticket ->
                    TicketCard(ticket) {
                        navController.navigate(AppRoute.CuratorChatConversation.createRoute(ticket.id, ticket.userPhone))
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketDetailPane(
    ticket: CuratorSupportTicketDto,
    navController: NavController,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header с AI badge + status
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        ticket.userName ?: "Пользователь",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    com.belsi.work.presentation.components.LazyAiTriageBadge(ticketId = ticket.id)
                }
                if (!ticket.userPhone.isNullOrBlank()) {
                    Text(
                        ticket.userPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TicketStatusBadge(ticket.status)
        }

        // Last message snippet
        if (!ticket.lastMessageSnippet.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Последнее сообщение",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(ticket.lastMessageSnippet, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Категория + unread
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ticket.category != null) {
                Surface(shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                    Text(
                        ticket.category,
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (ticket.unreadCount > 0) {
                Surface(shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
                    Text(
                        "${ticket.unreadCount} непрочитано",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Кнопка открыть полностью (full conversation на отдельном экране)
        Button(
            onClick = {
                navController.navigate(
                    AppRoute.CuratorChatConversation.createRoute(ticket.id, ticket.userPhone)
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Открыть переписку")
        }
    }
}

@Composable
private fun TicketCountCard(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TicketCard(
    ticket: CuratorSupportTicketDto,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors(),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary
        ) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ticket.userName ?: "Пользователь",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        // FIX(2026-05-11) BELSI 2.0.0: AI-приоритет тикета (lazy load).
                        com.belsi.work.presentation.components.LazyAiTriageBadge(ticketId = ticket.id)
                    }
                    Text(ticket.userPhone ?: "", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TicketStatusBadge(ticket.status)
            }

            if (!ticket.lastMessageSnippet.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(ticket.lastMessageSnippet, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (ticket.category != null) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                        Text(ticket.category, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ticket.unreadCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text("${ticket.unreadCount}", color = MaterialTheme.colorScheme.onError)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(formatDateTime(ticket.createdAt), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TicketStatusBadge(status: String) {
    val (text, color) = when (status) {
        "open" -> "Открыт" to MaterialTheme.belsiColors.info
        "in_progress" -> "В работе" to MaterialTheme.belsiColors.warning
        "resolved" -> "Решён" to MaterialTheme.belsiColors.success
        "closed" -> "Закрыт" to MaterialTheme.colorScheme.outline
        else -> status to MaterialTheme.colorScheme.outline
    }
    Surface(shape = MaterialTheme.shapes.medium, color = color) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
    }
}

// =====================================================================
// Site Management Components (Curator)
// =====================================================================

@Composable
private fun CuratorLocalSiteCard(
    site: LocalSite,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (site.isActive) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable { if (site.isActive) onEdit() else onSelect() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (site.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (site.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (site.isActive) Icons.Default.CheckCircle else Icons.Default.Business,
                    null,
                    tint = if (site.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(site.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (site.isActive) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Активный",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (site.address.isNotBlank()) {
                    Text(site.address, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val infoItems = mutableListOf<String>()
                if (site.measurements.isNotEmpty()) infoItems.add("${site.measurements.size} замер(ов)")
                if (site.comments.isNotBlank()) infoItems.add("есть комментарии")
                if (infoItems.isNotEmpty()) {
                    Text(infoItems.joinToString(" • "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, "Редактировать", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            if (!site.isActive) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Удалить",
                        tint = com.belsi.work.presentation.theme.Rose500.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CuratorAddSiteDialog(
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
                    value = name, onValueChange = { name = it },
                    label = { Text("Название объекта") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Адрес") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onAdd(name.trim(), address.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun CuratorEditSiteDialog(
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

    val statuses = listOf("active" to "Активный", "paused" to "На паузе", "completed" to "Завершён")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Редактировать объект")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = address, onValueChange = { address = it },
                        label = { Text("Адрес") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
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
                            onValueChange = { newKey -> measurements[index] = newKey to measurements[index].second },
                            modifier = Modifier.weight(1f), singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall, label = { Text("Назв.") }
                        )
                        OutlinedTextField(
                            value = measurements[index].second,
                            onValueChange = { newVal -> measurements[index] = measurements[index].first to newVal },
                            modifier = Modifier.weight(1f), singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall, label = { Text("Знач.") }
                        )
                        IconButton(onClick = { measurements.removeAt(index) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "Удалить", tint = com.belsi.work.presentation.theme.Rose500, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = newMeasKey, onValueChange = { newMeasKey = it },
                            modifier = Modifier.weight(1f), singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall, placeholder = { Text("Название") }
                        )
                        OutlinedTextField(
                            value = newMeasVal, onValueChange = { newMeasVal = it },
                            modifier = Modifier.weight(1f), singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall, placeholder = { Text("Значение") }
                        )
                        IconButton(
                            onClick = {
                                if (newMeasKey.isNotBlank() && newMeasVal.isNotBlank()) {
                                    measurements.add(newMeasKey.trim() to newMeasVal.trim())
                                    newMeasKey = ""; newMeasVal = ""
                                }
                            },
                            modifier = Modifier.size(32.dp),
                            enabled = newMeasKey.isNotBlank() && newMeasVal.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, "Добавить",
                                tint = if (newMeasKey.isNotBlank() && newMeasVal.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedTextField(
                        value = comments, onValueChange = { comments = it },
                        label = { Text("Комментарии") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 4, minLines = 2
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// =====================================================================
// Shared
// =====================================================================

@Composable
private fun EmptyStateView(icon: ImageVector, title: String, subtitle: String,
                           iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(64.dp), tint = iconColor)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
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
        isoString.take(16)
    }
}
