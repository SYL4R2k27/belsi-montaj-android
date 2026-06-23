package com.belsi.work.presentation.components.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalShipping
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
import com.belsi.work.data.repositories.ToolTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-18) BELSI 2.0.1: Reusable bottom-sheet picker для driver/logist.
 * Использует backend /tools/transfers/drivers endpoint — он же был исправлен
 * (routing conflict) в fix-волне.
 * Поле `busy: true` показывает индикатор «занят активной поставкой».
 */

data class DriverItem(
    val id: String,
    val name: String,
    val phone: String,
    val busy: Boolean,
)

data class DriverPickerUi(
    val drivers: List<DriverItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DriverPickerViewModel @Inject constructor(
    private val repo: ToolTransferRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DriverPickerUi())
    val state: StateFlow<DriverPickerUi> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.listDriversForReturn()
                .onSuccess { list ->
                    val items = list.map {
                        DriverItem(
                            id = it.id,
                            name = it.name ?: it.phone ?: "(без имени)",
                            phone = it.phone ?: "—",
                            busy = it.busy,
                        )
                    }
                    _state.update { it.copy(loading = false, drivers = items) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverPickerSheet(
    selectedId: String?,
    onPick: (DriverItem) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Выберите водителя",
    viewModel: DriverPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 500.dp)) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${state.drivers.count { !it.busy }} свободны / ${state.drivers.count { it.busy }} заняты",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.loading && state.drivers.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.drivers.isEmpty() ->
                        Text("Нет водителей", Modifier.align(Alignment.Center))
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.drivers, key = { it.id }) { d ->
                            val isSelected = d.id == selectedId
                            ListItem(
                                modifier = Modifier.clickable { onPick(d) },
                                headlineContent = {
                                    Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = { Text(d.phone) },
                                leadingContent = {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = if (d.busy)
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    ) {
                                        Icon(
                                            Icons.Default.LocalShipping, null,
                                            Modifier.padding(8.dp).size(20.dp),
                                            tint = if (d.busy)
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                trailingContent = {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    } else if (d.busy) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("занят", style = MaterialTheme.typography.labelSmall) },
                                        )
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
