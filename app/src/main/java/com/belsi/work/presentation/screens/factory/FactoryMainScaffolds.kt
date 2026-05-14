package com.belsi.work.presentation.screens.factory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.belsi.work.presentation.screens.messenger.ChatHubScreen
import com.belsi.work.presentation.screens.profile.ProfileScreen

/**
 * FIX(2026-05-06): Scaffold-обёртки для 5 ролей производства (BELSI.Команда).
 *
 * До этого фикса каждая роль рендерилась как один экран без навигации.
 * Теперь у каждой роли — свой нижний таб-бар, как у монтажника:
 *   [роль-специфичный экран] + [Партии] + [Чат] + [Профиль]
 *
 * Профиль и Чат — общие компоненты (ProfileScreen, ChatHubScreen),
 * не зависят от роли. Они уже работают для монтажников и других
 * существующих ролей, поэтому переиспользуются как есть.
 *
 * Поскольку API для SeniorWorker / ProductionChief / Supplier / Engineer
 * пока в разработке (production_batches.py / factory_shifts.py готовы,
 * но не задеплоены), главные экраны этих ролей пока показывают
 * демо-данные из FactoryMockData. Чтобы это было честно и видно
 * руководителю при прокликивании — добавлен баннер "ДЕМО".
 */

// ─────────────────────────────────────────────────────────────────
//  Worker (рабочий) — линейный исполнитель на смене
// ─────────────────────────────────────────────────────────────────
private enum class WorkerTab(val title: String, val icon: ImageVector) {
    SHIFT("Смена", Icons.Default.AccessTime),
    BATCHES("Партии", Icons.Default.Widgets),
    CHAT("Чат", Icons.Default.Chat),
    PROFILE("Профиль", Icons.Default.Person),
}

@Composable
fun WorkerMainScaffold(navController: NavController) {
    var tab by remember { mutableStateOf(WorkerTab.SHIFT) }
    BackHandler { /* блок возврата */ }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            WorkerTab.entries.forEach { t ->
                item(
                    icon = { Icon(t.icon, contentDescription = t.title) },
                    label = { Text(t.title) },
                    selected = tab == t,
                    onClick = { tab = t },
                )
            }
        }
    ) {
        when (tab) {
            WorkerTab.SHIFT -> WorkerMainScreen(navController)
            WorkerTab.BATCHES -> BatchListScreen(navController)
            WorkerTab.CHAT -> ChatHubScreen(navController)
            WorkerTab.PROFILE -> ProfileScreen(navController)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  SeniorWorker (старший рабочий) — бригадир производства
// ─────────────────────────────────────────────────────────────────
private enum class SeniorWorkerTab(val title: String, val icon: ImageVector) {
    BRIGADE("Бригада", Icons.Default.Groups),
    IDLES("Простои", Icons.Default.Pause),
    BATCHES("Партии", Icons.Default.Widgets),
    CHAT("Чат", Icons.Default.Chat),
    PROFILE("Профиль", Icons.Default.Person),
}

@Composable
fun SeniorWorkerMainScaffold(navController: NavController) {
    var tab by remember { mutableStateOf(SeniorWorkerTab.BRIGADE) }
    BackHandler { }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            SeniorWorkerTab.entries.forEach { t ->
                item(
                    icon = { Icon(t.icon, contentDescription = t.title) },
                    label = { Text(t.title) },
                    selected = tab == t,
                    onClick = { tab = t },
                )
            }
        }
    ) {
        when (tab) {
            SeniorWorkerTab.BRIGADE -> SeniorWorkerMainScreen(navController)
            // FIX(2026-05-12) build14: вместо формы «свой простой» — вид простоев бригады
            SeniorWorkerTab.IDLES -> SeniorWorkerIdleScreen(navController)
            SeniorWorkerTab.BATCHES -> BatchListScreen(navController)
            SeniorWorkerTab.CHAT -> ChatHubScreen(navController)
            SeniorWorkerTab.PROFILE -> ProfileScreen(navController)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  ProductionChief (нач. производства) — руководитель фабрики
// ─────────────────────────────────────────────────────────────────
private enum class ChiefTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Дашборд", Icons.Default.Dashboard),
    BATCHES("Партии", Icons.Default.Widgets),
    BRIGADES("Бригады", Icons.Default.Groups),
    CHAT("Чат", Icons.Default.Chat),
    PROFILE("Профиль", Icons.Default.Person),
}

@Composable
fun ProductionChiefMainScaffold(navController: NavController) {
    var tab by remember { mutableStateOf(ChiefTab.DASHBOARD) }
    BackHandler { }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            ChiefTab.entries.forEach { t ->
                item(
                    icon = { Icon(t.icon, contentDescription = t.title) },
                    label = { Text(t.title) },
                    selected = tab == t,
                    onClick = { tab = t },
                )
            }
        }
    ) {
        when (tab) {
            ChiefTab.DASHBOARD -> ProductionChiefMainScreen(navController)
            ChiefTab.BATCHES -> BatchListScreen(navController)
            // FIX(2026-05-12) build14: реальный список ВСЕХ бригад (раньше — личная Старшего)
            ChiefTab.BRIGADES -> ProductionChiefBrigadesScreen(navController)
            ChiefTab.CHAT -> ChatHubScreen(navController)
            ChiefTab.PROFILE -> ProfileScreen(navController)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Supplier (снабженец) — материалы, заявки на закупку
// ─────────────────────────────────────────────────────────────────
private enum class SupplierTab(val title: String, val icon: ImageVector) {
    REQUESTS("Заявки", Icons.Default.Assignment),
    INVENTORY("Склад", Icons.Default.Inventory),
    BATCHES("Партии", Icons.Default.Widgets),
    CHAT("Чат", Icons.Default.Chat),
    PROFILE("Профиль", Icons.Default.Person),
}

@Composable
fun SupplierMainScaffold(navController: NavController) {
    var tab by remember { mutableStateOf(SupplierTab.REQUESTS) }
    BackHandler { }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            SupplierTab.entries.forEach { t ->
                item(
                    icon = { Icon(t.icon, contentDescription = t.title) },
                    label = { Text(t.title) },
                    selected = tab == t,
                    onClick = { tab = t },
                )
            }
        }
    ) {
        when (tab) {
            // FIX(2026-05-12) build14: внешний навбар → внутренний tab синхронизирован
            SupplierTab.REQUESTS -> SupplierMainScreen(navController, initialTab = 0)
            SupplierTab.INVENTORY -> SupplierMainScreen(navController, initialTab = 1)
            SupplierTab.BATCHES -> BatchListScreen(navController)
            SupplierTab.CHAT -> ChatHubScreen(navController)
            SupplierTab.PROFILE -> ProfileScreen(navController)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Engineer (инженер) — техническое сопровождение партий
// ─────────────────────────────────────────────────────────────────
private enum class EngineerTab(val title: String, val icon: ImageVector) {
    TASKS("Задачи", Icons.Default.Engineering),
    BATCHES("Партии", Icons.Default.Widgets),
    TOOLS("Инструменты", Icons.Default.Build),
    CHAT("Чат", Icons.Default.Chat),
    PROFILE("Профиль", Icons.Default.Person),
}

@Composable
fun EngineerMainScaffold(navController: NavController) {
    var tab by remember { mutableStateOf(EngineerTab.TASKS) }
    BackHandler { }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            EngineerTab.entries.forEach { t ->
                item(
                    icon = { Icon(t.icon, contentDescription = t.title) },
                    label = { Text(t.title) },
                    selected = tab == t,
                    onClick = { tab = t },
                )
            }
        }
    ) {
        when (tab) {
            EngineerTab.TASKS -> EngineerMainScreen(navController)
            EngineerTab.BATCHES -> BatchListScreen(navController)
            // FIX(2026-05-12) build14: реальный tools catalog из БД (16 SKU seed)
            EngineerTab.TOOLS -> EngineerToolsScreen(navController)
            EngineerTab.CHAT -> ChatHubScreen(navController)
            EngineerTab.PROFILE -> ProfileScreen(navController)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Demo-баннер — честно говорит "это пока тестовые данные"
// ─────────────────────────────────────────────────────────────────
@Composable
private fun DemoBanner(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "ДЕМО",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "  Тестовые данные — API в разработке",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = 12.sp,
            )
        }
        content()
    }
}
