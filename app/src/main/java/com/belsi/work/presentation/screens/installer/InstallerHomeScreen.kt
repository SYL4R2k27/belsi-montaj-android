package com.belsi.work.presentation.screens.installer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import com.belsi.work.presentation.components.CoachmarkOverlay
import com.belsi.work.presentation.screens.wallet.DayMetersSheet
import com.belsi.work.presentation.components.CoachmarkStep
import com.belsi.work.presentation.components.coachmarkTarget
import com.belsi.work.presentation.components.rememberCoachmark
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.screens.shift.PhotoSlot
import com.belsi.work.presentation.screens.shift.PhotoSlotStatus
import com.belsi.work.presentation.screens.shift.ShiftUiState
import com.belsi.work.presentation.screens.shift.ShiftViewModel
import com.belsi.work.presentation.theme.belsiColors

/**
 * Главный экран монтажника (BELSI 2.1.0, по моку 1.1).
 *
 * Данные — НАСТОЯЩИЕ:
 *  - смена/таймер/пауза/простой/фото/объект/действия → [ShiftViewModel] (прод-эндпоинты);
 *  - структура объекта (прогресс по окнам) → [InstallerHomeViewModel] (ObjectV3Repository, реальные;
 *    секция рендерится только при наличии данных — без фейка, до импорта замеров скрыта).
 *
 * Кнопки — аккуратные Material 3 (FilledTonal / Outlined), без «боксового» вида мока.
 */
private val installerCoachSteps = listOf(
    CoachmarkStep("shift", "Ваша смена", "Таймер смены и чистое время. Пауза, обед, перекур и простой считаются отдельно."),
    CoachmarkStep("cabinet", "Сейчас работаю", "Отметьте кабинет — фото будут привязываться к нему автоматически."),
    CoachmarkStep("breaks", "Перерывы и простой", "Пауза / Обед / Перекур / Простой — всё фиксируется на сервере, видит бригадир и куратор."),
    CoachmarkStep("camera", "Камера", "Снимайте фото по часам с привязкой к окну и этапу монтажа."),
    CoachmarkStep("curator", "Связь с куратором", "Нажмите «Куратор» — позвонить куратору или открыть чат."),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerHomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ShiftViewModel = hiltViewModel(),
    v3ViewModel: InstallerHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val photoSlots by viewModel.photoSlots.collectAsState()
    val pendingPhotoCount by viewModel.pendingPhotoCount.collectAsState()
    val idleReasons by viewModel.idleReasons.collectAsState()
    val availableObjects by viewModel.availableObjects.collectAsState()
    val selectedObjectId by viewModel.selectedObjectId.collectAsState()
    val currentObjectName by viewModel.currentObjectName.collectAsState()
    val tree by v3ViewModel.tree.collectAsState()
    val cabinetOptions by v3ViewModel.cabinets.collectAsState()
    val currentCabinet by v3ViewModel.currentCabinet.collectAsState()
    var showCabinetPicker by remember { mutableStateOf(false) }

    LaunchedEffect(selectedObjectId) { v3ViewModel.loadObject(selectedObjectId) }
    LaunchedEffect(Unit) {
        v3ViewModel.logoutEvent.collect {
            navController.navigate(AppRoute.Login.route) { popUpTo(0) { inclusive = true } }
        }
    }

    val active = uiState as? ShiftUiState.Active
    val progress = tree.toProgress()
    val coach = rememberCoachmark("installer", installerCoachSteps)
    // Ф3: плашка «За сегодня X п.м.» при завершении смены (опц. самоотчёт монтажника →
    // куратор подтверждает и начисляет). Screen-scope: переживает закрытие смены (active→null).
    var showDayMeters by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (active != null) {
                // P1-3: подтверждение завершения смены (защита от случайного/двойного тапа).
                var showEndConfirm by remember { mutableStateOf(false) }
                if (showEndConfirm) {
                    AlertDialog(
                        onDismissRequest = { showEndConfirm = false },
                        title = { Text("Завершить смену?") },
                        text = { Text("Смена будет закрыта. Убедитесь, что все фото за час загружены.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showEndConfirm = false
                                viewModel.endShift()
                                showDayMeters = true
                            }) {
                                Text("Завершить", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEndConfirm = false }) { Text("Отмена") }
                        },
                    )
                }
                // Нижняя панель: «Завершить смену» (красная) и «Куратор» одинакового размера,
                // по центру между ними — круглая камера, чуть выступающая вверх как основное действие.
                // FIX(2026-06-02): navigationBarsPadding() ПОСЛЕ background() — surface заливает фон
                // за системной 3-кнопочной навигацией Android (edge-to-edge включён в MainActivity),
                // а Row кнопок + FAB-камера поднимаются НАД ней (раньше панель перекрывала ⬜◯◁).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { showEndConfirm = true },
                            modifier = Modifier.weight(1f).height(52.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text("Завершить", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }

                        Spacer(Modifier.width(64.dp)) // место под центральную камеру

                        Box(modifier = Modifier.weight(1f).coachmarkTarget(coach, "curator")) {
                            var curatorMenu by remember { mutableStateOf(false) }
                            val ctx = LocalContext.current
                            FilledTonalButton(
                                onClick = { curatorMenu = true },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Куратор", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
                            }
                            // Раскрывается вверх (кнопка у нижнего края → Material сам флипает меню).
                            DropdownMenu(expanded = curatorMenu, onDismissRequest = { curatorMenu = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Позвонить куратору", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Сибилев А. А.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Call, contentDescription = null) },
                                    onClick = {
                                        curatorMenu = false
                                        runCatching {
                                            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+7XXXXXXXXXX")))
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Чат с куратором") },
                                    leadingIcon = { Icon(Icons.Filled.Chat, contentDescription = null) },
                                    onClick = {
                                        curatorMenu = false
                                        navController.navigate(AppRoute.Chat.route)
                                    },
                                )
                            }
                        }
                    }
                    FloatingActionButton(
                        onClick = { navController.navigate("camera/${active.shiftId}/0") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-22).dp)
                            .size(64.dp)
                            .coachmarkTarget(coach, "camera"),
                    ) { Icon(Icons.Filled.PhotoCamera, contentDescription = "Сделать фото", modifier = Modifier.size(28.dp)) }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            TopRow(
                roleLabel = "Монтажник",
                pendingPhotoCount = pendingPhotoCount,
                navController = navController,
                onLogout = { v3ViewModel.logout() },
            )

            ObjectSelector(
                name = currentObjectName ?: availableObjects.firstOrNull { it.id == selectedObjectId }?.name,
                objects = availableObjects,
                selectedId = selectedObjectId,
                onSelect = { viewModel.changeObject(it) },
            )

            when (uiState) {
                is ShiftUiState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is ShiftUiState.NoShift -> NoShiftCard(onStart = { viewModel.startShift() })

                is ShiftUiState.Active -> {
                    Box(Modifier.fillMaxWidth().coachmarkTarget(coach, "shift")) { ShiftHero(active!!) }

                    // Когда идёт перерыв/простой — баннер ВВЕРХУ, чтобы «Продолжить»
                    // был сразу виден без прокрутки вниз.
                    if (active!!.isPaused || active.isIdle) {
                        ActiveBreakBanner(
                            active = active,
                            onResume = { if (active.isIdle) viewModel.resumeFromIdle() else viewModel.resumeShift() },
                        )
                    }

                    Box(Modifier.fillMaxWidth().coachmarkTarget(coach, "cabinet")) {
                        CurrentCabinetCard(currentCabinet = currentCabinet, onClick = { showCabinetPicker = true })
                    }

                    if (progress.hasData) {
                        ObjectProgressCard(progress)
                    }

                    PhotosTodayRow(
                        slots = photoSlots,
                        onSlotClick = { slot ->
                            navController.navigate("camera/${active.shiftId}/${slot.index}")
                        },
                    )

                    // Кнопки старта перерыва — только когда смена идёт без перерыва.
                    if (!active.isPaused && !active.isIdle) {
                        Box(Modifier.fillMaxWidth().coachmarkTarget(coach, "breaks")) {
                            ShiftBreakControls(
                                idleReasons = idleReasons,
                                onPause = { reason -> viewModel.pauseShift(reason) },
                                onIdle = { reason -> viewModel.startIdle(reason) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    CoachmarkOverlay(coach)
    if (showDayMeters) {
        DayMetersSheet(onDismiss = { showDayMeters = false })
    }
    }

    if (showCabinetPicker) {
        CabinetPickerSheet(
            options = cabinetOptions,
            onPick = { opt -> v3ViewModel.setCurrentCabinet(opt.id, opt.label); showCabinetPicker = false },
            onManual = { num -> v3ViewModel.setCurrentCabinet(null, "Каб $num"); showCabinetPicker = false },
            onDismiss = { showCabinetPicker = false },
        )
    }
}

// ───────────────────────── секции ─────────────────────────

@Composable
private fun TopRow(
    roleLabel: String,
    pendingPhotoCount: Int,
    navController: NavController,
    onLogout: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var showRoleSheet by remember { mutableStateOf(false) }
    val isMultiRole = com.belsi.work.presentation.components.rememberIsMultiRole()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Иконка приложения (BS) вместо буквы «B».
        com.belsi.work.presentation.screens.auth.login.BelsiMark(size = 28.dp)

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(enabled = isMultiRole) { showRoleSheet = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Engineering, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
                Text(
                    roleLabel + if (isMultiRole) "  ▾" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (pendingPhotoCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.belsiColors.warningContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Filled.HourglassEmpty, contentDescription = null, tint = MaterialTheme.belsiColors.onWarningContainer, modifier = Modifier.size(12.dp))
                    Text("$pendingPhotoCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.belsiColors.onWarningContainer, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Меню", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Задачи") }, onClick = { menu = false; navController.navigate(AppRoute.InstallerTasks.route) })
                DropdownMenuItem(text = { Text("Мои инструменты") }, onClick = { menu = false; navController.navigate(AppRoute.ToolsList.route) })
                DropdownMenuItem(text = { Text("История смен") }, onClick = { menu = false; navController.navigate(AppRoute.ShiftHistory.route) })
                DropdownMenuItem(text = { Text("Профиль") }, onClick = { menu = false; navController.navigate(AppRoute.Profile.route) })
                DropdownMenuItem(text = { Text("Настройки") }, onClick = { menu = false; navController.navigate(AppRoute.Settings.route) })
                if (isMultiRole) {
                    DropdownMenuItem(text = { Text("Сменить роль") }, onClick = { menu = false; showRoleSheet = true })
                }
                DropdownMenuItem(text = { Text("Выйти") }, onClick = { menu = false; onLogout() })
            }
        }
    }

    com.belsi.work.presentation.components.BrandRoleSwitchSheet(showRoleSheet, navController) { showRoleSheet = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObjectSelector(
    name: String?,
    objects: List<com.belsi.work.data.remote.dto.objects.SiteObjectDto>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(name ?: "Объект не выбран", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (name != null) "Нажмите, чтобы сменить объект" else "Выберите объект", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            objects.forEach { obj ->
                DropdownMenuItem(
                    text = { Text(obj.name) },
                    trailingIcon = if (obj.id == selectedId) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = { expanded = false; if (obj.id != selectedId) onSelect(obj.id) },
                )
            }
        }
    }
}

@Composable
private fun ShiftHero(active: ShiftUiState.Active) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(MaterialTheme.belsiColors.brandGradientTop, MaterialTheme.belsiColors.brandGradientBottom)))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("АКТИВНАЯ СМЕНА", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            val (statusIcon, statusText) = when {
                active.isIdle -> Icons.Filled.Warning to "простой"
                active.pauseReason == "break:lunch" -> Icons.Filled.Restaurant to "обед"
                active.pauseReason == "break:smoke" -> Icons.Filled.SmokingRooms to "перекур"
                active.isPaused -> Icons.Filled.Pause to "пауза"
                else -> Icons.Filled.PlayArrow to "идёт"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(statusIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(statusText, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            text = active.formattedTime,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
        )
        Text("чистое ${fmtHm(active.netWorkTime)}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeroStat("пауза", fmtHm(active.totalPauseSeconds), Icons.Filled.Pause, Modifier.weight(1f))
            HeroStat("простой", fmtHm(active.totalIdleSeconds), Icons.Filled.Warning, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(11.dp))
            Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Заголовок секции: иконка + капс-текст (вместо эмодзи). */
@Composable
private fun SectionLabel(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ObjectProgressCard(p: ObjectProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.belsiColors.successContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(Icons.Filled.Apartment, "ПРОГРЕСС ОБЪЕКТА", MaterialTheme.belsiColors.onSuccessContainer)
            Text(
                "${(p.fraction * 100).toInt()}% · ${p.doneCabinets}/${p.totalCabinets} кабинетов готово",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.belsiColors.onSuccessContainer,
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.5f)),
            ) {
                Box(Modifier.fillMaxWidth(p.fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.belsiColors.success))
            }
        }
    }
}

@Composable
private fun PhotosTodayRow(slots: List<PhotoSlot>, onSlotClick: (PhotoSlot) -> Unit) {
    if (slots.isEmpty()) return
    val uploaded = slots.count { it.status == PhotoSlotStatus.UPLOADED }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(Icons.Filled.PhotoCamera, "СЕГОДНЯ · $uploaded/${slots.size}", MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            slots.forEach { slot -> PhotoSlotChip(slot) { onSlotClick(slot) } }
        }
    }
}

@Composable
private fun PhotoSlotChip(slot: PhotoSlot, onClick: () -> Unit) {
    val belsi = MaterialTheme.belsiColors
    val (bg, markIcon) = when (slot.status) {
        PhotoSlotStatus.UPLOADED -> belsi.success to Icons.Filled.Check
        PhotoSlotStatus.PENDING -> belsi.warning to Icons.Filled.HourglassEmpty
        PhotoSlotStatus.REJECTED -> MaterialTheme.colorScheme.error to Icons.Filled.Close
        PhotoSlotStatus.LOCKED -> MaterialTheme.colorScheme.surfaceVariant to Icons.Filled.Lock
        PhotoSlotStatus.EMPTY -> MaterialTheme.colorScheme.surfaceVariant to Icons.Filled.Add
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bg.copy(alpha = if (slot.status == PhotoSlotStatus.EMPTY || slot.status == PhotoSlotStatus.LOCKED) 0.4f else 1f))
                .clickable(enabled = slot.status != PhotoSlotStatus.LOCKED, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(markIcon, contentDescription = null, tint = if (slot.status == PhotoSlotStatus.EMPTY || slot.status == PhotoSlotStatus.LOCKED) MaterialTheme.colorScheme.onSurfaceVariant else Color.White, modifier = Modifier.size(24.dp))
        }
        Text(slot.timeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Управление перерывами монтажника. Когда смена идёт — 4 действия
 * (Пауза / Обед / Перекур / Простой с выбором причины). Когда активен
 * перерыв/простой — заметный баннер с живым таймером MM:SS и кнопкой «Продолжить».
 */
/**
 * Баннер активного перерыва/простоя. Рендерится ВВЕРХУ экрана (сразу под сменой),
 * чтобы «Продолжить работу» был виден без прокрутки. Таймер MM:SS не переносится
 * (раньше при длинной причине простоя цифры сыпались вертикально).
 */
@Composable
private fun ActiveBreakBanner(active: ShiftUiState.Active, onResume: () -> Unit) {
    val label: String
    val seconds: Long
    val reason: String?
    when {
        active.isIdle -> {
            label = "Простой"; seconds = active.idleSeconds
            reason = active.idleReason?.takeIf { it.isNotBlank() && it != "Простой" }
        }
        active.pauseReason == "break:lunch" -> { label = "Обед"; seconds = active.pauseSeconds; reason = null }
        active.pauseReason == "break:smoke" -> { label = "Перекур"; seconds = active.pauseSeconds; reason = null }
        else -> { label = "Пауза"; seconds = active.pauseSeconds; reason = null }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "⏸ $label",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    fmtMmSs(seconds),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (reason != null) {
                Text(
                    reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                "● фиксируется на сервере · видит бригадир и куратор",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Продолжить работу", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Кнопки старта перерыва/простоя — показываются только когда смена идёт без перерыва. */
@Composable
private fun ShiftBreakControls(
    idleReasons: List<String>,
    onPause: (String?) -> Unit,
    onIdle: (String) -> Unit,
) {
    var showReasonDialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BreakButton("Пауза", Icons.Filled.Pause, Modifier.weight(1f)) { onPause(null) }
            BreakButton("Обед", Icons.Filled.Restaurant, Modifier.weight(1f)) { onPause("break:lunch") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BreakButton("Перекур", Icons.Filled.SmokingRooms, Modifier.weight(1f)) { onPause("break:smoke") }
            BreakButton("Простой", Icons.Filled.Warning, Modifier.weight(1f)) { showReasonDialog = true }
        }
    }
    if (showReasonDialog) {
        IdleReasonDialog(
            reasons = idleReasons,
            onDismiss = { showReasonDialog = false },
            onPick = { onIdle(it); showReasonDialog = false },
        )
    }
}

@Composable
private fun BreakButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

/** Диалог выбора причины простоя — причины из каталога бэкенда + «Другая причина». */
@Composable
private fun IdleReasonDialog(
    reasons: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val fallback = listOf("Нет материалов", "Ожидание задачи", "Нет инструмента", "Помеха на объекте")
    val list = if (reasons.isNotEmpty()) reasons else fallback
    val other = "Другая причина"
    var selected by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf("") }
    val finalReason = if (selected == other) custom.trim() else selected.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Причина простоя") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                (list + other).forEach { reason ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = reason }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == reason, onClick = { selected = reason })
                        Spacer(Modifier.width(4.dp))
                        Text(reason)
                    }
                }
                if (selected == other) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = { Text("Укажите причину") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = finalReason.isNotBlank(), onClick = { onPick(finalReason) }) {
                Text("Начать простой")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun fmtMmSs(s: Long): String {
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
}

@Composable
private fun NoShiftCard(onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Смена не начата", style = MaterialTheme.typography.titleMedium)
            Text("Начните смену, чтобы фиксировать работу и фото", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Начать смену")
            }
        }
    }
}

@Composable
private fun CurrentCabinetCard(currentCabinet: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel(Icons.Filled.Construction, "СЕЙЧАС РАБОТАЮ", MaterialTheme.colorScheme.onSurfaceVariant)
            if (currentCabinet != null) {
                Text(currentCabinet, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Тап — сменить кабинет", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Выбери кабинет →", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CabinetPickerSheet(
    options: List<CabinetOption>,
    onPick: (CabinetOption) -> Unit,
    onManual: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) options
    else options.filter { it.cabinetNumber.contains(query, true) || it.label.contains(query, true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Куда отнести фото?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Выбери кабинет из списка или впиши номер", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск / номер кабинета") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isNotBlank()) {
                FilledTonalButton(onClick = { onManual(query.trim()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Использовать «${query.trim()}» (вручную)")
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.id }) { opt ->
                    val markColor = when (opt.status) {
                        "in_progress" -> MaterialTheme.belsiColors.warning
                        "done" -> MaterialTheme.belsiColors.success
                        "problem" -> MaterialTheme.colorScheme.error
                        else -> null
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(opt) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(opt.label, style = MaterialTheme.typography.bodyMedium)
                            if (markColor != null) Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = markColor, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun fmtHm(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}ч ${m}м" else "${m}м"
}
