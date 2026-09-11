package com.tkc.screener.ui.screens.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkc.screener.model.TradingPair
import com.tkc.screener.trading.SimulationOrder
import com.tkc.screener.trading.SimulationTradeHistoryItem
import com.tkc.screener.trading.SimulationWallet
import com.tkc.screener.ui.components.simulation.OpenOrderItemCard
import com.tkc.screener.ui.theme.*
import com.tkc.screener.util.PriceFormatter
import java.util.Locale

/**
 * Tampilan khusus Portofolio Simulasi:
 * Menampilkan ringkasan portofolio virtual, kas Rupiah simulasi,
 * daftar koin virtual yang dimiliki, antrean open orders, serta riwayat eksekusi.
 */
@Composable
fun SimulationPortfolioView(
    wallet: SimulationWallet,
    history: List<SimulationTradeHistoryItem>,
    openOrders: List<SimulationOrder>,
    holdings: List<HoldingItem>,
    totalPortfolioValueIdr: Double,
    totalUnrealizedPnlIdr: Double,
    totalUnrealizedPnlPct: Double,
    selectedTab: PortfolioTab,
    onSelectTab: (PortfolioTab) -> Unit,
    onOpenTopUp: () -> Unit,
    onNavigateToDetail: (TradingPair) -> Unit,
    onNavigateToSimulation: (TradingPair) -> Unit,
    onCancelOrder: (String) -> Unit,
    onCancelAllOrders: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // CARD 1: TOTAL PORTOFOLIO & PNL OVERVIEW
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = TvCardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, TvBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ESTIMASI TOTAL NILAI",
                            color = TvTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier
                                .background(TvSurfaceVariant, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = TvBlue,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${holdings.size} Koin",
                                color = TvBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Total Nilai Portofolio
                    Text(
                        text = PriceFormatter.formatPrice(totalPortfolioValueIdr, quoteAsset = "IDR"),
                        color = TvTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.height(6.dp))

                    // Total Floating PnL
                    val pnlColor = if (totalUnrealizedPnlIdr >= 0) TvGreen else TvRed
                    val pnlPrefix = if (totalUnrealizedPnlIdr >= 0) "+" else ""
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Floating PnL: ",
                            color = TvTextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "$pnlPrefix${PriceFormatter.formatPrice(totalUnrealizedPnlIdr, quoteAsset = "IDR")} ($pnlPrefix${String.format(Locale.US, "%.2f", totalUnrealizedPnlPct)}%)",
                            color = pnlColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // CARD 2: SALDO KAS SIMULASI (DUAL: SALDO IDR & SALDO USDT)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = TvCardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, TvBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = TvGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "SALDO KAS SIMULASI (IDR & USDT)",
                                color = TvTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = onOpenTopUp,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TvGreen)
                        ) {
                            Text("+ Kelola Saldo", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 2-Kolom: Saldo IDR vs Saldo USDT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Kolom 1: Saldo IDR
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(TvBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, TvGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Saldo IDR", color = TvTextSecondary, fontSize = 10.sp)
                                Box(
                                    modifier = Modifier
                                        .background(TvGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("RUPIAH", color = TvGreen, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = PriceFormatter.formatPrice(wallet.getAvailableIdr(), quoteAsset = "IDR"),
                                color = TvGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            if (wallet.lockedIdr > 0.0) {
                                Text(
                                    text = "Lock: ${PriceFormatter.formatPrice(wallet.lockedIdr, quoteAsset = "IDR")}",
                                    color = TvAmber,
                                    fontSize = 9.sp
                                )
                            } else {
                                Text(
                                    text = "Tersedia pair IDR",
                                    color = TvTextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Kolom 2: Saldo USDT
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(TvBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, TvBlue.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Saldo USDT", color = TvTextSecondary, fontSize = 10.sp)
                                Box(
                                    modifier = Modifier
                                        .background(TvBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("USDT", color = TvBlue, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = String.format("%.2f USDT", wallet.getAvailableUsdt()),
                                color = TvBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            if (wallet.lockedUsdt > 0.0) {
                                Text(
                                    text = "Lock: ${String.format("%.2f USDT", wallet.lockedUsdt)}",
                                    color = TvAmber,
                                    fontSize = 9.sp
                                )
                            } else {
                                Text(
                                    text = "Tersedia pair USDT",
                                    color = TvTextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Keterangan Pemotongan Saldo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TvSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TvBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Trading pair USDT memotong saldo USDT, pair IDR memotong saldo IDR. Beli/top-up USDT lewat tombol Kelola Saldo.",
                            color = TvTextSecondary,
                            fontSize = 9.sp,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }

        // TAB SEGMENT: Koin Dimiliki | Riwayat Transaksi | Posisi Spot
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TvSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PortfolioTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) TvCardBackground else Color.Transparent)
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.title,
                            color = if (isSelected) TvBlue else TvTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // KONTEN TAB
        when (selectedTab) {
            PortfolioTab.HOLDINGS -> {
                if (holdings.isEmpty()) {
                    item {
                        EmptyHoldingsCard(
                            onNavigateToSimulation = {
                                val fallbackPair = TradingPair.popularPairsForSource().first()
                                onNavigateToSimulation(fallbackPair)
                            }
                        )
                    }
                } else {
                    items(holdings, key = { it.baseAsset }) { item ->
                        HoldingCoinCard(
                            item = item,
                            onTrade = { onNavigateToSimulation(item.tradingPair) },
                            onViewChart = { onNavigateToDetail(item.tradingPair) }
                        )
                    }
                }
            }

            PortfolioTab.OPEN_ORDERS -> {
                if (openOrders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TvCardBackground, RoundedCornerShape(10.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Tidak ada antrean order terbuka saat ini.",
                                color = TvTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total ${openOrders.size} Order Aktif",
                                color = TvTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TvSurfaceVariant)
                                    .clickable { onCancelAllOrders(null) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Batalkan Semua",
                                    color = TvRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    items(openOrders, key = { it.id }) { order ->
                        OpenOrderItemCard(
                            order = order,
                            onCancel = { onCancelOrder(order.id) }
                        )
                    }
                }
            }

            PortfolioTab.HISTORY -> {
                if (history.isEmpty()) {
                    item {
                        EmptyHistoryCard()
                    }
                } else {
                    items(history, key = { it.id }) { trade ->
                        TradeHistoryItemCard(trade = trade)
                    }
                }
            }
        }
    }
}
