package com.belsi.work.presentation.components.montage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.StageType
import com.belsi.work.presentation.theme.belsiColors

/**
 * Прогресс объекта по 3 этапам (🔨/🪵/🛡) одной полоской из 3 сегментов. Molecule.
 * Используется в Tab «AI» курaтора (4.4) — видно, где «застрял» этап.
 *
 * [karkasPct]/[podokonnikPct]/[ekranPct] — доли 0f..1f готовности соответствующего этапа.
 */
@Composable
fun StageProgressBar(
    karkasPct: Float,
    podokonnikPct: Float,
    ekranPct: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Segment(StageType.KARKAS, karkasPct, Modifier.weight(1f))
        Segment(StageType.PODOKONNIK, podokonnikPct, Modifier.weight(1f))
        Segment(StageType.EKRAN, ekranPct, Modifier.weight(1f))
    }
}

@Composable
private fun Segment(stage: StageType, pct: Float, modifier: Modifier = Modifier) {
    val belsi = MaterialTheme.belsiColors
    val color: Color = when {
        pct >= 1f -> belsi.ai
        pct >= 0.5f -> belsi.success
        pct >= 0.15f -> belsi.warning
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val onColor = if (pct >= 0.15f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier.background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${stage.emoji} ${(pct * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = onColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxSize().padding(top = 1.dp),
        )
    }
}

@Preview(name = "StageProgressBar")
@Composable
private fun StageProgressBarPreview() {
    BelsiWorkTheme {
        StageProgressBar(
            karkasPct = 0.67f,
            podokonnikPct = 0.28f,
            ekranPct = 0.10f,
            modifier = Modifier.padding(16.dp),
        )
    }
}
