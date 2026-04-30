package com.belsi.work.presentation.screens.chat.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Кнопка записи голосового сообщения
 *
 * Поведение:
 * - Тап: начать запись, повторный тап: остановить и отправить
 * - Свайп влево при записи (drag > 100px): отмена записи
 */
@Composable
fun VoiceRecordButton(
    isRecording: Boolean,
    recordingDurationMs: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartRecording()
        }
    }

    // Пульсирующая анимация при записи
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Drag offset для свайпа влево
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val isCancelling = dragOffsetX < -100f

    if (isRecording) {
        // Режим записи: показываем таймер и кнопку стоп
        Row(
            modifier = modifier
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { _, dragAmount ->
                            dragOffsetX += dragAmount.x
                        },
                        onDragEnd = {
                            if (dragOffsetX < -100f) {
                                onCancelRecording()
                            }
                            dragOffsetX = 0f
                        },
                        onDragCancel = {
                            dragOffsetX = 0f
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Пульсирующая точка записи
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )

            // Таймер
            Text(
                text = formatDuration(recordingDurationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCancelling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            // Подсказка свайпа
            if (isCancelling) {
                Text(
                    text = "Отпустите для отмены",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = "< Свайп для отмены",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Кнопка стоп (отправить)
            IconButton(
                onClick = onStopRecording,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Остановить запись",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    } else {
        // Обычный режим: кнопка микрофона
        IconButton(
            onClick = {
                if (!enabled) return@IconButton
                val permission = Manifest.permission.RECORD_AUDIO
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    onStartRecording()
                } else {
                    permissionLauncher.launch(permission)
                }
            },
            enabled = enabled,
            modifier = modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Записать голосовое",
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Хелпер для управления записью голоса через MediaRecorder
 */
class VoiceRecorderHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    val isRecording: Boolean get() = recorder != null

    fun startRecording(): File? {
        try {
            val file = File.createTempFile("voice_", ".m4a", context.cacheDir)
            outputFile = file

            @Suppress("DEPRECATION")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mr
            startTimeMs = System.currentTimeMillis()
            return file
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorder", "Failed to start recording", e)
            cleanup()
            return null
        }
    }

    fun stopRecording(): Pair<File, Float>? {
        return try {
            recorder?.stop()
            val durationMs = System.currentTimeMillis() - startTimeMs
            val durationSec = durationMs / 1000f
            val file = outputFile
            cleanup()

            if (file != null && file.exists() && durationSec > 0.5f) {
                Pair(file, durationSec)
            } else {
                file?.delete()
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorder", "Failed to stop recording", e)
            cleanup()
            null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // ignore
        }
        outputFile?.delete()
        cleanup()
    }

    fun getElapsedMs(): Long {
        return if (isRecording) System.currentTimeMillis() - startTimeMs else 0L
    }

    private fun cleanup() {
        try {
            recorder?.release()
        } catch (_: Exception) {
            // ignore
        }
        recorder = null
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
