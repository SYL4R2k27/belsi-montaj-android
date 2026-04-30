package com.belsi.work.presentation.screens.foreman.installerdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.remote.api.PauseStatsResponse
import com.belsi.work.data.remote.dto.team.InstallerPhotoDto
import com.belsi.work.data.remote.dto.team.InstallerShiftDto
import com.belsi.work.presentation.theme.BelsiPrimary
import com.belsi.work.presentation.theme.BelsiSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerDetailScreen(
    navController: NavController,
    installerId: String,
    viewModel: InstallerDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showReassignDialog by remember { mutableStateOf(false) }

    LaunchedEffect(installerId) {
        viewModel.loadInstallerDetail(installerId)
        viewModel.loadPauseStats(installerId)
    }

    LaunchedEffect(uiState.reassignSuccess) {
        uiState.reassignSuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearReassignSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Монтажник") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BelsiPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BelsiPrimary)
                    }
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            uiState.error ?: "Ошибка",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry(installerId) }) {
                            Text("Повторить")
                        }
                    }
                }
                uiState.detail != null -> {
                    val detail = uiState.detail!!
                    val member = detail.member

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // === Карточка профиля ===
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(BelsiPrimary, BelsiPrimary.copy(alpha = 0.8f))
                                            )
                                        )
                                        .padding(24.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Аватар
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = member.displayName().take(1).uppercase(),
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(Modifier.height(12.dp))

                                        // Имя
                                        Text(
                                            text = member.displayName(),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )

                                        // Телефон
                                        Text(
                                            text = member.phone,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )

                                        Spacer(Modifier.height(8.dp))

                                        // Статус
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (member.isWorkingNow) BelsiSuccess.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (member.isWorkingNow) BelsiSuccess else Color.White.copy(alpha = 0.5f))
                                                )
                                                Text(
                                                    text = if (member.isWorkingNow) "На смене" else "Не на смене",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // === Статистика ===
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Schedule,
                                    label = "Смены",
                                    value = "${member.totalShifts}",
                                    color = BelsiPrimary
                                )
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.AccessTime,
                                    label = "Часы",
                                    value = String.format("%.1f", member.totalHours),
                                    color = BelsiSuccess
                                )
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.PhotoCamera,
                                    label = "На проверке",
                                    value = "${member.pendingPhotosCount}",
                                    color = if (member.pendingPhotosCount > 0) Color(0xFFF59E0B) else Color.Gray
                                )
                            }
                        }

                        // === Информация ===
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "Информация",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    member.role?.let { role ->
                                        InfoRow(
                                            icon = Icons.Default.Badge,
                                            label = "Роль",
                                            value = when (role) {
                                                "installer" -> "Монтажник"
                                                "foreman" -> "Бригадир"
                                                "coordinator" -> "Координатор"
                                                "curator" -> "Куратор"
                                                else -> role
                                            }
                                        )
                                    }

                                    member.joinedAt?.let { joined ->
                                        InfoRow(
                                            icon = Icons.Default.CalendarToday,
                                            label = "В команде с",
                                            value = formatDate(joined)
                                        )
                                    }

                                    member.lastShiftAt?.let { lastShift ->
                                        InfoRow(
                                            icon = Icons.Default.History,
                                            label = "Последняя смена",
                                            value = formatDate(lastShift)
                                        )
                                    }

                                    member.lastPhotoAt?.let { lastPhoto ->
                                        InfoRow(
                                            icon = Icons.Default.CameraAlt,
                                            label = "Последнее фото",
                                            value = formatDate(lastPhoto)
                                        )
                                    }
                                }
                            }
                        }

                        // === Фотографии ===
                        if (detail.photos.isNotEmpty()) {
                            item {
                                Text(
                                    "Фотографии (${detail.photos.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(detail.photos) { photo ->
                                        PhotoCard(photo)
                                    }
                                }
                            }
                        }

                        // === Кнопка перевода на объект ===
                        item {
                            Button(
                                onClick = { showReassignDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = BelsiPrimary),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !uiState.isReassigning
                            ) {
                                if (uiState.isReassigning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Перевести на объект")
                            }
                        }

                        // === Статистика простоев ===
                        item {
                            Text(
                                "Статистика простоев",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        item {
                            if (uiState.isLoadingPauseStats) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = BelsiPrimary, modifier = Modifier.size(28.dp))
                                }
                            } else if (uiState.pauseStats != null) {
                                PauseStatsCard(uiState.pauseStats!!)
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "Нет данных о простоях",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        // === История смен ===
                        if (detail.shifts.isNotEmpty()) {
                            item {
                                Text(
                                    "История смен (${detail.shifts.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            items(detail.shifts.take(10)) { shift ->
                                ShiftCard(shift)
                            }
                        }

                        // Отступ снизу
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    // Диалог перевода на объект
    if (showReassignDialog) {
        ReassignDialog(
            onDismiss = { showReassignDialog = false },
            onConfirm = { siteObjectId ->
                showReassignDialog = false
                viewModel.reassignInstaller(installerId, siteObjectId)
            }
        )
    }
}

@Composable
private fun PauseStatsCard(stats: PauseStatsResponse) {
    val totalPauseMinutes = stats.totalPauseSeconds / 60.0
    val totalIdleMinutes = stats.totalIdleSeconds / 60.0
    val avgPauseMinutes = stats.avgPausePerShift / 60.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PauseStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Pause,
                    label = "Всего пауз",
                    value = formatMinutes(totalPauseMinutes),
                    color = Color(0xFFF59E0B)
                )
                PauseStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Timer,
                    label = "Простой",
                    value = formatMinutes(totalIdleMinutes),
                    color = Color(0xFFEF4444)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PauseStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.AvTimer,
                    label = "Сред. за смену",
                    value = formatMinutes(avgPauseMinutes),
                    color = BelsiPrimary
                )
                PauseStatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.PieChart,
                    label = "% простоя",
                    value = "%.1f%%".format(stats.idlePercentage),
                    color = if (stats.idlePercentage > 20) Color(0xFFEF4444) else BelsiSuccess
                )
            }
        }
    }
}

@Composable
private fun PauseStatItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun ReassignDialog(
    onDismiss: () -> Unit,
    onConfirm: (siteObjectId: String) -> Unit
) {
    var siteObjectId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перевод на объект") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Введите ID объекта, на который нужно перевести монтажника",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = siteObjectId,
                    onValueChange = { siteObjectId = it },
                    label = { Text("ID объекта") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (siteObjectId.isNotBlank()) onConfirm(siteObjectId) },
                enabled = siteObjectId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BelsiPrimary)
            ) {
                Text("Перевести")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

private fun formatMinutes(minutes: Double): String {
    return if (minutes >= 60) {
        val hours = (minutes / 60).toInt()
        val mins = (minutes % 60).toInt()
        "${hours}ч ${mins}м"
    } else {
        "%.0fм".format(minutes)
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = BelsiPrimary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PhotoCard(photo: InstallerPhotoDto) {
    Card(
        modifier = Modifier.size(120.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (photo.photoUrl != null) {
                AsyncImage(
                    model = photo.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Photo, null, tint = Color.Gray)
                }
            }

            // Статус-бейдж
            photo.status?.let { status ->
                val statusColor = when (status) {
                    "approved" -> BelsiSuccess
                    "rejected" -> Color(0xFFEF4444)
                    "pending", "uploaded" -> Color(0xFFF59E0B)
                    else -> Color.Gray
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                    shape = CircleShape,
                    color = statusColor
                ) {
                    Box(modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ShiftCard(shift: InstallerShiftDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                shift.startAt?.let { start ->
                    Text(
                        formatDate(start),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shift.durationHours?.let { hours ->
                        Text(
                            String.format("%.1f ч", hours),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    shift.status?.let { status ->
                        val label = when (status) {
                            "active" -> "Активна"
                            "finished" -> "Завершена"
                            else -> status
                        }
                        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
            shift.status?.let { status ->
                val color = when (status) {
                    "active" -> BelsiSuccess
                    "finished" -> Color.Gray
                    else -> Color.Gray
                }
                Icon(
                    if (status == "active") Icons.Default.PlayCircle else Icons.Default.CheckCircle,
                    null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun formatDate(isoDate: String): String {
    return try {
        // Парсим ISO 8601 формат
        val cleaned = isoDate.replace("T", " ").take(16)
        cleaned
    } catch (e: Exception) {
        isoDate.take(16)
    }
}
