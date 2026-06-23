package com.belsi.work.presentation.components

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * FIX(2026-06-23): универсальная inline-кнопка «Сменить роль» для экранов БЕЗ вкладки Профиль.
 *
 * Корень: переключатель ролей вшит только в монтажные шапки (curator/foreman/installer) и в
 * ProfileScreen (вкладка Профиль есть у производства через FactoryMainScaffolds). А логист и
 * водитель — standalone-экраны без вкладки Профиль → доступа к переключателю не было → юзер,
 * переключившись на них, застревал (нечем вернуться). Эта кнопка даёт переключатель в любом экране.
 *
 * Проводка 1:1 как в ProfileScreen: BrandRoleSwitcherViewModel + RoleSelectBottomSheet +
 * recreate() Activity (чтобы навигация/VM перечитали users.role). Показывается только при >1 роли.
 */
@Composable
fun RoleSwitchInlineButton(modifier: Modifier = Modifier) {
    val vm: BrandRoleSwitcherViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()
    var show by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    // Одна роль (обычный юзер) → кнопку не показываем.
    if (state.availableRoles.size <= 1) return

    if (show) {
        RoleSelectBottomSheet(
            availableRoles = state.availableRoles,
            currentRole = state.currentRole,
            onRoleSelected = { role ->
                vm.setActiveRole(role)
                show = false
                (ctx as? Activity)?.recreate()
            },
            onDismiss = { show = false },
        )
    }

    FilledTonalButton(onClick = { show = true }, modifier = modifier) {
        Icon(Icons.Default.ManageAccounts, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Сменить роль (${state.availableRoles.size})")
    }
}
