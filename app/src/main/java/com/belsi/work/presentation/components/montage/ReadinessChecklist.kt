package com.belsi.work.presentation.components.montage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.READINESS_TOTAL
import com.belsi.work.presentation.theme.ReadinessChecklistItems
import com.belsi.work.presentation.theme.belsiColors

/**
 * 7-пунктовый чеклист готовности помещения (окна/откосы/радиаторы/трубы/плинтус/стены/пол).
 * Molecule. Состояние снаружи: [readyKeys] = множество ключей ready_*, которые = true.
 *
 * Используется на карточке кабинета (coordinator/curator); installer видит read-only.
 */
@Composable
fun ReadinessChecklist(
    readyKeys: Set<String>,
    modifier: Modifier = Modifier,
    showItems: Boolean = true,
) {
    val readyCount = ReadinessChecklistItems.count { it.key in readyKeys }
    val allReady = readyCount == READINESS_TOTAL

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Готовность",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$readyCount/$READINESS_TOTAL",
                style = MaterialTheme.typography.labelLarge,
                color = if (allReady) MaterialTheme.belsiColors.success else MaterialTheme.belsiColors.warning,
            )
        }

        if (showItems) {
            // 2 колонки
            ReadinessChecklistItems.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { item ->
                        ReadinessRow(
                            label = item.label,
                            ready = item.key in readyKeys,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) {
                        // выравниваем последний нечётный пункт
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessRow(label: String, ready: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ready) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (ready) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp).padding(end = 4.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (ready) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Readiness 4/7")
@Composable
private fun ReadinessPreview() {
    BelsiWorkTheme {
        ReadinessChecklist(
            readyKeys = setOf("ready_windows", "ready_otkosy", "ready_radiators", "ready_plinth"),
            modifier = Modifier.padding(16.dp),
        )
    }
}
