package com.belsi.work.presentation.screens.chat.components

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Полноэкранный просмотрщик фото с зумом и свайпом.
 *
 * Функционал:
 * - Pinch-to-zoom (до 5x)
 * - Double-tap для зума/сброса
 * - Drag для перемещения при зуме
 * - Кнопка "Сохранить" в галерею
 * - Кнопка "Закрыть"
 */
@Composable
fun FullscreenPhotoViewer(
    photoUrl: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Фото с зумом и перетаскиванием
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Полноэкранное фото",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)

                            if (newScale > 1f) {
                                // Ограничиваем смещение при зуме
                                val maxX = (newScale - 1f) * size.width / 2f
                                val maxY = (newScale - 1f) * size.height / 2f
                                val newOffsetX = (offset.x + pan.x * newScale).coerceIn(-maxX, maxX)
                                val newOffsetY = (offset.y + pan.y * newScale).coerceIn(-maxY, maxY)
                                offset = Offset(newOffsetX, newOffsetY)
                            } else {
                                offset = Offset.Zero
                            }

                            scale = newScale
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                // Double-tap: переключение между 1x и 2.5x
                                if (scale > 1.5f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                            onTap = {
                                // Одиночный тап: показать/скрыть контролы
                                showControls = !showControls
                            }
                        )
                    },
                contentScale = ContentScale.Fit
            )

            // Контролы (кнопка закрытия и сохранения)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Кнопка закрытия (верхний правый угол)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .statusBarsPadding()
                            .size(48.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Кнопка сохранения (нижний правый угол)
                    IconButton(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                scope.launch {
                                    savePhotoToGallery(context, photoUrl)
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .navigationBarsPadding()
                            .size(48.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Сохранить в галерею",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Сохраняет фото по URL в галерею устройства.
 */
private suspend fun savePhotoToGallery(context: Context, photoUrl: String) {
    try {
        withContext(Dispatchers.IO) {
            val url = URL(photoUrl)
            val inputStream = url.openStream()
            val bytes = inputStream.readBytes()
            inputStream.close()

            val filename = "belsi_chat_${System.currentTimeMillis()}.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BelsiWork")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(bytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Фото сохранено в галерею", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show()
        }
    }
}
