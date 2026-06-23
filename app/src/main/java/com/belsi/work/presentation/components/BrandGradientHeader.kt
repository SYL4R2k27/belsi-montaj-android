package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.belsiColors

/**
 * Брендовый Steel-gradient hero (SteelTop → SteelDeep). Molecule.
 * Референс бренд-марки (splash/login/installer home). Белый текст поверх градиента.
 *
 * @param content доп. слот под заголовком (chips, статус смены и т.п.)
 */
@Composable
fun BrandGradientHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    cornerRadius: Int = 20,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val belsi = MaterialTheme.belsiColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                Brush.verticalGradient(
                    listOf(belsi.brandGradientTop, belsi.brandGradientBottom),
                ),
            )
            .padding(20.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        content()
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun BrandGradientHeaderPreview() {
    BelsiWorkTheme {
        BrandGradientHeader(
            title = "Малая Тульская 15",
            subtitle = "КАРЕ · 57 классов · бригада 4 чел",
            modifier = Modifier.padding(16.dp),
        )
    }
}
