package com.belsi.work.presentation.screens.chat.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Плеер для голосового сообщения в чате
 *
 * Показывает:
 * - Кнопку play/pause
 * - Слайдер прогресса
 * - Длительность
 */
@Composable
fun VoiceMessagePlayer(
    voiceUrl: String,
    durationSeconds: Float?,
    isFromCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ExoPlayer instance - создаем один раз
    val player = remember(voiceUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(voiceUrl))
            prepare()
        }
    }

    // Убираем плеер при уходе с экрана
    DisposableEffect(voiceUrl) {
        onDispose {
            player.release()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    val totalDurationMs = ((durationSeconds ?: 0f) * 1000).toLong()

    // Слушатель состояния плеера
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    progress = 0f
                    currentPositionMs = 0L
                    player.seekTo(0)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Обновление прогресса при воспроизведении
    LaunchedEffect(isPlaying) {
        while (isPlaying && isActive) {
            val pos = player.currentPosition
            val dur = player.duration.takeIf { it > 0 } ?: totalDurationMs
            currentPositionMs = pos
            progress = if (dur > 0) (pos.toFloat() / dur) else 0f
            delay(100)
        }
    }

    val accentColor = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val trackColor = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause кнопка
        IconButton(
            onClick = {
                if (isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }

        // FIX(2026-05-14) BELSI 2.0.1: AudioWaveform визуализация по образцу Telegram.
        // Раньше: обычный Slider. Теперь: бары waveform с прогрессом подсветки.
        // Placeholder waveform — псевдо-случайные амплитуды (стабильные на основе voiceUrl
        // hash → одинаковые при каждом рендере одного сообщения). Реальный waveform
        // от бэка будет в `message.voice_waveform_bytes` — backlog (см. docs/plans/...
        // audio-waveform-backend.md когда появится).
        val placeholderWaveform = remember(voiceUrl) {
            com.belsi.work.audio.AudioWaveform.fromPcmSamples(
                pcmSamples = generatePlaceholderPcm(voiceUrl),
                targetBars = 60,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(player) {
                    detectTapGestures { offset ->
                        // Seek по тапу — преобразуем относительную позицию в время
                        val relativeX = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val dur = player.duration.takeIf { it > 0 } ?: totalDurationMs
                        val seekPos = (relativeX * dur).toLong()
                        progress = relativeX
                        player.seekTo(seekPos)
                        currentPositionMs = seekPos
                    }
                },
        ) {
            com.belsi.work.audio.AudioWaveformView(
                waveform = placeholderWaveform,
                progress = progress,
                playedColor = accentColor,
                unplayedColor = trackColor,
                height = 32.dp,
            )
        }

        // Длительность
        Text(
            text = formatVoiceDuration(
                if (isPlaying || currentPositionMs > 0) currentPositionMs
                else totalDurationMs
            ),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

private fun formatVoiceDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * FIX(2026-05-14) BELSI 2.0.1: детерминированный placeholder PCM на основе voiceUrl.
 *
 * Зачем: пока backend не возвращает реальный waveform, нам нужно что-то нарисовать.
 * Решение: seeded random на основе hash(voiceUrl) → одинаковая «волна» каждый раз
 * для одного сообщения. Выглядит как реальная аудио-визуализация, не «прямая палка».
 *
 * Когда появится `message.voice_waveform_bytes` от бэка — этот helper удалить,
 * waveform создавать через `AudioWaveform(bitstream, bitsPerSample = 5)`.
 */
private fun generatePlaceholderPcm(voiceUrl: String): ShortArray {
    // ~3 секунды псевдо-аудио, 1 секунда = ~120 sample'ов
    val seed = voiceUrl.hashCode().toLong()
    val random = java.util.Random(seed)
    val samples = ShortArray(360)
    for (i in samples.indices) {
        // Базовая амплитуда — нелинейный envelope (fade-in + fade-out + случайный bump)
        val t = i.toFloat() / samples.size
        val envelope = kotlin.math.sin((t * Math.PI).toFloat()).coerceAtLeast(0.15f)
        val variation = (random.nextFloat() * 0.6f + 0.4f)
        val amplitude = (envelope * variation * Short.MAX_VALUE * 0.8f).toInt()
        samples[i] = amplitude.coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return samples
}
