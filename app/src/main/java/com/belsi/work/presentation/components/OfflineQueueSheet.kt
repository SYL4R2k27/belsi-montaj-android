package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.local.database.entities.PendingActionEntity
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.screens.offline.PendingActionsViewModel
import com.belsi.work.presentation.theme.Emerald500

/**
 * BELSI 2.1.0 — bottom-sheet «Очередь отправки» (Flow 6, master-spec §11).
 * Открывается тапом по OfflineChip когда есть очередь или офлайн.
 * Переиспользует PendingActionsViewModel (retry/cancel/retryAll).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineQueueSheet(
    onDismiss: () -> Unit,
    navController: NavController? = null,
    viewModel: PendingActionsViewModel = hiltViewModel(),
) {
    val list by viewModel.actions.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Очередь отправки",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (list.any { it.status == "failed" || it.status == "pending" }) {
                    TextButton(onClick = { viewModel.retryAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Повторить всё", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (list.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Emerald500,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Всё отправлено", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Нет действий, ожидающих сети",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Text(
                    "${list.size} действий ждут отправки",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id }) { item ->
                        QueueRow(
                            item = item,
                            onRetry = { viewModel.retry(item.id) },
                            onCancel = { viewModel.cancel(item.id) },
                        )
                    }
                }

                if (navController != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            onDismiss()
                            navController.navigate(AppRoute.PendingActions.route)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Открыть полный экран", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: PendingActionEntity,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val (emoji, label) = describePendingAction(item.actionType)
    val statusColor = pendingStatusColor(item.status)
    val statusText = pendingStatusLabel(item.status)

    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(formatQueuedTime(item.createdAt), fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(statusText, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }
            if (item.retries > 0 || item.lastError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Попыток: ${item.retries}${item.lastError?.let { " · $it" } ?: ""}",
                    fontSize = 11.sp,
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
                    Text(if (item.status == "failed") "Удалить" else "Отменить", fontSize = 12.sp)
                }
            }
        }
    }
}
