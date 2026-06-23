package com.belsi.work.presentation.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.belsi.work.presentation.screens.installer.InstallerHomeScreen

/**
 * Главный экран монтажника (BELSI 2.1.0).
 *
 * УПРОЩЕНО: убран нижний таб-бар (6 вкладок давали перегруз интерфейса).
 * Теперь один главный экран [InstallerHomeScreen]; всё вторичное (Задачи / Мои инструменты /
 * История смён / Профиль / Настройки / Выход) — в ⋮-меню сверху. Связь с куратором и камера —
 * как действия на самом экране (CTA «Куратор» + FAB камера).
 *
 * Старые экраны остаются доступны как маршруты (InstallerTasks/ToolsList/ShiftHistory/Profile/…),
 * прежний таб-контент (PhotosScreenSimple/ChatHubScreen) не удалён из кода.
 */
@Composable
fun MainScreen(navController: NavController) {
    // Блокируем возврат назад к экрану выбора роли.
    BackHandler { /* no-op */ }

    InstallerHomeScreen(navController = navController)
}
