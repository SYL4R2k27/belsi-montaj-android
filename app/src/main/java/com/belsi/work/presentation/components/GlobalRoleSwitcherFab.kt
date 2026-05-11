package com.belsi.work.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FIX(2026-05-06) → FIX(2026-05-10): Глобальная плавающая кнопка переключения ролей.
 *
 * Виден на ЛЮБОМ экране (даже DriverPlayground без bottom nav).
 * Решает проблему «застрял на экране без выхода».
 *
 * История фиксов:
 * - build4: появилась в Профиле (но не была видна на DriverPlayground)
 * - build5: глобальная плавающая в TopEnd — ОКАЗАЛАСЬ НЕ КЛИКАБЕЛЬНОЙ
 *   (uiautomator dump показал clickable=false, плюс bounds в пикселях,
 *   а не в dp). Использовали кастомный Box + clickable вместо стандартного
 *   FloatingActionButton — это и сломало семантику.
 * - build6 (этот): используем стандартный ExtendedFloatingActionButton
 *   из Material3, с явной надписью "СМЕНА РОЛИ (ТЕСТ)" — попробуй не заметить.
 *   Размещён в BottomEnd с отступом, чтобы не конфликтовать с TopAppBar
 *   и BottomNavigationBar.
 *
 * Только в debug-сборке (вызывается под if (BuildConfig.DEBUG) в MainActivity).
 */
@Composable
fun BoxScope.GlobalRoleSwitcherFab() {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        RoleSwitcherDialog(onDismiss = { showDialog = false })
    }

    // Стандартный Material3 ExtendedFloatingActionButton — гарантирует
    // правильную semantics (clickable=true), правильную обработку density
    // и правильный размер (56dp по высоте).
    ExtendedFloatingActionButton(
        onClick = { showDialog = true },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            // Отступ снизу чтобы не закрывать BottomNavigationBar
            // (который ~80dp, плюс system bars).
            .padding(end = 16.dp, bottom = 96.dp),
        containerColor = Color(0xFFF59E0B),
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp,
        ),
    ) {
        Icon(
            Icons.Default.SwapHoriz,
            contentDescription = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "ТЕСТ",
            fontSize = 13.sp,
        )
    }
}
