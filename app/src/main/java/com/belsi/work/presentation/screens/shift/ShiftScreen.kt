package com.belsi.work.presentation.screens.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.components.ShiftTimeline
import com.belsi.work.data.workers.PhotoReminderWorker
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors
import com.belsi.work.presentation.utils.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.style.TextOverflow
import com.belsi.work.data.remote.dto.objects.SiteObjectDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ShiftViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val error by viewModel.error.collectAsState()
    val photoSlots by viewModel.photoSlots.collectAsState()
    val pendingPhotoCount by viewModel.pendingPhotoCount.collectAsState()
    val availableObjects by viewModel.availableObjects.collectAsState()
    val selectedObjectId by viewModel.selectedObjectId.collectAsState()
    val currentObjectName by viewModel.currentObjectName.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    var showEndShiftDialog by remember { mutableStateOf(false) }
    var showIdleReasonDialog by remember { mutableStateOf(false) }
    var showFirstPhotoDialog by remember { mutableStateOf(false) }
    var pendingShiftStart by rememberSaveable { mutableStateOf(false) }

    // Обновить фотографии при возврате с камеры
    // Если pending shift start - смена уже создана в CameraViewModel, нужно обновить состояние
    DisposableEffect(navController) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        val photoLiveData = savedStateHandle?.getLiveData<Boolean>("photo_uploaded")
        val cancelLiveData = savedStateHandle?.getLiveData<Boolean>("photo_cancelled")
        val shiftCreatedLiveData = savedStateHandle?.getLiveData<String>("shift_created_id")

        val photoObserver = androidx.lifecycle.Observer<Boolean> { uploaded ->
            if (uploaded == true) {
                if (pendingShiftStart) {
                    PhotoReminderWorker.schedule(context)
                    pendingShiftStart = false
                    viewModel.checkActiveShiftPublic()
                } else {
                    viewModel.refreshPhotos()
                }
                savedStateHandle?.remove<Boolean>("photo_uploaded")
            }
        }
        val cancelObserver = androidx.lifecycle.Observer<Boolean> { cancelled ->
            if (cancelled == true) {
                pendingShiftStart = false
                savedStateHandle?.remove<Boolean>("photo_cancelled")
            }
        }

        // Гарантированный автостарт: если CameraViewModel создал смену,
        // переводим UI в активное состояние даже если pendingShiftStart потерялся
        val shiftCreatedObserver = androidx.lifecycle.Observer<String> { shiftId ->
            if (!shiftId.isNullOrBlank()) {
                android.util.Log.d("ShiftScreen", "Shift created via camera, activating: $shiftId")
                PhotoReminderWorker.schedule(context)
                pendingShiftStart = false
                viewModel.checkActiveShiftPublic()
                savedStateHandle?.remove<String>("shift_created_id")
            }
        }

        photoLiveData?.observeForever(photoObserver)
        cancelLiveData?.observeForever(cancelObserver)
        shiftCreatedLiveData?.observeForever(shiftCreatedObserver)

        onDispose {
            photoLiveData?.removeObserver(photoObserver)
            cancelLiveData?.removeObserver(cancelObserver)
            shiftCreatedLiveData?.removeObserver(shiftCreatedObserver)
        }
    }

    // FIX(2026-04-30): при возврате в foreground обновляем актуальное состояние смены
    // с сервера. Иначе локальный таймер продолжает тикать, даже если смену
    // закрыл администратор / shift_closer / другое устройство (баг Хрулёва, Гусева).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.clearError()
                // quiet=true — не мигаем Loading, иначе на каждом возврате экран дёргается
                viewModel.checkActiveShiftPublic(quiet = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // FIX(2026-05-01): периодическая синхронизация каждые 60 сек, пока экран открыт.
    // Покрывает кейс: пользователь сидит в приложении, не сворачивает, а смену
    // тем временем закрыл shift_closer / куратор / другое устройство.
    // Без этого таймер «убегает» вперёд от серверной правды.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            viewModel.checkActiveShiftPublic(quiet = true)
        }
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

    Scaffold(
        topBar = {
            if (uiState is ShiftUiState.Active) {
                TopAppBar(
                    title = { Text("Смена") },
                    actions = {
                        // BELSI 2.1.0: OfflineChip — статус связи + очередь отправки (Flow 6, master-spec §11).
                        com.belsi.work.presentation.components.OfflineChip(navController = navController)
                        IconButton(onClick = { showEndShiftDialog = true }) {
                            Icon(Icons.Default.Stop, "Завершить смену")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        // FIX(2026-05-01): pull-to-refresh — свайп вниз форсирует re-sync
        // с сервера. Полезно когда таймер показывает некорректное время.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.forceRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ShiftUiState.NoShift -> {
                    NoShiftView(
                        availableObjects = availableObjects,
                        selectedObjectId = selectedObjectId,
                        currentObjectName = currentObjectName,
                        onSelectObject = viewModel::selectObject,
                        onStartClick = {
                            // Показываем диалог о необходимости первого фото
                            showFirstPhotoDialog = true
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is ShiftUiState.Loading -> {
                    com.belsi.work.presentation.components.ShiftSkeleton(
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                is ShiftUiState.Active -> {
                    ActiveShiftView(
                        availableObjects = availableObjects,
                        selectedObjectId = selectedObjectId,
                        currentObjectName = currentObjectName,
                        onChangeObject = viewModel::changeObject,
                        shiftId = state.shiftId,
                        elapsedTime = state.formattedTime,
                        netWorkTime = state.formattedNetWorkTime,
                        pauseTime = state.formattedPauseTime,
                        totalPauseTime = state.formattedTotalPauseTime,
                        isPaused = state.isPaused,
                        idleTime = state.formattedIdleTime,
                        totalIdleTime = state.formattedTotalIdleTime,
                        isIdle = state.isIdle,
                        idleReason = state.idleReason,
                        photoSlots = photoSlots,
                        pendingPhotoCount = pendingPhotoCount,
                        // Timeline data
                        startTimeMillis = state.startTime,
                        elapsedSeconds = state.elapsedSeconds,
                        pauseSeconds = state.totalPauseSeconds + state.pauseSeconds,
                        idleSeconds = state.totalIdleSeconds + state.idleSeconds,
                        onPhotoSlotClick = { slot ->
                            // Если фото загружено и есть URL - открываем просмотр
                            if (slot.status == PhotoSlotStatus.UPLOADED && slot.photoUrl != null) {
                                val encodedUrl = java.net.URLEncoder.encode(slot.photoUrl, "UTF-8")
                                navController.navigate("photo_detail/$encodedUrl")
                            } else {
                                // Иначе открываем камеру для загрузки
                                navController.navigate("camera/${state.shiftId}/${slot.index}")
                            }
                        },
                        onPauseClick = {
                            if (state.isPaused) {
                                viewModel.resumeShift()
                            } else {
                                viewModel.pauseShift()
                            }
                        },
                        onIdleClick = {
                            if (state.isIdle) {
                                viewModel.resumeFromIdle()
                            } else {
                                showIdleReasonDialog = true
                            }
                        },
                        onTakePhotoClick = {
                            navController.navigate("camera/${state.shiftId}/0")
                        },
                        onEndShiftClick = {
                            showEndShiftDialog = true
                        },
                        onSupportChatClick = {
                            navController.navigate(AppRoute.Chat.route)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Диалог завершения смены
    // Диалог выбора причины простоя — причины из бэкенда (build9)
    val apiIdleReasons by viewModel.idleReasons.collectAsState()
    if (showIdleReasonDialog) {
        IdleReasonDialog(
            onDismiss = { showIdleReasonDialog = false },
            onReasonSelected = { reason ->
                viewModel.startIdle(reason)
                showIdleReasonDialog = false
            },
            apiReasons = apiIdleReasons,
        )
    }

    if (showEndShiftDialog) {
        AlertDialog(
            onDismissRequest = { showEndShiftDialog = false },
            title = { Text("Завершить смену?") },
            text = { Text("Вы действительно хотите завершить текущую смену?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.endShift()
                        // Отменяем напоминания о фото
                        PhotoReminderWorker.cancel(context)
                        showEndShiftDialog = false
                    }
                ) {
                    Text("Завершить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndShiftDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог требования первого фото при начале смены
    if (showFirstPhotoDialog) {
        AlertDialog(
            onDismissRequest = { showFirstPhotoDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Начало смены")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Для начала смены требуется сделать первое фото.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Фотография подтвердит ваше присутствие на рабочем месте.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFirstPhotoDialog = false
                        pendingShiftStart = true
                        // Переход на камеру для первого фото (без shiftId - он будет создан после)
                        navController.navigate("camera/pending/0")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сделать фото")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFirstPhotoDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun NoShiftView(
    availableObjects: List<SiteObjectDto> = emptyList(),
    selectedObjectId: String? = null,
    currentObjectName: String? = null,
    onSelectObject: (String?) -> Unit = {},
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // FIX(2026-05-11) BELSI 2.0.0: max-width 560dp + center для Z Fold/планшета —
    // экран ожидания начала смены не должен растягиваться на 884dp.
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Нет активной смены",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Выберите объект (опционально) и начните смену",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // FIX(2026-05-01): селектор объекта на экране НЕТ-смены, перед стартом
        ObjectPickerCard(
            availableObjects = availableObjects,
            selectedObjectId = selectedObjectId,
            currentObjectName = currentObjectName,
            onSelect = onSelectObject,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        com.belsi.work.presentation.components.SwipeToStartButton(
            onConfirmed = onStartClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
    }  // close Box (FIX 2026-05-11: max-width 560dp + center)
}

/**
 * FIX(2026-05-01): универсальный селектор объекта (опциональный).
 *
 * - Тап на карточку → диалог со списком всех `availableObjects` + опция «Без объекта»
 * - Текущее значение показывается в карточке
 * - Привязка не обязательна — кнопка «Без объекта» возвращает selectedObjectId=null
 *
 * Использование:
 *  - до старта смены: `onSelect = viewModel::selectObject` (только локальное состояние)
 *  - на активной смене: `onSelect = viewModel::changeObject` (отправляет на сервер)
 */
@Composable
private fun ObjectPickerCard(
    availableObjects: List<SiteObjectDto>,
    selectedObjectId: String?,
    currentObjectName: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val selectedName = currentObjectName
        ?: availableObjects.firstOrNull { it.id == selectedObjectId }?.name

    Card(
        modifier = modifier.clickable { showPicker = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (selectedName != null)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            ) {
                Icon(
                    Icons.Default.Business, null,
                    modifier = Modifier.padding(8.dp).size(22.dp),
                    tint = if (selectedName != null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Объект",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = selectedName ?: "Не выбран",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selectedName != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selectedName != null)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Default.ArrowDropDown, "Выбрать объект",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Объект") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        ObjectPickerRow(
                            name = "Без объекта",
                            address = "Не привязывать смену к объекту",
                            isSelected = selectedObjectId == null,
                            onClick = {
                                onSelect(null)
                                showPicker = false
                            }
                        )
                    }
                    if (availableObjects.isNotEmpty()) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                    items(availableObjects, key = { it.id }) { obj ->
                        ObjectPickerRow(
                            name = obj.name,
                            address = obj.address ?: "",
                            isSelected = selectedObjectId == obj.id,
                            onClick = {
                                onSelect(obj.id)
                                showPicker = false
                            }
                        )
                    }
                    if (availableObjects.isEmpty()) {
                        item {
                            Text(
                                "Список объектов пуст. Куратор может добавить объекты в дашборде.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
private fun ObjectPickerRow(
    name: String,
    address: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else
        Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (address.isNotBlank()) {
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelected) {
            Icon(
                Icons.Default.Done, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ActiveShiftView(
    availableObjects: List<SiteObjectDto> = emptyList(),
    selectedObjectId: String? = null,
    currentObjectName: String? = null,
    onChangeObject: (String) -> Unit = {},
    shiftId: String,
    elapsedTime: String,
    netWorkTime: String,
    pauseTime: String,
    totalPauseTime: String,
    isPaused: Boolean,
    idleTime: String,
    totalIdleTime: String,
    isIdle: Boolean,
    idleReason: String? = null,
    photoSlots: List<PhotoSlot>,
    pendingPhotoCount: Int = 0,
    // Timeline data
    startTimeMillis: Long = 0L,
    elapsedSeconds: Long = 0L,
    pauseSeconds: Long = 0L,
    idleSeconds: Long = 0L,
    onPhotoSlotClick: (PhotoSlot) -> Unit,
    onPauseClick: () -> Unit,
    onIdleClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
    onEndShiftClick: () -> Unit,
    onSupportChatClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // FIX(2026-05-11) BELSI 2.0.0 (Фаза 2): two-pane на expanded (>= 840dp).
    // Z Fold open / 11" tablet → status слева, фото справа. Compact (<840dp) —
    // одна колонка с max-width 720dp (читаемая ширина для текста).
    //
    // Extract'нул 2 LazyListScope-лямбды (statusItems / photoItems) чтобы
    // не дублировать ~480 строк UI между двумя ветками. Лямбды захватывают
    // все параметры функции через closure (availableObjects, taskParams и т.д.).
    val statusItems: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        // FIX(2026-05-01): селектор объекта на активной смене.
        // changeObject отправляет на сервер /shifts/change-object — куратор
        // увидит обновлённую привязку.
        item {
            ObjectPickerCard(
                availableObjects = availableObjects,
                selectedObjectId = selectedObjectId,
                currentObjectName = currentObjectName,
                onSelect = { id -> if (id != null) onChangeObject(id) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Таймер смены with purple gradient
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (isPaused) listOf(
                                    MaterialTheme.belsiColors.warning,
                                    MaterialTheme.belsiColors.warning.copy(alpha = 0.85f)
                                ) else listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Main timer - Время работы
                        Text(
                            text = netWorkTime,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )

                        Text(
                            text = "Время работы",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sub-timers row - adaptive font sizes
                        val timerFontSize = if (screenWidthDp() < 360) 13.sp else 16.sp
                        val labelFontSize = if (screenWidthDp() < 360) 9.sp else 11.sp

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = elapsedTime,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = timerFontSize
                                )
                                Text(
                                    text = "Общее",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                    fontSize = labelFontSize
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isPaused) pauseTime else totalPauseTime,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.belsiColors.warning,  // Оранжевый
                                    fontSize = timerFontSize
                                )
                                Text(
                                    text = if (isPaused) "Пауза" else "Пауза",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                    fontSize = labelFontSize
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isIdle) idleTime else totalIdleTime,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,  // Красный
                                    fontSize = timerFontSize
                                )
                                Text(
                                    text = if (isIdle) "Простой" else "Простой",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                    fontSize = labelFontSize
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Control buttons row - adaptive
                        val buttonFontSize = if (screenWidthDp() < 360) 13.sp else 15.sp
                        val buttonIconSize = if (screenWidthDp() < 360) 18.dp else 22.dp
                        val buttonHeight = if (screenWidthDp() < 360) 44.dp else 52.dp
                        val buttonSpacing = if (screenWidthDp() < 360) 8.dp else 12.dp

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                        ) {
                            // Pause button
                            Button(
                                onClick = onPauseClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(buttonHeight),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPaused)
                                        MaterialTheme.belsiColors.warning.copy(alpha = 0.9f)
                                    else
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(buttonIconSize)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isPaused) "Старт" else "Пауза",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = buttonFontSize
                                )
                            }

                            // Idle button
                            Button(
                                onClick = onIdleClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(buttonHeight),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isIdle)
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                    else
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    if (isIdle) Icons.Default.PlayArrow else Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(buttonIconSize)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isIdle) "Старт" else "Простой",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = buttonFontSize
                                )
                            }
                        }
                    }
                }
            }
        }

        // Информационный блок о паузе / простое
        if (isPaused || isIdle) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isIdle) Color(0xFFFF6B6B).copy(alpha = 0.1f) else Color(0xFFFFA726).copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (isIdle) Icons.Default.Warning else Icons.Default.Pause,
                                contentDescription = null,
                                tint = if (isIdle) Color(0xFFFF6B6B) else Color(0xFFFFA726),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (isIdle) "Простой" else "Пауза",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isIdle) Color(0xFFFF6B6B) else Color(0xFFFFA726)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (isIdle && !idleReason.isNullOrBlank()) {
                            Text(
                                text = "Причина: $idleReason",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            text = if (isIdle)
                                "Простой длится ${idleTime}. Если проблема не решается — обратитесь в поддержку."
                            else
                                "Пауза: ${pauseTime}. При возникновении проблем — обратитесь в поддержку.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onSupportChatClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.SupportAgent,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Написать в поддержку")
                        }
                    }
                }
            }
        }

        // Photo button
        item {
            TextButton(
                onClick = onTakePhotoClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Camera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Сделать фото",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // End shift button (moved from top bar to bottom)
        item {
            Button(
                onClick = onEndShiftClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Завершить работу",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Legacy stats section (can be removed if not needed)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Статистика смены",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Общее время",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = elapsedTime,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Всего в паузе",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (totalPauseTime != "00:00:00") totalPauseTime else "-",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Таймлайн смены
        if (elapsedSeconds > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    ShiftTimeline(
                        startTimeMillis = startTimeMillis,
                        elapsedSeconds = elapsedSeconds,
                        pauseSeconds = pauseSeconds,
                        idleSeconds = idleSeconds,
                        photoSlots = photoSlots,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

    }  // FIX(2026-05-11): конец statusItems лямбды

    val photoItems: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        // Заголовок почасовых фото
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Camera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Почасовые фотографии",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Загружайте фото каждый час с 10:00 до 17:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Бэйдж очереди фото на загрузку
                if (pendingPhotoCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$pendingPhotoCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Сетка фото-слотов (8 слотов) - адаптивная
        item {
            val screenWidth = screenWidthDp()
            val columns = when {
                screenWidth < 360 -> 1   // Очень узкий экран
                screenWidth < 600 -> 2   // Компактный экран
                screenWidth < 840 -> 3   // Средний экран
                else -> 4                // Широкий экран
            }
            // Рассчитываем высоту на основе числа рядов
            val rowCount = (photoSlots.size + columns - 1) / columns
            val itemHeight = if (columns == 1) 150.dp else 180.dp
            val gridHeight = (itemHeight + 12.dp) * rowCount

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.height(gridHeight),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(photoSlots) { slot ->
                    PhotoSlotCard(
                        slot = slot,
                        onClick = { onPhotoSlotClick(slot) }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }  // FIX(2026-05-11): конец photoItems лямбды

    // FIX(2026-05-11) BELSI 2.0.0: adaptive layout — two-pane на expanded,
    // single column max-720dp на compact/medium. BoxWithConstraints даёт
    // реальную доступную ширину после padding/insets — точнее чем
    // LocalConfiguration.screenWidthDp на split-screen и Fold half-open.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            // EXPANDED: two-pane. Status слева 45%, фото справа 55%.
            // widthIn(1400) — на Tab S Ultra (1232 dp) пройдёт целиком,
            // на условном будущем 16" планшете обрежет до 1400dp.
            Row(
                modifier = Modifier
                    .widthIn(max = 1400.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = statusItems,
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = photoItems,
                )
            }
        } else {
            // COMPACT / MEDIUM: одна колонка с max-width 720dp по центру.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    statusItems()
                    photoItems()
                }
            }
        }
    }
}

@Composable
private fun PhotoSlotCard(
    slot: PhotoSlot,
    onClick: () -> Unit
) {
    // Если фото загружено и есть URL - показываем превью, иначе - обычный интерфейс
    val canClick = when (slot.status) {
        PhotoSlotStatus.LOCKED -> false
        PhotoSlotStatus.UPLOADED -> slot.photoUrl != null // Можно кликнуть для просмотра
        else -> true // Можно кликнуть для загрузки
    }

    // Показываем фото для PENDING и UPLOADED статусов
    val showPhotoPreview = (slot.status == PhotoSlotStatus.UPLOADED || slot.status == PhotoSlotStatus.PENDING) && slot.photoUrl != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(enabled = canClick) { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = when (slot.status) {
                PhotoSlotStatus.EMPTY -> MaterialTheme.colorScheme.surface
                PhotoSlotStatus.PENDING -> MaterialTheme.belsiColors.warning.copy(alpha = 0.15f)  // На модерации
                PhotoSlotStatus.UPLOADED -> MaterialTheme.belsiColors.success.copy(alpha = 0.15f)  // Одобрено
                PhotoSlotStatus.REJECTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)  // Отклонено
                PhotoSlotStatus.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (showPhotoPreview) {
                // Отображаем загруженное фото
                Box(modifier = Modifier.fillMaxSize()) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(slot.photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Фото ${slot.timeLabel}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )

                    // Overlay с временем и статусом
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(12.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                when (slot.status) {
                                    PhotoSlotStatus.PENDING -> Icons.Default.AccessTime
                                    PhotoSlotStatus.UPLOADED -> Icons.Default.CheckCircle
                                    else -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = when (slot.status) {
                                    PhotoSlotStatus.PENDING -> MaterialTheme.belsiColors.warning
                                    PhotoSlotStatus.UPLOADED -> MaterialTheme.belsiColors.success
                                    else -> MaterialTheme.belsiColors.success
                                }
                            )
                            Text(
                                text = slot.timeLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Цветная рамка в зависимости от статуса
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = 3.dp,
                                color = when (slot.status) {
                                    PhotoSlotStatus.PENDING -> MaterialTheme.belsiColors.warning
                                    PhotoSlotStatus.UPLOADED -> MaterialTheme.belsiColors.success
                                    else -> MaterialTheme.belsiColors.success
                                },
                                shape = MaterialTheme.shapes.medium
                            )
                    )
                }
            } else {
                // Стандартный интерфейс без фото
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Иконка статуса
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                when (slot.status) {
                                    PhotoSlotStatus.EMPTY -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    PhotoSlotStatus.PENDING -> MaterialTheme.belsiColors.warning.copy(alpha = 0.2f)
                                    PhotoSlotStatus.UPLOADED -> MaterialTheme.belsiColors.success.copy(alpha = 0.2f)
                                    PhotoSlotStatus.REJECTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    PhotoSlotStatus.LOCKED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (slot.status) {
                                PhotoSlotStatus.EMPTY -> Icons.Default.AddAPhoto
                                PhotoSlotStatus.PENDING -> Icons.Default.AccessTime
                                PhotoSlotStatus.UPLOADED -> Icons.Default.CheckCircle
                                PhotoSlotStatus.REJECTED -> Icons.Default.Error
                                PhotoSlotStatus.LOCKED -> Icons.Default.Lock
                            },
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = when (slot.status) {
                                PhotoSlotStatus.EMPTY -> MaterialTheme.colorScheme.primary
                                PhotoSlotStatus.PENDING -> MaterialTheme.belsiColors.warning
                                PhotoSlotStatus.UPLOADED -> MaterialTheme.belsiColors.success
                                PhotoSlotStatus.REJECTED -> MaterialTheme.colorScheme.error
                                PhotoSlotStatus.LOCKED -> MaterialTheme.colorScheme.outline
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Время
                    Text(
                        text = slot.timeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Статус
                    Text(
                        text = when (slot.status) {
                            PhotoSlotStatus.EMPTY -> "Ожидает"
                            PhotoSlotStatus.PENDING -> "На проверке"
                            PhotoSlotStatus.UPLOADED -> "Сделано"
                            PhotoSlotStatus.REJECTED -> "Отклонено"
                            PhotoSlotStatus.LOCKED -> "Закрыто"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (slot.status) {
                            PhotoSlotStatus.UPLOADED -> MaterialTheme.belsiColors.success
                            PhotoSlotStatus.REJECTED -> MaterialTheme.colorScheme.error
                            PhotoSlotStatus.PENDING -> MaterialTheme.belsiColors.warning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (slot.status == PhotoSlotStatus.UPLOADED || slot.status == PhotoSlotStatus.REJECTED)
                            FontWeight.Bold
                        else
                            FontWeight.Normal
                    )

                    // Комментарий при отклонении
                    if (slot.status == PhotoSlotStatus.REJECTED && slot.rejectionReason != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = slot.rejectionReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Диалог выбора причины простоя.
 *
 * FIX(2026-05-11) BELSI 2.0.0 build9: список причин теперь подгружается с бэкенда
 * через GET /shift/idle-reasons?domain=installation (источник — таблица
 * shift_idle_reason_catalog, единая после миграции). Hardcoded список оставлен
 * как fallback если API недоступен (например, в полном офлайне без cache).
 */
@Composable
private fun IdleReasonDialog(
    onDismiss: () -> Unit,
    onReasonSelected: (String) -> Unit,
    apiReasons: List<String> = emptyList(),
) {
    val fallbackReasons = listOf(
        "Ожидание материалов",
        "Ожидание инструмента",
        "Технические проблемы",
        "Погодные условия",
        "Ожидание бригадира",
        "Другая причина"
    )
    val reasons = if (apiReasons.isNotEmpty()) apiReasons + "Другая причина" else fallbackReasons

    var selectedReason by remember { mutableStateOf<String?>(null) }
    var customReason by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Причина простоя",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Выберите причину простоя:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                reasons.forEach { reason ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (reason == "Другая причина") {
                                    showCustomInput = true
                                    selectedReason = reason
                                } else {
                                    showCustomInput = false
                                    selectedReason = reason
                                }
                            },
                        shape = MaterialTheme.shapes.small,
                        color = if (selectedReason == reason)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (selectedReason == reason)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = reason,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedReason == reason)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (showCustomInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        label = { Text("Укажите причину") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                    // FIX(2026-05-11) BELSI 2.0.0: голосовой ввод причины простоя.
                    // Открывает sheet с записью → Whisper транскрибирует → текст
                    // подставляется в customReason. Контекст "idle" подсказывает AI
                    // что речь о причине простоя.
                    var showVoiceCapture by remember { mutableStateOf(false) }
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { showVoiceCapture = true }) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Надиктовать причину", style = MaterialTheme.typography.bodySmall)
                    }
                    com.belsi.work.presentation.components.VoiceCaptureSheet(
                        visible = showVoiceCapture,
                        contextHint = "idle",
                        onDismiss = { showVoiceCapture = false },
                        onTextReady = { transcribed ->
                            customReason = if (customReason.isBlank()) transcribed
                                else "$customReason $transcribed".trim()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalReason = if (showCustomInput && customReason.isNotBlank()) {
                        customReason
                    } else {
                        selectedReason ?: "Не указана"
                    }
                    onReasonSelected(finalReason)
                },
                enabled = selectedReason != null && (!showCustomInput || customReason.isNotBlank())
            ) {
                Text("Начать простой")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// Модели данных для фото-слотов
data class PhotoSlot(
    val index: Int,
    val timeLabel: String,
    val status: PhotoSlotStatus,
    val photoUrl: String? = null,
    val rejectionReason: String? = null
)

enum class PhotoSlotStatus {
    EMPTY,      // Ожидает загрузки
    PENDING,    // На модерации
    UPLOADED,   // Загружено и одобрено
    REJECTED,   // Отклонено модератором
    LOCKED      // Время еще не наступило
}
