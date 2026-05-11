package com.belsi.work.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Belsi.Монтаж — Spacing tokens (4-pt grid).
 *
 * Usage:
 *   import com.belsi.work.presentation.theme.spacing
 *   Modifier.padding(MaterialTheme.spacing.md)
 *
 * Шкала кратна 4 dp — соответствует Figma / Material 3 / iOS HIG.
 */
@Immutable
data class BelsiSpacing(
    /** 4 dp — между чипом и иконкой, иконка-к-краю */
    val xxs: Dp = 4.dp,
    /** 8 dp — между списком и текстом, padding pill */
    val xs:  Dp = 8.dp,
    /** 12 dp — card padding, между параграфами */
    val sm:  Dp = 12.dp,
    /** 16 dp — screen edge padding (default) */
    val md:  Dp = 16.dp,
    /** 20 dp — между разделами в card */
    val ml:  Dp = 20.dp,
    /** 24 dp — между секциями */
    val lg:  Dp = 24.dp,
    /** 32 dp — top margin для заголовков */
    val xl:  Dp = 32.dp,
    /** 40 dp — между крупными блоками */
    val xxl: Dp = 40.dp,
    /** 48 dp — отступ hero-секции */
    val xxxl:Dp = 48.dp,

    // Минимальные размеры тач-таргетов (Material 3 = 48 dp минимум)
    val touchTargetMin: Dp = 48.dp,
    val touchTargetCompact: Dp = 40.dp,
)

val LocalBelsiSpacing = staticCompositionLocalOf { BelsiSpacing() }

val MaterialTheme.spacing: BelsiSpacing
    @Composable get() = LocalBelsiSpacing.current
