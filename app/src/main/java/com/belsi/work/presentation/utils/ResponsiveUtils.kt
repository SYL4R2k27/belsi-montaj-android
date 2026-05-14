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
    /** Ширина < 600dp — телефон в портретной ориентации, Z Fold closed */
    COMPACT,
    /** Ширина 600-839dp — телефон в ландшафте, Tab S6 Lite portrait, Z Fold open portrait */
    MEDIUM,
    /** Ширина 840-1239dp — Z Fold open landscape, 11" tablets, Pixel Fold */
    EXPANDED,
    /** Ширина >= 1240dp — 13"+ планшеты (Tab S Ultra 14.6", MatePad Pro 13.2"), Huawei Mate XT */
    XLARGE
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
            screenWidthDp < 1240 -> WindowWidthSizeClass.EXPANDED
            else -> WindowWidthSizeClass.XLARGE
        }
    }
}

/**
 * FIX(2026-05-11) BELSI 2.0.0 build5: проверка XLarge экрана (13"+ планшеты).
 * Брендбук foldable-tablet-design-guide раздел 02 (WSC table).
 */
@Composable
fun isXLargeScreen(): Boolean {
    return rememberWindowSizeClass() == WindowWidthSizeClass.XLARGE
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
        WindowWidthSizeClass.XLARGE -> 48.dp
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
        WindowWidthSizeClass.XLARGE -> 32.dp
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
        WindowWidthSizeClass.XLARGE -> 20.dp
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
        WindowWidthSizeClass.XLARGE -> 1200.dp
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
        WindowWidthSizeClass.XLARGE -> 4
    }
}

/**
 * FIX(2026-05-11) BELSI 2.0.0 build5: количество колонок в photo-grid.
 * Брендбук foldable-tablet раздел "PhotoGalleryScreen": «Грид 5-6 на expanded».
 */
@Composable
fun photoGridColumns(): Int {
    return when (rememberWindowSizeClass()) {
        WindowWidthSizeClass.COMPACT -> 3
        WindowWidthSizeClass.MEDIUM -> 4
        WindowWidthSizeClass.EXPANDED -> 5
        WindowWidthSizeClass.XLARGE -> 6
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
        WindowWidthSizeClass.XLARGE -> GridCells.Adaptive(minSize = 320.dp)
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
 * Минимальная ширина для NavigationRail.
 *
 * FIX(2026-05-11): порог 540dp выбран осознанно ниже Material 3 standard 600dp.
 * Z Fold 5 / 4 в half-folded posture или split-screen выдают screenWidthDp
 * примерно 540-580 — на этих ширинах уже комфортнее боковая навигация чем
 * нижняя. На COMPACT (<540) — BottomNavigation.
 */
const val NAV_RAIL_MIN_WIDTH_DP = 540

/**
 * Определяет, нужно ли показывать Navigation Rail вместо Bottom Navigation.
 *
 * Single source of truth для всех main-экранов BELSI:
 * - CuratorMainScreen, CoordinatorMainScreen, ShiftScreen и т.д.
 *
 * Правило: NavRail когда ширина >= [NAV_RAIL_MIN_WIDTH_DP] (540dp).
 * Это покрывает: phone landscape, Z Fold open, Tab S6 Lite portrait, любой tablet.
 */
@Composable
fun shouldUseNavigationRail(): Boolean {
    return LocalConfiguration.current.screenWidthDp >= NAV_RAIL_MIN_WIDTH_DP
}

/**
 * Нужно ли показывать постоянный NavigationDrawer (для XL-планшетов).
 * Только на 1240dp+ (Tab S Ultra 14.6", MatePad Pro 13.2").
 */
@Composable
fun shouldUseNavigationDrawer(): Boolean {
    return LocalConfiguration.current.screenWidthDp >= 1240
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
        WindowWidthSizeClass.XLARGE -> 320.dp
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
        WindowWidthSizeClass.XLARGE -> 48.dp
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
