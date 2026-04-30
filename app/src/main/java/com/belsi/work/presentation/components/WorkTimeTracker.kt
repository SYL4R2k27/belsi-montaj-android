package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Компонент для отображения времени работы с момента входа в приложение
 * Для куратора и бригадира
 */
@Composable
fun WorkTimeTracker(
    startTimeMillis: Long,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    // Обновляем каждую секунду
    LaunchedEffect(startTimeMillis) {
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
            delay(1000)
        }
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    if (compact) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
        ) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            Text(
                text = timeString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
        }
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Время в приложении",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Объект для хранения времени запуска приложения (singleton)
 */
object AppSessionTracker {
    private var sessionStartTime: Long = 0L

    fun startSession() {
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
        }
    }

    fun getSessionStartTime(): Long {
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
        }
        return sessionStartTime
    }

    fun resetSession() {
        sessionStartTime = 0L
    }
}
