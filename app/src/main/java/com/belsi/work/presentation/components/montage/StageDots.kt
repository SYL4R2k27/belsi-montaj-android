package com.belsi.work.presentation.components.montage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.StageStatus
import com.belsi.work.presentation.theme.stageStatusColor

/**
 * Одна цветная точка статуса этапа (🟢🟡⚪🟣). Atom.
 * Используется в гриде окна (installer 1.3, coordinator 3.2, curator 4.3).
 */
@Composable
fun StageDot(
    status: StageStatus,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(stageStatusColor(status)),
    )
}

/**
 * Ряд из 3 точек: каркас / подоконник / экран. Atom.
 * Порядок фиксированный (k → p → e).
 */
@Composable
fun StageDotsRow(
    karkas: StageStatus,
    podokonnik: StageStatus,
    ekran: StageStatus,
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    spacing: Dp = 3.dp,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        StageDot(karkas, size = dotSize)
        StageDot(podokonnik, size = dotSize)
        StageDot(ekran, size = dotSize)
    }
}

@Preview(name = "StageDotsRow — light")
@Composable
private fun StageDotsRowPreview() {
    BelsiWorkTheme {
        StageDotsRow(
            karkas = StageStatus.DONE,
            podokonnik = StageStatus.IN_PROGRESS,
            ekran = StageStatus.NOT_STARTED,
            dotSize = 12.dp,
        )
    }
}
