package com.belsi.work.presentation.components.montage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.belsiColors

/**
 * Прогресс партии материала: План / Отгружено / Осталось + полоса. Molecule.
 * Используется в Tab «Партии» (foreman/coordinator).
 */
@Composable
fun BatchProgressBar(
    label: String,
    planned: Int,
    shipped: Int,
    modifier: Modifier = Modifier,
    unit: String = "",
) {
    val belsi = MaterialTheme.belsiColors
    val remaining = (planned - shipped).coerceAtLeast(0)
    val fraction = if (planned > 0) (shipped.toFloat() / planned).coerceIn(0f, 1f) else 0f
    val done = remaining == 0 && planned > 0
    val barColor = if (done) belsi.success else MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$shipped / $planned${if (unit.isNotEmpty()) " $unit" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // полоса прогресса (ручная — без зависимости от версии M3 ProgressIndicator API)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
        }
        Text(
            text = if (done) "Отгружено полностью" else "Осталось: $remaining${if (unit.isNotEmpty()) " $unit" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = if (done) belsi.success else belsi.warning,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun BatchProgressBarPreview() {
    BelsiWorkTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BatchProgressBar(label = "Саморезы 12мм", planned = 11376, shipped = 4900, unit = "шт")
            BatchProgressBar(label = "Подоконник ЛДСП", planned = 120, shipped = 120, unit = "м")
        }
    }
}
