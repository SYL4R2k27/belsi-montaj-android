package com.belsi.work.presentation.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import com.belsi.work.presentation.screens.shift.ShiftScreen
import com.belsi.work.presentation.screens.photos.PhotosScreenSimple
import com.belsi.work.presentation.screens.profile.ProfileScreen
import com.belsi.work.presentation.screens.messenger.ChatHubScreen
import com.belsi.work.presentation.screens.tasks.InstallerTasksScreen

/**
 * FIX(2026-05-12) build18 P3: главный экран монтажника.
 * Добавлен таб TOOLS — «Мои инструменты», переход на ToolsList (с фильтром issued_to=me).
 */
@Composable
fun MainScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(MainTab.SHIFT) }

    // Блокируем возврат назад к экрану выбора роли
    BackHandler {
        // Ничего не делаем - блокируем возврат
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            MainTab.entries.forEach { tab ->
                item(
                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                    label = { Text(tab.title) },
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab }
                )
            }
        }
    ) {
        when (selectedTab) {
            MainTab.SHIFT -> ShiftScreen(navController = navController)
            MainTab.PHOTOS -> PhotosScreenSimple(navController = navController)
            MainTab.TASKS -> InstallerTasksScreen()
            // FIX(2026-05-12) build18 P3: «Мои инструменты» — открывается ToolsList в режиме installer.
            MainTab.TOOLS -> com.belsi.work.presentation.screens.tools.ToolsListScreen(navController = navController)
            MainTab.PROFILE -> ProfileScreen(navController = navController)
            MainTab.CHAT -> ChatHubScreen(navController = navController)
        }
    }
}

enum class MainTab(
    val title: String,
    val icon: ImageVector
) {
    SHIFT("Смена", Icons.Default.AccessTime),
    PHOTOS("Фото", Icons.Default.Photo),
    TASKS("Задачи", Icons.Default.Assignment),
    TOOLS("Инструменты", Icons.Default.Build),
    PROFILE("Профиль", Icons.Default.Person),
    CHAT("Чат", Icons.Default.Chat)
}
