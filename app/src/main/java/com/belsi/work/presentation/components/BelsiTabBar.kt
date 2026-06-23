package com.belsi.work.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme

/**
 * Сегментированный таб-бар (`tab-bar` из мока): pill-фон, активный сегмент — белая «таблетка».
 * Atom. Используется на главных экранах ролей (📊 Сейчас / 📷 Фото / 🔍 Объекты / 🤖 AI).
 *
 * Stateless: [selectedIndex] и [onSelect] — снаружи.
 */
@Composable
fun BelsiTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                label = "tabBg",
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onSelect(index) }
                    .background(bg)
                    .padding(vertical = 7.dp),
            )
        }
    }
}

@Preview(name = "BelsiTabBar")
@Composable
private fun BelsiTabBarPreview() {
    BelsiWorkTheme {
        BelsiTabBar(
            tabs = listOf("📊 Сейчас", "📷 Фото", "🔍 Объекты", "🤖 AI"),
            selectedIndex = 1,
            onSelect = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}
