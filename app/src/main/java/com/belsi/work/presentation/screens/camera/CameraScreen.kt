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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val comment by viewModel.comment.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is CameraViewModel.NavigationEvent.NavigateBack -> {
                    navController.popBackStack()
                }
                is CameraViewModel.NavigationEvent.NavigateBackWithSuccess -> {
                    // Фото успешно загружено, уведомляем ShiftScreen и возвращаемся
                    val prevHandle = navController.previousBackStackEntry?.savedStateHandle
                    prevHandle?.set("photo_uploaded", true)
                    // Если была создана новая смена — передаём её ID для автостарта
                    if (event.createdShiftId != null) {
                        prevHandle?.set("shift_created_id", event.createdShiftId)
                    }
                    navController.popBackStack()
                }
            }
        }
    }

    Scaffold(
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
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasCameraPermission) {
                // Camera Preview
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            previewView = pv
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(pv.surfaceProvider)
                            }

                            val imageCaptureBuilder = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            imageCapture = imageCaptureBuilder.build()

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
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

                // Capture Button, Category and Comment
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Категория фото
                    PhotoCategorySelector(
                        selectedCategory = selectedCategory,
                        onCategorySelected = { viewModel.updateCategory(it) },
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Поле для комментария с кнопкой голосового ввода
                    val sttContext = LocalContext.current
                    var isListeningSTT by remember { mutableStateOf(false) }
                    val sttHelper = remember { SpeechToTextHelper(sttContext) }
                    val sttAvailable = remember { sttHelper.isAvailable() }

                    // Пульсирующая анимация при записи
                    val micAlpha by animateFloatAsState(
                        targetValue = if (isListeningSTT) 0.5f else 1f,
                        animationSpec = if (isListeningSTT)
                            infiniteRepeatable(
                                animation = tween(600),
                                repeatMode = RepeatMode.Reverse
                            )
                        else snap(),
                        label = "mic_pulse"
                    )

                    // Запрос разрешения на микрофон
                    val audioPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) {
                            sttHelper.startListening(
                                onResult = { text ->
                                    val current = comment
                                    viewModel.updateComment(
                                        if (current.isBlank()) text
                                        else "$current $text"
                                    )
                                },
                                onError = { /* ignore */ },
                                onListeningStateChanged = { isListeningSTT = it }
                            )
                        }
                    }

                    DisposableEffect(Unit) {
                        onDispose { sttHelper.destroy() }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { viewModel.updateComment(it) },
                            placeholder = { Text("Комментарий (необязательно)") },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                                focusedContainerColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            singleLine = true,
                            enabled = !isLoading
                        )

                        if (sttAvailable) {
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (isListeningSTT) {
                                        sttHelper.stopListening()
                                        isListeningSTT = false
                                    } else {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            sttContext, Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            sttHelper.startListening(
                                                onResult = { text ->
                                                    val current = comment
                                                    viewModel.updateComment(
                                                        if (current.isBlank()) text
                                                        else "$current $text"
                                                    )
                                                },
                                                onError = { /* ignore */ },
                                                onListeningStateChanged = { isListeningSTT = it }
                                            )
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    if (isListeningSTT) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (isListeningSTT) "Остановить запись" else "Голосовой ввод",
                                    tint = if (isListeningSTT)
                                        MaterialTheme.colorScheme.error.copy(alpha = micAlpha)
                                    else
                                        Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Индикатор загрузки с текстом
                    if (isLoading) {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .fillMaxWidth(0.8f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Загрузка фото на сервер...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    errorMessage?.let { error ->
                        Card(
                            modifier = Modifier.padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = error,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

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
                                        val uri = Uri.fromFile(photoFile)
                                        viewModel.savePhoto(uri, context, shiftId, slotIndex)
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
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Сделать фото",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            } else {
                // Permission Request
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Требуется разрешение на использование камеры")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    ) {
                        Text("Предоставить доступ")
                    }
                }
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
    data class CategoryInfo(
        val key: String,
        val label: String,
        val icon: ImageVector,
        val color: Color
    )

    val categories = listOf(
        CategoryInfo("hourly", "Ежечасное", Icons.Default.Schedule, MaterialTheme.colorScheme.primary),
        CategoryInfo("problem", "Проблема", Icons.Default.Warning, MaterialTheme.colorScheme.error),
        CategoryInfo("question", "Вопрос", Icons.Default.HelpOutline, MaterialTheme.belsiColors.warning)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
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
                leadingIcon = {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.85f),
                    labelColor = Color.DarkGray,
                    iconColor = Color.DarkGray,
                    selectedContainerColor = category.color.copy(alpha = 0.9f),
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                ),
                modifier = Modifier.weight(1f),
                enabled = enabled
            )
        }
    }
}
