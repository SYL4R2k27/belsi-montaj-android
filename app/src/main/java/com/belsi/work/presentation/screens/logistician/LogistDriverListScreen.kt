package com.belsi.work.presentation.screens.logistician

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.driver.DriverFleetItemDto
import com.belsi.work.data.repositories.LogistRepository
import com.belsi.work.utils.NavigationIntents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-11) BELSI 2.0.0 build12: главный экран мониторинга водителей у логиста.
 *
 * Бриф BELSI.Driver, секция «Мониторинг водителей»:
 *  - Список всех водителей с карточками
 *  - Каждая карточка: имя, аватар, статус смены (на линии / нет), активный маршрут,
 *    время последнего события
 *  - Клик по водителю → детальный экран
 *
 * Live-обновление: polling каждые 30 сек (как у foreman/curator в build10).
 */
@HiltViewModel
class LogistDriverListViewModel @Inject constructor(
    private val repo: LogistRepository,
) : ViewModel() {

    private val _drivers = MutableStateFlow<List<DriverFleetItemDto>>(emptyList())
    val drivers: StateFlow<List<DriverFleetItemDto>> = _drivers.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
        // FIX(2026-05-11) build12: live polling каждые 30s — бриф «логистик видит, где
        // находится каждый водитель, какие точки он уже посетил, в реальном времени».
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                refresh(silent = true)
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _loading.value = true
            repo.listDrivers()
                .onSuccess {
                    _drivers.value = it
                    _error.value = null
                }
                .onFailure {
                    if (!silent) _error.value = it.message ?: "Ошибка загрузки"
                }
            _loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogistDriverListScreen(
    onDriverClick: (driverId: String) -> Unit,
    onCreateRoute: () -> Unit,
    onBack: () -> Unit = {},
    vm: LogistDriverListViewModel = hiltViewModel(),
) {
    val drivers by vm.drivers.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(error) { error?.let { snackbarHost.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Водители · ${drivers.size}") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRoute) {
                Icon(Icons.Default.Add, "Создать маршрут")
            }
        },
    ) { padding ->
        if (loading && drivers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (drivers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Водителей нет", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "В системе пока не зарегистрировано ни одного водителя",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            // Group by status
            val onLine = drivers.filter { it.status == "active" }
            val free = drivers.filter { it.status == "free" }
            val offline = drivers.filter { it.status == "offline" }

            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // KPI bar
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiPill("🟢 На линии", onLine.size, Color(0xFF10B981), modifier = Modifier.weight(1f))
                        KpiPill("⚪️ Свободны", free.size, Color(0xFF94A3B8), modifier = Modifier.weight(1f))
                        KpiPill("⚫️ Off", offline.size, MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                }

                if (onLine.isNotEmpty()) {
                    item { SectionLabel("НА ЛИНИИ") }
                    items(onLine, key = { it.id }) { drv ->
                        DriverCard(
                            driver = drv,
                            onClick = { onDriverClick(drv.id) },
                            onCall = { NavigationIntents.makePhoneCall(context, drv.phone) },
                        )
                    }
                }
                if (free.isNotEmpty()) {
                    item { SectionLabel("СВОБОДНЫ") }
                    items(free, key = { it.id }) { drv ->
                        DriverCard(
                            driver = drv,
                            onClick = { onDriverClick(drv.id) },
                            onCall = { NavigationIntents.makePhoneCall(context, drv.phone) },
                        )
                    }
                }
                if (offline.isNotEmpty()) {
                    item { SectionLabel("НЕ НА СМЕНЕ") }
                    items(offline, key = { it.id }) { drv ->
                        DriverCard(
                            driver = drv,
                            onClick = { onDriverClick(drv.id) },
                            onCall = { NavigationIntents.makePhoneCall(context, drv.phone) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiPill(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$value",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DriverCard(
    driver: DriverFleetItemDto,
    onClick: () -> Unit,
    onCall: () -> Unit,
) {
    val statusInfo = when (driver.status) {
        "active" -> Triple(Color(0xFF10B981), "🟢", "На линии")
        "free" -> Triple(Color(0xFF94A3B8), "⚪️", "Свободен")
        "offline" -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "⚫️", "Не на смене")
        else -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "•", driver.status)
    }
    val (statusColor, statusEmoji, statusLabel) = statusInfo

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (driver.name ?: driver.phone).take(2).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    driver.name ?: driver.phone,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$statusEmoji $statusLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                    )
                    driver.currentProgress?.let {
                        Text(
                            " · $it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (driver.currentRouteId != null) {
                    Text(
                        "Маршрут активен",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onCall) {
                Icon(Icons.Default.Phone, "Позвонить", tint = MaterialTheme.colorScheme.primary)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
