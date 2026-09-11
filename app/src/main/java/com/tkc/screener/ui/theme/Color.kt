package com.tkc.screener.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val green: Color,
    val greenLight: Color,
    val red: Color,
    val redLight: Color,
    val blue: Color,
    val blueSoft: Color,
    val amber: Color,
    val orange: Color
)

/** Dark: sleek deep obsidian / charcoal canvas with warm Tokocrypto gold & emerald accents, no blue accents. */
val DarkAppColors = AppColors(
    background = Color(0xFF121418),
    surface = Color(0xFF181B20),
    surfaceVariant = Color(0xFF222630),
    cardBackground = Color(0xFF181B20),
    textPrimary = Color(0xFFF3F5F7),
    textSecondary = Color(0xFF9EA7B4),
    textMuted = Color(0xFF676F7E),
    border = Color(0xFF2B313D),
    green = Color(0xFF0ECB81),
    greenLight = Color(0xFF26E396),
    red = Color(0xFFF6465D),
    redLight = Color(0xFFFF6A7E),
    blue = Color(0xFFF0B90B), // Tokocrypto Gold accent (eliminates blue)
    blueSoft = Color(0xFFFCD535), // Tokocrypto Light Gold accent (eliminates blue)
    amber = Color(0xFFF0B90B),
    orange = Color(0xFFFF9800)
)

/** Light: clean neutral off-white canvas with high-contrast dark amber/gold and emerald accents. */
val LightAppColors = AppColors(
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    cardBackground = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    border = Color(0xFFCBD5E1),
    green = Color(0xFF047857),
    greenLight = Color(0xFF059669),
    red = Color(0xFFB91C1C),
    redLight = Color(0xFFDC2626),
    blue = Color(0xFFB45309), // Tokocrypto Dark Amber/Gold (High-contrast on light background)
    blueSoft = Color(0xFFD97706),
    amber = Color(0xFFB45309),
    orange = Color(0xFFC2410C)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

val TvBackground: Color @Composable get() = LocalAppColors.current.background
val TvSurface: Color @Composable get() = LocalAppColors.current.surface
val TvSurfaceVariant: Color @Composable get() = LocalAppColors.current.surfaceVariant
val TvCardBackground: Color @Composable get() = LocalAppColors.current.cardBackground

val TvTextPrimary: Color @Composable get() = LocalAppColors.current.textPrimary
val TvTextSecondary: Color @Composable get() = LocalAppColors.current.textSecondary
val TvTextMuted: Color @Composable get() = LocalAppColors.current.textMuted
val TvBorder: Color @Composable get() = LocalAppColors.current.border

val TvGreen: Color @Composable get() = LocalAppColors.current.green
val TvGreenLight: Color @Composable get() = LocalAppColors.current.greenLight
val TvRed: Color @Composable get() = LocalAppColors.current.red
val TvRedLight: Color @Composable get() = LocalAppColors.current.redLight
val TvBlue: Color @Composable get() = LocalAppColors.current.blue
val TvBlueSoft: Color @Composable get() = LocalAppColors.current.blueSoft
val TvAmber: Color @Composable get() = LocalAppColors.current.amber
val TvOrange: Color @Composable get() = LocalAppColors.current.orange
