package com.belsi.work.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.belsi.work.utils.NetworkMonitor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * FIX(2026-05-11) BELSI 2.0.0 build8: глобальный offline-баннер.
 *
 * Показывается ВСЕМ юзерам когда:
 *  - нет сети (NetworkMonitor.isOnline = false), ИЛИ
 *  - есть pending_actions в очереди (PendingBadge сигнализирует что что-то ждёт отправки)
 *
 * Текст подсказывает что происходит. Цвета — слабый amber background, не агрессивный red,
 * потому что offline-режим — нормальная штатная ситуация по бизнесу (московские шатдауны).
 *
 * Встраивается в MainActivity на самый верх Box-а, поверх AppNavHost. Расположен под
 * статус-баром (системная шторка), не перекрывает контент — занимает свою строку.
 *
 * Brandbook р.05 (Offline mode): «приложение продолжает работать, но честно говорит юзеру».
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfflineBannerEntryPoint {
    fun networkMonitor(): NetworkMonitor
    fun offlineQueue(): com.belsi.work.data.offline.OfflineQueueRepository
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ep = remember(context.applicationContext) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OfflineBannerEntryPoint::class.java,
        )
    }
    val isOnline by ep.networkMonitor().isOnline.collectAsState()
    val pendingCount by ep.offlineQueue().pendingCount
        .collectAsState(initial = 0)

    val visible = !isOnline || pendingCount > 0

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val (bg, fg, label) = when {
            !isOnline && pendingCount > 0 -> Triple(
                Color(0xFFD97706),  // amber 600
                Color.White,
                "Нет сети · $pendingCount действий ждут отправки",
            )
            !isOnline -> Triple(
                Color(0xFFD97706),
                Color.White,
                "Нет сети · работа сохраняется локально",
            )
            else -> Triple(
                Color(0xFF0EA5E9),  // sky 500
                Color.White,
                "Отправляем $pendingCount ${pluralActions(pendingCount)} …",
            )
        }

        Surface(color = bg) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        // под статус-баром
                        WindowInsets.statusBars.asPaddingValues()
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color = fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun pluralActions(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "действие"
        mod10 in 2..4 && mod100 !in 12..14 -> "действия"
        else -> "действий"
    }
}
