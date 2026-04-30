package com.belsi.work.presentation.screens.curator.objects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.belsi.work.data.remote.dto.objects.SiteObjectDto
import com.belsi.work.presentation.theme.belsiColors

/**
 * Вкладка "Объекты" для куратора — встраивается в CuratorMainScreen как таб.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorObjectsTab(
    viewModel: CuratorObjectsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    val filteredObjects = uiState.objects.filter { obj ->
        val matchesSearch = searchQuery.isEmpty() ||
            obj.name.contains(searchQuery, ignoreCase = true) ||
            (obj.address ?: "").contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "active" -> obj.status == "active"
            "completed" -> obj.status == "completed"
            "archived" -> obj.status == "archived"
            else -> true
        }

        matchesSearch && matchesFilter
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Search and filter
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск по названию или адресу") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, "Очистить")
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )

                    Spacer(Modifier.height(12.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedFilter == "all",
                                onClick = { selectedFilter = "all" },
                                label = { Text("Все (${uiState.objects.size})") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "active",
                                onClick = { selectedFilter = "active" },
                                label = { Text("Активные (${uiState.objects.count { it.status == "active" }})") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "completed",
                                onClick = { selectedFilter = "completed" },
                                label = { Text("Завершённые (${uiState.objects.count { it.status == "completed" }})") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "archived",
                                onClick = { selectedFilter = "archived" },
                                label = { Text("Архив (${uiState.objects.count { it.status == "archived" }})") }
                            )
                        }
                    }
                }
            }

            when {
                uiState.isLoading && uiState.objects.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                filteredObjects.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Business, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (uiState.objects.isEmpty()) "Нет объектов" else "Ничего не найдено",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (uiState.objects.isEmpty()) "Создайте первый объект" else "Попробуйте изменить запрос",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredObjects, key = { it.id }) { obj ->
                            ObjectCard(obj) {
                                viewModel.loadObjectDetail(obj.id)
                            }
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { viewModel.showCreateDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, "Создать объект", tint = MaterialTheme.colorScheme.onPrimary)
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Create dialog
    if (uiState.showCreateDialog) {
        CreateObjectDialog(
            isProcessing = uiState.isProcessing,
            onConfirm = { name, address, description ->
                viewModel.createObject(name, address, description)
            },
            onDismiss = { viewModel.hideCreateDialog() }
        )
    }

    // Detail bottom sheet
    if (uiState.selectedDetail != null) {
        ObjectDetailSheet(
            detail = uiState.selectedDetail!!,
            isLoading = uiState.isLoadingDetail,
            onDismiss = { viewModel.clearDetail() },
            onArchive = { viewModel.archiveObject(uiState.selectedDetail!!.id) }
        )
    }
}

@Composable
private fun ObjectCard(obj: SiteObjectDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = statusColor(obj.status).copy(alpha = 0.1f)
                ) {
                    Icon(
                        Icons.Default.Business, null,
                        Modifier.padding(12.dp).size(28.dp),
                        tint = statusColor(obj.status)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        obj.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!obj.address.isNullOrBlank()) {
                        Text(
                            obj.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            // Stats row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ObjectInfoChip(Icons.Default.Person, "${obj.activeWorkersCount} работн.")
                ObjectInfoChip(Icons.Default.Schedule, "${obj.shiftsToday} смен")
                ObjectInfoChip(Icons.Default.CameraAlt, "${obj.totalPhotos} фото")
                ObjectStatusChip(obj.status)
            }

            // Coordinator
            if (!obj.coordinatorName.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SupervisorAccount, null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Координатор: ${obj.coordinatorName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectInfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ObjectStatusChip(status: String) {
    val (label, color) = when (status) {
        "active" -> "Активный" to MaterialTheme.belsiColors.success
        "completed" -> "Завершён" to MaterialTheme.belsiColors.info
        "archived" -> "Архив" to MaterialTheme.colorScheme.outline
        else -> status to MaterialTheme.colorScheme.outline
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.15f)) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CreateObjectDialog(
    isProcessing: Boolean,
    onConfirm: (name: String, address: String?, description: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!isProcessing) onDismiss() }) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Новый объект",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название *") },
                    placeholder = { Text("ЖК Солнечный, корпус 3") },
                    singleLine = true,
                    enabled = !isProcessing
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Адрес") },
                    placeholder = { Text("ул. Ленина 42, Москва") },
                    singleLine = true,
                    enabled = !isProcessing
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Описание") },
                    placeholder = { Text("Монтаж вентиляции, 3 этаж") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isProcessing
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = { onConfirm(name, address, description) },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Создать")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "active" -> MaterialTheme.belsiColors.success
    "completed" -> MaterialTheme.belsiColors.info
    "archived" -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.outline
}
