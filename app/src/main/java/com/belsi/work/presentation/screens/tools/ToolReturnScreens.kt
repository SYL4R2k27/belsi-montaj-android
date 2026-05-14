package com.belsi.work.presentation.screens.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.components.role.RolePrimaryButton
import com.belsi.work.presentation.components.role.Severity

/**
 * FIX(2026-05-14) BELSI 2.0.1: 4 экрана для return-flow.
 * - ToolReturnRequestScreen — инициация возврата (любой из 5 ролей)
 * - ToolReturnPickupScreen   — водитель забрал с объекта
 * - ToolReturnDeliverScreen  — водитель доставил на завод
 * - ToolReturnAcceptScreen   — комплектатор принимает на заводе
 *
 * Все экраны принимают transferId в роуте и используют общий
 * ToolTransferDetailViewModel для actions (методы returnRequest /
 * returnPickup / returnDeliver / returnAccept).
 */

// ─────────────────────────────────────────────────────────────────────
// 1. RETURN REQUEST — инициация возврата
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolReturnRequestScreen(
    navController: NavController,
    transferId: String,
    viewModel: ToolTransferDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var reason by remember { mutableStateOf("") }
    // FIX(2026-05-14) BELSI 2.0.1: реальное фото через ReturnPhotoCapture
    val photoCapture = rememberReturnPhotoCapture()
    val photoState by photoCapture.state.collectAsState()

    LaunchedEffect(transferId) {
        if (state.transfer?.id != transferId) viewModel.load(transferId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Запрос на возврат") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
            )
        }
    ) { padding ->
        val t = state.transfer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (t == null) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            Text("⏪ Возврат инструмента", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(
                "${t.toolName ?: "Инструмент"} × ${t.quantity}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "С объекта: ${t.toSiteObjectName ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Причина возврата") },
                placeholder = { Text("работа закончена / брак / не нужен / по решению куратора") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            ReturnPhotoCard(
                capture = photoCapture,
                title = "Фото состояния (опц.)",
                description = "Защитит от споров о повреждениях. Не обязательно.",
            )

            Spacer(Modifier.weight(1f))

            RolePrimaryButton(
                text = if (state.isActing) "Отправка..." else "Запросить возврат",
                onClick = {
                    viewModel.returnRequest(
                        transferId = transferId,
                        reason = reason.takeIf { it.isNotBlank() },
                        photoUrl = photoState.photoUrl,
                    ) {
                        navController.popBackStack()
                    }
                },
                enabled = !state.isActing && !photoState.isUploading,
                severity = Severity.WARNING,
            )

            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                ) {
                    Text(state.error ?: "", modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 2. RETURN PICKUP — водитель забрал с объекта
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolReturnPickupScreen(
    navController: NavController,
    transferId: String,
    viewModel: ToolTransferDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var comment by remember { mutableStateOf("") }
    // FIX(2026-05-14) BELSI 2.0.1: фото обязательно для аудита
    val photoCapture = rememberReturnPhotoCapture()
    val photoState by photoCapture.state.collectAsState()

    LaunchedEffect(transferId) {
        if (state.transfer?.id != transferId) viewModel.load(transferId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Забор инструмента") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
            )
        }
    ) { padding ->
        val t = state.transfer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (t == null) {
                CircularProgressIndicator(); return@Column
            }
            Text("📦 Забираю с объекта", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("${t.toolName ?: "Инструмент"} × ${t.quantity}",
                style = MaterialTheme.typography.titleMedium)
            Text("Объект: ${t.toSiteObjectName ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))

            ReturnPhotoCard(
                capture = photoCapture,
                title = "Фото при заборе",
                description = "Сфотографируй инструмент в руках на объекте — обязательно для аудита.",
            )

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Комментарий (опц.)") },
                placeholder = { Text("например: монтажник передал лично") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Spacer(Modifier.weight(1f))

            RolePrimaryButton(
                text = when {
                    state.isActing -> "Отправка..."
                    photoState.isUploading -> "Загрузка фото..."
                    photoState.photoUrl == null -> "Сначала фото →"
                    else -> "Забрал — еду на завод"
                },
                onClick = {
                    photoState.photoUrl?.let { url ->
                        viewModel.returnPickup(
                            transferId = transferId,
                            photoUrl = url,
                            comment = comment.takeIf { it.isNotBlank() },
                        ) { navController.popBackStack() }
                    }
                },
                enabled = !state.isActing && photoState.photoUrl != null,
            )

            if (state.error != null) ErrorCard(state.error!!)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 3. RETURN DELIVER — водитель доставил на завод
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolReturnDeliverScreen(
    navController: NavController,
    transferId: String,
    viewModel: ToolTransferDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // FIX(2026-05-14) BELSI 2.0.1: фото обязательно — для аудита приёмки
    val photoCapture = rememberReturnPhotoCapture()
    val photoState by photoCapture.state.collectAsState()

    LaunchedEffect(transferId) {
        if (state.transfer?.id != transferId) viewModel.load(transferId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Доставка на завод") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
            )
        }
    ) { padding ->
        val t = state.transfer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (t == null) { CircularProgressIndicator(); return@Column }

            Text("🏭 Привёз на завод", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("${t.toolName ?: "Инструмент"} × ${t.quantity}",
                style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(8.dp))

            ReturnPhotoCard(
                capture = photoCapture,
                title = "Фото на заводе",
                description = "Сфотографируй инструмент при передаче комплектатору. После этого комплектатор увидит запрос на приёмку.",
            )

            Spacer(Modifier.weight(1f))

            RolePrimaryButton(
                text = when {
                    state.isActing -> "Отправка..."
                    photoState.isUploading -> "Загрузка фото..."
                    photoState.photoUrl == null -> "Сначала фото →"
                    else -> "Доставил — комплектатор примет"
                },
                onClick = {
                    photoState.photoUrl?.let { url ->
                        viewModel.returnDeliver(
                            transferId = transferId,
                            photoUrl = url,
                        ) { navController.popBackStack() }
                    }
                },
                enabled = !state.isActing && photoState.photoUrl != null,
                severity = Severity.SUCCESS,
            )

            if (state.error != null) ErrorCard(state.error!!)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 4. RETURN ACCEPT — комплектатор принимает на заводе
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolReturnAcceptScreen(
    navController: NavController,
    transferId: String,
    viewModel: ToolTransferDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var acceptedQty by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    // FIX(2026-05-14) BELSI 2.0.1: фото опционально
    val photoCapture = rememberReturnPhotoCapture()
    val photoState by photoCapture.state.collectAsState()

    LaunchedEffect(transferId) {
        if (state.transfer?.id != transferId) viewModel.load(transferId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приёмка возврата") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
            )
        }
    ) { padding ->
        val t = state.transfer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (t == null) { CircularProgressIndicator(); return@Column }

            Text("✅ Приёмка возврата", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("${t.toolName ?: "Инструмент"} ожидается × ${t.quantity}",
                style = MaterialTheme.typography.titleMedium)
            Text("С объекта: ${t.toSiteObjectName ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = acceptedQty,
                onValueChange = { v -> acceptedQty = v.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Фактическое количество") },
                placeholder = { Text("По умолчанию ${t.quantity} — впишите если меньше") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Комментарий (опц.)") },
                placeholder = { Text("состояние, замечания…") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )

            ReturnPhotoCard(
                capture = photoCapture,
                title = "Фото принятого (опц.)",
                description = "Можно сфотографировать инструмент при приёмке.",
            )

            Spacer(Modifier.weight(1f))

            RolePrimaryButton(
                text = when {
                    state.isActing -> "..."
                    photoState.isUploading -> "Загрузка фото..."
                    else -> "Принять"
                },
                onClick = {
                    viewModel.returnAccept(
                        transferId = transferId,
                        acceptedQuantity = acceptedQty.toIntOrNull(),
                        photoUrl = photoState.photoUrl,
                        comment = comment.takeIf { it.isNotBlank() },
                    ) { navController.popBackStack() }
                },
                enabled = !state.isActing && !photoState.isUploading,
                severity = Severity.SUCCESS,
            )

            if (state.error != null) ErrorCard(state.error!!)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 5. CURATOR RETURNS OVERVIEW — все активные возвраты для куратора
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorReturnsScreen(
    navController: NavController,
    viewModel: CuratorReturnsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Возвраты инструмента") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading && state.transfers.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (state.transfers.isEmpty()) {
                Text(
                    "Активных возвратов нет.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            state.transfers.forEach { t ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "${t.toolName ?: "Инструмент"} × ${t.quantity}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Статус: ${statusLabelRu(t.status)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "С объекта: ${t.toSiteObjectName ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!t.returnReason.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Причина: ${t.returnReason}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!t.returnRejectReason.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Отказ: ${t.returnRejectReason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row {
                            TextButton(
                                onClick = { navController.navigate("tools/transfer/${t.id}") }
                            ) { Text("Открыть") }
                            if (t.status == "return_rejected") {
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        viewModel.resolveRejection(t.id, "accept")
                                    },
                                    enabled = !state.isActing,
                                ) { Text("Всё-таки принять") }
                                TextButton(
                                    onClick = {
                                        viewModel.resolveRejection(t.id, "lost")
                                    },
                                    enabled = !state.isActing,
                                ) { Text("Списать") }
                            }
                        }
                    }
                }
            }

            if (state.error != null) ErrorCard(state.error!!)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────

// FIX(2026-05-14) BELSI 2.0.1: PhotoPlaceholderCard заменён на ReturnPhotoCard
// (см. ReturnPhotoCapture.kt) — реальный capture+upload вместо placeholder.

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private fun statusLabelRu(status: String): String = when (status) {
    "returning_requested" -> "Запрос на возврат"
    "returning" -> "Назначен водитель"
    "returning_in_transit" -> "Везут на завод"
    "returning_delivered" -> "На заводе — ожидает приёмки"
    "return_rejected" -> "Отклонён — нужен разбор"
    "returned" -> "Возвращён"
    else -> status
}
