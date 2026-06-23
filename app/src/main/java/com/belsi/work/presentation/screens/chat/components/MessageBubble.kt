package com.belsi.work.presentation.screens.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.belsi.work.data.models.ChatMessageDTO
import java.text.SimpleDateFormat
import java.util.*
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessageDTO,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.senderRole == "USER" || message.senderRole == "INSTALLER"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            // Аватар поддержки/куратора
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Headset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(60.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Message bubble
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .then(
                        if (onLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = onLongClick
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            // Timestamp
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatMessageTime(message),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            // Аватар пользователя
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Spacer(Modifier.width(60.dp))
        }
    }
}

private fun formatMessageTime(message: ChatMessageDTO): String {
    try {
        val messageTime = message.parsedDate ?: return message.createdAt
        val messageInstant = messageTime.toInstant()
        val now = OffsetDateTime.now(ZoneId.systemDefault()).toInstant()

        val diffMinutes = ChronoUnit.MINUTES.between(messageInstant, now)
        val diffHours = ChronoUnit.HOURS.between(messageInstant, now)
        val diffDays = ChronoUnit.DAYS.between(messageInstant, now)

        return when {
            diffMinutes < 1 -> "только что"
            diffMinutes < 60 -> "$diffMinutes мин назад"
            diffHours < 24 -> "$diffHours ч назад"
            diffDays == 0L -> {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                "Сегодня, ${formatter.format(Date.from(messageInstant))}"
            }
            diffDays == 1L -> {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                "Вчера, ${formatter.format(Date.from(messageInstant))}"
            }
            else -> {
                SimpleDateFormat("dd.MM.yy, HH:mm", Locale.getDefault())
                    .format(Date.from(messageInstant))
            }
        }
    } catch (e: Exception) {
        return message.createdAt
    }
}
