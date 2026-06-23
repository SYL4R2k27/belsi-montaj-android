package com.belsi.work.presentation.components.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
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
import com.belsi.work.data.remote.api.ObjectsApi
import com.belsi.work.data.remote.dto.objects.SiteObjectDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-18) BELSI 2.0.1: Reusable bottom-sheet picker для site_object.
 * Используется в ToolKitDispatchScreen + любых будущих формах с выбором объекта.
 */

data class SiteObjectPickerUi(
    val objects: List<SiteObjectDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SiteObjectPickerViewModel @Inject constructor(
    private val api: ObjectsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(SiteObjectPickerUi())
    val state: StateFlow<SiteObjectPickerUi> = _state.asStateFlow()

    fun load(search: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val resp = api.getObjects(status = null, search = search, limit = 200)
                if (resp.isSuccessful) {
                    _state.update { it.copy(loading = false, objects = resp.body().orEmpty()) }
                } else {
                    _state.update { it.copy(loading = false, error = "HTTP ${resp.code()}") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteObjectPickerSheet(
    selectedId: String?,
    onPick: (SiteObjectDto) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Выберите объект",
    viewModel: SiteObjectPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(search) {
        // debounce-имитация — запрос на каждое изменение, но с trim
        viewModel.load(search.trim().ifBlank { null })
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 600.dp)) {
            Text(
                title,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Поиск по названию или адресу") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) }
                    }
                },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.loading && state.objects.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.objects.isEmpty() ->
                        Text("Ничего не найдено", Modifier.align(Alignment.Center))
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.objects, key = { it.id }) { obj ->
                            val isSelected = obj.id == selectedId
                            ListItem(
                                modifier = Modifier.clickable { onPick(obj) },
                                headlineContent = {
                                    Text(obj.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = obj.address?.let {
                                    { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                },
                                leadingContent = {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    ) {
                                        Icon(
                                            Icons.Default.Business, null,
                                            Modifier.padding(8.dp).size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                trailingContent = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Default.Check, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
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
