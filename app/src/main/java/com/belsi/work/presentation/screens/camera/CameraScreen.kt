package com.belsi.work.presentation.screens.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Carpenter
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.Wysiwyg
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.animation.core.*
import com.belsi.work.presentation.theme.belsiColors
import com.belsi.work.utils.SpeechToTextHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    navController: NavController,
    shiftId: String? = null,
    slotIndex: Int? = null,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    // Привязку готовим ДО съёмки — грузим дерево при открытии экрана.
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        viewModel.loadDestinationTree()
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is CameraViewModel.NavigationEvent.NavigateBack -> navController.popBackStack()
                is CameraViewModel.NavigationEvent.NavigateBackWithSuccess -> {
                    val prevHandle = navController.previousBackStackEntry?.savedStateHandle
                    prevHandle?.set("photo_uploaded", true)
                    if (event.createdShiftId != null) prevHandle?.set("shift_created_id", event.createdShiftId)
                    navController.popBackStack()
                }
            }
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true,
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 110.dp,
        topBar = {
            TopAppBar(
                title = { Text("Сделать фото") },
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
        sheetContent = {
            PhotoDestinationContent(
                viewModel = viewModel,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onSend = { viewModel.confirmAndSave(context, shiftId, slotIndex) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Только верхний отступ (под TopAppBar). Низ НЕ занимаем отступом scaffold,
                // иначе peek-лист учитывался дважды и кнопка съёмки уезжала вверх.
                .padding(top = padding.calculateTopPadding())
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(pv.surfaceProvider)
                            }
                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .build()
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Кнопка съёмки — фиксированно 16dp над peek-листом (110dp), у нижнего края.
                        .padding(bottom = 126.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PhotoCategorySelector(
                        selectedCategory = selectedCategory,
                        onCategorySelected = { viewModel.updateCategory(it) },
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FloatingActionButton(
                        onClick = {
                            val capture = imageCapture ?: return@FloatingActionButton
                            val photoFile = File(
                                context.cacheDir,
                                SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                                    .format(System.currentTimeMillis()) + ".jpg"
                            )
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            capture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        viewModel.onPhotoCaptured(Uri.fromFile(photoFile))
                                        if (viewModel.isReadyToSend()) {
                                            // Привязка готова — отправляем сразу (минимум нажатий).
                                            viewModel.confirmAndSave(context, shiftId, slotIndex)
                                        } else {
                                            // Не готово — раскрываем лист, чтобы дозаполнить.
                                            scope.launch { scaffoldState.bottomSheetState.expand() }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        exception.printStackTrace()
                                    }
                                }
                            )
                        },
                        modifier = Modifier.size(72.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Сделать фото",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Требуется разрешение на использование камеры")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Предоставить доступ")
                    }
                }
            }
        }
    }
}

/**
 * Контент pull-up листа «Куда отнести фото?» — каскад выпадающих списков
 * Этаж → Зона → Кабинет (с вводом номера) → Окно + Этап + обязательный комментарий
 * + чекбокс «Финальное фото». Доступен ДО съёмки (потяни снизу вверх).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoDestinationContent(
    viewModel: CameraViewModel,
    isLoading: Boolean,
    errorMessage: String?,
    onSend: () -> Unit,
) {
    val tree by viewModel.objectTree.collectAsState()
    val selFloor by viewModel.selectedFloorId.collectAsState()
    val selZone by viewModel.selectedZoneId.collectAsState()
    val selCab by viewModel.selectedCabinetId.collectAsState()
    val selWin by viewModel.selectedWindowId.collectAsState()
    val selStage by viewModel.selectedStage.collectAsState()
    val isFinal by viewModel.isFinal.collectAsState()
    val comment by viewModel.comment.collectAsState()
    val pendingPhoto by viewModel.pendingPhotoUri.collectAsState()
    val isCreatingNode by viewModel.isCreatingNode.collectAsState()
    var showAddCabinet by remember { mutableStateOf(false) }
    var showAddWindow by remember { mutableStateOf(false) }

    val floors = tree?.floors.orEmpty()
    val zones = floors.firstOrNull { it.floor.id == selFloor }?.zones.orEmpty()
    val cabinets = zones.firstOrNull { it.zone.id == selZone }?.cabinets.orEmpty()
    val windows = cabinets.firstOrNull { it.cabinet.id == selCab }?.windows.orEmpty()
    val treeHasData = floors.isNotEmpty()

    val ready = comment.isNotBlank() && (!treeHasData || selWin != null)
    val canSend = ready && pendingPhoto != null && !isLoading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("Куда отнести фото?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Каскадный выбор + автозаполнение",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (treeHasData) {
            DropdownField(
                label = "Этаж",
                options = floors.map { it.floor.id to "${it.floor.floorNumber}-й этаж" },
                selectedId = selFloor,
                onSelect = { viewModel.selectFloor(it) },
                enabled = !isLoading,
            )
            Spacer(Modifier.height(8.dp))
            DropdownField(
                label = "Зона",
                options = zones.map { it.zone.id to it.zone.zoneCode },
                selectedId = selZone,
                onSelect = { viewModel.selectZone(it) },
                enabled = !isLoading,
            )
            Spacer(Modifier.height(8.dp))
            SearchableDropdownField(
                label = "Кабинет",
                options = cabinets.map { it.cabinet.id to it.cabinet.cabinetNumber },
                selectedId = selCab,
                onSelect = { viewModel.selectCabinet(it) },
                enabled = !isLoading,
            )
            // «+Новый кабинет» — installer/foreman/coord/curator (бэк разрешает с 2.1.0)
            if (tree?.isDemo == false && selZone != null) {
                AddNodeLink("Новый кабинет", enabled = !isLoading && !isCreatingNode) { showAddCabinet = true }
            }
            Spacer(Modifier.height(8.dp))
            DropdownField(
                label = "Окно",
                options = windows.map { it.id to "Окно ${it.windowNumber}" },
                selectedId = selWin,
                onSelect = { viewModel.selectWindow(it) },
                enabled = !isLoading,
            )
            // «+Новое окно» в выбранном кабинете
            if (tree?.isDemo == false && selCab != null) {
                AddNodeLink("Новое окно", enabled = !isLoading && !isCreatingNode) { showAddWindow = true }
            }
            Spacer(Modifier.height(12.dp))

            Text(
                "Этап",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("karkas", "Каркас", Icons.Default.Carpenter),
                    Triple("podokonnik", "Подоконник", Icons.Default.Window),
                    Triple("ekran", "Экран", Icons.Default.Wysiwyg),
                ).forEach { (key, label, icon) ->
                    FilterChip(
                        selected = selStage == key,
                        onClick = { viewModel.selectStage(key) },
                        label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        enabled = !isLoading,
                    )
                }
            }
        } else {
            Text(
                "Объект не загружен — фото уйдёт как ежечасное (без привязки к окну).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        CommentWithVoice(
            comment = comment,
            onCommentChange = { viewModel.updateComment(it) },
            enabled = !isLoading,
        )

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = isFinal, onCheckedChange = { viewModel.setFinal(it) }, enabled = !isLoading)
            Icon(Icons.Default.Star, null, tint = MaterialTheme.belsiColors.warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Финальное фото (для приёмки)", style = MaterialTheme.typography.bodyMedium)
        }

        errorMessage?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text(
                    if (pendingPhoto == null) "Сделайте фото" else "Сохранить и отправить",
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (pendingPhoto == null) {
            Text(
                "Подготовьте привязку и нажмите кнопку съёмки",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (showAddCabinet) {
            AddCabinetDialog(
                isCreating = isCreatingNode,
                onConfirm = { num -> viewModel.addCabinet(num); showAddCabinet = false },
                onDismiss = { showAddCabinet = false },
            )
        }
        if (showAddWindow) {
            AddWindowDialog(
                suggested = viewModel.suggestNextWindowNumber(),
                isCreating = isCreatingNode,
                onConfirm = { n -> viewModel.addWindow(n); showAddWindow = false },
                onDismiss = { showAddWindow = false },
            )
        }
    }
}

/** Маленькая ссылка-кнопка «+ Новый …» под полем каскада. */
@Composable
private fun AddNodeLink(text: String, enabled: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(2.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Диалог создания нового кабинета (номер вводится вручную, напр. 305 / 101/2). */
@Composable
private fun AddCabinetDialog(
    isCreating: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var number by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("Новый кабинет") },
        text = {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Номер кабинета") },
                placeholder = { Text("напр. 305 или 101/2") },
                singleLine = true,
                enabled = !isCreating,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(number) }, enabled = number.isNotBlank() && !isCreating) {
                Text("Создать")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isCreating) { Text("Отмена") } },
    )
}

/** Диалог создания нового окна (номер — целое, пред-заполнен следующим по порядку). */
@Composable
private fun AddWindowDialog(
    suggested: Int,
    isCreating: Boolean,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var number by remember { mutableStateOf(suggested.toString()) }
    val parsed = number.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("Новое окно") },
        text = {
            OutlinedTextField(
                value = number,
                onValueChange = { v -> number = v.filter { it.isDigit() }.take(4) },
                label = { Text("Номер окна") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isCreating,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(parsed) }, enabled = parsed > 0 && !isCreating) {
                Text("Создать")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isCreating) { Text("Отмена") } },
    )
}

/** Простой выпадающий список (read-only). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: ""
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("—") }, onClick = { expanded = false })
            }
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}

/** Выпадающий список с вводом текста — фильтрует по подстроке (ввод «305»). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableDropdownField(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: ""
    var query by remember(selectedLabel) { mutableStateOf(selectedLabel) }
    val filtered = remember(query, options) {
        if (query.isBlank() || options.any { it.second == query }) options
        else options.filter { it.second.contains(query, ignoreCase = true) }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = { Text("$label (введите номер)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (filtered.isEmpty()) {
                DropdownMenuItem(text = { Text("Ничего не найдено") }, onClick = { expanded = false })
            }
            filtered.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(id); query = text; expanded = false },
                )
            }
        }
    }
}

/** Поле комментария (обязательное) с кнопкой голосового ввода. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentWithVoice(
    comment: String,
    onCommentChange: (String) -> Unit,
    enabled: Boolean,
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    val sttHelper = remember { SpeechToTextHelper(context) }
    val sttAvailable = remember { sttHelper.isAvailable() }

    val micAlpha by animateFloatAsState(
        targetValue = if (isListening) 0.5f else 1f,
        animationSpec = if (isListening)
            infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse)
        else snap(),
        label = "mic_pulse"
    )

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sttHelper.startListening(
                onResult = { text -> onCommentChange(if (comment.isBlank()) text else "$comment $text") },
                onError = { },
                onListeningStateChanged = { isListening = it }
            )
        }
    }

    DisposableEffect(Unit) { onDispose { sttHelper.destroy() } }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = comment,
            onValueChange = onCommentChange,
            label = { Text("Комментарий *") },
            placeholder = { Text("Например: Подоконник лежит ровно, готов") },
            isError = comment.isBlank(),
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            enabled = enabled,
        )
        if (sttAvailable) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (isListening) {
                        sttHelper.stopListening(); isListening = false
                    } else {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            sttHelper.startListening(
                                onResult = { text -> onCommentChange(if (comment.isBlank()) text else "$comment $text") },
                                onError = { },
                                onListeningStateChanged = { isListening = it }
                            )
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                enabled = enabled
            ) {
                Icon(
                    if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isListening) "Остановить запись" else "Голосовой ввод",
                    tint = if (isListening) MaterialTheme.colorScheme.error.copy(alpha = micAlpha)
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Селектор категории фото: ежечасное, проблема, вопрос
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoCategorySelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    enabled: Boolean = true
) {
    data class CategoryInfo(val key: String, val label: String, val icon: ImageVector, val color: Color)

    val categories = listOf(
        CategoryInfo("hourly", "Ежечасное", Icons.Default.Schedule, MaterialTheme.colorScheme.primary),
        CategoryInfo("urgent", "Срочно", Icons.Default.PriorityHigh, Color(0xFFDC2626)),
        CategoryInfo("problem", "Проблема", Icons.Default.Warning, MaterialTheme.colorScheme.error),
        CategoryInfo("question", "Вопрос", Icons.Default.HelpOutline, MaterialTheme.belsiColors.warning)
    )

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            val isSelected = selectedCategory == category.key
            FilterChip(
                selected = isSelected,
                onClick = { if (enabled) onCategorySelected(category.key) },
                label = {
                    Text(
                        category.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = { Icon(category.icon, null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.85f),
                    labelColor = Color.DarkGray,
                    iconColor = Color.DarkGray,
                    selectedContainerColor = category.color.copy(alpha = 0.9f),
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                ),
                enabled = enabled
            )
        }
    }
}
