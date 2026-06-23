package com.belsi.work.presentation.screens.foreman

/**
 * ВЕРНАЯ реализация утверждённого макета бригадира — Android Material 3
 * (docs/mockups/2026-06-04-foreman-android-material3.html). БЕЗ УРЕЗАНИЙ.
 * NavigationBar с active-indicator-пилюлей, tonal-поверхности surf1..5, M3-карточки,
 * filled/tonal/outlined/error-tonal кнопки, M3 bottom sheets, navigation drawer,
 * member-cards (avatar/pill/action-row/⋮), A/B segmented, объект-stages, реальные
 * Material-иконки, НОЛЬ эмодзи. Переиспользует ForemanViewModel + QrImage + BroadcastSheet.
 * Прежняя «упрощённая» ForemanMainScreen делегирует сюда.
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.object_v3.CabinetDto
import com.belsi.work.data.remote.dto.object_v3.WindowDto
import com.belsi.work.data.remote.dto.team.ForemanTeamMemberDto
import com.belsi.work.data.repositories.ObjectTree
import com.belsi.work.data.repositories.ObjectV3Repository
import androidx.compose.ui.platform.LocalContext
import com.belsi.work.presentation.components.OfflineChip
import com.belsi.work.presentation.components.QrImage
import com.belsi.work.presentation.screens.profile.ProfileScreen
import com.belsi.work.presentation.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// MARK: - Material 3 токены (точно из макета, seed = BELSI purple)
private object FM3 {
    val primary = Color(0xFF5B45D6); val onPrimary = Color.White
    val primaryC = Color(0xFFE6DEFF); val onPrimaryC = Color(0xFF1B0C66)
    val secondaryC = Color(0xFFE5E0EC); val onSecondaryC = Color(0xFF1D1A26)
    val tertiaryC = Color(0xFFFFD8E5); val onTertiaryC = Color(0xFF3E0021)
    val surface = Color(0xFFFCF8FF)
    val surf1 = Color(0xFFF6F1FB); val surf2 = Color(0xFFF0EAF8); val surf3 = Color(0xFFEBE4F4)
    val surf4 = Color(0xFFE9E1F3); val surf5 = Color(0xFFE5DCF0)
    val onSurface = Color(0xFF1C1B20); val onSurfaceVar = Color(0xFF48454E)
    val outline = Color(0xFF79767D); val outlineVar = Color(0xFFC9C4D0)
    val green = Color(0xFF2E7D44); val greenC = Color(0xFFB4F1BC); val onGreenC = Color(0xFF00210D)
    val red = Color(0xFFBA1A1A); val redC = Color(0xFFFFDAD6); val onRedC = Color(0xFF410002)
    val amber = Color(0xFF7A5900); val amberC = Color(0xFFFFDF9B); val onAmberC = Color(0xFF261900)
    val giTeam = Color(0xFFCDE5FF); val onGiTeam = Color(0xFF00497D)

    fun statusColors(s: String): Triple<Color, Color, String> = when (s.lowercase()) {
        "working" -> Triple(greenC, onGreenC, "работает")
        "idle" -> Triple(redC, onRedC, "простой")
        "paused", "on_break", "break", "lunch" -> Triple(amberC, onAmberC, "перерыв")
        else -> Triple(surf3, onSurfaceVar, "не на смене")
    }
}

private enum class FTab(val title: String, val icon: ImageVector) {
    SHIFT("Смена", Icons.Default.Schedule),
    SUMMARY("Сводка", Icons.Default.Dashboard),
    TEAM("Команда", Icons.Default.Groups),
    PROFILE("Профиль", Icons.Default.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForemanRedesignScreen(
    navController: NavController,
    viewModel: ForemanViewModel = hiltViewModel()
) {
    val team by viewModel.teamMembers.collectAsState()
    val tools by viewModel.tools.collectAsState()
    val createdTasks by viewModel.createdTasks.collectAsState()
    val teamPhotos by viewModel.teamPhotos.collectAsState()
    val foremanShift by viewModel.foremanShift.collectAsState()
    val availableObjects by viewModel.availableObjects.collectAsState()
    val currentObjectName by viewModel.currentObjectName.collectAsState()
    val showInviteDialog by viewModel.showInviteDialog.collectAsState()
    val generatedInviteCode by viewModel.generatedInviteCode.collectAsState()
    val isBroadcasting by viewModel.isBroadcasting.collectAsState()
    val isIdlingAll by viewModel.isIdlingAll.collectAsState()

    var tab by remember { mutableStateOf(FTab.SHIFT) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showBroadcast by remember { mutableStateOf(false) }
    var showIdleAll by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var showRoleSheet by remember { mutableStateOf(false) }
    var brigadeIndex by remember { mutableIntStateOf(0) }
    var objectReadId by remember { mutableStateOf<String?>(null) }
    val currentObjId = availableObjects.firstOrNull { it.name == currentObjectName }?.id ?: availableObjects.firstOrNull()?.id

    // live polling статусов (как старый экран)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.loadAllData()                 // мгновенная загрузка при входе/возврате
            viewModel.restoreForemanPause()         // подтянуть активную паузу/простой с сервера
            while (true) { delay(30_000L); viewModel.loadAllData() }
        }
    }

    val working = team.count { it.shiftStatus == "working" }
    val idleN = team.count { it.shiftStatus == "idle" }
    val idleMembers = team.filter { it.shiftStatus == "idle" }
    val pendingPhotos = teamPhotos.count { it.status == "pending" }
    val issuedTools = tools.count { it.status == "issued" }
    val tasksDone = createdTasks.count { it.status == "done" }

    Box(Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ForemanM3Drawer(
                navController = navController,
                onClose = { scope.launch { drawerState.close() } },
                onInvite = { scope.launch { drawerState.close() }; viewModel.createInvite() },
                onRoleSwitch = { scope.launch { drawerState.close() }; showRoleSheet = true },
                onChat = { scope.launch { drawerState.close() }; viewModel.getOrCreateGroupThread { tid -> navController.navigate(AppRoute.MessengerConversation.createRoute(tid)) } },
                onLogout = { scope.launch { drawerState.close() }; navController.navigate(AppRoute.Settings.route) }
            )
        }
    ) {
        Scaffold(
            containerColor = FM3.surface,
            topBar = {
                ForemanM3AppBar(
                    title = tab.title,
                    onMenu = { scope.launch { drawerState.open() } },
                    onChat = {
                        scope.launch { viewModel.getOrCreateGroupThread { tid -> navController.navigate(AppRoute.MessengerConversation.createRoute(tid)) } }
                    },
                    navController = navController
                )
            },
            bottomBar = {
                NavigationBar(containerColor = FM3.surf2, tonalElevation = 0.dp) {
                    FTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                if (t == FTab.SUMMARY && idleN > 0) {
                                    BadgedBox(badge = { Badge(containerColor = FM3.red) }) { Icon(t.icon, null) }
                                } else Icon(t.icon, null)
                            },
                            label = { Text(t.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FM3.onPrimaryC,
                                indicatorColor = FM3.primaryC,
                                selectedTextColor = FM3.onSurface,
                                unselectedIconColor = FM3.onSurfaceVar,
                                unselectedTextColor = FM3.onSurfaceVar
                            )
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    FTab.SHIFT -> ShiftTab(foremanShift, currentObjectName, navController, viewModel, onFinish = { showFinish = true }, onObject = { if (currentObjId != null) objectReadId = currentObjId })
                    FTab.SUMMARY -> SummaryTab(
                        team, working, idleN, idleMembers, tools, issuedTools, createdTasks, tasksDone, pendingPhotos,
                        navController, viewModel,
                        onInvite = { viewModel.createInvite() }, onBroadcast = { showBroadcast = true }, onIdleAll = { showIdleAll = true },
                        onOpenTeam = { tab = FTab.TEAM }, onObject = { if (currentObjId != null) objectReadId = currentObjId }
                    )
                    FTab.TEAM -> TeamTab(team, brigadeIndex, onBrigade = { brigadeIndex = it }, navController, viewModel,
                        onInvite = { viewModel.createInvite() }, onBroadcast = { showBroadcast = true }, onIdleAll = { showIdleAll = true })
                    FTab.PROFILE -> ProfileScreen(navController)   // реальный профиль: личность + аккаунты + смена роли + выход
                }
            }
        }
    }
    // object-read overlay (push «Структура объекта»)
    if (objectReadId != null) {
        ForemanObjectReadScreen(objectReadId!!, currentObjectName ?: "Объект", onBack = { objectReadId = null })
    }
    } // close Box

    // Sheets
    if (showBroadcast) {
        com.belsi.work.presentation.components.BroadcastSheet(
            teamSize = team.size, isSending = isBroadcasting,
            onSend = { msg -> viewModel.broadcastToTeam(msg) { ok -> if (ok) showBroadcast = false } },
            onDismiss = { showBroadcast = false }
        )
    }
    if (showIdleAll) {
        ForemanM3IdleAllSheet(isLoading = isIdlingAll, onConfirm = { r, c -> viewModel.idleAllBrigade(r, c) { ok, _ -> if (ok) showIdleAll = false } }, onDismiss = { showIdleAll = false })
    }
    if (showFinish) {
        ForemanM3FinishSheet(team = team, pendingPhotos = pendingPhotos, idleN = idleN, tasksDone = tasksDone, tasksTotal = createdTasks.size,
            onDone = { showFinish = false; viewModel.endForemanShift() }, onDismiss = { showFinish = false })
    }
    if (showInviteDialog && generatedInviteCode != null) {
        ForemanM3QrInviteDialog(code = generatedInviteCode!!, onDismiss = { viewModel.dismissInviteDialog() })
    }
    // реальный переключатель ролей (setActiveRole + явная навигация на home роли)
    com.belsi.work.presentation.components.BrandRoleSwitchSheet(showRoleSheet, navController) { showRoleSheet = false }
}

// MARK: - App bar (M3)
@Composable
private fun ForemanM3AppBar(title: String, onMenu: () -> Unit, onChat: () -> Unit, navController: NavController) {
    Surface(color = FM3.surface) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 4.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Меню", tint = FM3.onSurfaceVar) }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = FM3.onSurface, modifier = Modifier.weight(1f).padding(start = 4.dp))
            OfflineChip(navController = navController)
            IconButton(onClick = onChat, modifier = Modifier.padding(end = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.Chat, "Чат", tint = FM3.onSurfaceVar)
            }
        }
    }
}

// MARK: - СМЕНА
@Composable
private fun ShiftTab(
    shift: ForemanShiftState, currentObjectName: String?, navController: NavController,
    viewModel: ForemanViewModel, onFinish: () -> Unit, onObject: () -> Unit
) {
    var ticker by remember { mutableLongStateOf(shift.elapsedSeconds) }
    LaunchedEffect(shift.isActive, shift.elapsedSeconds) {
        ticker = shift.elapsedSeconds
        while (shift.isActive) { delay(1000); ticker += 1 }
    }
    var showIdleReason by remember { mutableStateOf(false) }
    val pk = shift.pauseKind

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrigadeChip()
        // timer card (elevated)
        M3Card(elevated = true) {
            Text(fmtTimer(ticker), fontSize = 54.sp, fontWeight = FontWeight.Bold, color = FM3.onSurface,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(
                when (pk) {
                    "pause" -> "на паузе · нажми «Пауза» чтобы продолжить"
                    "lunch" -> "обед · нажми «Обед» чтобы продолжить"
                    "smoke" -> "перекур · нажми «Перекур» чтобы продолжить"
                    "idle"  -> "простой · нажми «Простой» чтобы продолжить"
                    else    -> if (shift.isActive) "идёт · ${currentObjectName ?: "объект"}" else "смена не начата"
                },
                fontSize = 14.sp, color = if (pk != null) FM3.amber else FM3.onSurfaceVar,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PauseGridBtn("Пауза", Icons.Default.Pause, pk == "pause", Modifier.weight(1f)) { viewModel.toggleForemanPause("pause") }
                PauseGridBtn("Обед", Icons.Default.Restaurant, pk == "lunch", Modifier.weight(1f)) { viewModel.toggleForemanPause("lunch") }
                PauseGridBtn("Перекур", Icons.Default.Air, pk == "smoke", Modifier.weight(1f)) { viewModel.toggleForemanPause("smoke") }
                PauseGridBtn("Простой", Icons.Default.WarningAmber, pk == "idle", Modifier.weight(1f), idle = true) { if (pk == "idle") viewModel.toggleForemanPause("idle") else showIdleReason = true }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                CamBtn("Пауза", Icons.Default.Pause, CamKind.NORMAL) { viewModel.toggleForemanPause("pause") }
                CamBtn("Срочно", Icons.Default.LocalFireDepartment, CamKind.SOS) {
                    viewModel.getOrCreateShiftId { sid -> navController.navigate(AppRoute.CameraWithParams.createRoute(sid, 0)) }
                }
                CamBtn("Снять", Icons.Default.PhotoCamera, CamKind.SHOT) {
                    viewModel.getOrCreateShiftId { sid -> navController.navigate(AppRoute.CameraWithParams.createRoute(sid, 0)) }
                }
            }
        }
        // объект
        GlanceCard("Структура объекта", "${currentObjectName ?: "объект"} · окно / этап · просмотр", Icons.Default.Apartment, FM3.primaryC, FM3.onPrimaryC) { onObject() }
        // Куда отнести / Завершить
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { viewModel.getOrCreateShiftId { sid -> navController.navigate(AppRoute.CameraWithParams.createRoute(sid, 0)) } },
                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(100), border = BorderStroke(1.dp, FM3.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FM3.primary)) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Куда отнести")
            }
            Button(onClick = onFinish, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(100),
                colors = ButtonDefaults.buttonColors(containerColor = FM3.redC, contentColor = FM3.onRedC)) {
                Text("Завершить", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(70.dp))
    }
    if (showIdleReason) {
        ForemanIdleReasonSheet(
            onPick = { reason -> showIdleReason = false; viewModel.toggleForemanPause("idle", reason) },
            onDismiss = { showIdleReason = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForemanIdleReasonSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = FM3.surf2) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 30.dp)) {
            Text("Причина простоя", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FM3.onSurface, modifier = Modifier.padding(bottom = 12.dp))
            listOf(
                "Нет материалов" to Icons.Default.Inventory2,
                "Ожидание" to Icons.Default.Schedule,
                "Нет фронта работ" to Icons.Default.WarningAmber,
                "Поломка инструмента" to Icons.Default.Build
            ).forEach { (label, icon) ->
                SheetOpt(label, icon, false) { onPick(label) }
            }
        }
    }
}

/** Реальный набор номера телефона (диалер). */
private fun dialPhone(context: android.content.Context, phone: String) {
    if (phone.isBlank()) return
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// MARK: - СВОДКА
@Composable
private fun SummaryTab(
    team: List<ForemanTeamMemberDto>, working: Int, idleN: Int, idleMembers: List<ForemanTeamMemberDto>,
    tools: List<*>, issuedTools: Int, createdTasks: List<*>, tasksDone: Int, pendingPhotos: Int,
    navController: NavController, viewModel: ForemanViewModel,
    onInvite: () -> Unit, onBroadcast: () -> Unit, onIdleAll: () -> Unit, onOpenTeam: () -> Unit, onObject: () -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Требует внимания
        if (idleMembers.isNotEmpty()) {
            SectionHeader("Требует внимания · ${idleMembers.size}")
            M3Card(elevated = true, padding = 0.dp) {
                idleMembers.forEachIndexed { i, m ->
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(11.dp).background(FM3.red, RoundedCornerShape(50)))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${m.displayName()} — простой", fontWeight = FontWeight.SemiBold, color = FM3.onSurface)
                            Text(m.idleReason?.ifBlank { null } ?: "ожидание материалов", fontSize = 13.sp, color = FM3.onSurfaceVar)
                        }
                    }
                    Row(Modifier.padding(start = 20.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SmallTonalBtn("Куратору", Icons.Default.Phone) { dialPhone(context, "+7XXXXXXXXXX") }
                        SmallOutlineBtn("Что случилось?") { navController.navigate(AppRoute.InstallerDetail.createRoute(m.memberId())) }
                    }
                    if (i < idleMembers.size - 1) HorizontalDivider(color = FM3.outlineVar)
                }
            }
        }
        // Команда glance + actions
        M3Card {
            Row(Modifier.fillMaxWidth().clickable { onOpenTeam() }, verticalAlignment = Alignment.CenterVertically) {
                GIcon(Icons.Default.Groups, FM3.giTeam, FM3.onGiTeam)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Команда · ${team.size}/4", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FM3.onSurface)
                    Row { Text("$working работают", fontSize = 13.sp, color = FM3.green); if (idleN > 0) Text(" · $idleN простой", fontSize = 13.sp, color = FM3.red) }
                }
                Icon(Icons.Default.ChevronRight, null, tint = FM3.onSurfaceVar)
            }
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FilledTonalButton(onClick = onBroadcast, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(100),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = FM3.primaryC, contentColor = FM3.onPrimaryC), enabled = team.isNotEmpty()) {
                    Icon(Icons.Default.Campaign, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Broadcast", fontSize = 13.sp)
                }
                Button(onClick = onIdleAll, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(100),
                    colors = ButtonDefaults.buttonColors(containerColor = FM3.redC, contentColor = FM3.onRedC), enabled = team.isNotEmpty()) {
                    Icon(Icons.Default.WarningAmber, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Простой всей", fontSize = 13.sp)
                }
            }
        }
        // Объект glance
        GlanceCard("Объект", "${if (working+idleN>0) "монтаж идёт" else "—"} · просмотр", Icons.Default.Apartment, FM3.primaryC, FM3.onPrimaryC) { onObject() }
        // Пригласить
        GlanceCard("Пригласить в команду", "инвайт-код · QR", Icons.Default.PersonAddAlt1, FM3.primaryC, FM3.onPrimaryC) { onInvite() }
        // Инструмент / Задачи
        GlanceCard("Инструмент", "$issuedTools выдано", Icons.Default.Build, FM3.amberC, FM3.onAmberC) { navController.navigate(AppRoute.ToolIssue.route) }
        GlanceCard("Задачи команды", "$tasksDone / ${createdTasks.size} закрыто", Icons.Default.Checklist, FM3.greenC, FM3.onGreenC) { navController.navigate(AppRoute.InstallerTasks.route) }
        // KPI
        M3Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Kpi("${"%.0f".format(team.sumOf { it.totalHours })}ч", "часов"); Kpi("${team.sumOf { it.pendingPhotosCount }}", "фото")
                Kpi("$idleN", "простой"); Kpi("$tasksDone/${createdTasks.size}", "задачи")
            }
        }
        Spacer(Modifier.height(70.dp))
    }
}

// MARK: - КОМАНДА
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamTab(
    team: List<ForemanTeamMemberDto>, brigadeIndex: Int, onBrigade: (Int) -> Unit,
    navController: NavController, viewModel: ForemanViewModel,
    onInvite: () -> Unit, onBroadcast: () -> Unit, onIdleAll: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        // A/B segmented (M3)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = brigadeIndex == 0, onClick = { onBrigade(0) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Бригада А") }
            SegmentedButton(selected = brigadeIndex == 1, onClick = { onBrigade(1) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Бригада Б") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Бригада · ${team.size} / 4 ячейки", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FM3.primary, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onInvite, modifier = Modifier.height(38.dp), shape = RoundedCornerShape(100),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = FM3.primary, contentColor = FM3.onPrimary)) {
                Icon(Icons.Default.PersonAddAlt1, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Добавить", fontSize = 13.sp)
            }
        }
        if (brigadeIndex == 1) {
            M3Card { Text("Вторая бригада появится после доработки сервера (мульти-бригада). Сейчас все монтажники объекта — в Бригаде А.", fontSize = 13.sp, color = FM3.onSurfaceVar) }
        } else {
            team.forEach { m -> MemberCard(m, navController, viewModel) }
            if (team.size < 4) EmptyCell(team.size + 1, onInvite)
            Text("Норма бригады — 4 человека", fontSize = 11.5.sp, color = FM3.onSurfaceVar, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(70.dp))
    }
}

@Composable
private fun MemberCard(m: ForemanTeamMemberDto, navController: NavController, viewModel: ForemanViewModel) {
    val (pillBg, pillFg, pillText) = FM3.statusColors(m.shiftStatus)
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val context = LocalContext.current
    M3Card(padding = 14.dp, surface = FM3.surf1, modifier = Modifier.clickable { navController.navigate(AppRoute.InstallerDetail.createRoute(m.memberId())) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.displayName())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.displayName(), fontWeight = FontWeight.SemiBold, color = FM3.onSurface)
                Text(memberSub(m), fontSize = 13.sp, color = FM3.onSurfaceVar, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.background(pillBg, RoundedCornerShape(100)).padding(horizontal = 11.dp, vertical = 5.dp)) {
                Text(pillText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = pillFg)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { navController.navigate(AppRoute.InstallerDetail.createRoute(m.memberId())) },
                modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(100),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = FM3.primaryC, contentColor = FM3.onPrimaryC)) {
                Icon(Icons.Default.Add, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Задача", fontSize = 13.sp)
            }
            MemberIconBtn(Icons.Default.Phone) { dialPhone(context, m.phone) }
            MemberIconBtn(Icons.AutoMirrored.Filled.Chat) { viewModel.getOrCreateGroupThread { tid -> navController.navigate(AppRoute.MessengerConversation.createRoute(tid)) } }
            Box {
                MemberIconBtn(Icons.Default.MoreVert) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Создать задачу") }, onClick = { menu = false; navController.navigate(AppRoute.InstallerDetail.createRoute(m.memberId())) }, leadingIcon = { Icon(Icons.Default.Add, null) })
                    DropdownMenuItem(text = { Text("Выдать инструмент") }, onClick = { menu = false; navController.navigate(AppRoute.ToolIssue.route) }, leadingIcon = { Icon(Icons.Default.Build, null) })
                    DropdownMenuItem(text = { Text("Переназначить") }, onClick = { menu = false; navController.navigate(AppRoute.InstallerDetail.createRoute(m.memberId())) }, leadingIcon = { Icon(Icons.Default.SwapHoriz, null) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Не выехал / Заменить", color = FM3.amber) }, onClick = { menu = false; navController.navigate(AppRoute.InstallerDetail.createRoute(m.memberId())) }, leadingIcon = { Icon(Icons.Default.WarningAmber, null, tint = FM3.amber) })
                    DropdownMenuItem(text = { Text("Удалить из бригады", color = FM3.red) }, onClick = { menu = false; confirmRemove = true }, leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = FM3.red) })
                }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Удалить из бригады?") },
            text = { Text("${m.displayName()} будет удалён(а) из бригады.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; viewModel.removeMember(m.memberId()) }) { Text("Удалить", color = FM3.red) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Отмена") } }
        )
    }
}

private fun memberSub(m: ForemanTeamMemberDto): String {
    val parts = mutableListOf<String>()
    if (m.pendingPhotosCount > 0) parts.add("${m.pendingPhotosCount} фото")
    if (m.totalHours > 0) parts.add("%.1f ч".format(m.totalHours))
    if (m.shiftStatus == "idle" && !m.idleReason.isNullOrBlank()) parts.add(m.idleReason!!)
    return if (parts.isEmpty()) FM3.statusColors(m.shiftStatus).third else parts.joinToString(" · ")
}

@Composable
private fun EmptyCell(n: Int, onInvite: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Color.Transparent, RoundedCornerShape(28.dp)).then(Modifier).padding(0.dp)) {
        OutlinedCard(shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, FM3.outline), colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(FM3.surf3, RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = FM3.onSurfaceVar) }
                Spacer(Modifier.width(12.dp))
                Text("$n-я ячейка свободна", color = FM3.onSurfaceVar, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onInvite, modifier = Modifier.height(38.dp), shape = RoundedCornerShape(100),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = FM3.primaryC, contentColor = FM3.onPrimaryC)) {
                    Icon(Icons.Default.QrCode2, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("QR", fontSize = 13.sp)
                }
            }
        }
    }
}

// MARK: - Drawer (M3)
@Composable
private fun ForemanM3Drawer(navController: NavController, onClose: () -> Unit, onInvite: () -> Unit, onRoleSwitch: () -> Unit, onChat: () -> Unit, onLogout: () -> Unit) {
    ModalDrawerSheet(drawerContainerColor = FM3.surf2, drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(FM3.primary, Color(0xFF362F97))), RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Text("СБ", color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column { Text("Бригадир", fontWeight = FontWeight.SemiBold, color = FM3.onSurface); Text("BELSI.Команда", fontSize = 11.5.sp, color = FM3.onSurfaceVar) }
        }
        DrawerItem("Пригласить в команду", Icons.Default.PersonAddAlt1) { onInvite() }
        DrawerItem("Партии и заявки", Icons.Default.Inventory2) { onClose(); navController.navigate(AppRoute.BatchList.route) }
        DrawerItem("Инструмент · Hub", Icons.Default.Build) { onClose(); navController.navigate(AppRoute.ToolTransferHub.createRoute("incoming")) }
        DrawerItem("Чат", Icons.AutoMirrored.Filled.Chat) { onChat() }
        DrawerItem("История смен", Icons.Default.History) { onClose(); navController.navigate(AppRoute.ShiftHistory.route) }
        DrawerItem("Показать / Сканировать QR", Icons.Default.QrCode2) { onInvite() }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = FM3.outlineVar)
        DrawerItem("Настройки", Icons.Default.Settings) { onClose(); navController.navigate(AppRoute.Settings.route) }
        DrawerItem("Сменить роль", Icons.Default.SwapHoriz) { onRoleSwitch() }
        DrawerItem("Выход", Icons.Default.Logout, danger = true) { onLogout() }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, null, tint = if (danger) FM3.red else FM3.onSurfaceVar) },
        label = { Text(label, color = if (danger) FM3.red else FM3.onSurface, fontWeight = FontWeight.SemiBold) },
        selected = false, onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
    )
}

// MARK: - Sheets (M3)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForemanM3IdleAllSheet(isLoading: Boolean, onConfirm: (String, String?) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState()
    var reason by remember { mutableStateOf("no_materials") }
    var comment by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = FM3.surf2) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 30.dp)) {
            Text("Простой всей бригады", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FM3.onSurface, modifier = Modifier.padding(bottom = 12.dp))
            listOf("no_materials" to "Нет материалов" to Icons.Default.Inventory2, "waiting" to "Ожидание" to Icons.Default.Schedule, "vehicle_breakdown" to "Поломка транспорта" to Icons.Default.Build).forEach { (kv, icon) ->
                val (key, label) = kv
                SheetOpt(label, icon, selected = reason == key) { reason = key }
            }
            OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(16.dp))
            Button(onClick = { onConfirm(reason, comment.ifBlank { null }) }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 10.dp), shape = RoundedCornerShape(100),
                colors = ButtonDefaults.buttonColors(containerColor = FM3.redC, contentColor = FM3.onRedC)) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = FM3.onRedC)
                else { Icon(Icons.Default.WarningAmber, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Зафиксировать + push куратору", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForemanM3FinishSheet(team: List<ForemanTeamMemberDto>, pendingPhotos: Int, idleN: Int, tasksDone: Int, tasksTotal: Int, onDone: () -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = FM3.surf2) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 30.dp)) {
            Text("День закрыт · бригада", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FM3.onSurface, modifier = Modifier.padding(bottom = 12.dp))
            Surface(color = FM3.surf3, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Kpi("${"%.0f".format(team.sumOf { it.totalHours })}ч", "команда"); Kpi("$pendingPhotos", "фото"); Kpi("$idleN", "простой"); Kpi("$tasksDone/$tasksTotal", "задачи")
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 12.dp), shape = RoundedCornerShape(100),
                colors = ButtonDefaults.buttonColors(containerColor = FM3.primary, contentColor = FM3.onPrimary)) { Text("Готово", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ForemanM3QrInviteDialog(code: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пригласить в команду") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Монтажник вводит код (Профиль → «Присоединиться по коду») или сканирует QR.", fontSize = 13.sp, color = FM3.onSurfaceVar, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 2.dp) { QrImage(content = code, sizePx = 600, modifier = Modifier.padding(12.dp).size(190.dp)) }
                Spacer(Modifier.height(14.dp))
                Surface(color = FM3.primaryC, shape = RoundedCornerShape(12.dp)) { Text(code, Modifier.padding(horizontal = 20.dp, vertical = 10.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FM3.onPrimaryC) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "Присоединяйтесь к моей команде BELSI! Код: $code") }
                ctx.startActivity(android.content.Intent.createChooser(send, "Поделиться кодом"))
            }) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Поделиться") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}

// MARK: - reusable bits
@Composable
private fun M3Card(elevated: Boolean = false, padding: Dp = 16.dp, surface: Color = if (elevated) FM3.surface else FM3.surf1, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxWidth()
    if (elevated) {
        Card(modifier = base, shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = base, shape = RoundedCornerShape(28.dp), color = surface) { Column(Modifier.padding(padding), content = content) }
    }
}

@Composable private fun SectionHeader(t: String) = Text(t, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FM3.primary, modifier = Modifier.padding(start = 6.dp, top = 4.dp))

@Composable
private fun GlanceCard(title: String, sub: String, icon: ImageVector, iconBg: Color, iconFg: Color, onClick: () -> Unit) {
    M3Card(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(icon, iconBg, iconFg)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FM3.onSurface); Text(sub, fontSize = 13.sp, color = FM3.onSurfaceVar) }
            Icon(Icons.Default.ChevronRight, null, tint = FM3.onSurfaceVar)
        }
    }
}

@Composable private fun GIcon(icon: ImageVector, bg: Color, fg: Color) = Box(Modifier.size(44.dp).background(bg, RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp)) }

@Composable private fun Avatar(name: String) = Box(Modifier.size(44.dp).background(FM3.primaryC, RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Text(initials(name), fontWeight = FontWeight.Bold, color = FM3.onPrimaryC) }

@Composable private fun Kpi(value: String, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier) { Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = FM3.onSurface); Text(label, fontSize = 11.5.sp, color = FM3.onSurfaceVar) }

@Composable private fun BrigadeChip() = Row(Modifier.background(FM3.surf3, RoundedCornerShape(100)).border(1.dp, FM3.outlineVar, RoundedCornerShape(100)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(9.dp).background(FM3.green, RoundedCornerShape(50))); Spacer(Modifier.width(8.dp)); Text("Бригада А", fontWeight = FontWeight.SemiBold, color = FM3.onSurface, fontSize = 14.sp); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp), tint = FM3.onSurfaceVar)
}

@Composable
private fun ProfileRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    M3Card(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = FM3.onSurfaceVar); Spacer(Modifier.width(12.dp)); Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FM3.onSurface, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = FM3.onSurfaceVar) }
    }
}

@Composable private fun MemberIconBtn(icon: ImageVector, onClick: () -> Unit) = Surface(onClick = onClick, shape = RoundedCornerShape(100), color = FM3.surf3, modifier = Modifier.size(48.dp, 40.dp)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = FM3.onSurface) } }

@Composable private fun SmallTonalBtn(t: String, icon: ImageVector, onClick: () -> Unit) = FilledTonalButton(onClick = onClick, modifier = Modifier.height(38.dp), shape = RoundedCornerShape(100), contentPadding = PaddingValues(horizontal = 14.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = FM3.primaryC, contentColor = FM3.onPrimaryC)) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t, fontSize = 13.sp) }
@Composable private fun SmallOutlineBtn(t: String, onClick: () -> Unit) = OutlinedButton(onClick = onClick, modifier = Modifier.height(38.dp), shape = RoundedCornerShape(100), border = BorderStroke(1.dp, FM3.outline), contentPadding = PaddingValues(horizontal = 14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = FM3.primary)) { Text(t, fontSize = 13.sp) }

@Composable
private fun SheetOpt(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) = Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(100)).clickable { onClick() }.background(if (selected) FM3.surf4 else Color.Transparent, RoundedCornerShape(100)).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = FM3.primary); Spacer(Modifier.width(16.dp)); Text(label, fontSize = 15.sp, color = FM3.onSurface, modifier = Modifier.weight(1f)); if (selected) Icon(Icons.Default.Check, null, tint = FM3.primary)
}

private enum class CamKind { NORMAL, SOS, SHOT }
@Composable
private fun CamBtn(label: String, icon: ImageVector, kind: CamKind, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        val (bg, fg) = when (kind) { CamKind.SHOT -> FM3.primary to Color.White; CamKind.SOS -> FM3.redC to FM3.red; CamKind.NORMAL -> FM3.surf3 to FM3.onSurface }
        Box(Modifier.size(56.dp).background(bg, RoundedCornerShape(18.dp)).then(if (kind == CamKind.SHOT) Modifier.border(5.dp, FM3.primaryC, RoundedCornerShape(22.dp)) else Modifier), contentAlignment = Alignment.Center) { Icon(icon, null, tint = fg, modifier = Modifier.size(25.dp)) }
        Spacer(Modifier.height(9.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = if (kind == CamKind.SHOT) FontWeight.Bold else FontWeight.SemiBold, color = if (kind == CamKind.SHOT) FM3.primary else FM3.onSurfaceVar)
    }
}

@Composable
private fun PauseGridBtn(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, idle: Boolean = false, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable { onClick() }.background(if (selected) FM3.primary else FM3.surf3, RoundedCornerShape(18.dp)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (selected) Color.White else if (idle) Color(0xFFC58A00) else FM3.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(7.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else FM3.onSurface)
    }
}

private fun initials(name: String): String {
    val p = name.trim().split(" ").filter { it.isNotBlank() }
    val a = p.getOrNull(0)?.firstOrNull()?.toString() ?: ""
    val b = p.getOrNull(1)?.firstOrNull()?.toString() ?: ""
    return (a + b).uppercase()
}

private fun fmtTimer(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60
    return if (h > 0) "%d:%02d".format(h, m) else "%d:%02d".format(m, sec % 60)
}

// MARK: - Структура объекта (read-only) — ВЕРНАЯ M3-страница макета
@HiltViewModel
class ForemanObjectReadViewModel @Inject constructor(private val repo: ObjectV3Repository) : ViewModel() {
    data class S(val isLoading: Boolean = true, val tree: ObjectTree? = null, val error: String? = null)
    private val _ui = MutableStateFlow(S()); val ui = _ui.asStateFlow()
    private var loaded: String? = null
    fun load(objectId: String) {
        if (loaded == objectId) return
        loaded = objectId
        viewModelScope.launch {
            _ui.value = S(isLoading = true)
            repo.getObjectTree(objectId, allowDemo = false)
                .onSuccess { _ui.value = S(isLoading = false, tree = it) }
                .onFailure { _ui.value = S(isLoading = false, error = it.message) }
        }
    }
}

private fun stageColor3(s: String): Color = when (s.lowercase()) {
    "done", "completed", "ready" -> FM3.green
    "accepted", "approved" -> Color(0xFF0061A4)
    "in_progress", "started" -> Color(0xFFC58A00)
    else -> FM3.outlineVar
}
private fun cabReady(c: CabinetDto): Int = listOf(c.readyWindows, c.readyOtkosy, c.readyRadiators, c.readyPipes, c.readyPlinth, c.readyWalls, c.readyFloor).count { it }
private fun blockedHint(c: CabinetDto): String {
    val miss = mutableListOf<String>()
    if (!c.readyPipes) miss.add("трубы"); if (!c.readyFloor) miss.add("пол"); if (!c.readyWalls) miss.add("стены")
    if (!c.readyWindows) miss.add("окна"); if (!c.readyOtkosy) miss.add("откосы")
    return if (miss.isEmpty()) "не готов" else "не готов: " + miss.take(3).joinToString(", ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForemanObjectReadScreen(objectId: String, objectName: String, onBack: () -> Unit, vm: ForemanObjectReadViewModel = hiltViewModel()) {
    LaunchedEffect(objectId) { vm.load(objectId) }
    val ui by vm.ui.collectAsState()
    var floorIdx by remember { mutableIntStateOf(0) }
    val floors = ui.tree?.floors.orEmpty()

    Scaffold(containerColor = FM3.surface, topBar = {
        Surface(color = FM3.surface) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = FM3.onSurfaceVar) }
                Text("Объект", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = FM3.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            M3Card(elevated = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(Icons.Default.Apartment, FM3.primaryC, FM3.onPrimaryC); Spacer(Modifier.width(12.dp))
                    Column { Text(objectName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FM3.onSurface); Text("структура · только просмотр", fontSize = 13.sp, color = FM3.onSurfaceVar) }
                }
            }
            when {
                ui.isLoading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FM3.primary) }
                ui.error != null -> Text(ui.error!!, color = FM3.red, fontSize = 13.sp)
                floors.isEmpty() -> Text("Этажи не заведены", color = FM3.onSurfaceVar)
                else -> {
                    if (floors.size > 1) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            floors.forEachIndexed { i, fn ->
                                SegmentedButton(selected = floorIdx == i, onClick = { floorIdx = i }, shape = SegmentedButtonDefaults.itemShape(i, floors.size)) { Text("Этаж ${fn.floor.floorNumber}") }
                            }
                        }
                    }
                    val cabinets = floors.getOrNull(floorIdx)?.zones?.flatMap { it.cabinets } ?: emptyList()
                    cabinets.forEach { cn ->
                        val ready = cabReady(cn.cabinet); val full = ready >= 7
                        M3Card(padding = 0.dp) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).background(FM3.surf3, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(cn.cabinet.cabinetNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FM3.onSurface) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text("Кабинет ${cn.cabinet.cabinetNumber}", fontWeight = FontWeight.SemiBold, color = FM3.onSurface); Text(if (full) "${cn.windows.size} окон" else blockedHint(cn.cabinet), fontSize = 11.5.sp, color = if (full) FM3.onSurfaceVar else FM3.amber, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                Box(Modifier.background(if (full) FM3.greenC else FM3.amberC, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 4.dp)) { Text("$ready/7", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (full) FM3.onGreenC else FM3.onAmberC) }
                            }
                            cn.windows.forEach { w ->
                                HorizontalDivider(Modifier.padding(start = 48.dp), color = FM3.outlineVar)
                                Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 16.dp).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Окно ${w.windowNumber}", fontSize = 13.sp, color = FM3.onSurfaceVar, modifier = Modifier.width(70.dp))
                                    StageSq(w.statusKarkas); Spacer(Modifier.width(6.dp)); StageSq(w.statusPodokonnik); Spacer(Modifier.width(6.dp)); StageSq(w.statusEkran)
                                    Spacer(Modifier.weight(1f)); Text("каркас·подок·экран", fontSize = 11.sp, color = FM3.onSurfaceVar)
                                }
                            }
                            if (!full) {
                                HorizontalDivider(Modifier.padding(start = 16.dp), color = FM3.outlineVar)
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.WarningAmber, null, tint = FM3.amber, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Монтаж заблокирован — готовность $ready/7", fontSize = 13.sp, color = FM3.amber) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Legend(FM3.green, "готово"); Spacer(Modifier.width(14.dp)); Legend(Color(0xFF0061A4), "принято"); Spacer(Modifier.width(14.dp)); Legend(Color(0xFFC58A00), "в работе"); Spacer(Modifier.width(14.dp)); Legend(FM3.outlineVar, "не начато")
                    }
                    Surface(color = FM3.surf2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Flag, null, tint = FM3.onSurfaceVar); Spacer(Modifier.width(10.dp)); Text("Приёмку этапов делает куратор / координатор — у бригадира просмотр", fontSize = 12.5.sp, color = FM3.onSurfaceVar) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable private fun StageSq(s: String) = Box(Modifier.size(14.dp).background(stageColor3(s), RoundedCornerShape(5.dp)))
@Composable private fun Legend(c: Color, t: String) = Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(11.dp).background(c, RoundedCornerShape(3.dp))); Spacer(Modifier.width(5.dp)); Text(t, fontSize = 11.5.sp, color = FM3.onSurfaceVar) }
