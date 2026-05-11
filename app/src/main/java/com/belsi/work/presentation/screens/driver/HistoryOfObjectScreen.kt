package com.belsi.work.presentation.screens.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HistoryOfObjectScreen(objectName: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Шапка объекта
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(objectName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Активный · 3 человека · 5 смен",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Фильтры
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            var selected by remember { mutableStateOf(0) }
            listOf("Все", "Доставки", "Монтаж", "Куратор").forEachIndexed { idx, label ->
                FilterChip(
                    selected = selected == idx,
                    onClick = { selected = idx },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        // Лента
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // Сегодня
            item { DayLabel("Сегодня · 04 мая") }
            items(DriverMockData.historyToday) { event -> TimelineItem(event) }

            item { DayLabel("Вчера · 03 мая") }
            items(DriverMockData.historyYesterday) { event -> TimelineItem(event) }
        }
    }
}

@Composable
private fun DayLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun TimelineItem(event: DriverMockData.HistoryEvent) {
    val dotColor = when (event.type) {
        DriverMockData.HistoryType.DELIVERY -> MaterialTheme.colorScheme.tertiary
        DriverMockData.HistoryType.SHIFT -> MaterialTheme.colorScheme.primary
        DriverMockData.HistoryType.PHOTO -> Color(0xFF0EA5E9) // sky-500
        DriverMockData.HistoryType.ADMIN -> Color(0xFF8B5CF6) // violet-500
        DriverMockData.HistoryType.CREATE -> MaterialTheme.colorScheme.secondary
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp).padding(top = 4.dp)) {
            Text(event.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Dot + line
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(12.dp).clip(CircleShape)
                    .background(dotColor)
                    .padding(2.dp)
            )
            Box(
                modifier = Modifier.width(2.dp).height(50.dp).background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(event.emoji, style = MaterialTheme.typography.titleMedium)
                    Text(event.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                }
                event.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
