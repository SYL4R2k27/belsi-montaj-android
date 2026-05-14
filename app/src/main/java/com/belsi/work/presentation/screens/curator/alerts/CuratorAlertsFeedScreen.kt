package com.belsi.work.presentation.screens.curator.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.remote.api.CuratorApi
import com.belsi.work.data.remote.api.CuratorAlertItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-14) BELSI 2.0.1: единая лента AI-алертов и критичных событий
 * для куратора. Раньше куратору нужно было ходить по табам — теперь одна лента.
 */

data class AlertsFeedState(
    val loading: Boolean = false,
    val items: List<CuratorAlertItemDto> = emptyList(),
    val severityFilter: String = "info",  // info | warning | error | critical
    val error: String? = null,
)

@HiltViewModel
class CuratorAlertsFeedViewModel @Inject constructor(
    private val curatorApi: CuratorApi,
) : ViewModel() {

    private val _state = MutableStateFlow(AlertsFeedState())
    val state: StateFlow<AlertsFeedState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val resp = curatorApi.getAlertsFeed(_state.value.severityFilter)
                if (resp.isSuccessful) {
                    _state.value = _state.value.copy(
                        loading = false,
                        items = resp.body() ?: emptyList(),
                    )
                } else {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = "HTTP ${resp.code()}",
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun setSeverityFilter(f: String) {
        _state.value = _state.value.copy(severityFilter = f)
        load()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorAlertsFeedScreen(
    navController: NavController,
    vm: CuratorAlertsFeedViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Лента алертов") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Фильтр по severity
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "info" to "Всё",
                    "warning" to "⚠ Warning+",
                    "error" to "🔴 Error+",
                    "critical" to "🚨 Critical",
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = state.severityFilter == key,
                        onClick = { vm.setSeverityFilter(key) },
                        label = { Text(label, fontSize = 12.sp) },
                    )
                }
            }
            HorizontalDivider()

            when {
                state.loading && state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Алертов нет", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Всё под контролем",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.items, key = { it.id }) { alert ->
                            AlertCard(alert, onClick = {
                                alert.deepLink?.let { link ->
                                    runCatching { navController.navigate(link) }
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: CuratorAlertItemDto, onClick: () -> Unit) {
    val (bgColor, fgColor) = when (alert.severity) {
        "critical" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        "error" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) to MaterialTheme.colorScheme.error
        "warning" -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(fgColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    alert.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    alert.severity.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = fgColor,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                alert.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
            if (alert.actorName != null || alert.createdAt != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        alert.actorName,
                        alert.createdAt?.take(16)?.replace("T", " "),
                    ).joinToString(" · "),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
