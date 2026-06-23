package com.belsi.work.presentation.components.montage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.StageStatus
import com.belsi.work.presentation.theme.stageStatusColor
import com.belsi.work.presentation.theme.windowOverallStatus
import com.belsi.work.presentation.theme.windowProgress

/**
 * Карточка одного окна: № + ширина + 3 этапа (точки) + общий прогресс. Molecule.
 * Используется в гриде кабинета (installer 1.3, coordinator 3.2, curator 4.3).
 *
 * Stateless: статусы и onClick — снаружи.
 */
@Composable
fun WindowCard(
    windowNumber: Int,
    karkas: StageStatus,
    podokonnik: StageStatus,
    ekran: StageStatus,
    modifier: Modifier = Modifier,
    widthMm: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val progress = windowProgress(karkas, podokonnik, ekran)
    val overall = windowOverallStatus(karkas, podokonnik, ekran)

    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Окно $windowNumber",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = stageStatusColor(overall),
                )
            }

            if (widthMm != null) {
                Text(
                    text = "ширина ${"%.2f".format(widthMm / 1000.0)} м",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StageDotsRow(
                karkas = karkas,
                podokonnik = podokonnik,
                ekran = ekran,
                dotSize = 10.dp,
            )
        }
    }
}

@Preview(name = "WindowCard")
@Composable
private fun WindowCardPreview() {
    BelsiWorkTheme {
        WindowCard(
            windowNumber = 1,
            widthMm = 2322,
            karkas = StageStatus.DONE,
            podokonnik = StageStatus.IN_PROGRESS,
            ekran = StageStatus.NOT_STARTED,
            modifier = Modifier.padding(16.dp),
        )
    }
}
