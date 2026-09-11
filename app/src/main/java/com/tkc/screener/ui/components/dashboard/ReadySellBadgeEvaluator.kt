package com.tkc.screener.ui.components.dashboard

import androidx.compose.ui.graphics.Color
import com.tkc.screener.config.TradingFeeConfig
import com.tkc.screener.engine.sell.SellSignalEvaluator
import com.tkc.screener.model.CoinHoldingStatus
import com.tkc.screener.model.MarketTick
import com.tkc.screener.model.PositionContext
import com.tkc.screener.model.SellLifecycleState
import com.tkc.screener.model.TechnicalIndicators
import com.tkc.screener.ui.theme.DarkAppColors
import java.util.Locale

/**
 * Single source of truth for evaluating "Ready Sell" badges across the application,
 * delegating all calculations to [SellSignalEvaluator].
 */
data class ReadySellBadge(
    val label: String,
    val color: Color,
    val isExitDecisionEvent: Boolean = false
)

object ReadySellBadgeEvaluator {
    fun computeReadyBadge(
        context: PositionContext?,
        tick: MarketTick?,
        tradingFees: TradingFeeConfig,
        colorOrange: Color = DarkAppColors.orange,
        colorGreen: Color = DarkAppColors.green,
        colorRed: Color = DarkAppColors.red,
        rsi: Double? = null
    ): ReadySellBadge? {
        if (context == null || !context.hasPosition || (context.quantity ?: 0.0) <= 0.00000001) return null
        val currentPrice = tick?.price ?: context.currentPrice ?: 0.0
        if (currentPrice <= 0.0) return null

        val indicators = if (rsi != null) TechnicalIndicators(rsi14 = rsi) else null
        val sellState = SellSignalEvaluator.evaluate(
            context = context,
            indicators = indicators,
            tradingFees = tradingFees,
            high24h = tick?.high24h ?: 0.0
        )

        return when (sellState.state) {
            SellLifecycleState.TRAILING_TRIGGERED ->
                ReadySellBadge("🚨 TRAILING", colorOrange, isExitDecisionEvent = true)
            SellLifecycleState.STOP_LOSS_HIT ->
                ReadySellBadge("⚠️ STOP LOSS", colorRed, isExitDecisionEvent = true)
            SellLifecycleState.READY_TO_SELL -> {
                when {
                    sellState.reason == "Target TP1 tercapai" ->
                        ReadySellBadge("🎯 READY TO SELL", colorGreen, isExitDecisionEvent = true)
                    sellState.reason == "RSI Overbought" ->
                        ReadySellBadge("⚠️ RSI OVERBOUGHT", colorRed, isExitDecisionEvent = true)
                    sellState.reason == "Dekat High 24j" ->
                        ReadySellBadge("📈 DEKAT HIGH 24J", colorGreen, isExitDecisionEvent = true)
                    sellState.netProfitPct >= 2.0 ->
                        ReadySellBadge("💰 PROFIT +${String.format(Locale.US, "%.1f", sellState.netProfitPct)}%", colorGreen, isExitDecisionEvent = true)
                    else ->
                        ReadySellBadge("🎯 READY TO SELL", colorGreen, isExitDecisionEvent = true)
                }
            }
            SellLifecycleState.APPROACHING_TARGET ->
                ReadySellBadge("🎯 APPROACHING TARGET", colorOrange, isExitDecisionEvent = true)
            SellLifecycleState.MONITORING ->
                ReadySellBadge("🛡️ HOLDING", Color(0xFFF0B90B), isExitDecisionEvent = false)
            SellLifecycleState.NOT_HOLDING -> null
        }
    }

    fun computeReadyBadge(
        holding: CoinHoldingStatus?,
        tick: MarketTick?,
        tradingFees: TradingFeeConfig,
        colorOrange: Color = DarkAppColors.orange,
        colorGreen: Color = DarkAppColors.green,
        colorRed: Color = DarkAppColors.red,
        rsi: Double? = null
    ): ReadySellBadge? {
        if (holding == null || !holding.isHolding || holding.quantity <= 0.00000001) return null
        val currentPrice = tick?.price ?: 0.0
        if (currentPrice <= 0.0) return null

        val pairSymbol = tick?.symbol ?: ""
        val context = PositionContext.create(
            symbol = pairSymbol,
            spotPosition = null,
            holdingStatus = holding,
            currentPrice = currentPrice,
            fees = tradingFees
        )
        return computeReadyBadge(
            context = context,
            tick = tick,
            tradingFees = tradingFees,
            colorOrange = colorOrange,
            colorGreen = colorGreen,
            colorRed = colorRed,
            rsi = rsi
        )
    }
}
