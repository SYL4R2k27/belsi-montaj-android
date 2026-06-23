package com.belsi.work.presentation.screens.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.local.database.entities.PendingActionEntity
import com.belsi.work.data.offline.OfflineQueueRepository
import com.belsi.work.data.offline.PhotoFileStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * FIX(2026-05-05): Экран «Ожидают отправки» — список pending-действий.
 *
 * Показывается:
 * - тип действия (📷 фото / ⏸ простой / ⏵ старт смены / ...)
 * - время создания
 * - количество попыток
 * - lastError если есть
 *
 * Действия:
 * - 🔁 retry для failed
 * - 🗑 cancel (удалить из очереди)
 *
 * Если очередь пуста — empty-state «Всё отправлено».
 */
@HiltViewModel
class PendingActionsViewModel @Inject constructor(
    private val queue: OfflineQueueRepository,
    private val photoStorage: PhotoFileStorage,
) : ViewModel() {

    val actions: StateFlow<List<PendingActionEntity>> = queue.allActions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun retry(id: Long) = viewModelScope.launch { queue.retry(id) }
    fun cancel(id: Long) = viewModelScope.launch { queue.cancel(id) }
    fun retryAll() = viewModelScope.launch { queue.scheduleWorker() }

    fun storageStats(): String {
        val mb = photoStorage.totalSize() / 1024.0 / 1024.0
        return "Фото на диске: ${photoStorage.count()} · ${"%.1f".format(mb)} МБ"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingActionsScreen(
    navController: NavController,
    viewModel: PendingActionsViewModel = hiltViewModel(),
) {
    val list by viewModel.actions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ожидают отправки") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (list.any { it.status == "failed" || it.status == "pending" }) {
                        IconButton(onClick = { viewModel.retryAll() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Запустить отправку")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (list.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 64.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Всё отправлено",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Все ваши действия успешно дошли до сервера",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        viewModel.storageStats(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Сводка вверху
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFEF3C7),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${list.size} действий ждут отправки",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF92400E),
                        )
                        Text(
                            viewModel.storageStats(),
                            fontSize = 11.sp,
                            color = Color(0xFFB45309),
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list) { item ->
                    PendingActionRow(
                        item = item,
                        onRetry = { viewModel.retry(item.id) },
                        onCancel = { viewModel.cancel(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingActionRow(
    item: PendingActionEntity,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val (emoji, label) = describeAction(item.actionType)
    val statusColor = when (item.status) {
        "failed" -> Color(0xFFF43F5E)
        "sending" -> Color(0xFFFBBF24)
        else -> Color(0xFF6366F1)
    }
    val statusLabel = when (item.status) {
        "failed" -> "Не отправлено"
        "sending" -> "Отправляется…"
        else -> "Ждёт сеть"
    }

    Card {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        formatTime(item.createdAt),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(statusLabel, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }
            if (item.retries > 0 || item.lastError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Попыток: ${item.retries}${item.lastError?.let { " · $it" } ?: ""}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.status == "failed") {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Повторить", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onCancel) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Удалить", fontSize = 12.sp)
                }
            }
        }
    }
}

// BELSI 2.1.0: единый маппинг на все 18 типов — в presentation.components (OfflineChip.kt).
private fun describeAction(actionType: String): Pair<String, String> =
    com.belsi.work.presentation.components.describePendingAction(actionType)

private fun formatTime(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    return when {
        diff < 60_000 -> "только что"
        diff < 3600_000 -> "${diff / 60_000} мин назад"
        diff < 86400_000 -> "${diff / 3600_000} ч назад"
        else -> SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(epochMillis))
    }
}
