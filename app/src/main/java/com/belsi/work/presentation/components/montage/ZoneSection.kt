package com.belsi.work.presentation.components.montage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.StageStatus

/**
 * Секция зоны: код зоны + погонные метры + кол-во кабинетов, затем слот с кабинетами. Molecule.
 * Используется в Tab «Этажи». Кабинеты передаются через [content] (CabinetCard).
 */
@Composable
fun ZoneSection(
    zoneCode: String,
    cabinetCount: Int,
    modifier: Modifier = Modifier,
    totalPm: Double? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Зона $zoneCode",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val meta = buildString {
                if (totalPm != null) append("${"%.1f".format(totalPm)} п.м. · ")
                append(plural(cabinetCount, "кабинет", "кабинета", "кабинетов"))
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ZoneSectionPreview() {
    BelsiWorkTheme {
        ZoneSection(zoneCode = "А1", cabinetCount = 4, totalPm = 8.2, modifier = Modifier.padding(16.dp)) {
            CabinetCard(
                cabinetNumber = "101/1",
                totalLengthMm = 11380,
                readyKeys = setOf("ready_windows", "ready_otkosy"),
                windows = listOf(
                    WindowUi(1, 2322, StageStatus.DONE, StageStatus.IN_PROGRESS, StageStatus.NOT_STARTED),
                    WindowUi(2, 2319, StageStatus.DONE, StageStatus.DONE, StageStatus.NOT_STARTED),
                ),
            )
        }
    }
}
