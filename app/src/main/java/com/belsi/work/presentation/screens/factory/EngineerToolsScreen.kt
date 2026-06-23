package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import com.belsi.work.presentation.components.role.colors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.models.ToolCatalogItem
import com.belsi.work.data.repositories.ProductionRepository
// MaterialTheme.colorScheme.primary — package-internal в WorkerMainScreen.kt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) BELSI 2.0.0 build14: каталог инструментов из реальной таблицы tools_catalog.
 *
 * Заменяет дубль таба «Инструменты» который раньше вёл на EngineerMainScreen (Задачи).
 * Источник: GET /production/engineer/tools-catalog (build14 backend → tools_catalog table).
 * 16 SKU начальный seed: электроинструмент / станки / измерительные / СИЗ / расходники.
 */
@HiltViewModel
class EngineerToolsViewModel @Inject constructor(
    private val repo: ProductionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(
        val loading: Boolean = false,
        val tools: List<ToolCatalogItem> = emptyList(),
        val error: String? = null,
    )

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.getToolsCatalog()
                .onSuccess { _state.value = State(loading = false, tools = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }
}

@Composable
fun EngineerToolsScreen(
    navController: NavController,
    vm: EngineerToolsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val categories: List<String> = remember(state.tools) {
        state.tools.map { it.category }.distinct().sorted()
    }
    val visibleTools = if (selectedCategory == null) state.tools
        else state.tools.filter { it.category == selectedCategory }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
            Column(Modifier.padding(16.dp)) {
                Text("Каталог инструментов", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "${state.tools.size} позиций · ${categories.size} категорий",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            state.loading -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null -> Box(
                Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center,
            ) { Text(state.error ?: "Ошибка", color = MaterialTheme.colorScheme.error) }
            state.tools.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛠", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Инструментов нет", fontWeight = FontWeight.SemiBold)
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Category chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("Все", fontSize = 12.sp) },
                        )
                    }
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) },
                            )
                        }
                    }
                }
                items(visibleTools, key = { it.id }) { tool ->
                    ToolRow(tool)
                }
            }
        }
    }
}

@Composable
private fun ToolRow(tool: ToolCatalogItem) {
    // FIX(2026-05-12) build19 hotfix: категории — через Severity, единая система.
    val severity = when (tool.category) {
        "Электроинструмент" -> com.belsi.work.presentation.components.role.Severity.INFO
        "Станок"            -> com.belsi.work.presentation.components.role.Severity.AI
        "Измерительный"     -> com.belsi.work.presentation.components.role.Severity.SUCCESS
        "СИЗ"               -> com.belsi.work.presentation.components.role.Severity.WARNING
        "Расходник"         -> com.belsi.work.presentation.components.role.Severity.NEUTRAL
        else                -> com.belsi.work.presentation.components.role.Severity.PRIMARY
    }
    val categoryColor = severity.colors().first
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = categoryColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Build,
                        null,
                        tint = categoryColor,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row {
                    Text(
                        tool.category,
                        fontSize = 11.sp,
                        color = categoryColor,
                        fontWeight = FontWeight.Medium,
                    )
                    tool.inventoryNumber?.let {
                        Text(
                            " · $it",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!tool.description.isNullOrBlank()) {
                    Text(
                        tool.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (tool.isConsumable) {
                val (warnFg, warnBg) = com.belsi.work.presentation.components.role.Severity.WARNING.colors()
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = warnBg.copy(alpha = 0.5f),
                ) {
                    Text(
                        "Расходник",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        color = warnFg,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
