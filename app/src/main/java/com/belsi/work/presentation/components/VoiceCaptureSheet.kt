package com.belsi.work.presentation.components

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * FIX(2026-05-10) BELSI 1.3.0: Голосовой ввод с транскрипцией через XeroCode → Whisper.
 *
 * UX (push-to-talk модель):
 * 1. Юзер тапает кнопку 🎙
 * 2. Открывается диалог с большой красной кнопкой записи
 * 3. Тап → запись начинается (анимированный кружок-пульс)
 * 4. Тап ещё раз → запись останавливается
 * 5. Audio шлётся на /shift/voice/transcribe
 * 6. Получаем текст → показываем его юзеру
 * 7. Юзер: ✓ принять (вставить текст в поле) или ✕ отменить (записать заново)
 *
 * Контексты использования (передаём как параметр):
 * - "idle": причина простоя
 * - "material": заявка на материал
 * - "photo_comment": комментарий к фото
 * - "general": общий текст
 *
 * Audio-формат: m4a (AAC), оптимально для Whisper.
 */

@HiltViewModel
class VoiceCaptureViewModel @Inject constructor(
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceCaptureState())
    val state: StateFlow<VoiceCaptureState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(context: Context) {
        try {
            val file = File.createTempFile("voice_", ".m4a", context.cacheDir)
            outputFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(16000)
            rec.setAudioEncodingBitRate(64_000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()

            recorder = rec
            _state.value = VoiceCaptureState(recording = true)
        } catch (e: Exception) {
            android.util.Log.e("VoiceCapture", "startRecording failed", e)
            _state.value = VoiceCaptureState(error = "Не удалось начать запись: ${e.message}")
        }
    }

    fun stopAndTranscribe(context: String = "general") {
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
        } catch (e: Exception) {
            android.util.Log.w("VoiceCapture", "stop failed (audio too short?)", e)
        }

        val file = outputFile
        if (file == null || !file.exists() || file.length() < 1000) {
            _state.value = VoiceCaptureState(error = "Слишком короткая запись")
            return
        }

        _state.value = _state.value.copy(recording = false, transcribing = true)

        viewModelScope.launch {
            val bytes = file.readBytes()
            aiRepository.transcribeVoice(
                audioBytes = bytes,
                filename = "voice.m4a",
                mime = "audio/mp4",
                language = "ru",
                context = context,
            ).onSuccess { response ->
                _state.value = VoiceCaptureState(
                    transcribedText = response.text,
                    transcribing = false,
                )
                file.delete()
            }.onFailure {
                _state.value = VoiceCaptureState(
                    error = "Не удалось распознать: ${it.message}",
                    transcribing = false,
                )
            }
        }
    }

    fun cancel() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        outputFile?.delete()
        outputFile = null
        _state.value = VoiceCaptureState()
    }

    fun reset() {
        _state.value = VoiceCaptureState()
        outputFile?.delete()
        outputFile = null
    }
}

data class VoiceCaptureState(
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    val transcribedText: String? = null,
    val error: String? = null,
)

@Composable
fun VoiceCaptureSheet(
    visible: Boolean,
    contextHint: String = "general",  // idle / material / photo_comment / general
    onDismiss: () -> Unit,
    onTextReady: (String) -> Unit,
    viewModel: VoiceCaptureViewModel = hiltViewModel(),
) {
    if (!visible) return

    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) viewModel.startRecording(context)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.cancel() }
    }

    Dialog(
        onDismissRequest = {
            viewModel.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "🎙 Голосовой ввод",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when (contextHint) {
                        "idle" -> "Назовите причину простоя"
                        "material" -> "Назовите материал и количество"
                        "photo_comment" -> "Опишите фото"
                        else -> "Скажите что нужно"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))

                when {
                    state.error != null -> {
                        Text(
                            state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.reset() }) {
                            Text("Попробовать снова")
                        }
                    }
                    state.transcribing -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("AI распознаёт речь…", fontSize = 13.sp)
                    }
                    state.transcribedText != null -> {
                        // Показываем результат
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                state.transcribedText ?: "",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // FIX(2026-05-12) build19 hotfix: "XeroCode" убран из UI.
                        Text(
                            "AI processing",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.reset() }) {
                                Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Заново")
                            }
                            Button(
                                onClick = {
                                    onTextReady(state.transcribedText ?: "")
                                    viewModel.reset()
                                    onDismiss()
                                },
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Принять")
                            }
                        }
                    }
                    state.recording -> {
                        // Большая красная кнопка stop
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .clickable { viewModel.stopAndTranscribe(contextHint) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                "Стоп",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("● Запись… Тап чтобы остановить", fontSize = 13.sp, color = Color(0xFFEF4444))
                    }
                    else -> {
                        // Большая кнопка start
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    if (permissionGranted) viewModel.startRecording(context)
                                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                "Запись",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Тап чтобы начать запись", fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        viewModel.cancel()
                        onDismiss()
                    },
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Отмена", fontSize = 12.sp)
                }
            }
        }
    }
}

