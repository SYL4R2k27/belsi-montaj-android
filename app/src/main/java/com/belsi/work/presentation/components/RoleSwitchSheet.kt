package com.belsi.work.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.UserRole
import com.belsi.work.presentation.navigation.AppRoute

/**
 * FIX(2026-05-21) BELSI 2.1.0: рабочий переключатель ролей.
 *
 * Раньше мульти-роль был мёртв в release: чип в шапке монтажника был декоративным,
 * авто-пикер ([RoleSelectBottomSheet] в MainActivity) не срабатывал (currentRole
 * никогда не был null), а debug-FAB убран в release/2.0.1-internal.
 *
 * FIX(2026-05-22): `Activity.recreate()` НЕ переключал экран — NavController
 * восстанавливал сохранённый back stack (старый экран роли), игнорируя новый
 * startDestination (тот же класс граблей, из-за которого debug-свитчер делал
 * killProcess, а не recreate). Теперь — ЯВНАЯ навигация на домашний экран новой
 * роли с очисткой стека (`popUpTo(0)`), плюс [BrandRoleSwitcherViewModel.setActiveRole]
 * обновляет prefs (на случай рестарта).
 */
@Composable
fun BrandRoleSwitchSheet(
    show: Boolean,
    navController: NavController,
    vm: BrandRoleSwitcherViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
    if (!show) return
    if (state.availableRoles.size <= 1) {
        onDismiss()
        return
    }
    RoleSelectBottomSheet(
        availableRoles = state.availableRoles,
        currentRole = state.currentRole,
        onRoleSelected = { role ->
            onDismiss()
            if (role != state.currentRole) {
                vm.setActiveRole(role) {
                    runCatching {
                        navController.navigate(homeRouteForRole(role)) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
        },
        onDismiss = onDismiss,
    )
}

/** Домашний маршрут для роли (синхронно с MainActivity.startDestination). */
fun homeRouteForRole(role: UserRole): String = when (role) {
    UserRole.FOREMAN -> AppRoute.ForemanMain.route
    UserRole.COORDINATOR -> AppRoute.CoordinatorMain.route
    UserRole.CURATOR -> AppRoute.CuratorMain.route
    UserRole.PRODUCTION_CHIEF -> AppRoute.ProductionChiefMain.route
    UserRole.SENIOR_WORKER -> AppRoute.SeniorWorkerMain.route
    UserRole.WORKER -> AppRoute.WorkerMain.route
    UserRole.SUPPLIER -> AppRoute.SupplierMain.route
    UserRole.ENGINEER -> AppRoute.EngineerMain.route
    UserRole.DRIVER -> AppRoute.DriverHome.route
    UserRole.LOGISTICIAN -> AppRoute.LogisticianHome.route
    UserRole.INSTALLER -> AppRoute.Main.route
}

/** true если у пользователя >1 активной роли — показывать ли аффорданс переключения. */
@Composable
fun rememberIsMultiRole(vm: BrandRoleSwitcherViewModel = hiltViewModel()): Boolean {
    val state by vm.uiState.collectAsState()
    return state.availableRoles.size > 1
}
