package com.tkc.screener.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkc.screener.model.BadgeType
import com.tkc.screener.model.CoinBadge
import com.tkc.screener.ui.theme.*

/**
 * Reusable Chip Component untuk menampilkan Badge Koin dengan style konsisten Material 3.
 */
@Composable
fun CoinBadgeChip(
    badge: CoinBadge,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, textColor) = getBadgeColors(badge.type)

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .border(0.6.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.5.dp, vertical = 1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badge.label,
            color = textColor,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

/**
 * Row untuk merender multiple badges secara rapi.
 */
@Composable
fun CoinBadgeRow(
    badges: List<CoinBadge>,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp)
) {
    if (badges.isEmpty()) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement
    ) {
        badges.forEach { badge ->
            CoinBadgeChip(badge = badge)
        }
    }
}

@Composable
fun getBadgeColors(type: BadgeType): Triple<Color, Color, Color> {
    val isLight = LocalAppColors.current == LightAppColors
    return when (type) {
        BadgeType.OFFICEDAILY -> Triple(
            Color(0xFF4F46E5).copy(alpha = if (isLight) 0.12f else 0.18f),
            Color(0xFF4F46E5).copy(alpha = 0.6f),
            if (isLight) Color(0xFF3730A3) else Color(0xFFA5B4FC)
        )
        BadgeType.SECONDWAVE -> Triple(
            Color(0xFF0D9488).copy(alpha = if (isLight) 0.12f else 0.18f),
            Color(0xFF0D9488).copy(alpha = 0.6f),
            if (isLight) Color(0xFF115E59) else Color(0xFF5EEAD4)
        )
        BadgeType.SWING -> Triple(
            Color(0xFFD97706).copy(alpha = if (isLight) 0.12f else 0.18f),
            Color(0xFFD97706).copy(alpha = 0.6f),
            if (isLight) Color(0xFF92400E) else Color(0xFFFCD34D)
        )
        BadgeType.SCALPING -> Triple(
            Color(0xFF059669).copy(alpha = if (isLight) 0.12f else 0.18f),
            Color(0xFF059669).copy(alpha = 0.6f),
            if (isLight) Color(0xFF065F46) else Color(0xFF6EE7B7)
        )
        BadgeType.TRENCHING -> Triple(
            Color(0xFFD97706).copy(alpha = if (isLight) 0.12f else 0.18f),
            Color(0xFFD97706).copy(alpha = 0.6f),
            if (isLight) Color(0xFF92400E) else Color(0xFFFCD34D)
        )
        BadgeType.READY -> Triple(
            Color(0xFF16A34A).copy(alpha = if (isLight) 0.12f else 0.22f),
            Color(0xFF16A34A).copy(alpha = 0.8f),
            if (isLight) Color(0xFF14532D) else Color(0xFF86EFAC)
        )
        BadgeType.HOT -> Triple(
            Color(0xFFEA580C).copy(alpha = if (isLight) 0.12f else 0.18f),
            Color(0xFFEA580C).copy(alpha = 0.6f),
            if (isLight) Color(0xFF9A3412) else Color(0xFFFDBA74)
        )
        BadgeType.PUMP -> Triple(
            Color(0xFF15803D).copy(alpha = if (isLight) 0.12f else 0.18f),
            Color(0xFF15803D).copy(alpha = 0.6f),
            if (isLight) Color(0xFF14532D) else Color(0xFF86EFAC)
        )
        BadgeType.DUMP -> Triple(
            Color(0xFFDC2626).copy(alpha = if (isLight) 0.12f else 0.18f),
            Color(0xFFDC2626).copy(alpha = 0.6f),
            if (isLight) Color(0xFF991B1B) else Color(0xFFFCA5A5)
        )
        BadgeType.VOL24 -> Triple(
            Color(0xFF7C3AED).copy(alpha = if (isLight) 0.12f else 0.18f),
            Color(0xFF7C3AED).copy(alpha = 0.6f),
            if (isLight) Color(0xFF5B21B6) else Color(0xFFC4B5FD)
        )
    }
}
