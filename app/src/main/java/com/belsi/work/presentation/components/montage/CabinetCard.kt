package com.belsi.work.presentation.components.montage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.CabinetStatus
import com.belsi.work.presentation.theme.READINESS_TOTAL
import com.belsi.work.presentation.theme.ReadinessChecklistItems
import com.belsi.work.presentation.theme.StageStatus
import com.belsi.work.presentation.theme.belsiColors
import com.belsi.work.presentation.theme.cabinetStatusColor

/** Лёгкая UI-модель окна (для совместимости/счётчика; детализация по окнам в UI убрана). */
data class WindowUi(
    val number: Int,
    val widthMm: Int? = null,
    val karkas: StageStatus = StageStatus.NOT_STARTED,
    val podokonnik: StageStatus = StageStatus.NOT_STARTED,
    val ekran: StageStatus = StageStatus.NOT_STARTED,
)

/**
 * Карточка кабинета (упрощённая модель 2.1.0): цвет по статусу кабинета целиком
 * (не начат — без цвета, начат — 🟡, закончен — 🟢, проблема — 🔴) + готовность.
 * Детализация по окнам убрана (зашли → делают полностью).
 */
@Composable
fun CabinetCard(
    cabinetNumber: String,
    windows: List<WindowUi>,
    modifier: Modifier = Modifier,
    totalLengthMm: Int? = null,
    readyKeys: Set<String> = emptySet(),
    status: CabinetStatus = CabinetStatus.NOT_STARTED,
) {
    val readyCount = ReadinessChecklistItems.count { it.key in readyKeys }
    val belsi = MaterialTheme.belsiColors
    val statusColor: Color? = cabinetStatusColor(status)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor?.copy(alpha = 0.10f) ?: MaterialTheme.colorScheme.surface,
        ),
        border = if (statusColor != null) BorderStroke(1.5.dp, statusColor) else CardDefaults.outlinedCardBorder(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Кабинет $cabinetNumber", style = MaterialTheme.typography.titleSmall)
                CabinetStatusChip(status, statusColor)
            }
            val sub = buildString {
                if (totalLengthMm != null) append("Длина ${"%.2f".format(totalLengthMm / 1000.0)} м · ")
                append("${windows.size} окон")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "готовность $readyCount/$READINESS_TOTAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (readyCount == READINESS_TOTAL) belsi.success else belsi.warning,
                )
            }
        }
    }
}

@Composable
private fun CabinetStatusChip(status: CabinetStatus, color: Color?) {
    if (color == null) {
        Text("не начат", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(status.label, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun CabinetCardPreview() {
    BelsiWorkTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CabinetCard(cabinetNumber = "101/1", windows = List(4) { WindowUi(it + 1) }, totalLengthMm = 11380, readyKeys = setOf("ready_windows"), status = CabinetStatus.IN_PROGRESS)
            CabinetCard(cabinetNumber = "102", windows = List(2) { WindowUi(it + 1) }, totalLengthMm = 8366, status = CabinetStatus.DONE)
            CabinetCard(cabinetNumber = "103", windows = List(3) { WindowUi(it + 1) }, status = CabinetStatus.PROBLEM)
        }
    }
}
