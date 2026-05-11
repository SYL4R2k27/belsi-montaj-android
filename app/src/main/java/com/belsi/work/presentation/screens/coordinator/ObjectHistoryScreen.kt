package com.belsi.work.presentation.screens.coordinator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/**
 * FIX(2026-05-05): История объекта — сквозная лента событий через все три домена.
 * Brandbook: координатор / куратор видит timeline объекта от создания партии
 * до сдачи монтажа. Каждое событие подсвечено цветом своего домена.
 *
 * Mock-режим до подключения backend timeline-эндпоинта.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectHistoryScreen(navController: NavController, objectId: String) {
    val events = mockEventsFor(objectId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("История объекта", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Школа №7, Коломенская 16", fontSize = 16.sp,
                        fontWeight = FontWeight.Bold)
                } },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Экспорт PDF")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Сводка в шапке
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Партий получено", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("4 / 5", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Установлено", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("3", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Готовность", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("60%", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Timeline
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(events) { e ->
                    TimelineItem(e)
                }
            }
        }
    }
}

private data class TimelineEvent(
    val time: String,
    val author: String,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val domainColor: Color,
)

@Composable
private fun TimelineItem(e: TimelineEvent) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        // Левая колонка — линия с точкой
        Column(
            modifier = Modifier.width(40.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .size(28.dp)
                    .background(e.domainColor, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    e.icon, contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        // Правая колонка — карточка события
        Card(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp).weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.time, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = e.domainColor)
                    Spacer(Modifier.width(8.dp))
                    Text("· ${e.author}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(e.description, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun mockEventsFor(objectId: String): List<TimelineEvent> {
    val production = Color(0xFFD97706)
    val logistics = Color(0xFF0EA5E9)
    val installation = Color(0xFF4F46E5)
    return listOf(
        TimelineEvent("04 мая 09:00", "Начальник производства", "Партия UGL-0042 создана",
            "20 подоконников, дедлайн 05.05 14:00", Icons.Default.Inventory, production),
        TimelineEvent("04 мая 18:30", "Начальник производства", "Партия готова к отгрузке",
            "Все 20 шт. собраны и упакованы", Icons.Default.CheckCircle, production),
        TimelineEvent("05 мая 06:30", "Логист Иванов", "Партия в маршруте",
            "Назначен водитель Петров, выезд 06:30", Icons.Default.LocalShipping, logistics),
        TimelineEvent("05 мая 13:50", "Водитель Петров", "Доставка на объекте",
            "Фото выгрузки прикреплены", Icons.Default.LocationOn, logistics),
        TimelineEvent("05 мая 14:00", "Бригадир Хрулёв", "Приёмка подтверждена",
            "Партия принята, начат монтаж", Icons.Default.Verified, installation),
        TimelineEvent("05 мая 14:15", "Монтажник Сидоров", "Смена начата",
            "Установка подоконников", Icons.Default.Build, installation),
        TimelineEvent("05 мая 17:00", "Бригадир Хрулёв", "Партия установлена",
            "20/20 шт. смонтированы. Часовые фото — 6 шт.", Icons.Default.Done, installation),
    )
}
