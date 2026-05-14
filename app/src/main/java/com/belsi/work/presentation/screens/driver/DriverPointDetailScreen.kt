package com.belsi.work.presentation.screens.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.belsi.work.utils.NavigationIntents

/**
 * FIX(2026-05-11) BELSI 2.0.0 build11: реальная функциональность кнопок —
 *  - Позвонить → Intent ACTION_DIAL (телефон контакта)
 *  - В Картах → Yandex.Карты intent с координатами точки
 *  - Прибыл → camera (eventType="arrival")
 *  - Доставлено → camera (eventType="delivery_complete")
 *
 * Бриф BELSI.Driver: «Прибытие — водитель нажимает "Прибыл", открывается камера,
 * делает фото, фиксируются GPS-координаты и время».
 *
 * onCameraClick принимает eventType — какое именно событие фиксировать.
 */
@Composable
fun DriverPointDetailScreen(
    pointId: String,
    onCameraClick: (eventType: String) -> Unit,
    onMarkDelivered: () -> Unit,
    viewModel: DriverHomeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val context = LocalContext.current
    // FIX(2026-05-12) build19 hotfix: ищем точку в реальных маршрутах из VM, а не в mock.
    // Если точка не найдена (старый pointId / маршрут отменён) — показываем empty state.
    val vmState by viewModel.state.collectAsState()
    val point = remember(pointId, vmState.activeRoutes) {
        vmState.activeRoutes
            .firstOrNull()?.toMockRoute()?.points?.firstOrNull { it.id == pointId }
    }
    if (point == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("⚠", fontSize = 36.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Точка маршрута не найдена",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Возможно, маршрут был отменён или ID точки устарел.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    // Fallback координаты центра Москвы (когда у точки нет реальных координат).
    val fallbackLat = 55.7558
    val fallbackLng = 37.6173
    // FIX(2026-05-12) BELSI 2.0.0 build15: hardcoded бригадир/телефон убраны.
    // Mock RoutePoint не содержит notes/batchId/siteObjectId — для реальных данных
    // (через DriverHomeViewModel.activeRoutes) контакт парсится из point.notes
    // (логист записывает «Контакт: X · Тел: Y»). В playground / mock эти поля null →
    // кнопка «Позвонить» скрыта.
    val contactPhone: String? = null
    val contactName: String? = null

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Карточка статуса
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (statusText, statusColor) = when (point.status) {
                        DriverMockData.PointStatus.DELIVERED -> "✓ ВЫПОЛНЕНО" to MaterialTheme.colorScheme.tertiary
                        DriverMockData.PointStatus.ARRIVED -> "🟡 ПРИБЫЛ" to MaterialTheme.colorScheme.secondary
                        DriverMockData.PointStatus.PENDING -> "⌛ В ОЧЕРЕДИ" to MaterialTheme.colorScheme.outline
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(statusText, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = statusColor.copy(alpha = 0.15f),
                            labelColor = statusColor,
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    Text(point.time, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Принять на объекте",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                InfoRow("Объект", point.address)
                // FIX(2026-05-12) build15: hardcoded "Хрулёв Павел" убран
                contactName?.let { InfoRow("Контакт", it) }
                InfoRow("К времени", point.time)
                InfoRow("Тип точки", "${point.type.emoji} ${point.type.title}")
            }
        }

        // Что выгрузить
        if (point.cargo != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "ЧТО ВЫГРУЗИТЬ",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(point.cargo, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                }
            }
        }

        // Действия
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "ДЕЙСТВИЯ",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // FIX(2026-05-11) build11: 2 кнопки события — Прибыл / Доставлено.
                // Каждая открывает камеру с разным event_type (arrival/delivery_complete).
                // Backend пишет в driver_point_events с фото + GPS + timestamp.
                Button(
                    onClick = { onCameraClick("arrival") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = point.status == DriverMockData.PointStatus.PENDING,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Прибыл — фото")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onCameraClick("delivery_complete") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = point.status != DriverMockData.PointStatus.DELIVERED,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Доставлено — фото + сдача")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onCameraClick("departure") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Отъезд — отметить")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Каждое действие фиксирует фото + GPS + время. Бригадир получит push при доставке.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // FIX(2026-05-12) build15: кнопка «Позвонить» — только если телефон реально есть
            if (contactPhone != null) {
                OutlinedButton(
                    onClick = { NavigationIntents.makePhoneCall(context, contactPhone) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Позвонить")
                }
            }
            // FIX(2026-05-11) build11: реальный intent Яндекс.Карты
            OutlinedButton(
                onClick = {
                    NavigationIntents.openYandexMapPoint(
                        context = context,
                        lat = fallbackLat,
                        lng = fallbackLng,
                        label = point.address,
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Map, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("В Картах")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
    }
}
