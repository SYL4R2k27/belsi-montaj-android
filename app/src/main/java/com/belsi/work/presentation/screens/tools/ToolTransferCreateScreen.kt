package com.belsi.work.presentation.screens.tools

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.tool_transfer.InventoryItemDto
import com.belsi.work.presentation.components.role.RoleEmptyState
import com.belsi.work.presentation.components.role.RolePrimaryButton
import com.belsi.work.presentation.components.role.RoleSectionHeader
import com.belsi.work.presentation.components.role.RoleStatusPill
import com.belsi.work.presentation.components.role.Severity

/**
 * FIX(2026-05-12) build19 Этап3: Экран комплектатора «Сформировать передачу».
 *
 * Flow:
 *  1. Из route принимается опц. siteObjectId/batchId (пред-выбор)
 *  2. Загружается warehouse-inventory
 *  3. Юзер чекбоксами выбирает tools + ставит qty
 *  4. Submit → POST /supplier/tool-transfers/bulk → возврат назад
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTransferCreateScreen(
    navController: NavController,
    siteObjectId: String?,
    batchId: String?,
    viewModel: ToolTransferCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(siteObjectId, batchId) {
        viewModel.setSiteObject(siteObjectId)
        viewModel.setBatchId(batchId)
        viewModel.loadWarehouse()
    }

    // После успешной отправки — назад
    LaunchedEffect(state.createdTransferIds) {
        if (state.createdTransferIds.isNotEmpty()) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сформировать передачу") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                // FIX(2026-06-02): navigationBarsPadding — кнопка над системной навигацией Android.
                Column(modifier = Modifier.navigationBarsPadding().padding(16.dp)) {
                    Text(
                        "Выбрано: ${state.selectedToolIds.size} позиций",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    RolePrimaryButton(
                        text = if (state.isSending) "Отправка…" else "Передать на объект",
                        onClick = { viewModel.submit { /* nav handled in LaunchedEffect */ } },
                        enabled = !state.isSending && state.selectedToolIds.isNotEmpty() && state.toSiteObjectId != null,
                    )
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (state.warehouseInventory.isEmpty() && !state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                RoleEmptyState(
                    emoji = "📦",
                    title = "Склад пуст",
                    subtitle = "Нет инструментов доступных для передачи",
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (siteObjectId != null) {
                item {
                    RoleSectionHeader(
                        title = "Назначение",
                        subtitle = "Объект: ${siteObjectId.take(8)}…  ${batchId?.let { "· Партия: ${it.take(8)}…" } ?: ""}",
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = state.comment,
                    onValueChange = viewModel::setComment,
                    label = { Text("Комментарий") },
                    placeholder = { Text("Кто, куда, для какой работы…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                )
            }

            // Группируем по категории
            val byCategory = state.warehouseInventory.groupBy { it.category ?: "Без категории" }
            byCategory.forEach { (cat, list) ->
                item { RoleSectionHeader(title = cat, subtitle = "${list.size} позиций") }
                items(list, key = { it.toolId }) { item ->
                    ToolItemRow(
                        item = item,
                        isSelected = item.toolId in state.selectedToolIds,
                        qty = state.quantities[item.toolId] ?: 1,
                        onToggle = { viewModel.toggleToolSelected(item.toolId) },
                        onQtyChange = { viewModel.setQuantity(item.toolId, it) },
                    )
                }
            }

            if (state.error != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            state.error ?: "",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolItemRow(
    item: InventoryItemDto,
    isSelected: Boolean,
    qty: Int,
    onToggle: () -> Unit,
    onQtyChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row {
                    if (item.serialNumber != null) {
                        Text(
                            "S/N ${item.serialNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.inventoryNumber != null) {
                        Text(
                            " · № ${item.inventoryNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.kind == "consumable") {
                    RoleStatusPill(
                        text = "Расходник",
                        severity = Severity.WARNING,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (isSelected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onQtyChange(qty - 1) },
                        enabled = qty > 1,
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Default.Remove, null) }
                    Text(
                        "$qty / ${item.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = { onQtyChange(qty + 1) },
                        enabled = qty < item.quantity,
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Default.Add, null) }
                }
            }
        }
    }
}
