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

/** Light: clean neutral off-white canvas with warm gold and emerald accents, no blue. */
val LightAppColors = AppColors(
    background = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F2F5),
    cardBackground = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF14171A),
    textSecondary = Color(0xFF5E6773),
    textMuted = Color(0xFF8B94A0),
    border = Color(0xFFE2E5E9),
    green = Color(0xFF03A66D),
    greenLight = Color(0xFF0ECB81),
    red = Color(0xFFCF304A),
    redLight = Color(0xFFF6465D),
    blue = Color(0xFFDDA200), // Gold accent
    blueSoft = Color(0xFFF0B90B),
    amber = Color(0xFFDDA200),
    orange = Color(0xFFE65100)
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
