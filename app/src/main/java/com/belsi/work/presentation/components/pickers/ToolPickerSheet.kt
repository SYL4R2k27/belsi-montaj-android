package com.belsi.work.presentation.components.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.tool_kit.ToolCatalogItem
import com.belsi.work.data.repositories.ToolKitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-18 Phase 3) BELSI 2.0.1: Reusable bottom-sheet picker для tool из
 * каталога. Используется в AddItemDialog (создание позиции kit) для выбора
 * tool_id из existing tools таблицы.
 */

data class ToolPickerUi(
    val tools: List<ToolCatalogItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ToolPickerViewModel @Inject constructor(
    private val repo: ToolKitRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ToolPickerUi())
    val state: StateFlow<ToolPickerUi> = _state.asStateFlow()
    private var searchJob: Job? = null

    init { load(null, null, null) }

    fun load(search: String?, kind: String?, category: String?) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // Простой debounce — 250ms
            delay(250)
            _state.update { it.copy(loading = true, error = null) }
            repo.toolsCatalog(
                search = search?.takeIf { it.isNotBlank() },
                kind = kind?.takeIf { it.isNotBlank() },
                category = category?.takeIf { it.isNotBlank() },
            )
                .onSuccess { list -> _state.update { it.copy(loading = false, tools = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPickerSheet(
    selectedId: String?,
    onPick: (ToolCatalogItem) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Выберите инструмент",
    initialKindFilter: String? = null,  // 'tool' | 'consumable' | null
    viewModel: ToolPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var search by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf(initialKindFilter) }

    LaunchedEffect(search, kindFilter) {
        viewModel.load(search.trim().ifBlank { null }, kindFilter, null)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 600.dp)) {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Поиск") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (search.isNotEmpty()) {
                    { IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } }
                } else null,
                singleLine = true,
            )
            // Filter chips: tool / consumable / all
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = kindFilter == null,
                    onClick = { kindFilter = null },
                    label = { Text("Все") },
                )
                FilterChip(
                    selected = kindFilter == "tool",
                    onClick = { kindFilter = "tool" },
                    label = { Text("Инструмент") },
                )
                FilterChip(
                    selected = kindFilter == "consumable",
                    onClick = { kindFilter = "consumable" },
                    label = { Text("Расходники") },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.loading && state.tools.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.tools.isEmpty() ->
                        Text("Ничего не найдено", Modifier.align(Alignment.Center))
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.tools, key = { it.id }) { t ->
                            val isSelected = t.id == selectedId
                            ListItem(
                                modifier = Modifier.clickable { onPick(t) },
                                headlineContent = {
                                    Text(t.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    val parts = listOfNotNull(
                                        t.category,
                                        t.description?.takeIf { it.isNotBlank() },
                                        "доступно: ${t.quantity}",
                                    )
                                    Text(parts.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    ) {
                                        Icon(
                                            if (t.kind == "consumable") Icons.Default.Inventory2 else Icons.Default.Build,
                                            null,
                                            Modifier.padding(8.dp).size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                trailingContent = if (isSelected) {
                                    { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
