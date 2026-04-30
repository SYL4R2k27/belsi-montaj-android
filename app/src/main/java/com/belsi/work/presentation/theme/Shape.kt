package com.belsi.work.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Shape System
 * Based on Google Pixel interface guidelines
 *
 * Shape scale:
 * - extraSmall: 4dp (chips, small buttons)
 * - small: 8dp (cards, text fields)
 * - medium: 12dp (dialogs, bottom sheets)
 * - large: 16dp (large cards, FABs)
 * - extraLarge: 28dp (large dialogs, modals)
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
