package com.tkc.screener.engine.global

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GlobalContextManager {
    private val _context = MutableStateFlow(GlobalMarketContext())
    val context: StateFlow<GlobalMarketContext> = _context.asStateFlow()

    private val priceHistory = mutableListOf<PriceTick>()
    private val HISTORY_WINDOW_MS = 3 * 60 * 1000L // 3 minutes window for crash detection

    private var isStarted = false

    fun start() {
        if (isStarted) return
        isStarted = true
        _context.value = _context.value.copy(isConnected = true, dataSource = "Tokocrypto")
    }

    fun stop() {
        isStarted = false
    }

    fun subscribeCoin(baseAsset: String) {
        start()
    }

    /**
     * Memperbarui status pasar global / BTC menggunakan data BTC dari Tokocrypto.
     */
    fun updateFallbackFromTokocrypto(priceIdr: Double, changePct: Double, usdtRate: Double = 16200.0) {
        val now = System.currentTimeMillis()
        if (priceIdr <= 0) return

        val effectiveUsdtRate = if (usdtRate > 0) usdtRate else 16200.0
        val priceUsdt = priceIdr / effectiveUsdtRate

        priceHistory.add(PriceTick(priceUsdt, now))
        priceHistory.removeAll { now - it.timestamp > HISTORY_WINDOW_MS }

        val tokocryptoTicker = BtcTickerData(price = priceUsdt, changePct = changePct, source = "Tokocrypto")
        val currentContext = evaluateGlobalContext(tokocryptoTicker, now).copy(
            isConnected = true,
            dataSource = "Tokocrypto"
        )
        _context.value = currentContext
    }

    fun updateFromBinance(btcPrice: Double, change24hPct: Double) {
        val now = System.currentTimeMillis()
        if (btcPrice <= 0) return

        priceHistory.add(PriceTick(btcPrice, now))
        priceHistory.removeAll { now - it.timestamp > HISTORY_WINDOW_MS }

        val binanceTicker = BtcTickerData(price = btcPrice, changePct = change24hPct, source = "Binance")
        val currentContext = evaluateGlobalContext(binanceTicker, now)
        _context.value = currentContext
    }

    private fun evaluateGlobalContext(ticker: BtcTickerData, now: Long): GlobalMarketContext {
        val isCrashing = detectCrash()
        val regime = when {
            isCrashing -> GlobalRegime.FLASH_CRASH
            ticker.changePct >= 3.0 -> GlobalRegime.BULLISH
            ticker.changePct <= -3.0 -> GlobalRegime.BEARISH
            else -> GlobalRegime.SIDEWAYS
        }

        return GlobalMarketContext(
            btcPriceUsdt = ticker.price,
            btc24hChangePct = ticker.changePct,
            regime = regime,
            isVetoActive = isCrashing,
            vetoReason = if (isCrashing) "Flash Crash BTC Terdeteksi!" else null,
            isConnected = true,
            dataSource = ticker.source,
            lastUpdateTime = now
        )
    }

    private fun detectCrash(): Boolean {
        if (priceHistory.size < 5) return false
        val now = System.currentTimeMillis()
        val recentHistory = priceHistory.filter { now - it.timestamp <= 3 * 60 * 1000L }
        if (recentHistory.size < 2) return false

        val newest = recentHistory.maxByOrNull { it.timestamp } ?: return false
        val oldest = recentHistory.minByOrNull { it.timestamp } ?: return false

        if (newest.price < oldest.price) {
            val dropPct = (oldest.price - newest.price) / oldest.price * 100
            if (dropPct >= 1.5) { // 1.5% drop in 3 mins is considered a flash crash
                return true
            }
        }
        return false
    }

    private data class PriceTick(val price: Double, val timestamp: Long)
}
