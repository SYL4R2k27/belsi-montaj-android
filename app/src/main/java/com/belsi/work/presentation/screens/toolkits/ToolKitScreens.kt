package com.belsi.work.presentation.screens.toolkits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.tool_kit.BatchItemDto
import com.belsi.work.data.remote.dto.tool_kit.ToolKitDto
import com.belsi.work.data.remote.dto.tool_kit.ToolKitItemDto
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors

/**
 * FIX(2026-05-18) BELSI 2.0.1: 5 экранов для tool-kits / Тележка.
 *   - ToolKitListScreen — список шаблонов
 *   - ToolKitDetailScreen — детали + 60 items + кнопка «Выдать»
 *   - ToolKitDispatchScreen — форма выдачи (объект, водитель, team_count)
 *   - ToolKitBatchScreen — driver/receiver видит batch, bulk операции
 *   - ToolKitCreateScreen — curator/coordinator создаёт новый kit
 */

// ════════════════════════════════════════════════════════════════════
// 1. List Screen
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolKitListScreen(
    navController: NavController,
    viewModel: ToolKitListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тележки и комплекты") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(AppRoute.ToolKitCreate.route) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, "Новый kit")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.kits.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.kits.isEmpty() -> {
                    EmptyStateColumn(
                        icon = Icons.Default.Inventory,
                        title = "Нет шаблонов",
                        subtitle = "Создайте первый шаблон комплекта инструмента (например, Тележку для подоконников).",
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.kits, key = { it.id }) { kit ->
                            ToolKitCard(
                                kit = kit,
                                onClick = {
                                    navController.navigate(AppRoute.ToolKitDetail.createRoute(kit.id))
                                },
                            )
                        }
                    }
                }
            }
            state.error?.let { e ->
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text(e)
                }
            }
        }
    }
}

@Composable
private fun ToolKitCard(kit: ToolKitDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Icon(
                    Icons.Default.Inventory2,
                    null,
                    Modifier.padding(10.dp).size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    kit.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Groups, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("${kit.teamSize} чел.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.ListAlt, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("${kit.itemsCount} позиций", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!kit.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        kit.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// 2. Detail Screen
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolKitDetailScreen(
    navController: NavController,
    viewModel: ToolKitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showOverflow by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showAddItem by remember { mutableStateOf(false) }
    var showDeleteKit by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ToolKitItemDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.kit?.name ?: "Загрузка...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    if (state.kit != null) {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, null)
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Редактировать") },
                                onClick = { showOverflow = false; showEdit = true },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Добавить позицию") },
                                onClick = { showOverflow = false; showAddItem = true },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Архивировать", color = MaterialTheme.colorScheme.error) },
                                onClick = { showOverflow = false; showDeleteKit = true },
                                leadingIcon = { Icon(Icons.Default.Archive, null, tint = MaterialTheme.colorScheme.error) },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            if (state.kit != null && (state.kit?.itemsCount ?: 0) > 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        navController.navigate(AppRoute.ToolKitDispatch.createRoute(viewModel.kitId))
                    },
                    icon = { Icon(Icons.Default.LocalShipping, null) },
                    text = { Text("Выдать тележку") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text("Ошибка: ${state.error}", Modifier.align(Alignment.Center))
                state.kit != null -> KitDetailContent(
                    kit = state.kit!!,
                    onDeleteItem = { item -> itemToDelete = item },
                )
            }
        }
    }

    // Edit dialog
    if (showEdit && state.kit != null) {
        EditKitDialog(
            kit = state.kit!!,
            onDismiss = { showEdit = false },
            onSave = { name, desc, teamSize ->
                viewModel.patch(name, desc, teamSize, null)
                showEdit = false
            },
        )
    }

    // Add item dialog
    if (showAddItem) {
        AddItemDialog(
            onDismiss = { showAddItem = false },
            onSave = { req ->
                viewModel.addItem(req) { showAddItem = false }
            },
        )
    }

    // Delete kit confirmation
    if (showDeleteKit) {
        AlertDialog(
            onDismissRequest = { showDeleteKit = false },
            icon = { Icon(Icons.Default.Archive, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Архивировать шаблон?") },
            text = { Text("Шаблон станет неактивным. Уже выданные тележки сохранятся, но новые dispatch'и будут заблокированы. Это можно отменить через изменение active=true.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete { navController.popBackStack() }
                        showDeleteKit = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Архивировать") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteKit = false }) { Text("Отмена") }
            },
        )
    }

    // Delete item confirmation
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить позицию?") },
            text = { Text("«${item.name}» будет удалена из шаблона.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditKitDialog(
    kit: ToolKitDto,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, teamSize: Int) -> Unit,
) {
    var name by remember { mutableStateOf(kit.name) }
    var desc by remember { mutableStateOf(kit.description.orEmpty()) }
    var teamSize by remember { mutableStateOf(kit.teamSize.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать шаблон") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Text("Размер команды: ${teamSize.toInt()}",
                    style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = teamSize,
                    onValueChange = { teamSize = it },
                    valueRange = 1f..20f,
                    steps = 18,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name.trim().takeIf { it != kit.name } ?: name,
                        desc.trim().ifBlank { null }, teamSize.toInt())
                },
                enabled = name.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onSave: (com.belsi.work.data.remote.dto.tool_kit.ToolKitItemCreateRequest) -> Unit,
) {
    var section by remember { mutableStateOf("tools") }
    var name by remember { mutableStateOf("") }
    var itemNo by remember { mutableStateOf("") }
    var spec by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var sectionExpanded by remember { mutableStateOf(false) }
    var toolId by remember { mutableStateOf<String?>(null) }
    var toolLabel by remember { mutableStateOf<String?>(null) }
    var showToolPicker by remember { mutableStateOf(false) }

    val sectionLabels = mapOf(
        "tools" to "🔧 Инструмент",
        "consumables" to "📦 Расходники",
        "measurement" to "📏 Измерители",
        "auxiliary" to "🧰 Вспомогательный",
        "transport" to "🛒 Перемещение",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая позиция") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExposedDropdownMenuBox(
                    expanded = sectionExpanded,
                    onExpandedChange = { sectionExpanded = !sectionExpanded },
                ) {
                    OutlinedTextField(
                        value = sectionLabels[section] ?: section,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Раздел") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = sectionExpanded,
                        onDismissRequest = { sectionExpanded = false },
                    ) {
                        sectionLabels.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { section = code; sectionExpanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = itemNo, onValueChange = { itemNo = it },
                    label = { Text("Номер по списку (опц.)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = spec, onValueChange = { spec = it },
                    label = { Text("Спецификация / комплектация") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Количество на команду*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
                // Tool picker — связь с tools таблицей (для inventory check'а)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showToolPicker = true },
                    colors = CardDefaults.cardColors(
                        containerColor = if (toolId != null)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Build, null,
                            tint = if (toolId != null) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Привязать к инструменту (опц.)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                toolLabel ?: "Не связан — выдача без проверки наличия",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (toolId != null) {
                            IconButton(onClick = { toolId = null; toolLabel = null }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(com.belsi.work.data.remote.dto.tool_kit.ToolKitItemCreateRequest(
                        section = section,
                        name = name.trim(),
                        itemNo = itemNo.trim().ifBlank { null },
                        spec = spec.trim().ifBlank { null },
                        quantityPerTeam = qty.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        toolId = toolId,
                    ))
                },
                enabled = name.isNotBlank() && qty.toIntOrNull() != null,
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )

    if (showToolPicker) {
        com.belsi.work.presentation.components.pickers.ToolPickerSheet(
            selectedId = toolId,
            initialKindFilter = if (section == "consumables") "consumable" else "tool",
            onPick = { t ->
                toolId = t.id
                toolLabel = "${t.name}${t.category?.let { " · $it" } ?: ""} (доступно: ${t.quantity})"
                // Если name ещё пустой — заполнить из tool, плюс auto-spec
                if (name.isBlank()) name = t.name
                if (spec.isBlank() && !t.description.isNullOrBlank()) spec = t.description
                showToolPicker = false
            },
            onDismiss = { showToolPicker = false },
        )
    }
}

@Composable
private fun KitDetailContent(
    kit: ToolKitDto,
    onDeleteItem: (ToolKitItemDto) -> Unit = {},
) {
    val sectionsByName = remember(kit.items) {
        kit.items.groupBy { it.section }.toSortedMap()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { KitHeaderCard(kit) }
        if (kit.items.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlaylistAdd, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Шаблон пуст", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Откройте меню ⋮ → «Добавить позицию» чтобы наполнить шаблон.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
        sectionsByName.forEach { (section, items) ->
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(section, items.size)
            }
            items(items, key = { it.id }) { item ->
                DeletableKitItemRow(item, onDelete = { onDeleteItem(item) })
            }
        }
    }
}

@Composable
private fun DeletableKitItemRow(item: ToolKitItemDto, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "×${item.quantityPerTeam}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp).padding(start = 8.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!item.spec.isNullOrBlank()) {
                    Text(
                        item.spec,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun KitHeaderCard(kit: ToolKitDto) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(kit.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!kit.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(kit.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip(Icons.Default.Groups, "На ${kit.teamSize} чел.")
                StatChip(Icons.Default.ListAlt, "${kit.itemsCount} позиций")
            }
        }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SectionHeader(sectionCode: String, count: Int) {
    val title = when (sectionCode) {
        "tools" -> "🔧 Инструмент"
        "consumables" -> "📦 Расходники"
        "measurement" -> "📏 Измерители"
        "auxiliary" -> "🧰 Вспомогательный"
        "transport" -> "🛒 Перемещение"
        else -> sectionCode.replaceFirstChar { it.uppercase() }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "($count)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KitItemRow(item: ToolKitItemDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "×${item.quantityPerTeam}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!item.spec.isNullOrBlank()) {
                    Text(
                        item.spec,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// 3. Dispatch Screen
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolKitDispatchScreen(
    navController: NavController,
    viewModel: ToolKitDispatchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.success) {
        state.success?.let { resp ->
            // После успешной выдачи — переходим в Batch screen
            navController.navigate(AppRoute.ToolKitBatch.createRoute(resp.kitBatchId)) {
                popUpTo(AppRoute.ToolKitDetail.createRoute(viewModel.kitId)) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выдача комплекта", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.kit == null -> Text("Ошибка загрузки", Modifier.align(Alignment.Center))
                else -> DispatchForm(state = state, viewModel = viewModel)
            }
            state.error?.let { e ->
                LaunchedEffect(e) {
                    snackbar.showSnackbar(e)
                    viewModel.clearError()
                }
            }
        }
    }
}

@Composable
private fun DispatchForm(
    state: ToolKitDispatchUiState,
    viewModel: ToolKitDispatchViewModel,
) {
    val kit = state.kit!!
    var showSitePicker by remember { mutableStateOf(false) }
    var showDriverPicker by remember { mutableStateOf(false) }
    val included = remember(state.excludedItemIds, kit) {
        kit.items.count { it.id !in state.excludedItemIds }
    }
    val totalUnits = remember(included, state.teamCount, state.quantityOverrides) {
        kit.items.filter { it.id !in state.excludedItemIds }.sumOf { item ->
            (state.quantityOverrides[item.id] ?: item.quantityPerTeam) * state.teamCount
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(kit.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$included/${kit.itemsCount} позиций включены • $totalUnits ед.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Site object picker
        PickerField(
            icon = Icons.Default.Place,
            label = "Объект назначения",
            value = state.siteObjectLabel ?: state.toSiteObjectId,
            placeholder = "Выберите объект",
            onClick = { showSitePicker = true },
        )

        // Driver picker
        PickerField(
            icon = Icons.Default.LocalShipping,
            label = "Водитель",
            value = state.driverLabel ?: state.driverUserId,
            placeholder = "Выберите водителя",
            onClick = { showDriverPicker = true },
        )

        // Team count slider 1..20
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Команд: ${state.teamCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$totalUnits ед.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = state.teamCount.toFloat(),
                    onValueChange = { viewModel.setTeamCount(it.toInt()) },
                    valueRange = 1f..20f,
                    steps = 18,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("20", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Items list — каждая позиция с checkbox (exclude) + qty override field
        Text(
            "Состав комплекта (тап чтобы исключить/изменить количество)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        kit.items.groupBy { it.section }.forEach { (sec, items) ->
            SectionHeader(sec, items.size)
            items.forEach { item ->
                EditableKitItemRow(
                    item = item,
                    excluded = item.id in state.excludedItemIds,
                    overrideQty = state.quantityOverrides[item.id],
                    teamCount = state.teamCount,
                    onToggleExclude = { viewModel.toggleItemExcluded(item.id) },
                    onQtyChange = { qty -> viewModel.setQuantityOverride(item.id, qty) },
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Inventory pre-check warnings
        state.preview?.let { preview ->
            if (preview.insufficientCount > 0 || preview.lowCount > 0) {
                PreviewWarningsCard(preview)
            } else if (preview.warnings.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.belsiColors.success.copy(alpha = 0.12f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.belsiColors.success)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Инвентарь достаточен для выдачи",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.belsiColors.success,
                        )
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.dispatch() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !state.dispatching &&
                     !state.toSiteObjectId.isNullOrBlank() &&
                     !state.driverUserId.isNullOrBlank() &&
                     included > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = if ((state.preview?.insufficientCount ?: 0) > 0)
                    MaterialTheme.belsiColors.warning
                else MaterialTheme.belsiColors.success,
            ),
        ) {
            if (state.dispatching) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.LocalShipping, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if ((state.preview?.insufficientCount ?: 0) > 0)
                        "Выдать всё равно (${state.preview?.insufficientCount} нехватка)"
                    else "Выдать ($included поз. × ${state.teamCount} команд)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    // Pickers
    if (showSitePicker) {
        com.belsi.work.presentation.components.pickers.SiteObjectPickerSheet(
            selectedId = state.toSiteObjectId,
            onPick = { obj ->
                viewModel.setSiteObject(obj.id, "${obj.name}${obj.address?.let { " · $it" } ?: ""}")
                showSitePicker = false
            },
            onDismiss = { showSitePicker = false },
        )
    }
    if (showDriverPicker) {
        com.belsi.work.presentation.components.pickers.DriverPickerSheet(
            selectedId = state.driverUserId,
            onPick = { d ->
                viewModel.setDriver(d.id, "${d.name}${if (d.busy) " (занят)" else ""}")
                showDriverPicker = false
            },
            onDismiss = { showDriverPicker = false },
        )
    }
}

@Composable
private fun PickerField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    value ?: placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditableKitItemRow(
    item: ToolKitItemDto,
    excluded: Boolean,
    overrideQty: Int?,
    teamCount: Int,
    onToggleExclude: () -> Unit,
    onQtyChange: (Int?) -> Unit,
) {
    val qty = overrideQty ?: item.quantityPerTeam
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (excluded)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = !excluded,
                onCheckedChange = { onToggleExclude() },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (excluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (!item.spec.isNullOrBlank()) {
                    Text(
                        item.spec,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            // Qty editor
            QtyStepper(
                qty = qty,
                enabled = !excluded,
                modified = overrideQty != null,
                onChange = { newQty ->
                    if (newQty == item.quantityPerTeam) onQtyChange(null) else onQtyChange(newQty)
                },
            )
            Text(
                " ×$teamCount",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun PreviewWarningsCard(preview: com.belsi.work.data.remote.dto.tool_kit.DispatchPreviewResponse) {
    var expanded by remember { mutableStateOf(preview.insufficientCount > 0) }
    val bgColor = if (preview.insufficientCount > 0)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    else MaterialTheme.belsiColors.warning.copy(alpha = 0.15f)

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (preview.insufficientCount > 0) Icons.Default.Error else Icons.Default.Warning,
                    null,
                    tint = if (preview.insufficientCount > 0)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.belsiColors.warning,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (preview.insufficientCount > 0)
                            "Не хватает ${preview.insufficientCount} позиций"
                        else "Низкие остатки ${preview.lowCount} позиций",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Проверьте инвентарь перед выдачей",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                preview.warnings.forEach { w ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (w.severity == "insufficient") Icons.Default.Error else Icons.Default.Warning,
                            null,
                            Modifier.size(16.dp),
                            tint = if (w.severity == "insufficient")
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.belsiColors.warning,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            w.kitItemName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${w.requested}/${w.available}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (w.severity == "insufficient")
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.belsiColors.warning,
                        )
                    }
                }
                if (preview.warnings.any { it.inTransit > 0 }) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "* «5/10» = нужно 5, доступно 10 (за вычетом уже выданного на других объектах)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QtyStepper(
    qty: Int,
    enabled: Boolean,
    modified: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onChange((qty - 1).coerceAtLeast(1)) },
            enabled = enabled && qty > 1,
            modifier = Modifier.size(28.dp),
        ) { Icon(Icons.Default.Remove, null, Modifier.size(16.dp)) }
        Text(
            "$qty",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (modified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 24.dp).padding(horizontal = 2.dp),
        )
        IconButton(
            onClick = { onChange(qty + 1) },
            enabled = enabled,
            modifier = Modifier.size(28.dp),
        ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }
    }
}

// ════════════════════════════════════════════════════════════════════
// 4. Batch Screen (driver/receiver видит kit_batch)
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolKitBatchScreen(
    navController: NavController,
    viewModel: ToolKitBatchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar("Ошибка: $it")
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.batch?.kitName ?: "Партия", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.batch == null -> Text("Партия не найдена", Modifier.align(Alignment.Center))
                else -> BatchContent(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun BatchContent(state: ToolKitBatchUiState, viewModel: ToolKitBatchViewModel) {
    val batch = state.batch!!
    val dominantStatus = batch.statuses.maxByOrNull { it.value }?.key ?: "?"
    val canPartial = dominantStatus == "delivered"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            BatchHeaderCard(batch.kitName ?: "Партия", batch.totalItems, batch.statuses)
            Spacer(Modifier.height(8.dp))
            BatchActionPanel(
                dominantStatus = dominantStatus,
                processing = state.processing,
                partialMode = state.partialAcceptMode,
                selectedCount = state.selectedAcceptIds.size,
                hasPhoto = state.pendingPhotoUrl != null,
                viewModel = viewModel,
                canPartial = canPartial,
            )
            Spacer(Modifier.height(8.dp))
        }
        batch.sections.toSortedMap().forEach { (section, items) ->
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(section, items.size)
            }
            items(items, key = { it.id }) { item ->
                BatchItemRow(
                    item = item,
                    partialMode = state.partialAcceptMode && item.status == "delivered",
                    selected = item.id in state.selectedAcceptIds,
                    onToggle = { viewModel.toggleSelected(item.id) },
                )
            }
        }
    }
}

@Composable
private fun BatchHeaderCard(kitName: String, total: Int, statuses: Map<String, Int>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Icon(Icons.Default.Inventory2, null,
                        Modifier.padding(10.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(kitName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$total позиций", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                statuses.forEach { (status, count) ->
                    StatusBadge(status, count)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String, count: Int) {
    val (label, color) = when (status) {
        "pending" -> "Ожидает" to MaterialTheme.colorScheme.onSurfaceVariant
        "dispatched" -> "Отгружено" to MaterialTheme.colorScheme.primary
        "in_transit" -> "В пути" to MaterialTheme.belsiColors.info
        "delivered" -> "Доставлено" to MaterialTheme.belsiColors.warning
        "accepted" -> "Принято" to MaterialTheme.belsiColors.success
        "rejected" -> "Отказ" to MaterialTheme.colorScheme.error
        "in_use" -> "В работе" to MaterialTheme.belsiColors.info
        else -> status to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.12f)) {
        Text(
            "$label: $count",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BatchActionPanel(
    dominantStatus: String,
    processing: Boolean,
    partialMode: Boolean,
    selectedCount: Int,
    hasPhoto: Boolean,
    canPartial: Boolean,
    viewModel: ToolKitBatchViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Партial mode toggle (только когда delivered)
        if (canPartial) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (partialMode)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (partialMode) Icons.Default.Checklist else Icons.Default.DoneAll,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (partialMode) "Выборочный приём" else "Полный приём",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (partialMode) "Отметьте принятые позиции"
                            else "Принять все доставленные позиции",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = partialMode,
                        onCheckedChange = { viewModel.togglePartialMode() },
                    )
                }
                if (partialMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { viewModel.selectAllDelivered() }) {
                            Text("Все")
                        }
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text("Очистить")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Выбрано: $selectedCount",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp, end = 8.dp),
                        )
                    }
                }
            }
        }

        // Photo attach row
        PhotoAttachRow(
            hasPhoto = hasPhoto,
            onPhotoTaken = { url -> viewModel.setPendingPhoto(url) },
            onClear = { viewModel.setPendingPhoto(null) },
        )

        // Main action
        val isClosed = dominantStatus == "accepted"
        val (label, icon, action) = when {
            isClosed -> Triple("Партия закрыта", Icons.Default.Done, null as (() -> Unit)?)
            dominantStatus == "dispatched" -> Triple("Забрать (я водитель)", Icons.Default.PlayArrow, { viewModel.pickup() } as (() -> Unit)?)
            dominantStatus == "in_transit" -> Triple("Доставил", Icons.Default.CheckCircle, { viewModel.deliver() } as (() -> Unit)?)
            dominantStatus == "delivered" && partialMode -> Triple(
                "Принять выбранные ($selectedCount)", Icons.Default.Checklist,
                ({ viewModel.acceptSelected() }) as (() -> Unit)?,
            )
            dominantStatus == "delivered" -> Triple("Принять всё", Icons.Default.DoneAll, ({ viewModel.acceptAll() }) as (() -> Unit)?)
            else -> Triple(dominantStatus, Icons.Default.Done, null as (() -> Unit)?)
        }
        val enabled = action != null && !processing &&
                (!partialMode || selectedCount > 0)

        Button(
            onClick = { action?.invoke() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.belsiColors.success),
        ) {
            if (processing) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(icon, null)
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PhotoAttachRow(
    hasPhoto: Boolean,
    onPhotoTaken: (String) -> Unit,
    onClear: () -> Unit,
) {
    // Простой intent на камеру через ActivityResultContracts.TakePicturePreview
    val context = androidx.compose.ui.platform.LocalContext.current
    val tempUriHolder = remember { mutableStateOf<android.net.Uri?>(null) }

    val takePicture = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) tempUriHolder.value?.let { onPhotoTaken(it.toString()) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasPhoto)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (hasPhoto) Icons.Default.CheckCircle else Icons.Default.CameraAlt,
                null,
                tint = if (hasPhoto) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (hasPhoto) "Фото прикреплено" else "Прикрепить фото (опционально)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (hasPhoto) "Будет загружено вместе с действием"
                    else "Например подтверждение приёмки",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasPhoto) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = {
                    val file = java.io.File(context.cacheDir, "kit_${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        context.packageName + ".fileprovider",
                        file,
                    )
                    tempUriHolder.value = uri
                    takePicture.launch(uri)
                }) {
                    Text("Снять")
                }
            }
        }
    }
}

@Composable
private fun BatchItemRow(
    item: BatchItemDto,
    partialMode: Boolean = false,
    selected: Boolean = false,
    onToggle: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (partialMode) Modifier.clickable { onToggle() } else Modifier),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = when {
                partialMode && selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            },
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (partialMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
                Spacer(Modifier.width(4.dp))
            }
            Text(
                "×${item.quantity}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp),
            )
            Text(
                item.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StatusBadge(item.status, 1)
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// 5. Create Screen (curator/coordinator)
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolKitCreateScreen(
    navController: NavController,
    viewModel: ToolKitCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.createdKitId) {
        state.createdKitId?.let { kitId ->
            // После создания — переходим в Detail
            navController.navigate(AppRoute.ToolKitDetail.createRoute(kitId)) {
                popUpTo(AppRoute.ToolKitCreate.route) { inclusive = true }
            }
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar("Ошибка: $it")
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый комплект") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::setCode,
                label = { Text("Code (slug)") },
                placeholder = { Text("telezhka-doors-team3") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Название") },
                placeholder = { Text("Тележка — Двери / Команда 3") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Описание (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Размер команды: ${state.teamSize}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Slider(
                        value = state.teamSize.toFloat(),
                        onValueChange = { viewModel.setTeamSize(it.toInt()) },
                        valueRange = 1f..20f,
                        steps = 18,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1", style = MaterialTheme.typography.labelSmall)
                        Text("20", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.create() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.creating && state.code.isNotBlank() && state.name.isNotBlank(),
            ) {
                if (state.creating) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Создать шаблон")
                }
            }
            Text(
                "После создания откроется экран шаблона. Меню ⋮ → «Добавить позицию» — заполните состав. " +
                "Например, для тележки на подоконники: ~25 инструментов + 10 расходников + 6 измерителей + " +
                "7 вспомогательных + 12 средств перемещения = 60 позиций.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// Shared
// ════════════════════════════════════════════════════════════════════

@Composable
private fun BoxScope.EmptyStateColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Column(
        Modifier.align(Alignment.Center).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
