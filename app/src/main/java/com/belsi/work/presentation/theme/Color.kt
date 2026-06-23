package com.belsi.work.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Belsi.Монтаж — Design System v2 (April 2026)
 *
 * Brand: Indigo + Steel Blue.
 * Reference: Linear, Stripe, Notion (SaaS, professional).
 *
 * Token naming:
 *  - Primary  = Indigo 600 (#4F46E5)  · CTAs, active states
 *  - Steel    = brand surface (icon, splash, hero)
 *  - Slate    = neutrals (text, borders, surfaces)
 *  - Semantic = emerald · amber · rose · sky · violet (AI)
 */

// ──────────────────────────────────────────────────────────────────────
// PRIMITIVE PALETTE — single source of truth, не использовать напрямую в UI
// ──────────────────────────────────────────────────────────────────────

// Indigo — primary brand
val Indigo50  = Color(0xFFEEF2FF)
val Indigo100 = Color(0xFFE0E7FF)
val Indigo200 = Color(0xFFC7D2FE)
val Indigo300 = Color(0xFFA5B4FC)
val Indigo400 = Color(0xFF818CF8)
val Indigo500 = Color(0xFF6366F1)
val Indigo600 = Color(0xFF4F46E5)  // ★ PRIMARY
val Indigo700 = Color(0xFF4338CA)
val Indigo800 = Color(0xFF3730A3)
val Indigo900 = Color(0xFF312E81)
val Indigo950 = Color(0xFF1E1B4B)

// Slate — neutrals
val Slate50  = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)
val Slate950 = Color(0xFF020617)

// Steel — brand mark colors (icon, splash gradient)
val SteelTop   = Color(0xFF5A7186)
val SteelMid   = Color(0xFF3A5067)
val SteelDeep  = Color(0xFF324859)
val HammerGray = Color(0xFFA0A8B0)

// Semantic
val Emerald500 = Color(0xFF10B981)
val Emerald600 = Color(0xFF059669)
val Amber500   = Color(0xFFF59E0B)
val Amber600   = Color(0xFFD97706)
val Rose500    = Color(0xFFF43F5E)
val Rose600    = Color(0xFFE11D48)
val Sky500     = Color(0xFF0EA5E9)
val Violet500  = Color(0xFF8B5CF6)  // AI accent
val Violet600  = Color(0xFF7C3AED)

// ──────────────────────────────────────────────────────────────────────
// MATERIAL 3 SCHEME — Light
// ──────────────────────────────────────────────────────────────────────

val md_theme_light_primary           = Indigo600
val md_theme_light_onPrimary         = Color.White
val md_theme_light_primaryContainer  = Indigo100
val md_theme_light_onPrimaryContainer = Indigo900

val md_theme_light_secondary         = Slate600
val md_theme_light_onSecondary       = Color.White
val md_theme_light_secondaryContainer = Slate100
val md_theme_light_onSecondaryContainer = Slate800

val md_theme_light_tertiary          = Violet500
val md_theme_light_onTertiary        = Color.White
val md_theme_light_tertiaryContainer = Color(0xFFEDE9FE)
val md_theme_light_onTertiaryContainer = Color(0xFF5B21B6)

val md_theme_light_error             = Rose500
val md_theme_light_onError           = Color.White
val md_theme_light_errorContainer    = Color(0xFFFEE2E2)
val md_theme_light_onErrorContainer  = Color(0xFF991B1B)

val md_theme_light_background        = Slate50
val md_theme_light_onBackground      = Slate900
val md_theme_light_surface           = Color.White
val md_theme_light_onSurface         = Slate900
val md_theme_light_surfaceVariant    = Slate100
val md_theme_light_onSurfaceVariant  = Slate600

val md_theme_light_outline           = Slate300
val md_theme_light_outlineVariant    = Slate200
val md_theme_light_inverseOnSurface  = Slate100
val md_theme_light_inverseSurface    = Slate800
val md_theme_light_inversePrimary    = Indigo300
val md_theme_light_surfaceTint       = Indigo600
val md_theme_light_scrim             = Color(0x80000000)

// ──────────────────────────────────────────────────────────────────────
// MATERIAL 3 SCHEME — Dark
// ──────────────────────────────────────────────────────────────────────

val md_theme_dark_primary            = Indigo400
val md_theme_dark_onPrimary          = Indigo900
val md_theme_dark_primaryContainer   = Indigo700
val md_theme_dark_onPrimaryContainer = Indigo100

val md_theme_dark_secondary          = Slate400
val md_theme_dark_onSecondary        = Slate900
val md_theme_dark_secondaryContainer = Slate700
val md_theme_dark_onSecondaryContainer = Slate100

val md_theme_dark_tertiary           = Color(0xFFA78BFA)
val md_theme_dark_onTertiary         = Color(0xFF4C1D95)
val md_theme_dark_tertiaryContainer  = Color(0xFF6D28D9)
val md_theme_dark_onTertiaryContainer = Color(0xFFEDE9FE)

val md_theme_dark_error              = Color(0xFFFCA5A5)
val md_theme_dark_onError            = Color(0xFF7F1D1D)
val md_theme_dark_errorContainer     = Color(0xFFB91C1C)
val md_theme_dark_onErrorContainer   = Color(0xFFFEE2E2)

val md_theme_dark_background         = Slate950
val md_theme_dark_onBackground       = Slate100
val md_theme_dark_surface            = Slate900
val md_theme_dark_onSurface          = Slate100
val md_theme_dark_surfaceVariant     = Slate800
val md_theme_dark_onSurfaceVariant   = Slate400

val md_theme_dark_outline            = Slate600
val md_theme_dark_outlineVariant     = Slate700
val md_theme_dark_inverseOnSurface   = Slate800
val md_theme_dark_inverseSurface     = Slate100
val md_theme_dark_inversePrimary     = Indigo600
val md_theme_dark_surfaceTint        = Indigo400
val md_theme_dark_scrim              = Color(0x80000000)

// ──────────────────────────────────────────────────────────────────────
// EXTENDED — semantic colors not in Material 3 ColorScheme
// ──────────────────────────────────────────────────────────────────────

@Immutable
data class BelsiExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,

    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,

    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,

    val neutral: Color,
    val onNeutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,

    /** AI-specific accent — used everywhere AI is involved (analysis, score, auto-approve). */
    val ai: Color,
    val onAi: Color,
    val aiContainer: Color,
    val onAiContainer: Color,

    /** Brand mark gradient (icon, splash). Read-only — не меняется. */
    val brandGradientTop: Color,
    val brandGradientBottom: Color,
)

val LightBelsiExtendedColors = BelsiExtendedColors(
    success           = Emerald500,
    onSuccess         = Color.White,
    successContainer  = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF166534),

    warning           = Amber500,
    onWarning         = Color.White,
    warningContainer  = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF92400E),

    info              = Sky500,
    onInfo            = Color.White,
    infoContainer     = Color(0xFFDBEAFE),
    onInfoContainer   = Color(0xFF1E40AF),

    neutral           = Slate500,
    onNeutral         = Color.White,
    neutralContainer  = Slate100,
    onNeutralContainer = Slate700,

    ai                = Violet500,
    onAi              = Color.White,
    aiContainer       = Color(0xFFEDE9FE),
    onAiContainer     = Color(0xFF5B21B6),

    brandGradientTop  = SteelTop,
    brandGradientBottom = SteelDeep,
)

val DarkBelsiExtendedColors = BelsiExtendedColors(
    success           = Color(0xFF34D399),
    onSuccess         = Color(0xFF022C22),
    successContainer  = Color(0xFF14532D),
    onSuccessContainer = Color(0xFF86EFAC),

    warning           = Color(0xFFFCD34D),
    onWarning         = Color(0xFF422006),
    warningContainer  = Color(0xFF78350F),
    onWarningContainer = Color(0xFFFCD34D),

    info              = Color(0xFF38BDF8),
    onInfo            = Color(0xFF082F49),
    infoContainer     = Color(0xFF1E3A8A),
    onInfoContainer   = Color(0xFF93C5FD),

    neutral           = Slate400,
    onNeutral         = Slate900,
    neutralContainer  = Slate700,
    onNeutralContainer = Slate200,

    ai                = Color(0xFFA78BFA),
    onAi              = Color(0xFF2E1065),
    aiContainer       = Color(0xFF4C1D95),
    onAiContainer     = Color(0xFFC4B5FD),

    brandGradientTop  = SteelTop,
    brandGradientBottom = SteelDeep,
)

val LocalBelsiColors = staticCompositionLocalOf { LightBelsiExtendedColors }

// ──────────────────────────────────────────────────────────────────────
// LEGACY ALIASES — для совместимости со старым кодом (постепенно убираем)
// ──────────────────────────────────────────────────────────────────────

@Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
val BelsiPrimary = Indigo600
@Deprecated("Use MaterialTheme.colorScheme.primary in dark theme")
val BelsiPrimaryDark = Indigo400
@Deprecated("Use MaterialTheme.belsiColors.success")
val BelsiSuccess = Emerald500
@Deprecated("Use MaterialTheme.colorScheme.error")
val BelsiError = Rose500
@Deprecated("Use MaterialTheme.belsiColors.warning")
val BelsiWarning = Amber500
@Deprecated("Use MaterialTheme.belsiColors.info")
val BelsiInfo = Sky500

// Status colors
@Deprecated("Use MaterialTheme.belsiColors.info")
val StatusOpen = Sky500
@Deprecated("Use MaterialTheme.belsiColors.warning")
val StatusInProgress = Amber500
@Deprecated("Use MaterialTheme.belsiColors.success")
val StatusResolved = Emerald500
@Deprecated("Use MaterialTheme.belsiColors.neutral")
val StatusClosed = Slate500
