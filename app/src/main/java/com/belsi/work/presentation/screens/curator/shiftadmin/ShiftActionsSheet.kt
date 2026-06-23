package com.belsi.work.presentation.screens.curator.shiftadmin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.belsi.work.data.remote.api.AdminShiftDto
import com.belsi.work.data.remote.api.ShiftAuditEntry
import com.belsi.work.presentation.theme.belsiColors
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/**
 * Bottom sheet с тремя действиями над сменой:
 *   ⤴ Re-open (только для finished)
 *   ✏ Edit time
 *   📜 Audit log
 *
 * Все мутации проходят через server-side audit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftActionsSheet(
    shift: AdminShiftDto,
    isProcessing: Boolean,
    audit: List<ShiftAuditEntry>?,
    onDismiss: () -> Unit,
    onReopen: (reason: String?) -> Unit,
    onEdit: (startIso: String?, finishIso: String?, reason: String?) -> Unit,
    onLoadAudit: () -> Unit,
) {
    var mode by remember { mutableStateOf(SheetMode.MENU) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                "Управление сменой",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "ID: ${shift.id.take(8)}…   Статус: ${shift.status}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            when (mode) {
                SheetMode.MENU -> MenuMode(
                    shift = shift,
                    onReopen = { mode = SheetMode.REOPEN },
                    onEdit = { mode = SheetMode.EDIT },
                    onAudit = {
                        onLoadAudit()
                        mode = SheetMode.AUDIT
                    },
                )
                SheetMode.REOPEN -> ReopenMode(
                    isProcessing = isProcessing,
                    onConfirm = { reason -> onReopen(reason); mode = SheetMode.MENU },
                    onBack = { mode = SheetMode.MENU },
                )
                SheetMode.EDIT -> EditMode(
                    shift = shift,
                    isProcessing = isProcessing,
                    onConfirm = { s, f, reason -> onEdit(s, f, reason); mode = SheetMode.MENU },
                    onBack = { mode = SheetMode.MENU },
                )
                SheetMode.AUDIT -> AuditMode(
                    audit = audit,
                    onBack = { mode = SheetMode.MENU },
                )
            }
        }
    }
}

private enum class SheetMode { MENU, REOPEN, EDIT, AUDIT }

@Composable
private fun MenuMode(
    shift: AdminShiftDto,
    onReopen: () -> Unit,
    onEdit: () -> Unit,
    onAudit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (shift.status != "active") {
            ActionRow(
                icon = Icons.Default.LockOpen,
                title = "Re-open смену",
                subtitle = "Перевести из finished в active",
                accent = MaterialTheme.belsiColors.success,
                onClick = onReopen,
            )
        }
        ActionRow(
            icon = Icons.Default.Schedule,
            title = "Изменить время",
            subtitle = "Старт / финиш / объект",
            accent = MaterialTheme.colorScheme.primary,
            onClick = onEdit,
        )
        ActionRow(
            icon = Icons.Default.History,
            title = "История изменений",
            subtitle = if (shift.auditCount > 0) "${shift.auditCount} записей" else "Нет записей",
            accent = MaterialTheme.colorScheme.tertiary,
            onClick = onAudit,
        )
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.08f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = accent)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReopenMode(
    isProcessing: Boolean,
    onConfirm: (reason: String?) -> Unit,
    onBack: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Re-open смены",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Смена будет переведена обратно в статус active. " +
                "Время finish обнулится. Изменение будет записано в журнал.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Причина (опционально)") },
            placeholder = { Text("Например: ошибочно нажал Завершить") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            enabled = !isProcessing,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), enabled = !isProcessing) {
                Text("Отмена")
            }
            Button(
                onClick = { onConfirm(reason.trim().ifBlank { null }) },
                modifier = Modifier.weight(1f),
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.belsiColors.success,
                ),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else Text("Re-open")
            }
        }
    }
}

@Composable
private fun EditMode(
    shift: AdminShiftDto,
    isProcessing: Boolean,
    onConfirm: (startIso: String?, finishIso: String?, reason: String?) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var newStart by remember(shift.id) { mutableStateOf(shift.startAt) }
    var newFinish by remember(shift.id) { mutableStateOf(shift.finishAt) }
    var reason by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Изменение времени смены",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Text(
            "Тапните по полю чтобы выбрать дату/время. Пустое поле = не менять.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Start
        DateTimeField(
            label = "Старт",
            iso = newStart,
            onChange = { newStart = it },
            context = context,
        )

        // Finish (показываем только если есть; если хочется завершить — отдельный flow)
        DateTimeField(
            label = "Финиш",
            iso = newFinish,
            onChange = { newFinish = it },
            context = context,
        )

        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Причина изменения") },
            placeholder = { Text("Например: монтажник забыл запустить таймер") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            enabled = !isProcessing,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), enabled = !isProcessing) {
                Text("Отмена")
            }
            Button(
                onClick = {
                    val s = if (newStart != shift.startAt) newStart else null
                    val f = if (newFinish != shift.finishAt) newFinish else null
                    if (s == null && f == null) return@Button
                    onConfirm(s, f, reason.trim().ifBlank { null })
                },
                modifier = Modifier.weight(1f),
                enabled = !isProcessing &&
                    (newStart != shift.startAt || newFinish != shift.finishAt),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else Text("Сохранить")
            }
        }
    }
}

@Composable
private fun DateTimeField(
    label: String,
    iso: String?,
    onChange: (String?) -> Unit,
    context: android.content.Context,
) {
    val parsed = remember(iso) {
        try {
            iso?.let {
                OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            }
        } catch (_: Exception) { null }
    }
    val display = parsed?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) ?: "—"

    val openPicker = {
        val cal = Calendar.getInstance().apply {
            parsed?.let {
                set(it.year, it.monthValue - 1, it.dayOfMonth, it.hour, it.minute)
            }
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val ldt = LocalDateTime.of(year, month + 1, day, hour, minute)
                        val newIso = ldt.atZone(ZoneId.systemDefault())
                            .toOffsetDateTime()
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        onChange(newIso)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = openPicker,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(display, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            if (iso != null) {
                TextButton(onClick = { onChange(null) }) { Text("Очистить") }
            }
        }
    }
}

@Composable
private fun AuditMode(
    audit: List<ShiftAuditEntry>?,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            Text("История", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
        }

        when {
            audit == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            audit.isEmpty() -> Text(
                "Нет записей в журнале",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(audit, key = { it.id }) { AuditRow(it) }
            }
        }
    }
}

@Composable
private fun AuditRow(entry: ShiftAuditEntry) {
    val actionLabel = when (entry.action) {
        "reopen" -> "↪ Re-open"
        "edit_time" -> "✏ Время"
        "edit_object" -> "📍 Объект"
        "edit_both" -> "✏📍 Время + объект"
        "finish" -> "✓ Завершено"
        "delete" -> "✗ Удалено"
        else -> entry.action
    }
    val ts = try {
        entry.createdAt?.let {
            OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale("ru")))
        }
    } catch (_: Exception) { entry.createdAt?.take(16) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actionLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(ts ?: "—", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${entry.performedByName ?: "?"} (${entry.performedByRole ?: ""})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.reason?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text("«$it»", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
