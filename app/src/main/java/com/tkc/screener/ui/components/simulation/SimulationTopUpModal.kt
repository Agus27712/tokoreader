package com.tkc.screener.ui.components.simulation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tkc.screener.trading.SimulationWallet
import com.tkc.screener.ui.theme.*
import com.tkc.screener.util.PriceFormatter

@Composable
fun SimulationTopUpModal(
    wallet: SimulationWallet,
    onTopUpIdr: (Double) -> Unit,
    onTopUpUsdt: (Double) -> Unit,
    onBuyUsdtWithIdr: (idrAmount: Double, rate: Double) -> Unit = { _, _ -> },
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: IDR, 1: USDT, 2: Beli USDT
    val quickIdrAmounts = listOf(1_000_000.0, 5_000_000.0, 10_000_000.0, 50_000_000.0)
    val quickUsdtAmounts = listOf(50.0, 100.0, 500.0, 1000.0)
    val buyUsdtPacks = listOf(100.0, 250.0, 500.0, 1000.0)
    val usdtRate = 16250.0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = TvCardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Wallet",
                            tint = TvGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Kelola Saldo Simulasi",
                            color = TvTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TvTextSecondary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Current Balance Dual Cards (IDR & USDT)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // IDR Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TvSurfaceVariant)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(text = "Saldo IDR", color = TvTextSecondary, fontSize = 10.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = PriceFormatter.formatPrice(wallet.getAvailableIdr()),
                                color = TvGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (wallet.lockedIdr > 0) {
                                Text(
                                    text = "Lock: ${PriceFormatter.formatPrice(wallet.lockedIdr)}",
                                    color = TvOrange,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    // USDT Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TvSurfaceVariant)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(text = "Saldo USDT", color = TvTextSecondary, fontSize = 10.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = String.format("%.2f USDT", wallet.getAvailableUsdt()),
                                color = TvBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (wallet.lockedUsdt > 0) {
                                Text(
                                    text = "Lock: ${String.format("%.2f", wallet.lockedUsdt)}",
                                    color = TvOrange,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TvSurfaceVariant)
                        .padding(2.dp)
                ) {
                    listOf("Top Up IDR", "Top Up USDT", "Beli USDT").forEachIndexed { idx, title ->
                        val isSelected = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) TvCardBackground else Color.Transparent)
                                .clickable { selectedTab = idx }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) TvTextPrimary else TvTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        // IDR Top Up
                        Text(
                            text = "Pilih Nominal Top Up Rupiah:",
                            color = TvTextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                quickIdrAmounts.take(2).forEach { amount ->
                                    Button(
                                        onClick = {
                                            onTopUpIdr(amount)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TvSurfaceVariant),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("+Rp ${formatShort(amount)}", color = TvGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                quickIdrAmounts.takeLast(2).forEach { amount ->
                                    Button(
                                        onClick = {
                                            onTopUpIdr(amount)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TvSurfaceVariant),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("+Rp ${formatShort(amount)}", color = TvGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Direct USDT Top Up
                        Text(
                            text = "Pilih Nominal Top Up USDT Langsung:",
                            color = TvTextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                quickUsdtAmounts.take(2).forEach { amount ->
                                    Button(
                                        onClick = {
                                            onTopUpUsdt(amount)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TvSurfaceVariant),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("+${amount.toInt()} USDT", color = TvBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                quickUsdtAmounts.takeLast(2).forEach { amount ->
                                    Button(
                                        onClick = {
                                            onTopUpUsdt(amount)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TvSurfaceVariant),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("+${amount.toInt()} USDT", color = TvBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Buy USDT with IDR (Simulated Convert)
                        Text(
                            text = "Beli USDT pakai Saldo IDR (Kurs ~Rp 16.250):",
                            color = TvTextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            buyUsdtPacks.chunked(2).forEach { rowPacks ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rowPacks.forEach { usdtQty ->
                                        val reqIdr = usdtQty * usdtRate
                                        val hasEnough = wallet.getAvailableIdr() >= reqIdr
                                        Button(
                                            onClick = {
                                                onBuyUsdtWithIdr(reqIdr, usdtRate)
                                                onDismiss()
                                            },
                                            enabled = hasEnough,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = TvSurfaceVariant,
                                                disabledContainerColor = TvSurfaceVariant.copy(alpha = 0.4f)
                                            ),
                                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                                brush = androidx.compose.ui.graphics.SolidColor(if (hasEnough) TvBlue.copy(alpha = 0.5f) else TvBorder)
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Beli ${usdtQty.toInt()} USDT", color = if (hasEnough) TvBlue else TvTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text("Rp ${formatShort(reqIdr)}", color = TvTextSecondary, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Reset Button
                OutlinedButton(
                    onClick = {
                        onReset()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TvRed),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TvRed.copy(alpha = 0.5f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = TvRed,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Reset Saldo (Rp 10 Jt & 500 USDT)",
                        color = TvRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatShort(amount: Double): String {
    return when {
        amount >= 1_000_000 -> "${(amount / 1_000_000).toInt()} Jt"
        amount >= 1_000 -> "${(amount / 1_000).toInt()} Rb"
        else -> amount.toInt().toString()
    }
}
