package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.utils.isExpandedScreen
import com.belsi.work.presentation.utils.shouldUseNavigationRail

/**
 * FIX(2026-05-11) BELSI 2.0.0 build4: универсальный wrapper list-detail для табов куратора.
 *
 * Брендбук foldable-tablet-design-guide раздел 10C (List-Detail expanded):
 *   «Для: Z Fold open landscape, 11" планшеты landscape.
 *    Idiomatic для управленческих экранов: список слева, детали выбранного справа.
 *    BELSI: CuratorMainScreen Tab «Тикеты», «Фото», «Бригады», «Люди».»
 *
 * Пропорции (брендбук):
 *   Compact:  list-only, на клик переход к detail-экрану через navigation
 *   Medium:   список max 720dp по центру
 *   Expanded: list 35-40% / detail 60-65%
 *   XLarge:   list 30% / detail 70%
 */
@Composable
fun <T : Any> AdaptiveListDetail(
    items: List<T>,
    keyOf: (T) -> String,
    listItem: @Composable (item: T, isSelected: Boolean, onClick: () -> Unit) -> Unit,
    detailContent: @Composable (item: T) -> Unit,
    emptyDetail: @Composable () -> Unit = { DefaultEmptyDetail() },
    listHeader: @Composable (() -> Unit)? = null,
    onCompactItemClick: ((item: T) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isExpanded = isExpandedScreen()
    var selectedId by remember(items.size) {
        mutableStateOf(items.firstOrNull()?.let(keyOf))
    }
    val selectedItem = items.firstOrNull { keyOf(it) == selectedId }

    if (isExpanded) {
        // ── Two-pane: список + детали ──
        Row(modifier = modifier.fillMaxSize()) {
            // List pane (~38%)
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                listHeader?.invoke()
                LazyColumn(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = keyOf) { item ->
                        listItem(item, keyOf(item) == selectedId) {
                            selectedId = keyOf(item)
                        }
                    }
                }
            }
            // Vertical divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            // Detail pane (~62%)
            Surface(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.background,
            ) {
                if (selectedItem != null) {
                    detailContent(selectedItem)
                } else {
                    emptyDetail()
                }
            }
        }
    } else {
        // ── Compact: список во всю ширину ──
        Column(modifier = modifier.fillMaxSize()) {
            listHeader?.invoke()
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = keyOf) { item ->
                    listItem(item, false) {
                        onCompactItemClick?.invoke(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultEmptyDetail() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("→", style = MaterialTheme.typography.displayLarge,
                 color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text(
                "Выберите элемент из списка слева",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Wrapper для контента который НЕ имеет list-detail структуры, но должен на широких
 * экранах быть ограничен max-width 1100dp и центрирован.
 * Брендбук foldable-tablet 16: «BatchScreens, PhotoGalleryScreen, etc — max-width + center».
 */
@Composable
fun AdaptiveContentMaxWidth(
    maxWidth: androidx.compose.ui.unit.Dp = 1100.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (shouldUseNavigationRail()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
                content()
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) { content() }
    }
}
