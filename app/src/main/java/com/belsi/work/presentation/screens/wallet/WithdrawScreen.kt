package com.belsi.work.presentation.screens.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController

/**
 * Ф2 (2026-06-15): вывод средств теперь — ModalBottomSheet внутри кошелька
 * (WithdrawSheet в WalletScreen.kt). Этот экран оставлен ради совместимости
 * маршрута AppRoute.Withdraw — при заходе просто возвращает назад.
 */
@Composable
fun WithdrawScreen(navController: NavController) {
    LaunchedEffect(Unit) { navController.popBackStack() }
}
