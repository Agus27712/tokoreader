package com.tkc.screener.ui.components.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkc.screener.ui.theme.*
import com.tkc.screener.util.PriceFormatter

@Composable
fun RadarBuySection(
    validPrice: Double,
    baseAsset: String,
    quoteAsset: String,
    availableIdr: Double,
    selectedNominalIdr: Double,
    onNominalIdrChanged: (Double) -> Unit,
    activeFeePct: Double,
    isRealMode: Boolean,
    signal: com.tkc.screener.model.AISignalState? = null,
    onExecuteBuy: ((Double, Double, Double, Double) -> Unit)?
) {
    var customNominalInput by remember { mutableStateOf("") }
    var isCustomNominalOpen by remember { mutableStateOf(false) }
    var showBuyOrderDialog by remember { mutableStateOf(false) }
    var showBuyConfirmDialog by remember { mutableStateOf(false) }
    var customTargetBuyPrice by remember { mutableStateOf(0.0) }

    // Auto Limit Sell Server Settings (TP Direct to Server)
    var isAutoLimitSellEnabled by remember { mutableStateOf(false) }
    val defaultTpPrice1 = remember(validPrice, signal) {
        if (signal != null && signal.targetPrice1 > validPrice) signal.targetPrice1 else validPrice * 1.03
    }
    val defaultTpPrice2 = remember(validPrice, signal) {
        if (signal != null && signal.targetPrice2 > validPrice) signal.targetPrice2 else validPrice * 1.06
    }
    var tp1PriceInput by remember(defaultTpPrice1) { mutableStateOf(PriceFormatter.formatRawDecimal(defaultTpPrice1)) }
    var tp2PriceInput by remember(defaultTpPrice2) { mutableStateOf(PriceFormatter.formatRawDecimal(defaultTpPrice2)) }
    
    val tp1Price = tp1PriceInput.toDoubleOrNull() ?: defaultTpPrice1
    val tp2Price = tp2PriceInput.toDoubleOrNull() ?: defaultTpPrice2

    val focusManager = LocalFocusManager.current

    val effectiveBuyPrice = if (customTargetBuyPrice > 0.0) customTargetBuyPrice else validPrice
    val minBuyAmount = if (quoteAsset.equals("USDT", ignoreCase = true)) 2.0 else 10000.0
    val grossBuyOrderAmount = selectedNominalIdr.coerceAtLeast(minBuyAmount)
    val isMakerOrder = validPrice > 0 && effectiveBuyPrice < validPrice
    val effectiveFeePct = if (isMakerOrder) 0.0 else activeFeePct
    val buyFeeIdr = grossBuyOrderAmount * (effectiveFeePct / 100.0)
    val netBuyAmountIdr = (grossBuyOrderAmount - buyFeeIdr).coerceAtLeast(0.0)
    val estimatedBuyCoinQty = if (effectiveBuyPrice > 0) netBuyAmountIdr / effectiveBuyPrice else 0.0

    Column {
        // Header Saldo IDR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TvCardBackground, RoundedCornerShape(10.dp))
                .border(1.dp, TvBorder, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
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
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Saldo $quoteAsset (${if (isRealMode) "Real Tokocrypto" else "Simulasi"}):",
                        color = TvTextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${PriceFormatter.formatAmountWithQuote(availableIdr, quoteAsset)} $quoteAsset",
                    color = if (availableIdr > 0) TvGreen else TvTextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Auto Limit Sell (Server Tokocrypto Direct Order) Card
        if (isRealMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TvSurfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, TvGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isAutoLimitSellEnabled,
                                onCheckedChange = { isAutoLimitSellEnabled = it },
                                colors = CheckboxDefaults.colors(checkedColor = TvGreen, uncheckedColor = TvTextSecondary),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "SPLIT AUTO LIMIT SELL",
                                color = TvGreen,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isAutoLimitSellEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Setelah BUY OK, sistem langsung pasang 2 Limit Sell otomatis (Masing-masing 50% Qty koin):",
                                color = TvTextSecondary,
                                fontSize = 9.5.sp
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = tp1PriceInput,
                                    onValueChange = { input ->
                                        tp1PriceInput = input.filter { it.isDigit() || it == '.' }
                                    },
                                    label = { Text("TP 1 (50% Qty)", fontSize = 9.5.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TvGreen,
                                        unfocusedBorderColor = TvBorder,
                                        focusedContainerColor = TvSurfaceVariant,
                                        unfocusedContainerColor = TvSurfaceVariant,
                                        focusedTextColor = TvTextPrimary,
                                        unfocusedTextColor = TvTextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = tp2PriceInput,
                                    onValueChange = { input ->
                                        tp2PriceInput = input.filter { it.isDigit() || it == '.' }
                                    },
                                    label = { Text("TP 2 (50% Qty)", fontSize = 9.5.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TvGreen,
                                        unfocusedBorderColor = TvBorder,
                                        focusedContainerColor = TvSurfaceVariant,
                                        unfocusedContainerColor = TvSurfaceVariant,
                                        focusedTextColor = TvTextPrimary,
                                        unfocusedTextColor = TvTextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))

        // Quick Nominal Selector

        Spacer(Modifier.height(8.dp))

        // Quick Nominal Selector
        Text(
            text = "PILIH JUMLAH SALDO DIGUNAKAN:",
            color = TvTextSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val percentages = listOf(25, 50, 75, 100)
            percentages.forEach { pct ->
                val rawAmount = if (availableIdr > 0) (availableIdr * (pct / 100.0)) else 0.0
                val calculatedAmount = if (quoteAsset.equals("USDT", ignoreCase = true)) rawAmount else rawAmount.toLong().toDouble()
                QuickNominalChip(
                    label = "$pct%",
                    selected = !isCustomNominalOpen && selectedNominalIdr > 0 && 
                              (Math.abs(selectedNominalIdr - calculatedAmount) < 100 || (pct == 100 && selectedNominalIdr == availableIdr)),
                    onClick = {
                        val amount = if (pct == 100) availableIdr else calculatedAmount
                        onNominalIdrChanged(if (quoteAsset.equals("USDT", ignoreCase = true)) amount else amount.toLong().toDouble()) // Ensure integer for IDR only
                        isCustomNominalOpen = false
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            
            QuickNominalChip(
                label = "Lainnya",
                selected = isCustomNominalOpen,
                onClick = { isCustomNominalOpen = !isCustomNominalOpen },
                modifier = Modifier.weight(1.1f)
            )
        }

        // Custom Input Field
        AnimatedVisibility(
            visible = isCustomNominalOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(
                    value = customNominalInput,
                    onValueChange = { input ->
                        val filtered = if (quoteAsset.equals("USDT", ignoreCase = true)) {
                            input.filter { it.isDigit() || it == '.' }
                        } else {
                            input.filter { it.isDigit() }
                        }
                        customNominalInput = filtered
                        val parsed = filtered.toDoubleOrNull()
                        if (parsed != null && parsed > 0) {
                            onNominalIdrChanged(parsed)
                        }
                    },
                    label = { Text("Masukkan Nominal Pembelian ($quoteAsset)", fontSize = 11.sp) },
                    placeholder = { Text(if (quoteAsset.equals("USDT", ignoreCase = true)) "Contoh: 50.5" else "Contoh: 250000", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TvBlue,
                        unfocusedBorderColor = TvBorder,
                        focusedContainerColor = TvSurfaceVariant,
                        unfocusedContainerColor = TvSurfaceVariant,
                        focusedTextColor = TvTextPrimary,
                        unfocusedTextColor = TvTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Table / Detail Transaksi
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TvCardBackground, RoundedCornerShape(10.dp))
                .border(0.5.dp, TvBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (customTargetBuyPrice > 0.0 && customTargetBuyPrice != validPrice) "Harga Beli (Limit)" else "Harga Beli (Pasar)",
                        color = TvTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        modifier = Modifier.clickable { showBuyOrderDialog = true },
                        shape = RoundedCornerShape(4.dp),
                        color = TvGreen.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, TvGreen)
                    ) {
                        Text(
                            text = "Atur Limit",
                            color = TvGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${PriceFormatter.formatIdrNumber(effectiveBuyPrice)} $quoteAsset",
                    color = if (customTargetBuyPrice > 0.0 && customTargetBuyPrice != validPrice) TvGreen else TvTextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            TransactionDetailRow(
                label = "Nominal Order",
                value = "${PriceFormatter.formatIdrNumber(grossBuyOrderAmount)} $quoteAsset"
            )
            TransactionDetailRow(
                label = "Biaya Fee (${String.format("%.2f", effectiveFeePct)}%)",
                value = if (isMakerOrder) "Rp 0 (Maker)" else "- ${PriceFormatter.formatIdrNumber(buyFeeIdr)} $quoteAsset",
                valueColor = if (isMakerOrder) TvGreen else TvRed
            )

            HorizontalDivider(color = TvBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))

            // Total Diterima Bersih Koin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Koin Diterima",
                    color = TvGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${PriceFormatter.formatCryptoExact(estimatedBuyCoinQty, 8)} $baseAsset",
                        color = TvGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "= ${PriceFormatter.formatIdrNumber(netBuyAmountIdr)} $quoteAsset Net",
                        color = TvTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Tombol Eksekusi Beli Terintegrasi
        if (onExecuteBuy != null) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (isRealMode) {
                        showBuyConfirmDialog = true
                    } else {
                        onExecuteBuy?.invoke(
                            grossBuyOrderAmount,
                            customTargetBuyPrice,
                            if (isAutoLimitSellEnabled) tp1Price else 0.0,
                            if (isAutoLimitSellEnabled) tp2Price else 0.0
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TvGreen,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isRealMode) {
                        "[REAL] BELI / PASANG LIMIT (${PriceFormatter.formatAmountWithQuote(grossBuyOrderAmount, quoteAsset)} $quoteAsset)"
                    } else {
                        "[SIM] BELI / PASANG LIMIT (${PriceFormatter.formatAmountWithQuote(grossBuyOrderAmount, quoteAsset)} $quoteAsset)"
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }

        if (showBuyConfirmDialog) {
            val confirmMsg = if (isAutoLimitSellEnabled && tp1Price > 0.0) {
                "Anda akan membeli $baseAsset senilai ${PriceFormatter.formatAmountWithQuote(grossBuyOrderAmount, quoteAsset)} $quoteAsset " +
                (if (customTargetBuyPrice > 0.0) "pada harga Limit ${PriceFormatter.formatAmountWithQuote(customTargetBuyPrice, quoteAsset)} $quoteAsset" else "pada harga Pasar saat ini") +
                "\n\ndan memasang otomatis split limit sell TP1 di ${PriceFormatter.formatAmountWithQuote(tp1Price, quoteAsset)} $quoteAsset."
            } else {
                "Anda akan membeli $baseAsset senilai ${PriceFormatter.formatAmountWithQuote(grossBuyOrderAmount, quoteAsset)} $quoteAsset " +
                (if (customTargetBuyPrice > 0.0) "pada harga Limit ${PriceFormatter.formatAmountWithQuote(customTargetBuyPrice, quoteAsset)} $quoteAsset" else "pada harga Pasar saat ini") + "."
            }

            AlertDialog(
                onDismissRequest = { showBuyConfirmDialog = false },
                containerColor = TvSurface,
                titleContentColor = TvGreen,
                title = { Text("Konfirmasi Beli Order", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        confirmMsg,
                        color = TvTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onExecuteBuy?.invoke(
                                grossBuyOrderAmount,
                                customTargetBuyPrice,
                                if (isAutoLimitSellEnabled) tp1Price else 0.0,
                                if (isAutoLimitSellEnabled) tp2Price else 0.0
                            )
                            showBuyConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TvGreen, contentColor = Color.Black)
                    ) {
                        Text("IYA, PASANG ORDER", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBuyConfirmDialog = false }) {
                        Text("BATAL", color = TvTextSecondary)
                    }
                }
            )
        }

        if (showBuyOrderDialog) {
            CustomBuyOrderDialog(
                show = showBuyOrderDialog,
                onDismiss = { showBuyOrderDialog = false },
                validPrice = validPrice,
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                availableIdr = availableIdr,
                initialNominalIdr = grossBuyOrderAmount,
                activeFeePct = activeFeePct,
                isRealMode = isRealMode,
                initialTp1 = defaultTpPrice1,
                initialTp2 = defaultTpPrice2,
                onConfirmBuy = { nominal, targetBuyPrice, tp1, tp2 ->
                    customTargetBuyPrice = targetBuyPrice
                    onNominalIdrChanged(nominal)
                    onExecuteBuy?.invoke(nominal, targetBuyPrice, tp1, tp2)
                }
            )
        }
    }
}
