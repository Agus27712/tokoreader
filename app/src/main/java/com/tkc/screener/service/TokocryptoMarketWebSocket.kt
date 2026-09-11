package com.tkc.screener.service

import com.tkc.screener.model.CandleBar
import com.tkc.screener.model.MarketTick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Realtime Tokocrypto WebSocket Stream.
 * Menghubungkan ke live stream ticker, kline 1m, dan trades.
 * Reconnect dengan exponential backoff.
 */
class TokocryptoMarketWebSocket(
    private val scope: CoroutineScope,
    private val onTick: (MarketTick) -> Unit,
    private val onCandle: (CandleBar) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var pairId = ""
    private var streamSymbol = ""
    private var reconnectAttempt = 0
    private var candle: CandleBar? = null
    private val lastMessageAt = AtomicLong(0L)

    fun start(symbol: String) {
        val nextPair = TokocryptoMarketService.toDepthPairId(symbol).lowercase()
        if (nextPair.isBlank()) return
        stop(false)
        pairId = nextPair.uppercase()
        streamSymbol = nextPair
        reconnectAttempt = 0
        connect()
    }

    fun stop(notify: Boolean = true) {
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "switch pair")
        socket = null
        pairId = ""
        streamSymbol = ""
        candle = null
        if (notify) onDisconnected()
    }

    fun close() = stop(false)

    fun isStale(staleMs: Long = 25_000L): Boolean {
        val last = lastMessageAt.get()
        if (last <= 0L) return false
        return System.currentTimeMillis() - last > staleMs
    }

    private val wsDomains = listOf(
        "stream.binance.com:9443",
        "stream1.binance.com:9443",
        "stream2.binance.com:9443",
        "stream3.binance.com:9443",
        "stream4.binance.com:9443",
        "stream.binance.com:443"
    )

    private fun connect() {
        if (streamSymbol.isBlank()) return
        val domain = wsDomains[reconnectAttempt % wsDomains.size]
        val url = "wss://$domain/stream?streams=${streamSymbol}@ticker/${streamSymbol}@kline_1m/${streamSymbol}@trade"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "TKCScreener/1.0.0")
            .build()
        socket = client.newWebSocket(request, Listener())
    }

    private fun reconnect() {
        if (streamSymbol.isBlank() || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val waitMs = min(12_000L, (800L * (1L shl min(reconnectAttempt, 4))))
            reconnectAttempt++
            delay(waitMs)
            if (isActive && streamSymbol.isNotBlank()) connect()
        }
    }

    private fun consume(message: String) {
        lastMessageAt.set(System.currentTimeMillis())
        val root = try { JSONObject(message) } catch (e: Exception) {
            Timber.e(e, "Gagal parse message WebSocket Tokocrypto")
            null
        } ?: return

        val data = if (root.has("data")) root.optJSONObject("data") ?: root else root
        val eventType = data.optString("e", "")

        when (eventType) {
            "24hrTicker" -> {
                val last = data.optString("c", "0").toDoubleOrNull() ?: 0.0
                if (last <= 0.0) return
                val high = data.optString("h", "0").toDoubleOrNull() ?: last
                val low = data.optString("l", "0").toDoubleOrNull() ?: last
                val quoteVol = data.optString("q", "0").toDoubleOrNull() ?: 0.0
                val change = data.optString("P", "0").toDoubleOrNull() ?: Double.NaN
                val eventTime = data.optLong("E", System.currentTimeMillis())
                onTick(MarketTick(pairId, last, high, low, quoteVol, change, eventTime))
                updateOneMinuteCandle(eventTime, last, 0.0)
            }
            "kline" -> {
                val k = data.optJSONObject("k") ?: return
                val openTime = k.optLong("t", 0L)
                val open = k.optString("o", "0").toDoubleOrNull() ?: 0.0
                val high = k.optString("h", "0").toDoubleOrNull() ?: 0.0
                val low = k.optString("l", "0").toDoubleOrNull() ?: 0.0
                val close = k.optString("c", "0").toDoubleOrNull() ?: 0.0
                val volume = k.optString("v", "0").toDoubleOrNull() ?: 0.0
                if (openTime > 0 && close > 0) {
                    val klineBar = CandleBar(openTime, open, high, low, close, volume)
                    candle = klineBar
                    onCandle(klineBar)
                    val eventTime = data.optLong("E", System.currentTimeMillis())
                    onTick(MarketTick(pairId, close, high, low, volume * close, Double.NaN, eventTime))
                }
            }
            "trade" -> {
                val price = data.optString("p", "0").toDoubleOrNull() ?: 0.0
                val qty = data.optString("q", "0").toDoubleOrNull() ?: 0.0
                val tradeTime = data.optLong("T", System.currentTimeMillis())
                if (price > 0) {
                    onTick(MarketTick(pairId, price, price, price, 0.0, Double.NaN, tradeTime))
                    updateOneMinuteCandle(tradeTime, price, qty)
                }
            }
        }
    }

    private fun updateOneMinuteCandle(timestamp: Long, price: Double, volume: Double) {
        val minute = timestamp - (timestamp % 60_000L)
        val current = candle
        val next = if (current == null || current.timestamp != minute) {
            current?.let(onCandle)
            CandleBar(minute, price, price, price, price, max(0.0, volume))
        } else {
            current.copy(
                high = max(current.high, price),
                low = min(current.low, price),
                close = price,
                volume = current.volume + max(0.0, volume)
            )
        }
        candle = next
        onCandle(next)
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempt = 0
            lastMessageAt.set(System.currentTimeMillis())
            onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) = consume(text)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket === webSocket) socket = null
            onDisconnected()
            reconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket === webSocket) socket = null
            onDisconnected()
            if (code != 1000) reconnect()
        }
    }
}
