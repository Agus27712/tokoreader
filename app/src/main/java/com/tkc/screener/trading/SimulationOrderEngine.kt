package com.tkc.screener.trading

import java.util.UUID

object SimulationOrderEngine {
    const val TOKOCRYPTO_MAKER_FEE_RATE = 0.001 // 0.1%
    const val TOKOCRYPTO_TAKER_FEE_RATE = 0.003 // 0.3%

    data class ExecutionResult(
        val updatedWallet: SimulationWallet,
        val historyItem: SimulationTradeHistoryItem,
        val completedOrder: SimulationOrder
    )

    fun isUsdtQuote(quote: String): Boolean {
        return quote.equals("USDT", ignoreCase = true) || 
               quote.equals("USDC", ignoreCase = true) || 
               quote.equals("USD", ignoreCase = true)
    }

    fun executeMarketBuy(
        wallet: SimulationWallet,
        symbol: String,
        baseKey: String,
        quote: String,
        execPrice: Double,
        quantity: Double
    ): Result<ExecutionResult> {
        val isUsdt = isUsdtQuote(quote)
        val totalQuote = quantity * execPrice
        val feeQuote = totalQuote * TOKOCRYPTO_TAKER_FEE_RATE
        val requiredQuote = totalQuote + feeQuote
        val availableQuote = if (isUsdt) wallet.getAvailableUsdt() else wallet.getAvailableIdr()

        if (availableQuote < requiredQuote) {
            val curLabel = if (isUsdt) "USDT" else "IDR"
            return Result.failure(
                IllegalArgumentException(
                    "Saldo $curLabel tidak cukup. Dibutuhkan ${formatMoney(requiredQuote, quote)}, saldo tersedia ${formatMoney(availableQuote, quote)}. Silakan beli/top up $curLabel terlebih dahulu."
                )
            )
        }

        val newCoinBalances = wallet.coinBalances.toMutableMap()
        val currentCoin = newCoinBalances[baseKey] ?: 0.0
        val currentAvg = wallet.avgBuyPrices[baseKey] ?: 0.0
        val newTotalCoin = currentCoin + quantity
        val newAvgPrice = if (newTotalCoin > 0.0) {
            ((currentCoin * currentAvg) + totalQuote) / newTotalCoin
        } else execPrice

        newCoinBalances[baseKey] = newTotalCoin
        val newAvgMap = wallet.avgBuyPrices.toMutableMap().apply { put(baseKey, newAvgPrice) }

        val updatedWallet = if (isUsdt) {
            wallet.copy(
                usdtBalance = (wallet.usdtBalance - requiredQuote).coerceAtLeast(0.0),
                coinBalances = newCoinBalances,
                avgBuyPrices = newAvgMap
            )
        } else {
            wallet.copy(
                idrBalance = (wallet.idrBalance - requiredQuote).coerceAtLeast(0.0),
                coinBalances = newCoinBalances,
                avgBuyPrices = newAvgMap
            )
        }

        val history = SimulationTradeHistoryItem(
            id = UUID.randomUUID().toString(),
            orderId = UUID.randomUUID().toString(),
            symbol = symbol,
            baseAsset = baseKey,
            quoteAsset = quote,
            side = SimulationOrderSide.BUY,
            type = SimulationOrderType.MARKET,
            executionPrice = execPrice,
            quantity = quantity,
            totalIdr = totalQuote,
            feeIdr = feeQuote,
            timestamp = System.currentTimeMillis()
        )

        val order = SimulationOrder(
            id = history.orderId,
            symbol = symbol,
            baseAsset = baseKey,
            quoteAsset = quote,
            side = SimulationOrderSide.BUY,
            type = SimulationOrderType.MARKET,
            limitPrice = execPrice,
            quantity = quantity,
            totalIdr = totalQuote,
            filledQuantity = quantity,
            filledAvgPrice = execPrice,
            feeIdr = feeQuote,
            status = SimulationOrderStatus.FILLED,
            filledAt = System.currentTimeMillis()
        )

        return Result.success(ExecutionResult(updatedWallet, history, order))
    }

    fun executeMarketSell(
        wallet: SimulationWallet,
        symbol: String,
        baseKey: String,
        quote: String,
        execPrice: Double,
        quantity: Double
    ): Result<ExecutionResult> {
        val isUsdt = isUsdtQuote(quote)
        val available = wallet.getAvailableCoin(baseKey)
        val actualQty = if (quantity > available && (quantity - available) < 0.0001) available else quantity

        if (available < actualQty) {
            return Result.failure(
                IllegalArgumentException("Saldo $baseKey tidak cukup. Tersedia: ${wallet.getAvailableCoin(baseKey)}")
            )
        }

        val totalQuote = actualQty * execPrice
        val feeQuote = totalQuote * TOKOCRYPTO_TAKER_FEE_RATE
        val netQuote = (totalQuote - feeQuote).coerceAtLeast(0.0)
        val avgBuy = wallet.avgBuyPrices[baseKey] ?: execPrice
        val costBasis = actualQty * avgBuy
        val pnlQuote = totalQuote - costBasis - feeQuote
        val pnlPercent = if (costBasis > 0.0) (pnlQuote / costBasis) * 100.0 else 0.0

        val newCoinBalances = wallet.coinBalances.toMutableMap()
        val remaining = (newCoinBalances[baseKey] ?: 0.0) - actualQty
        if (remaining <= 0.00000001) {
            newCoinBalances.remove(baseKey)
        } else {
            newCoinBalances[baseKey] = remaining
        }

        val updatedWallet = if (isUsdt) {
            wallet.copy(
                usdtBalance = wallet.usdtBalance + netQuote,
                coinBalances = newCoinBalances
            )
        } else {
            wallet.copy(
                idrBalance = wallet.idrBalance + netQuote,
                coinBalances = newCoinBalances
            )
        }

        val history = SimulationTradeHistoryItem(
            id = UUID.randomUUID().toString(),
            orderId = UUID.randomUUID().toString(),
            symbol = symbol,
            baseAsset = baseKey,
            quoteAsset = quote,
            side = SimulationOrderSide.SELL,
            type = SimulationOrderType.MARKET,
            executionPrice = execPrice,
            quantity = quantity,
            totalIdr = totalQuote,
            feeIdr = feeQuote,
            timestamp = System.currentTimeMillis(),
            pnlIdr = pnlQuote,
            pnlPercent = pnlPercent
        )

        val order = SimulationOrder(
            id = history.orderId,
            symbol = symbol,
            baseAsset = baseKey,
            quoteAsset = quote,
            side = SimulationOrderSide.SELL,
            type = SimulationOrderType.MARKET,
            limitPrice = execPrice,
            quantity = quantity,
            totalIdr = totalQuote,
            filledQuantity = quantity,
            filledAvgPrice = execPrice,
            feeIdr = feeQuote,
            status = SimulationOrderStatus.FILLED,
            filledAt = System.currentTimeMillis()
        )

        return Result.success(ExecutionResult(updatedWallet, history, order))
    }

    fun formatMoney(value: Double, quoteAsset: String): String {
        val isUsdt = isUsdtQuote(quoteAsset)
        return if (isUsdt) {
            String.format("%.4f %s", value, quoteAsset.uppercase())
        } else {
            "Rp " + String.format("%,.0f", value).replace(",", ".")
        }
    }
}
