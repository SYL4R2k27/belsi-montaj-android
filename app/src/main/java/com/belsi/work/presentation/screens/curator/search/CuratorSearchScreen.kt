package com.belsi.work.presentation.screens.curator.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Support
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.navigation.AppRoute

/**
 * BELSI 2.1.0 — универсальный поиск куратора (открывается из 🔍 в шапке).
 * Ищет одновременно: разделы приложения, людей, объекты + ссылку на AI-поиск фото.
 * Заменяет отдельный пункт «AI-поиск фото» (он теперь часть этого поиска).
 */
private data class SearchDest(val label: String, val icon: ImageVector, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorSearchScreen(
    navController: NavController,
    viewModel: CuratorSearchViewModel = hiltViewModel(),
) {
    val users by viewModel.users.collectAsState()
    val objects by viewModel.objects.collectAsState()
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val q = query.trim()
    val ql = q.lowercase()

    val sections = remember {
        listOf(
            SearchDest("Настройки", Icons.Filled.Settings, AppRoute.Settings.route),
            SearchDest("Аналитика", Icons.Filled.BarChart, AppRoute.CuratorAnalytics.route),
            SearchDest("AI Dashboard", Icons.Filled.AutoAwesome, AppRoute.AiDashboard.route),
            SearchDest("AI-поиск фото", Icons.Filled.Search, AppRoute.AiPhotoSearch.route),
            SearchDest("Партии (все)", Icons.Filled.Inventory, AppRoute.BatchList.route),
            SearchDest("Фото (вся лента)", Icons.Filled.PhotoLibrary, AppRoute.CuratorPhotos.route),
            SearchDest("Тикеты", Icons.Filled.Support, AppRoute.CuratorSupport.route),
            SearchDest("Инструменты", Icons.Filled.Build, AppRoute.CuratorTools.route),
            SearchDest("Передачи инструмента", Icons.Filled.Inventory2, AppRoute.ToolTransferHub.createRoute("incoming")),
            SearchDest("Возвраты инструмента", Icons.Filled.AssignmentReturn, AppRoute.CuratorReturns.route),
            SearchDest("Тележки / комплекты", Icons.Filled.Inventory, AppRoute.ToolKitList.route),
            SearchDest("Мой профиль", Icons.Filled.Person, AppRoute.Profile.route),
        )
    }

    val filteredSections = if (ql.isBlank()) sections else sections.filter { it.label.lowercase().contains(ql) }
    val filteredUsers = if (ql.isBlank()) emptyList() else users.filter {
        it.displayName.lowercase().contains(ql) || it.phone.contains(q)
    }.take(15)
    val filteredObjects = if (ql.isBlank()) emptyList() else objects.filter {
        it.name.lowercase().contains(ql) || (it.address?.lowercase()?.contains(ql) == true)
    }.take(15)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text("Поиск: люди, объекты, разделы…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Очистить")
                                }
                            }
                        },
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // Фото — через AI-поиск
            if (q.isNotBlank()) {
                item { SearchSectionHeader("Фото") }
                item {
                    ListItem(
                        headlineContent = { Text("Искать «$q» среди фото") },
                        supportingContent = { Text("AI-поиск по фото объектов") },
                        leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                        modifier = Modifier.clickable { navController.navigate(AppRoute.AiPhotoSearch.route) },
                    )
                }
            }
            if (filteredUsers.isNotEmpty()) {
                item { SearchSectionHeader("Люди · ${filteredUsers.size}") }
                items(filteredUsers, key = { it.id }) { u ->
                    ListItem(
                        headlineContent = { Text(u.displayName) },
                        supportingContent = { Text(u.phone) },
                        leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                        modifier = Modifier.clickable { navController.navigate(AppRoute.CuratorUserDetail.createRoute(u.id)) },
                    )
                }
            }
            if (filteredObjects.isNotEmpty()) {
                item { SearchSectionHeader("Объекты · ${filteredObjects.size}") }
                items(filteredObjects, key = { it.id }) { o ->
                    ListItem(
                        headlineContent = { Text(o.name) },
                        supportingContent = { o.address?.let { Text(it) } },
                        leadingContent = { Icon(Icons.Filled.Business, contentDescription = null) },
                        modifier = Modifier.clickable { navController.navigate(AppRoute.ObjectV3.createRoute(o.id)) },
                    )
                }
            }
            if (filteredSections.isNotEmpty()) {
                item { SearchSectionHeader("Разделы") }
                items(filteredSections, key = { it.label }) { d ->
                    ListItem(
                        headlineContent = { Text(d.label) },
                        leadingContent = { Icon(d.icon, contentDescription = null) },
                        modifier = Modifier.clickable { navController.navigate(d.route) },
                    )
                }
            }
            if (q.isNotBlank() && filteredUsers.isEmpty() && filteredObjects.isEmpty() && filteredSections.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}
