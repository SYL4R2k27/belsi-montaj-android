package com.belsi.work.presentation.screens.logistician

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.belsi.work.presentation.screens.driver.DriverMockData

@Composable
fun LogistDriverDetailScreen(
    driverId: String,
    onCreateRoute: () -> Unit,
) {
    val driver = remember(driverId) {
        DriverMockData.fleet.firstOrNull { it.id == driverId }
            ?: DriverMockData.fleet.first()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Заголовок
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        driver.initials,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(driver.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(4.dp))
                val (status, color) = when (driver.status) {
                    DriverMockData.FleetStatus.ACTIVE -> "🟢 На маршруте" to MaterialTheme.colorScheme.tertiary
                    DriverMockData.FleetStatus.FREE -> "🟢 Свободен" to MaterialTheme.colorScheme.tertiary
                    DriverMockData.FleetStatus.OFFLINE -> "⚫ Не на смене" to MaterialTheme.colorScheme.outline
                }
                Text(status, color = color, style = MaterialTheme.typography.labelLarge)
                driver.current?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Действия
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {},
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Позвонить")
            }
            Button(
                onClick = onCreateRoute,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = driver.status != DriverMockData.FleetStatus.OFFLINE
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Назначить маршрут")
            }
        }

        // История
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "ИСТОРИЯ ПОСЛЕДНИХ МАРШРУТОВ",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple("03 мая", "Маршрут #234", "1/4 точек · идёт"),
                    Triple("02 мая", "Маршрут #228", "5/5 точек · завершён"),
                    Triple("30 апр", "Маршрут #221", "4/4 точек · завершён"),
                    Triple("29 апр", "Маршрут #217", "6/6 точек · завершён"),
                ).forEach { (date, name, detail) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(date, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
