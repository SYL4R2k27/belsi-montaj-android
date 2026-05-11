package com.belsi.work.presentation.screens.driver

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.belsi.work.data.models.UserRole
import com.belsi.work.presentation.screens.coordinator.CoordCreateRequestScreen
import com.belsi.work.presentation.screens.logistician.LogisticianHomeScreen
import com.belsi.work.presentation.screens.logistician.LogistCreateRouteScreen
import com.belsi.work.presentation.screens.logistician.LogistDriverDetailScreen
import com.belsi.work.presentation.screens.logistician.LogistRequestDetailScreen
// FIX(2026-05-05): экраны производства (BELSI.Команда)
import com.belsi.work.presentation.screens.factory.WorkerMainScreen
import com.belsi.work.presentation.screens.factory.SeniorWorkerMainScreen
import com.belsi.work.presentation.screens.factory.ProductionChiefMainScreen
import com.belsi.work.presentation.screens.factory.SupplierMainScreen
import com.belsi.work.presentation.screens.factory.EngineerMainScreen
import com.belsi.work.presentation.screens.factory.FactoryIdleReasonsScreen
import com.belsi.work.presentation.screens.factory.FactoryFacilitySwitchScreen
import com.belsi.work.presentation.screens.auth.roleselect.RoleSelectScreenV2

/**
 * FIX(2026-05-04): Sandbox для просмотра Driver/Logistician/Coord UI с интерактивной
 * навигацией внутри. Стек локальный (mutableStateListOf) — не путается с глобальным NavGraph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleTestPlaygroundScreen(navController: NavController) {
    val backStack = remember { mutableStateListOf<PlaygroundDestination>(PlaygroundDestination.DriverHome) }
    val current: PlaygroundDestination = backStack.last()

    fun push(d: PlaygroundDestination) { backStack.add(d) }
    fun pop(): Boolean = if (backStack.size > 1) { backStack.removeAt(backStack.size - 1); true } else false

    var pendingSwitchRole by remember { mutableStateOf<UserRole?>(null) }

    val activeRole: UserRole = when (current) {
        is PlaygroundDestination.DriverHome,
        is PlaygroundDestination.DriverPointDetail,
        is PlaygroundDestination.DriverCamera -> UserRole.DRIVER
        is PlaygroundDestination.LogisticianHome,
        is PlaygroundDestination.LogistRequestDetail,
        is PlaygroundDestination.LogistDriverDetail,
        is PlaygroundDestination.LogistCreateRoute,
        is PlaygroundDestination.LogistRouteDetail -> UserRole.LOGISTICIAN
        is PlaygroundDestination.CoordHome,
        is PlaygroundDestination.CoordCreateRequest -> UserRole.COORDINATOR
        is PlaygroundDestination.HistoryOfObject -> UserRole.CURATOR
        // FIX(2026-05-05): производство
        is PlaygroundDestination.WorkerHome -> UserRole.WORKER
        is PlaygroundDestination.SeniorWorkerHome -> UserRole.SENIOR_WORKER
        is PlaygroundDestination.ProductionChiefHome -> UserRole.PRODUCTION_CHIEF
        is PlaygroundDestination.SupplierHome -> UserRole.SUPPLIER
        is PlaygroundDestination.EngineerHome -> UserRole.ENGINEER
        is PlaygroundDestination.FactoryIdleReasons -> UserRole.WORKER
        is PlaygroundDestination.FactoryFacilitySwitch -> UserRole.WORKER
        is PlaygroundDestination.RoleSelectV2 -> UserRole.INSTALLER
    }

    val title: String = when (current) {
        PlaygroundDestination.DriverHome -> "Главная"
        is PlaygroundDestination.DriverPointDetail -> "Точка маршрута"
        is PlaygroundDestination.DriverCamera -> "Камера"
        PlaygroundDestination.LogisticianHome -> "Дашборд"
        is PlaygroundDestination.LogistRequestDetail -> "Заявка"
        is PlaygroundDestination.LogistDriverDetail -> "Водитель"
        PlaygroundDestination.LogistCreateRoute -> "Новый маршрут"
        is PlaygroundDestination.LogistRouteDetail -> "Маршрут"
        PlaygroundDestination.CoordHome -> "Мои объекты"
        PlaygroundDestination.CoordCreateRequest -> "Новая заявка"
        is PlaygroundDestination.HistoryOfObject -> current.objectName
        // FIX(2026-05-05): производство
        PlaygroundDestination.WorkerHome -> "Смена"
        PlaygroundDestination.SeniorWorkerHome -> "Группа"
        PlaygroundDestination.ProductionChiefHome -> "Углич — фабрика"
        PlaygroundDestination.SupplierHome -> "Склад"
        PlaygroundDestination.EngineerHome -> "Чертежи"
        PlaygroundDestination.FactoryIdleReasons -> "Причина простоя"
        PlaygroundDestination.FactoryFacilitySwitch -> "Выбор фабрики"
        PlaygroundDestination.RoleSelectV2 -> "Регистрация"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(activeRole.emoji, style = MaterialTheme.typography.labelMedium)
                            Text(
                                activeRole.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!pop()) navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Text("⋮", style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.widthIn(min = 240.dp)) {
                            // Активная роль
                            Text(
                                "АКТИВНАЯ РОЛЬ",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            // FIX(2026-05-05): добавлены 5 производственных ролей + регистрация V2
                            listOf(
                                UserRole.DRIVER, UserRole.LOGISTICIAN, UserRole.COORDINATOR,
                                UserRole.WORKER, UserRole.SENIOR_WORKER, UserRole.PRODUCTION_CHIEF,
                                UserRole.SUPPLIER, UserRole.ENGINEER,
                            ).forEach { role ->
                                val isActive = role == activeRole
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Text(role.emoji, style = MaterialTheme.typography.titleMedium)
                                            Text(role.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                                            if (isActive) Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        }
                                    },
                                    onClick = {
                                        menuOpen = false
                                        if (!isActive) pendingSwitchRole = role
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            // Прочее
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.History, null)
                                        Text("История объекта")
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    push(PlaygroundDestination.HistoryOfObject("Коломенская набережная 16"))
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Add, null)
                                        Text("Создать заявку (Координатор)")
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    backStack.clear()
                                    backStack.add(PlaygroundDestination.CoordCreateRequest)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Crossfade(targetState = current, modifier = Modifier.padding(padding), label = "playground-stack") { dest ->
            when (dest) {
                PlaygroundDestination.DriverHome -> DriverHomeScreen(
                    onPointClick = { id -> push(PlaygroundDestination.DriverPointDetail(id)) },
                )
                is PlaygroundDestination.DriverPointDetail -> DriverPointDetailScreen(
                    pointId = dest.pointId,
                    onCameraClick = { push(PlaygroundDestination.DriverCamera(dest.pointId)) },
                    onMarkDelivered = { pop() }
                )
                is PlaygroundDestination.DriverCamera -> DriverCameraStub(
                    pointId = dest.pointId,
                    onShoot = { pop() }
                )
                PlaygroundDestination.LogisticianHome -> LogisticianHomeScreen(
                    onCreateRoute = { push(PlaygroundDestination.LogistCreateRoute) },
                    onRequestClick = { id -> push(PlaygroundDestination.LogistRequestDetail(id)) },
                    onRouteClick = { id -> push(PlaygroundDestination.LogistRouteDetail(id)) }
                )
                is PlaygroundDestination.LogistRequestDetail -> LogistRequestDetailScreen(
                    requestId = dest.requestId,
                    onAddToRoute = { pop() },
                    onCreateNewRoute = { backStack[backStack.lastIndex] = PlaygroundDestination.LogistCreateRoute }
                )
                is PlaygroundDestination.LogistDriverDetail -> LogistDriverDetailScreen(
                    driverId = dest.driverId,
                    onCreateRoute = { backStack[backStack.lastIndex] = PlaygroundDestination.LogistCreateRoute }
                )
                PlaygroundDestination.LogistCreateRoute -> LogistCreateRouteScreen(
                    onAssign = { pop() }
                )
                is PlaygroundDestination.LogistRouteDetail -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Маршрут — детали (TODO)")
                }
                PlaygroundDestination.CoordHome -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Координатор — главная (TODO)")
                }
                PlaygroundDestination.CoordCreateRequest -> CoordCreateRequestScreen(onSubmit = { pop() })
                is PlaygroundDestination.HistoryOfObject -> HistoryOfObjectScreen(objectName = dest.objectName)
                // FIX(2026-05-05): производство — реальные экраны вместо заглушек
                PlaygroundDestination.WorkerHome -> WorkerMainScreen(navController)
                PlaygroundDestination.SeniorWorkerHome -> SeniorWorkerMainScreen(navController)
                PlaygroundDestination.ProductionChiefHome -> ProductionChiefMainScreen(navController)
                PlaygroundDestination.SupplierHome -> SupplierMainScreen(navController)
                PlaygroundDestination.EngineerHome -> EngineerMainScreen(navController)
                PlaygroundDestination.FactoryIdleReasons -> FactoryIdleReasonsScreen(navController)
                PlaygroundDestination.FactoryFacilitySwitch -> FactoryFacilitySwitchScreen(navController)
                PlaygroundDestination.RoleSelectV2 -> RoleSelectScreenV2(
                    navController = navController,
                    onContinue = { pop() }
                )
            }
        }
    }

    // Диалог переключения роли (как из brandbook)
    pendingSwitchRole?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSwitchRole = null },
            title = { Text("Переключиться на ${target.title}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeRole == UserRole.DRIVER) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("У вас активный маршрут", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("Маршрут #234 · 1 из 4 точек выполнено", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("Маршрут продолжит идти в фоне.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Переключаемся в режим ${target.title}.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newDest = when (target) {
                        UserRole.DRIVER -> PlaygroundDestination.DriverHome
                        UserRole.LOGISTICIAN -> PlaygroundDestination.LogisticianHome
                        UserRole.COORDINATOR -> PlaygroundDestination.CoordHome
                        // FIX(2026-05-05): производство
                        UserRole.WORKER -> PlaygroundDestination.WorkerHome
                        UserRole.SENIOR_WORKER -> PlaygroundDestination.SeniorWorkerHome
                        UserRole.PRODUCTION_CHIEF -> PlaygroundDestination.ProductionChiefHome
                        UserRole.SUPPLIER -> PlaygroundDestination.SupplierHome
                        UserRole.ENGINEER -> PlaygroundDestination.EngineerHome
                        else -> PlaygroundDestination.DriverHome
                    }
                    backStack.clear()
                    backStack.add(newDest)
                    pendingSwitchRole = null
                }) { Text("Переключиться") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitchRole = null }) { Text("Отмена") }
            }
        )
    }
}
