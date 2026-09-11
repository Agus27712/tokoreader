package com.tkc.screener.ui.screens.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkc.screener.ui.theme.*
import com.tkc.screener.util.PriceFormatter
import java.util.Locale

@Composable
fun RealPortfolioSummaryCard(
    totalRealPortfolioIdr: Double,
    realIdr: Double,
    freeIdr: Double,
    lockedIdr: Double,
    realUsdt: Double,
    freeUsdt: Double,
    lockedUsdt: Double,
    estTotalCryptoIdr: Double,
    isFetchingRealBalance: Boolean,
    onRefreshRealBalance: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TvCardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, TvGreen.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = TvGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "SALDO REAL TOKOCRYPTO",
                        color = TvGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                if (isFetchingRealBalance) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TvGreen, strokeWidth = 2.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TvSurfaceVariant)
                            .border(0.8.dp, TvBorder, RoundedCornerShape(6.dp))
                            .clickable(onClick = onRefreshRealBalance),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = TvTextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = PriceFormatter.formatPrice(totalRealPortfolioIdr, quoteAsset = "IDR"),
                color = TvTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Estimasi Total Aset (Cash IDR + USDT + Koin Kripto)",
                color = TvTextSecondary,
                fontSize = 10.sp
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = TvBorder)
            Spacer(Modifier.height(12.dp))

            // 2-KOLOM: SALDO CASH IDR & SALDO USDT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Kolom 1: Saldo IDR
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(TvBackground, RoundedCornerShape(8.dp))
                        .border(0.8.dp, TvGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SALDO IDR", color = TvTextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .background(TvGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("RUPIAH", color = TvGreen, fontSize = 7.5.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(PriceFormatter.formatPrice(realIdr, quoteAsset = "IDR"), color = TvGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Tersedia: ${PriceFormatter.formatPrice(freeIdr, quoteAsset = "IDR")}", color = TvTextSecondary, fontSize = 8.5.sp)
                    if (lockedIdr > 0.0) {
                        Text("Terkunci: ${PriceFormatter.formatPrice(lockedIdr, quoteAsset = "IDR")}", color = TvRed, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Kolom 2: Saldo USDT
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(TvBackground, RoundedCornerShape(8.dp))
                        .border(0.8.dp, TvBlue.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SALDO USDT", color = TvTextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .background(TvBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("USDT", color = TvBlue, fontSize = 7.5.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(String.format(Locale.US, "%.2f USDT", realUsdt), color = TvBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Tersedia: ${String.format(Locale.US, "%.2f USDT", freeUsdt)}", color = TvTextSecondary, fontSize = 8.5.sp)
                    if (lockedUsdt > 0.0) {
                        Text("Terkunci: ${String.format(Locale.US, "%.2f USDT", lockedUsdt)}", color = TvRed, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Estimasi Koin Lainnya
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TvSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ESTIMASI KOIN LAINNYA", color = TvTextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Text(PriceFormatter.formatPrice(estTotalCryptoIdr, quoteAsset = "IDR"), color = TvTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

