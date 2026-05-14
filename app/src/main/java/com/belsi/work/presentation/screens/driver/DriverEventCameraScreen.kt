package com.belsi.work.presentation.screens.driver

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.belsi.work.utils.LocationProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

/**
 * FIX(2026-05-11) BELSI 2.0.0 build11: РЕАЛЬНЫЙ CameraX экран для водителя.
 *
 * Заменяет DriverCameraStub (визуальная имитация без CameraX).
 *
 * Бриф BELSI.Driver, секция «Камера»:
 *  - Полноэкранный интерфейс с превью и возможностью переснять
 *  - Используется задняя камера
 *  - Фото сохраняется как File, отправляется multipart-запросом на сервер
 *
 * Возврат: callback `onPhotoTaken(file, lat, lng, accuracy)` — путь к фото
 * + GPS координаты на момент съёмки.
 *
 * Использование (из навигации):
 *   composable("driver/camera/{eventType}") {
 *       DriverEventCameraScreen(
 *           eventType = "arrival",  // или "delivery_complete" / "departure"
 *           contextLabel = "Школа №7, Коломенская 16",
 *           onCancel = { navController.popBackStack() },
 *           onPhotoTaken = { file, lat, lng, acc ->
 *               // save to savedStateHandle, popBackStack
 *           },
 *       )
 *   }
 */
@Composable
fun DriverEventCameraScreen(
    eventType: String,
    contextLabel: String?,
    onCancel: () -> Unit,
    onPhotoTaken: (file: File, latitude: Double?, longitude: Double?, accuracy: Float?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var locationLabel by remember { mutableStateOf<String?>(null) }
    var currentLocation by remember { mutableStateOf<LocationProvider.Coords?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> /* пытаемся получить координаты ниже независимо от результата */ }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        // Запрашиваем геолокацию (не блокируем камеру)
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        // Параллельно тянем текущие координаты
        coroutineScope.launch {
            val coords = LocationProvider.getCurrent(context)
            currentLocation = coords
            locationLabel = coords?.let {
                "📍 %.4f, %.4f · ±%.0fм · %s".format(it.latitude, it.longitude, it.accuracyMeters, it.source)
            } ?: "📍 GPS недоступен"
        }
    }

    val eventLabel = when (eventType) {
        "arrival" -> "🚛 Прибытие"
        "delivery_complete" -> "✅ Доставка"
        "departure" -> "👋 Отъезд"
        "shift_start" -> "▶️ Начало смены"
        "shift_end" -> "⏹ Конец смены"
        else -> eventType
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            // Camera preview — full screen
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { pv ->
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(pv.surfaceProvider)
                        }
                        val imgCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        imageCapture = imgCapture
                        val selector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, selector, preview, imgCapture,
                            )
                        } catch (e: Exception) {
                            error = "Не удалось открыть камеру: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Верхняя плашка — контекст и геолокация
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        eventLabel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    if (!contextLabel.isNullOrBlank()) {
                        Text(
                            contextLabel,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                        )
                    }
                    locationLabel?.let {
                        Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }

            // Кнопка закрыть
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(Icons.Default.Close, "Отмена", tint = Color.White)
            }

            // Большая кнопка съёмки — внизу
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(if (isCapturing) Color.Gray else Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        enabled = !isCapturing && imageCapture != null,
                        onClick = {
                            val capture = imageCapture ?: return@IconButton
                            isCapturing = true
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val photoFile = File(
                                context.filesDir,
                                "driver_event_${eventType}_${timestamp}.jpg",
                            )
                            val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            capture.takePicture(
                                output,
                                Executors.newSingleThreadExecutor(),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                                        onPhotoTaken(
                                            photoFile,
                                            currentLocation?.latitude,
                                            currentLocation?.longitude,
                                            currentLocation?.accuracyMeters,
                                        )
                                    }
                                    override fun onError(e: ImageCaptureException) {
                                        isCapturing = false
                                        error = "Ошибка съёмки: ${e.message}"
                                    }
                                },
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            "Снять",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }

            error?.let {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Нет доступа к камере",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Без доступа к камере нельзя зафиксировать прибытие/доставку — это критично для логистики.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Разрешить камеру")
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancel) {
                    Text("Отменить", color = Color.White)
                }
            }
        }
    }
}
