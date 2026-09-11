package com.tkc.screener.ui.components.detail.sell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkc.screener.ui.theme.*
import com.tkc.screener.util.PriceFormatter
import androidx.compose.foundation.clickable
import com.tkc.screener.ui.components.detail.TransactionDetailRow
import java.util.Locale

@Composable
fun SellCalculationCard(
    validPrice: Double,
    baseAsset: String,
    quoteAsset: String,
    activeSellQty: Double,
    grossSellValueIdr: Double,
    effectiveBuyPrice: Double,
    costBasisIdr: Double,
    sellFeeIdr: Double,
    activeFeePct: Double,
    netReceivedSellIdr: Double,
    isProfitable: Boolean,
    netProfitIdr: Double,
    netProfitPct: Double,
    onManualBuyClick: () -> Unit,
    customTargetSellPrice: Double = 0.0,
    onSetLimitClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvCardBackground, RoundedCornerShape(12.dp))
            .border(1.dp, TvBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (customTargetSellPrice > 0.0 && customTargetSellPrice != validPrice) "Harga Jual (Limit)" else "Harga Jual (Pasar)",
                    color = TvTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    modifier = Modifier.clickable { onSetLimitClick?.invoke() },
                    shape = RoundedCornerShape(4.dp),
                    color = TvRed.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, TvRed)
                ) {
                    Text(
                        text = "Atur Limit",
                        color = TvRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "${PriceFormatter.formatAmountWithQuote(if (customTargetSellPrice > 0.0) customTargetSellPrice else validPrice, quoteAsset)} $quoteAsset",
                color = if (customTargetSellPrice > 0.0 && customTargetSellPrice != validPrice) TvRed else TvTextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        TransactionDetailRow(
            label = "Jumlah Koin Dijual",
            value = "${PriceFormatter.formatCryptoExact(activeSellQty, 8)} $baseAsset",
            subValue = "= ${PriceFormatter.formatAmountWithQuote(grossSellValueIdr, quoteAsset)} $quoteAsset (Kotor)"
        )
        if (effectiveBuyPrice > 0.0) {
            TransactionDetailRow(
                label = "Modal Pembelian",
                value = "${PriceFormatter.formatAmountWithQuote(costBasisIdr, quoteAsset)} $quoteAsset",
                subValue = "(@ ${PriceFormatter.formatAmountWithQuote(effectiveBuyPrice, quoteAsset)})"
            )
        }
        TransactionDetailRow(
            label = "Biaya Fee (${String.format(Locale.US, "%.2f", activeFeePct)}%)",
            value = "- ${PriceFormatter.formatAmountWithQuote(sellFeeIdr, quoteAsset)} $quoteAsset",
            valueColor = TvRed
        )

        HorizontalDivider(color = TvBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hasil Kas Diterima",
                color = TvTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${PriceFormatter.formatAmountWithQuote(netReceivedSellIdr, quoteAsset)} $quoteAsset",
                color = TvTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (effectiveBuyPrice > 0.0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isProfitable) TvGreen.copy(alpha = 0.12f) else TvRed.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        if (isProfitable) TvGreen.copy(alpha = 0.5f) else TvRed.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isProfitable) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (isProfitable) TvGreen else TvRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isProfitable) "Keuntungan Bersih" else "Kerugian / Cut Loss",
                                color = if (isProfitable) TvGreen else TvRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Sudah dipotong fee (${String.format(Locale.US, "%.2f", activeFeePct)}%)",
                            color = TvTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${if (isProfitable) "+" else ""}${PriceFormatter.formatAmountWithQuote(netProfitIdr, quoteAsset)} $quoteAsset",
                            color = if (isProfitable) TvGreen else TvRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "(${if (isProfitable) "+" else ""}${String.format(Locale.US, "%.2f", netProfitPct)}%)",
                            color = if (isProfitable) TvGreen else TvRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TvSurfaceVariant, RoundedCornerShape(10.dp))
                    .border(1.dp, TvAmber.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Histori >7 hari tidak tersedia di API",
                            color = TvAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Isi harga beli manual agar PnL bisa dihitung.",
                            color = TvTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onManualBuyClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TvAmber,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("+ Manual", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
