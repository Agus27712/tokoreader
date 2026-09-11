package com.tkc.screener.ui.components.detail.sell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkc.screener.ui.theme.*
import com.tkc.screener.util.PriceFormatter
import java.util.Locale

@Composable
fun CustomSellOrderDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    validPrice: Double,
    baseAsset: String,
    quoteAsset: String,
    initialTargetSellPrice: Double,
    isRealMode: Boolean,
    onConfirmSell: (sellPrice: Double) -> Unit
) {
    if (!show) return

    val focusManager = LocalFocusManager.current

    fun formatPriceForInput(p: Double): String {
        if (p <= 0.0) return ""
        return if (p < 1.0) {
            String.format(Locale.US, "%.8f", p).trimEnd('0').trimEnd('.')
        } else if (p < 100.0) {
            String.format(Locale.US, "%.2f", p)
        } else {
            PriceFormatter.formatIdrNumber(p)
        }
    }

    var sellPriceInput by remember(show, validPrice) {
        val initialVal = if (initialTargetSellPrice > 0.0) initialTargetSellPrice else validPrice
        mutableStateOf(formatPriceForInput(initialVal))
    }

    val targetPrice = PriceFormatter.parseCleanDouble(sellPriceInput, quoteAsset)
    val diffPct = if (validPrice > 0 && targetPrice > 0) {
        ((targetPrice - validPrice) / validPrice) * 100.0
    } else {
        0.0
    }

    val isPriceValid = targetPrice > 0
    val canSubmit = isPriceValid

    fun applyPricePremium(pct: Double) {
        if (validPrice <= 0.0) return
        val calculated = if (validPrice < 1.0) {
            validPrice * (1.0 + pct / 100.0)
        } else if (validPrice < 100.0) {
            String.format(Locale.US, "%.2f", validPrice * (1.0 + pct / 100.0)).toDoubleOrNull() ?: validPrice
        } else {
            kotlin.math.round(validPrice * (1.0 + pct / 100.0))
        }
        sellPriceInput = formatPriceForInput(calculated)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TvSurface,
        titleContentColor = TvTextPrimary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = TvRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Atur Limit Jual $baseAsset / $quoteAsset",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = TvTextPrimary
                        )
                        Text(
                            text = if (isRealMode) "Real Tokocrypto TAPI v2" else "Simulasi Jual Limit",
                            fontSize = 10.sp,
                            color = if (isRealMode) TvAmber else TvBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TvTextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Harga Jual Sekarang (Reference)
                Surface(
                    color = TvSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Harga Pasar Saat Ini",
                            color = TvTextSecondary,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "${PriceFormatter.formatAmountWithQuote(validPrice, quoteAsset)} $quoteAsset",
                            color = TvTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Input Target Harga Jual (Limit)
                Column {
                    Text(
                        text = "Harga Limit Jual Anda ($quoteAsset)",
                        color = TvTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = sellPriceInput,
                        onValueChange = { sellPriceInput = it },
                        placeholder = { Text("Masukkan target harga jual...", fontSize = 12.sp, color = TvTextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TvRed,
                            unfocusedBorderColor = TvBorder,
                            focusedContainerColor = TvCardBackground,
                            unfocusedContainerColor = TvCardBackground,
                            focusedTextColor = TvTextPrimary,
                            unfocusedTextColor = TvTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Quick Premium Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Market" to 0.0,
                        "+1%" to 1.0,
                        "+2%" to 2.0,
                        "+3%" to 3.0,
                        "+5%" to 5.0
                    ).forEach { (label, pct) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (pct == 0.0 && targetPrice == validPrice) TvRed.copy(alpha = 0.15f) else TvSurfaceVariant,
                            border = BorderStroke(
                                0.5.dp,
                                if (pct == 0.0 && targetPrice == validPrice) TvRed else TvBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (pct == 0.0) {
                                        sellPriceInput = formatPriceForInput(validPrice)
                                    } else {
                                        applyPricePremium(pct)
                                    }
                                }
                        ) {
                            Text(
                                text = label,
                                color = if (pct == 0.0 && targetPrice == validPrice) TvRed else TvTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                // Premium / Discount Indicator
                if (diffPct != 0.0 && targetPrice > 0.0) {
                    val color = if (diffPct > 0.0) TvGreen else TvRed
                    val symbol = if (diffPct > 0.0) "+" else ""
                    Text(
                        text = "Harga limit Anda $symbol${String.format(Locale.US, "%.2f", diffPct)}% dari harga pasar saat ini.",
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSubmit) {
                        onConfirmSell(targetPrice)
                        onDismiss()
                    }
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TvRed,
                    contentColor = Color.White,
                    disabledContainerColor = TvBorder,
                    disabledContentColor = TvTextMuted
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text(
                    text = "TERAPKAN LIMIT JUAL",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    )
}
