package com.tkc.screener.engine

import com.tkc.screener.config.ScalpingSensitivity
import com.tkc.screener.config.StrategyMode
import com.tkc.screener.config.TradingFeeConfig
import com.tkc.screener.engine.scalping.ScalpingMtfEvaluator
import com.tkc.screener.engine.secondwave.SecondWaveEvaluator
import com.tkc.screener.engine.swing.SwingEvaluator
import com.tkc.screener.model.AISignalState
import com.tkc.screener.model.CandleBar
import com.tkc.screener.model.MarketTick
import com.tkc.screener.model.ScalpingStage
import com.tkc.screener.model.SignalAction
import com.tkc.screener.model.TechnicalIndicators
import com.tkc.screener.model.Timeframe
import com.tkc.screener.model.TrendSentiment
import com.tkc.screener.service.TokocryptoMarketService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Thin orchestrator: realtime buffers + mode routing. Scoring stays in dedicated evaluators. */
class LearningTradingEngine(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    var strategyMode: StrategyMode = StrategyMode.SCALPING
    var isScalpingMode: Boolean
        get() = strategyMode == StrategyMode.SCALPING
        set(value) {
            if (value) {
                strategyMode = StrategyMode.SCALPING
            } else if (strategyMode == StrategyMode.SCALPING) {
                strategyMode = StrategyMode.SWING
            }
        }
    var scalpingSensitivity = ScalpingSensitivity.CONSERVATIVE
    var tradingFees = TradingFeeConfig()

    private val candles = mutableListOf<CandleBar>()
    private var currentTick: MarketTick? = null
    private var mtfRefreshJob: Job? = null
    private var lastMtfRefresh = 0L
    private var mtfSymbol = ""
    private var h4Candles: List<CandleBar> = emptyList()
    private var h1Candles: List<CandleBar> = emptyList()
    private var m15Candles: List<CandleBar> = emptyList()
    private var m1Candles: List<CandleBar> = emptyList()
    private val _signalState = MutableStateFlow(AISignalState())
    val signalState: StateFlow<AISignalState> = _signalState.asStateFlow()
    private val _indicators = MutableStateFlow(TechnicalIndicators())
    val indicators: StateFlow<TechnicalIndicators> = _indicators.asStateFlow()

    var onCandidateSignalTransition: ((com.tkc.screener.engine.scalping.SignalTransition) -> Unit)? = null

    var currentFormingVolume: Double = 0.0

    var currentOrderBookBids: List<com.tkc.screener.model.OrderBookItem> = emptyList()
    var currentOrderBookAsks: List<com.tkc.screener.model.OrderBookItem> = emptyList()

    fun onOrderBookUpdate(bids: List<com.tkc.screener.model.OrderBookItem>, asks: List<com.tkc.screener.model.OrderBookItem>) {
        currentOrderBookBids = bids
        currentOrderBookAsks = asks
    }

    fun onTickUpdate(tick: MarketTick) {
        if (tick.price <= 0.0) return
        currentTick = tick

        when (strategyMode) {
            StrategyMode.SCALPING -> {
                if (m1Candles.isNotEmpty()) runScalping()
            }
            StrategyMode.SECOND_WAVE -> {
                if (m15Candles.isNotEmpty()) runSecondWave()
            }
            StrategyMode.SWING -> {
                val hasCandles = synchronized(candles) { candles.isNotEmpty() }
                if (hasCandles) runSwing()
            }
            StrategyMode.OFFICE_DAILY -> {
                val hasCandles = synchronized(candles) { candles.isNotEmpty() }
                if (hasCandles) runOfficeDaily()
            }
            StrategyMode.TRENCHING -> {
                if (m15Candles.isNotEmpty()) runTrenching()
            }
        }

        refreshScalpingTimeframesIfDue(tick.symbol)
    }

    private fun lastLastLow(currLow: Double, price: Double): Double = if (currLow <= 0.0) price else minOf(currLow, price)

    fun onCandleUpdate(candle: CandleBar) {
        if (candle.open <= 0.0 || candle.high <= 0.0 || candle.low <= 0.0 || candle.close <= 0.0) return
        synchronized(candles) {
            val index = candles.indexOfFirst { it.timestamp == candle.timestamp }
            if (index >= 0) candles[index] = candle else candles.add(candle)
            candles.sortBy { it.timestamp }
            while (candles.size > 500) candles.removeAt(0)
        }
        when (strategyMode) {
            StrategyMode.SCALPING -> {
                if (candle.timestamp >= (m1Candles.lastOrNull()?.timestamp ?: 0L)) {
                    val updated = (m1Candles + candle).distinctBy { it.timestamp }.sortedBy { it.timestamp }.takeLast(250)
                    m1Candles = updated
                    runScalping()
                }
            }
            StrategyMode.SECOND_WAVE -> runSecondWave()
            StrategyMode.SWING -> runSwing()
            StrategyMode.OFFICE_DAILY -> runOfficeDaily()
            StrategyMode.TRENCHING -> runTrenching()
        }
    }

    fun resetForOffline(preserveState: Boolean = false, lastKnownPrice: Double = 0.0, cachedCandles: List<CandleBar> = emptyList()) {
        currentTick = null
        mtfRefreshJob?.cancel()
        mtfRefreshJob = null
        lastMtfRefresh = 0L
        mtfSymbol = ""
        h4Candles = emptyList(); h1Candles = emptyList(); m15Candles = emptyList(); m1Candles = emptyList()
        synchronized(candles) { candles.clear() }
        if (preserveState) return

        val priceText = if (lastKnownPrice > 0.0) "Rp ${String.format(java.util.Locale.US, "%,.0f", lastKnownPrice)}" else "Terakhir Disimpan"
        _indicators.value = TechnicalIndicators()
        _signalState.value = AISignalState(
            action = SignalAction.HOLD,
            confidence = 0,
            sentiment = TrendSentiment.NEUTRAL_CONSOLIDATION,
            reasoning = listOf(
                "MODE OFFLINE: Terputus dari Server Tokocrypto.",
                "Snapshot Harga Terakhir: $priceText",
                "Sinyal LIVE ditangguhkan untuk keamanan modal.",
                "Periksa koneksi internet / status API Tokocrypto."
            ),
            timestamp = System.currentTimeMillis(),
            scalpingStage = ScalpingStage.HOLD,
            isOfflineMode = true,
            offlineSnapshotTime = System.currentTimeMillis(),
            offlineReason = "Koneksi terputus — Sinyal live dihentikan."
        )
    }

    private fun refreshScalpingTimeframesIfDue(symbol: String) {
        if (symbol.isBlank()) return
        val now = System.currentTimeMillis()
        val intervalMs = if (strategyMode == StrategyMode.SCALPING) 10_000L else 20_000L
        if (now - lastMtfRefresh < intervalMs && mtfSymbol == symbol) return
        if (mtfRefreshJob?.isActive == true) return
        lastMtfRefresh = now; mtfSymbol = symbol
        mtfRefreshJob = scope.launch {
            when (strategyMode) {
                StrategyMode.SECOND_WAVE -> {
                    com.tkc.screener.util.MtfCacheManager.setActiveSymbol(symbol)
                    
                    var h4 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.H4) ?: emptyList()
                    var h1 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.H1) ?: emptyList()
                    var m15 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.M15) ?: emptyList()

                    if (h4.size < 20 || h1.size < 20 || m15.size < 20) {
                        kotlinx.coroutines.delay(500)
                        h4 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.H4) ?: emptyList()
                        h1 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.H1) ?: emptyList()
                        m15 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.M15) ?: emptyList()
                    }

                    if (h4.size >= 20 && h1.size >= 20 && m15.size >= 20 && currentTick?.symbol == symbol) {
                        h4Candles = h4.dropLast(1); h1Candles = h1.dropLast(1); m15Candles = m15.dropLast(1)
                        runSecondWave()
                    }
                }
                StrategyMode.SCALPING -> {
                    com.tkc.screener.util.MtfCacheManager.setActiveSymbol(symbol)
                    
                    var h1 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.H1) ?: emptyList()
                    var m15 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.M15) ?: emptyList()
                    var m1 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.M1) ?: emptyList()

                    if (h1.size < 20 || m15.size < 20 || m1.size < 20) {
                        kotlinx.coroutines.delay(500)
                        h1 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.H1) ?: emptyList()
                        m15 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.M15) ?: emptyList()
                        m1 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.M1) ?: emptyList()
                    }

                    if (h1.size >= 20 && m15.size >= 20 && m1.size >= 20 && currentTick?.symbol == symbol) {
                        h1Candles = h1.dropLast(1); m15Candles = m15.dropLast(1)
                        m1Candles = m1.dropLast(1)
                        runScalping()
                    }
                }
                StrategyMode.SWING -> {
                    val h1Job = async { TokocryptoMarketService.fetchCandles(symbol, Timeframe.H1, 200) }
                    val h1 = h1Job.await()
                    if (h1.isNotEmpty() && currentTick?.symbol == symbol) {
                        val closedH1 = h1.dropLast(1)
                        synchronized(candles) {
                            if (candles.isEmpty() || candles.size < closedH1.size) {
                                candles.clear()
                                candles.addAll(closedH1)
                            }
                        }
                        runSwing()
                    }
                }
                StrategyMode.OFFICE_DAILY -> {
                    val h1Job = async { TokocryptoMarketService.fetchCandles(symbol, Timeframe.H1, 200) }
                    val h1 = h1Job.await()
                    if (h1.isNotEmpty() && currentTick?.symbol == symbol) {
                        val closedH1 = h1.dropLast(1)
                        synchronized(candles) {
                            if (candles.isEmpty() || candles.size < closedH1.size) {
                                candles.clear()
                                candles.addAll(closedH1)
                            }
                        }
                        runOfficeDaily()
                    }
                }
                StrategyMode.TRENCHING -> {
                    com.tkc.screener.util.MtfCacheManager.setActiveSymbol(symbol)
                    
                    var h1 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.H1) ?: emptyList()
                    var m15 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.M15) ?: emptyList()

                    if (h1.size < 20 || m15.size < 20) {
                        kotlinx.coroutines.delay(500)
                        h1 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.H1) ?: emptyList()
                        m15 = com.tkc.screener.util.MtfCacheManager.getCachedCandles(symbol, Timeframe.M15) ?: emptyList()
                    }

                    if (h1.size >= 20 && m15.size >= 20 && currentTick?.symbol == symbol) {
                        h1Candles = h1.dropLast(1)
                        m15Candles = m15.dropLast(1)
                        runTrenching()
                    }
                }
            }
        }
    }

    private fun runScalping() {
        val tick = currentTick ?: return
        // Jangan silent-return total: update state "menunggu data" biar UI nggak beku/bengong
        if (h1Candles.size < 20 || m15Candles.size < 20 || m1Candles.size < 20) {
            val need = "H1 ${h1Candles.size}/20 · M15 ${m15Candles.size}/20 · M1 ${m1Candles.size}/20"
            _signalState.value = AISignalState(
                action = SignalAction.HOLD,
                confidence = 15,
                entryPrice = tick.price,
                reasoning = listOf("Menunggu data MTF scalping ($need)."),
                timestamp = System.currentTimeMillis(),
                scalpingStage = ScalpingStage.WATCH,
                isOfflineMode = false
            )
            return
        }
        val result = ScalpingMtfEvaluator.evaluate(
            com.tkc.screener.engine.global.GlobalContextManager.context.value,
            tick.price, h1Candles, m15Candles, m1Candles,
            currentFormingVolume, currentOrderBookBids, currentOrderBookAsks,
            tradingFees, scalpingSensitivity
        ) ?: return

        // P2.2 Signal Lifecycle Tracking
        val tracked = com.tkc.screener.engine.scalping.SignalLifecycleManager.process(tick.symbol, tick.price, result.signal, StrategyMode.SCALPING)
        val finalSignal = (tracked.activeSignalState ?: result.signal).copy(
            lifecycleState = tracked.state
        )

        _indicators.value = result.indicators
        _signalState.value = finalSignal
        tracked.transition?.let { trans ->
            if (trans.hasTriggeringTransition) {
                onCandidateSignalTransition?.invoke(trans)
            }
        }
    }

    private fun runSecondWave() {
        val tick = currentTick ?: return
        if (h4Candles.size < 20 || h1Candles.size < 20 || m15Candles.size < 20) return
        val result = SecondWaveEvaluator.evaluate(com.tkc.screener.engine.global.GlobalContextManager.context.value, tick.price, h4Candles, h1Candles, m15Candles, tradingFees)
        
        val tracked = com.tkc.screener.engine.scalping.SignalLifecycleManager.process(tick.symbol, tick.price, result.signal, StrategyMode.SECOND_WAVE)
        val finalSignal = (tracked.activeSignalState ?: result.signal).copy(
            lifecycleState = tracked.state
        )

        _indicators.value = result.indicators
        _signalState.value = finalSignal
        tracked.transition?.let { trans ->
            if (trans.hasTriggeringTransition) {
                onCandidateSignalTransition?.invoke(trans)
            }
        }
    }

    private fun runSwing() {
        if (strategyMode != StrategyMode.SWING) return
        val tick = currentTick ?: return
        val history = synchronized(candles) { candles.toList() }
        val result = SwingEvaluator.evaluate(
            globalContext = com.tkc.screener.engine.global.GlobalContextManager.context.value,
            price = tick.price,
            history = history,
            fees = tradingFees
        )
        
        val tracked = com.tkc.screener.engine.scalping.SignalLifecycleManager.process(tick.symbol, tick.price, result.signal, StrategyMode.SWING)
        val finalSignal = (tracked.activeSignalState ?: result.signal).copy(
            lifecycleState = tracked.state
        )

        _indicators.value = result.indicators
        _signalState.value = finalSignal
        tracked.transition?.let { trans ->
            if (trans.hasTriggeringTransition) {
                onCandidateSignalTransition?.invoke(trans)
            }
        }
    }

    private fun runOfficeDaily() {
        if (strategyMode != StrategyMode.OFFICE_DAILY) return
        val tick = currentTick ?: return
        val history = synchronized(candles) { candles.toList() }
        val result = com.tkc.screener.engine.officedaily.OfficeDailyEvaluator.evaluate(com.tkc.screener.engine.global.GlobalContextManager.context.value, tick.price, history, tradingFees)
        
        val tracked = com.tkc.screener.engine.scalping.SignalLifecycleManager.process(tick.symbol, tick.price, result.signal, StrategyMode.OFFICE_DAILY)
        val finalSignal = (tracked.activeSignalState ?: result.signal).copy(
            lifecycleState = tracked.state
        )

        _indicators.value = result.indicators
        _signalState.value = finalSignal
        tracked.transition?.let { trans ->
            if (trans.hasTriggeringTransition) {
                onCandidateSignalTransition?.invoke(trans)
            }
        }
    }

    private fun runTrenching() {
        if (strategyMode != StrategyMode.TRENCHING) return
        val tick = currentTick ?: return
        
        // Pass position awareness
        val store = com.tkc.screener.trading.SpotPositionStore(com.tkc.screener.AppContextProvider.context)
        val position = store.get(tick.symbol)
        val hasPosition = position.state != com.tkc.screener.trading.SpotPositionState.NO_POSITION
        val entryPrice = position.entryPrice
        
        val result = com.tkc.screener.engine.trenching.TrenchingEvaluator.evaluate(
            globalContext = com.tkc.screener.engine.global.GlobalContextManager.context.value,
            currentPrice = tick.price,
            candles = h1Candles,
            ltfCandles = m15Candles,
            bids = currentOrderBookBids,
            asks = currentOrderBookAsks,
            hasPosition = hasPosition,
            entryPrice = entryPrice,
            tradingFees = tradingFees
        )
        if (result != null) {
            val tracked = com.tkc.screener.engine.scalping.SignalLifecycleManager.process(tick.symbol, tick.price, result, StrategyMode.TRENCHING)
            val finalSignal = (tracked.activeSignalState ?: result).copy(
                lifecycleState = tracked.state
            )
            _signalState.value = finalSignal
            tracked.transition?.let { trans ->
                if (trans.hasTriggeringTransition) {
                    onCandidateSignalTransition?.invoke(trans)
                }
            }
        }
    }
}
