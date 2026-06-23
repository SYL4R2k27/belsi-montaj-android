package com.belsi.work.presentation.screens.siteobject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.belsi.work.data.repositories.CabinetNode
import com.belsi.work.presentation.components.montage.ReadinessChecklist
import com.belsi.work.presentation.theme.CabinetStatus
import com.belsi.work.presentation.theme.belsiColors
import com.belsi.work.presentation.theme.cabinetStatusColor

/**
 * Детали кабинета (упрощённая модель 2.1.0): параметры + готовность 7/7 + СТАТУС КАБИНЕТА
 * с кнопками (Не начат / Начат / Закончен / Проблема). «Проблема» — с обязательным комментарием.
 * Любая роль может менять. Детализация по окнам убрана.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CabinetDetailSheet(
    node: CabinetNode,
    onDismiss: () -> Unit,
    onSetStatus: (status: String, comment: String?) -> Unit,
    canAccrue: Boolean = false,
    onAccrue: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cab = node.cabinet
    val current = CabinetStatus.fromDb(cab.status)
    var problemDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Кабинет ${cab.cabinetNumber}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    StatusBadge(current)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        Param(if (cab.totalLengthMm != null) "%.2f м".format(cab.totalLengthMm / 1000.0) else "—", "Длина")
                        Param("${node.windows.size}", "Окон")
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.belsiColors.successContainer)) {
                    ReadinessChecklist(readyKeys = cab.readyKeys(), modifier = Modifier.padding(12.dp))
                }
            }
            if (!cab.statusComment.isNullOrBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text("⚠ ${cab.statusComment}", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            // кнопки статуса
            item {
                Text("Статус кабинета", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusButton("Начат", CabinetStatus.IN_PROGRESS, current, MaterialTheme.belsiColors.warning, Modifier.weight(1f)) { onSetStatus("in_progress", null) }
                    StatusButton("Закончен", CabinetStatus.DONE, current, MaterialTheme.belsiColors.success, Modifier.weight(1f)) { onSetStatus("done", null) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { problemDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("🔴 Проблема") }
                    OutlinedButton(onClick = { onSetStatus("not_started", null) }, modifier = Modifier.weight(1f)) {
                        Text("Сбросить")
                    }
                }
            }
            // FIX(2026-06-15) Ф2: куратор/координатор — принять кабинет и начислить бригаде
            if (canAccrue) {
                item {
                    Button(
                        onClick = onAccrue,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.belsiColors.success),
                    ) { Text("Принять кабинет → начислить", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (problemDialog) {
        var comment by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { problemDialog = false },
            title = { Text("Проблема с кабинетом") },
            text = {
                Column {
                    Text("Опишите, почему не готов к монтажу:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = comment, onValueChange = { comment = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = { problemDialog = false; onSetStatus("problem", comment.ifBlank { "Проблема" }) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Отметить проблему") }
            },
            dismissButton = { TextButton(onClick = { problemDialog = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun StatusButton(label: String, target: CabinetStatus, current: CabinetStatus, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val active = current == target
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (active) ButtonDefaults.buttonColors(containerColor = color)
        else ButtonDefaults.outlinedButtonColors(),
        border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, color),
    ) {
        Text(label, color = if (active) Color.White else color)
    }
}

@Composable
private fun StatusBadge(status: CabinetStatus) {
    val color = cabinetStatusColor(status)
    if (color == null) {
        Text(status.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(status.label, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Param(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
