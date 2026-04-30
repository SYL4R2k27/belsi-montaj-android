package com.belsi.work.presentation.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Классификация размера окна по ширине
 * Соответствует Material Design 3 Window Size Classes
 */
enum class WindowWidthSizeClass {
    /** Ширина < 600dp - телефон в портретной ориентации */
    COMPACT,
    /** Ширина 600-840dp - телефон в ландшафте, маленький планшет */
    MEDIUM,
    /** Ширина >= 840dp - планшет, складное устройство в развёрнутом виде */
    EXPANDED
}

/**
 * Получить текущий класс размера окна
 */
@Composable
fun rememberWindowSizeClass(): WindowWidthSizeClass {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    return remember(screenWidthDp) {
        when {
            screenWidthDp < 600 -> WindowWidthSizeClass.COMPACT
            screenWidthDp < 840 -> WindowWidthSizeClass.MEDIUM
            else -> WindowWidthSizeClass.EXPANDED
        }
    }
}

/**
 * Получить ширину экрана в dp
 */
@Composable
fun screenWidthDp(): Int {
    return LocalConfiguration.current.screenWidthDp
}

/**
 * Проверка: компактный экран (телефон)
 */
@Composable
fun isCompactScreen(): Boolean {
    return rememberWindowSizeClass() == WindowWidthSizeClass.COMPACT
}

/**
 * Проверка: средний экран (ландшафт, маленький планшет)
 */
@Composable
fun isMediumScreen(): Boolean {
    return rememberWindowSizeClass() == WindowWidthSizeClass.MEDIUM
}

/**
 * Проверка: широкий экран (планшет, складное устройство)
 */
@Composable
fun isExpandedScreen(): Boolean {
    return rememberWindowSizeClass() == WindowWidthSizeClass.EXPANDED
}

/**
 * Адаптивный горизонтальный отступ
 */
@Composable
fun adaptiveHorizontalPadding(): Dp {
    return when (rememberWindowSizeClass()) {
        WindowWidthSizeClass.COMPACT -> 16.dp
        WindowWidthSizeClass.MEDIUM -> 24.dp
        WindowWidthSizeClass.EXPANDED -> 32.dp
    }
}

/**
 * Адаптивный вертикальный отступ
 */
@Composable
fun adaptiveVerticalPadding(): Dp {
    return when (rememberWindowSizeClass()) {
        WindowWidthSizeClass.COMPACT -> 16.dp
        WindowWidthSizeClass.MEDIUM -> 20.dp
        WindowWidthSizeClass.EXPANDED -> 24.dp
    }
}

/**
 * Адаптивный ContentPadding для списков
 */
@Composable
fun adaptiveContentPadding(): PaddingValues {
    val horizontal = adaptiveHorizontalPadding()
    val vertical = adaptiveVerticalPadding()
    return PaddingValues(horizontal = horizontal, vertical = vertical)
}

/**
 * Адаптивный spacing между элементами
 */
@Composable
fun adaptiveSpacing(): Dp {
    return when (rememberWindowSizeClass()) {
        WindowWidthSizeClass.COMPACT -> 8.dp
        WindowWidthSizeClass.MEDIUM -> 12.dp
        WindowWidthSizeClass.EXPANDED -> 16.dp
    }
}

/**
 * Адаптивная максимальная ширина контента
 * Для широких экранов ограничиваем контент по центру
 */
@Composable
fun adaptiveMaxWidth(): Dp {
    return when (rememberWindowSizeClass()) {
        WindowWidthSizeClass.COMPACT -> Dp.Infinity
        WindowWidthSizeClass.MEDIUM -> 720.dp
        WindowWidthSizeClass.EXPANDED -> 900.dp
    }
}

/**
 * Количество колонок для сетки карточек
 */
@Composable
fun adaptiveGridColumns(): Int {
    return when (rememberWindowSizeClass()) {
        WindowWidthSizeClass.COMPACT -> 1
        WindowWidthSizeClass.MEDIUM -> 2
        WindowWidthSizeClass.EXPANDED -> 3
    }
}

/**
 * GridCells для LazyVerticalGrid
 */
@Composable
fun adaptiveGridCells(): GridCells {
    return when (rememberWindowSizeClass()) {
        WindowWidthSizeClass.COMPACT -> GridCells.Fixed(1)
        WindowWidthSizeClass.MEDIUM -> GridCells.Fixed(2)
        WindowWidthSizeClass.EXPANDED -> GridCells.Adaptive(minSize = 300.dp)
    }
}

/**
 * Адаптивный Row/Column:
 * - На узких экранах: Column (вертикально)
 * - На широких экранах: Row (горизонтально)
 */
@Composable
fun AdaptiveRowColumn(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    forceColumn: Boolean = false,
    content: @Composable () -> Unit
) {
    val windowSize = rememberWindowSizeClass()

    if (forceColumn || windowSize == WindowWidthSizeClass.COMPACT) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = verticalArrangement
        ) {
            content()
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = horizontalArrangement
        ) {
            content()
        }
    }
}

/**
 * Контейнер с адаптивной максимальной шириной
 * На широких экранах центрирует контент
 */
@Composable
fun AdaptiveContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

/**
 * Определяет, нужно ли показывать Navigation Rail вместо Bottom Navigation
 * На широких экранах лучше использовать боковую навигацию
 */
@Composable
fun shouldUseNavigationRail(): Boolean {
    return rememberWindowSizeClass() == WindowWidthSizeClass.EXPANDED
}

/**
 * Адаптивная высота изображения
 */
@Composable
fun adaptiveImageHeight(): Dp {
    return when (rememberWindowSizeClass()) {
        WindowWidthSizeClass.COMPACT -> 180.dp
        WindowWidthSizeClass.MEDIUM -> 220.dp
        WindowWidthSizeClass.EXPANDED -> 280.dp
    }
}

/**
 * Адаптивный размер иконки
 */
@Composable
fun adaptiveIconSize(): Dp {
    return when (rememberWindowSizeClass()) {
        WindowWidthSizeClass.COMPACT -> 24.dp
        WindowWidthSizeClass.MEDIUM -> 28.dp
        WindowWidthSizeClass.EXPANDED -> 32.dp
    }
}

/**
 * Количество статистических карточек в ряду
 */
@Composable
fun statCardsPerRow(): Int {
    val screenWidth = screenWidthDp()
    return when {
        screenWidth < 360 -> 2  // Очень узкий экран - 2 карточки
        screenWidth < 600 -> 3  // Компактный - 3 карточки
        screenWidth < 840 -> 4  // Средний - 4 карточки
        else -> 6               // Широкий - до 6 карточек
    }
}
