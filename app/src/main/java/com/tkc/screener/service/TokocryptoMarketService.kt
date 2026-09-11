package com.tkc.screener.service

import com.tkc.screener.model.CandleBar
import com.tkc.screener.model.MarketTick
import com.tkc.screener.model.OrderBookItem
import com.tkc.screener.model.Timeframe
import com.tkc.screener.model.TradeStreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Tokocrypto Market Data Service
 * Menggunakan infrastruktur REST Tokocrypto & Binance Cloud untuk live tickers, klines, orderbook, dan trades.
 */
object TokocryptoMarketService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("id", "ID"))
    private data class ChangeReference(val close: Double, val fetchedAt: Long)
    private val changeReferenceCache = mutableMapOf<String, ChangeReference>()

    @Volatile
    private var cachedTokocryptoSymbols: Set<String>? = null
    private var cachedTokocryptoSymbolsTime = 0L

    suspend fun getOfficialTokocryptoSymbols(): Set<String> {
        val now = System.currentTimeMillis()
        val current = cachedTokocryptoSymbols
        if (current != null && (now - cachedTokocryptoSymbolsTime) < 30 * 60 * 1000L) {
            return current
        }
        return withContext(Dispatchers.IO) {
            try {
                val body = get("https://www.tokocrypto.com/open/v1/common/symbols")
                if (body != null) {
                    val json = JSONObject(body)
                    val list = json.optJSONObject("data")?.optJSONArray("list")
                    if (list != null && list.length() > 0) {
                        val set = HashSet<String>(list.length() * 4)
                        for (i in 0 until list.length()) {
                            val item = list.getJSONObject(i)
                            val sym = item.optString("symbol", "")
                            val base = item.optString("baseAsset", "")
                            val quote = item.optString("quoteAsset", "")
                            if (sym.isNotBlank()) {
                                set.add(sym.uppercase())
                                set.add(sym.replace("_", "").uppercase())
                            }
                            if (base.isNotBlank() && quote.isNotBlank()) {
                                set.add("${base}${quote}".uppercase())
                                set.add("${base}_${quote}".uppercase())
                            }
                        }
                        cachedTokocryptoSymbols = set
                        cachedTokocryptoSymbolsTime = now
                        return@withContext set
                    }
                }
            } catch (_: Exception) {}
            cachedTokocryptoSymbols ?: emptySet()
        }
    }

    // --- Rate limit + retry ---
    private val rateMutex = Mutex()
    private val lastRequestAt = AtomicLong(0L)
    private const val MIN_INTERVAL_MS = 150L
    private const val MAX_RETRIES = 3

    fun toPairId(symbol: String): String {
        val s = symbol.trim().lowercase().replace("/", "_").replace("-", "_").replace(" ", "")
        return when {
            s.contains("_") -> s.replace("bidr", "idr")
            s.endsWith("bidr") -> s.dropLast(4) + "_idr"
            s.endsWith("idr") -> s.dropLast(3) + "_idr"
            s.endsWith("usdt") -> s.dropLast(4) + "_usdt"
            s.endsWith("usdc") -> s.dropLast(4) + "_usdc"
            else -> s + "_idr"
        }
    }

    fun toDepthPairId(symbol: String): String = toPairId(symbol).replace("_", "").uppercase()

    fun toTokocryptoOpenPair(symbol: String): String {
        val p = toPairId(symbol).uppercase()
        val formatted = if (p.contains("_")) p else {
            if (p.endsWith("IDR")) p.dropLast(3) + "_IDR"
            else if (p.endsWith("BIDR")) p.dropLast(4) + "_BIDR"
            else if (p.endsWith("USDT")) p.dropLast(4) + "_USDT"
            else "${p}_IDR"
        }
        // Tokocrypto open API uses _IDR for IDR pairs, not _BIDR
        return formatted.replace("_BIDR", "_IDR")
    }

    private suspend fun throttle() {
        rateMutex.withLock {
            val now = System.currentTimeMillis()
            val wait = MIN_INTERVAL_MS - (now - lastRequestAt.get())
            if (wait > 0) delay(wait)
            lastRequestAt.set(System.currentTimeMillis())
        }
    }

    /** GET dengan rate-limit + exponential backoff pada 429 / 5xx / network error. Selalu di Dispatchers.IO */
    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        val urlsToTry = mutableListOf(url)
        if (url.startsWith("https://api.binance.com")) {
            urlsToTry.add(url.replace("https://api.binance.com", "https://api1.binance.com"))
            urlsToTry.add(url.replace("https://api.binance.com", "https://api2.binance.com"))
            urlsToTry.add(url.replace("https://api.binance.com", "https://api3.binance.com"))
            urlsToTry.add(url.replace("https://api.binance.com", "https://data-api.binance.vision"))
        }

        for (currentUrl in urlsToTry) {
            var attempt = 0
            while (attempt < 2) {
                attempt++
                try {
                    throttle()
                    val req = Request.Builder()
                        .url(currentUrl)
                        .get()
                        .header("User-Agent", "TKCScreener/1.0.0 (Android)")
                        .header("Accept", "application/json")
                        .build()
                    client.newCall(req).execute().use { response ->
                        val code = response.code
                        val body = response.body?.string()
                        when {
                            response.isSuccessful -> return@withContext body
                            code == 429 || code in 500..599 -> {
                                val backoff = 200L * attempt
                                delay(backoff)
                            }
                            else -> {
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                    val backoff = 150L * attempt
                    delay(backoff)
                }
            }
        }
        null
    }

    suspend fun fetchTicker(symbol: String, prevPrice: Double = 0.0): MarketTick? = withContext(Dispatchers.IO) {
        try {
            val binanceSymbol = toDepthPairId(symbol)
            val body = get("https://api.binance.com/api/v3/ticker/24hr?symbol=$binanceSymbol")
                ?: get("https://www.tokocrypto.com/open/v1/market/ticker/24hr?symbol=${toTokocryptoOpenPair(symbol)}")
                ?: return@withContext null

            val json = JSONObject(body)
            val t = if (json.has("data")) json.optJSONObject("data") ?: json else json
            val last = t.optString("lastPrice", "0").toDoubleOrNull()
                ?: t.optString("price", "0").toDoubleOrNull() ?: 0.0
            if (last <= 0) return@withContext null

            val high = t.optString("highPrice", "0").toDoubleOrNull() ?: last
            val low = t.optString("lowPrice", "0").toDoubleOrNull() ?: last
            val quoteVol = t.optString("quoteVolume", "0").toDoubleOrNull()
                ?: (t.optString("volume", "0").toDoubleOrNull() ?: 0.0) * last
            val change = t.optString("priceChangePercent", "0").toDoubleOrNull() ?: Double.NaN

            MarketTick(
                symbol = binanceSymbol,
                price = last,
                high24h = high,
                low24h = low,
                volume24h = quoteVol,
                change24h = change,
                timestamp = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchTickers(pairIds: List<String>): List<MarketTick> = withContext(Dispatchers.IO) {
        try {
            val all = fetchAllMarketTicks()
            pairIds.mapNotNull { raw ->
                val depthId = toDepthPairId(raw)
                all[depthId] ?: all[raw.uppercase()] ?: all[raw.lowercase()]
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isSafeTradableAsset(
        price: Double,
        volume24h: Double,
        high24h: Double,
        low24h: Double,
        isIdrPair: Boolean = true,
        isExplicitlyFavored: Boolean = false
    ): Boolean {
        if (isExplicitlyFavored) return true
        if (!price.isFinite() || price <= 0.0) return false

        if (isIdrPair) {
            if (price <= 1.0) return false
            if (price < 25.0 && volume24h < 500_000_000.0) return false
            if (volume24h < 50_000_000.0) return false
            if (low24h <= 1.0 && price < 50.0) return false
        } else {
            if (volume24h < 5_000.0 && volume24h > 0) return false
        }
        return true
    }

    suspend fun fetchTopVolumeTicks(limit: Int = 15, excludeStable: Boolean = true): List<MarketTick> = withContext(Dispatchers.IO) {
        try {
            val all = fetchAllMarketTicks().values.distinctBy { it.symbol }
            val stableBases = setOf("USDT", "USDC", "DAI", "BUSD", "TUSD", "IDRT", "FDUSD")
            val filtered = all.filter { tick ->
                val base = when {
                    tick.symbol.endsWith("IDR") -> tick.symbol.removeSuffix("IDR")
                    tick.symbol.endsWith("BIDR") -> tick.symbol.removeSuffix("BIDR")
                    tick.symbol.endsWith("USDT") -> tick.symbol.removeSuffix("USDT")
                    else -> tick.symbol
                }
                if (excludeStable && base in stableBases) return@filter false
                isSafeTradableAsset(
                    price = tick.price,
                    volume24h = tick.volume24h,
                    high24h = tick.high24h,
                    low24h = tick.low24h,
                    isIdrPair = tick.symbol.endsWith("IDR") || tick.symbol.endsWith("BIDR")
                )
            }
            filtered.sortedByDescending { it.volume24h }.take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchScalpingGainersTicks(limit: Int = 15, excludeStable: Boolean = true): List<MarketTick> = withContext(Dispatchers.IO) {
        try {
            val all = fetchAllMarketTicks().values.distinctBy { it.symbol }
            val stableBases = setOf("USDT", "USDC", "DAI", "BUSD", "TUSD", "IDRT", "FDUSD")
            val filtered = all.filter { tick ->
                val base = when {
                    tick.symbol.endsWith("IDR") -> tick.symbol.removeSuffix("IDR")
                    tick.symbol.endsWith("BIDR") -> tick.symbol.removeSuffix("BIDR")
                    tick.symbol.endsWith("USDT") -> tick.symbol.removeSuffix("USDT")
                    else -> tick.symbol
                }
                if (excludeStable && base in stableBases) return@filter false
                if (!tick.change24h.isFinite() || tick.change24h <= 0.0) return@filter false
                isSafeTradableAsset(
                    price = tick.price,
                    volume24h = tick.volume24h,
                    high24h = tick.high24h,
                    low24h = tick.low24h,
                    isIdrPair = tick.symbol.endsWith("IDR") || tick.symbol.endsWith("BIDR")
                )
            }
            filtered.sortedWith(
                compareByDescending<MarketTick> { tick ->
                    val volScore = kotlin.math.ln((tick.volume24h / 10_000_000.0).coerceAtLeast(1.0))
                    tick.change24h * (1.0 + volScore * 0.3)
                }.thenByDescending { it.volume24h }
            ).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchCandles(
        symbol: String,
        timeframe: Timeframe,
        limit: Int = 300,
        explicitFromSec: Long? = null,
        explicitToSec: Long? = null
    ): List<CandleBar> = withContext(Dispatchers.IO) {
        try {
            val interval = when (timeframe.code) {
                "1" -> "1m"
                "5" -> "5m"
                "15" -> "15m"
                "60" -> "1h"
                "240" -> "4h"
                "D" -> "1d"
                else -> "1m"
            }
            val binanceSymbol = toDepthPairId(symbol)
            val reqLimit = limit.coerceIn(10, 1000)
            val url = StringBuilder("https://api.binance.com/api/v3/klines?symbol=$binanceSymbol&interval=$interval&limit=$reqLimit")
            if (explicitFromSec != null && explicitFromSec > 0) {
                url.append("&startTime=${explicitFromSec * 1000L}")
            }
            if (explicitToSec != null && explicitToSec > 0) {
                url.append("&endTime=${explicitToSec * 1000L}")
            }

            val body = get(url.toString()) ?: return@withContext emptyList()
            val array = JSONArray(body)
            val result = mutableListOf<CandleBar>()
            for (i in 0 until array.length()) {
                val row = array.optJSONArray(i) ?: continue
                val openTime = row.optLong(0, 0L)
                val open = row.optString(1, "0").toDoubleOrNull() ?: 0.0
                val high = row.optString(2, "0").toDoubleOrNull() ?: 0.0
                val low = row.optString(3, "0").toDoubleOrNull() ?: 0.0
                val close = row.optString(4, "0").toDoubleOrNull() ?: 0.0
                val volume = row.optString(5, "0").toDoubleOrNull() ?: 0.0
                if (openTime <= 0 || open <= 0 || high <= 0 || low <= 0 || close <= 0) continue
                result += CandleBar(
                    timestamp = openTime,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume
                )
            }
            result.sortedBy { it.timestamp }.takeLast(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchOrderBook(symbol: String, limit: Int = 15): Pair<List<OrderBookItem>, List<OrderBookItem>> =
        withContext(Dispatchers.IO) {
            try {
                val tkcSymbol = toTokocryptoOpenPair(symbol)
                val reqLimit = limit.coerceIn(5, 50)
                var body = get("https://www.tokocrypto.com/open/v1/market/depth?symbol=$tkcSymbol&limit=$reqLimit")
                
                var dataObj: JSONObject? = null
                if (body != null) {
                    val tkcJson = JSONObject(body)
                    if (tkcJson.optInt("code") == 0) {
                        dataObj = tkcJson.optJSONObject("data")
                    }
                }
                
                if (dataObj == null) {
                    val binanceSymbol = toDepthPairId(symbol)
                    val binanceBody = get("https://api.binance.com/api/v3/depth?symbol=$binanceSymbol&limit=$reqLimit")
                    if (binanceBody != null) {
                        dataObj = JSONObject(binanceBody)
                    }
                }
                
                if (dataObj == null) return@withContext emptyList<OrderBookItem>() to emptyList()
                
                val j = dataObj
                val bids = mutableListOf<OrderBookItem>()
                val asks = mutableListOf<OrderBookItem>()
                var bidSum = 0.0
                var askSum = 0.0
                val bidArr = j.optJSONArray("bids") ?: JSONArray()
                for (i in 0 until minOf(limit, bidArr.length())) {
                    val row = bidArr.optJSONArray(i) ?: continue
                    val price = row.optString(0).toDoubleOrNull() ?: 0.0
                    val amount = row.optString(1).toDoubleOrNull() ?: 0.0
                    if (price <= 0 || amount <= 0) continue
                    bidSum += amount
                    bids.add(OrderBookItem(price, amount, bidSum, true))
                }
                val askArr = j.optJSONArray("asks") ?: JSONArray()
                for (i in 0 until minOf(limit, askArr.length())) {
                    val row = askArr.optJSONArray(i) ?: continue
                    val price = row.optString(0).toDoubleOrNull() ?: 0.0
                    val amount = row.optString(1).toDoubleOrNull() ?: 0.0
                    if (price <= 0 || amount <= 0) continue
                    askSum += amount
                    asks.add(OrderBookItem(price, amount, askSum, false))
                }
                bids to asks
            } catch (_: Exception) {
                emptyList<OrderBookItem>() to emptyList()
            }
        }

    suspend fun fetchRecentTrades(symbol: String, limit: Int = 15): List<TradeStreamItem> = withContext(Dispatchers.IO) {
        try {
            val binanceSymbol = toDepthPairId(symbol)
            val reqLimit = limit.coerceIn(5, 50)
            val body = get("https://api.binance.com/api/v3/trades?symbol=$binanceSymbol&limit=$reqLimit")
                ?: return@withContext emptyList()
            val arr = JSONArray(body)
            val list = mutableListOf<TradeStreamItem>()
            for (i in 0 until minOf(limit, arr.length())) {
                val t = arr.getJSONObject(i)
                val ts = t.optLong("time", System.currentTimeMillis())
                val isBuyerMaker = t.optBoolean("isBuyerMaker", false)
                list.add(
                    TradeStreamItem(
                        id = t.optString("id", ts.toString()),
                        price = t.optString("price", "0").toDoubleOrNull() ?: 0.0,
                        amount = t.optString("qty", "0").toDoubleOrNull() ?: 0.0,
                        timeFormatted = timeFormat.format(Date(ts)),
                        isBuy = !isBuyerMaker
                    )
                )
            }
            list.reversed()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchAllMarketTicks(): Map<String, MarketTick> = withContext(Dispatchers.IO) {
        try {
            val officialSymbols = getOfficialTokocryptoSymbols()
            val body = get("https://api.binance.com/api/v3/ticker/24hr") ?: return@withContext emptyMap()
            val array = JSONArray(body)
            val now = System.currentTimeMillis()
            val map = mutableMapOf<String, MarketTick>()

            for (i in 0 until array.length()) {
                val t = array.optJSONObject(i) ?: continue
                val s = t.optString("symbol", "").uppercase()
                // Index pair IDR, BIDR, dan USDT
                if (!s.endsWith("IDR") && !s.endsWith("BIDR") && !s.endsWith("USDT") && !s.endsWith("IDRT")) continue

                // Pastikan hanya memproses pair yang benar-benar terdaftar di Tokocrypto
                if (officialSymbols.isNotEmpty()) {
                    val sNormalized = s.replace("BIDR", "IDR")
                    if (!officialSymbols.contains(s) && !officialSymbols.contains(sNormalized)) {
                        continue
                    }
                }

                val last = t.optString("lastPrice", "0").toDoubleOrNull() ?: 0.0
                if (last <= 0) continue

                val high = t.optString("highPrice", "0").toDoubleOrNull() ?: last
                val low = t.optString("lowPrice", "0").toDoubleOrNull() ?: last
                val quoteVol = t.optString("quoteVolume", "0").toDoubleOrNull()
                    ?: (t.optString("volume", "0").toDoubleOrNull() ?: 0.0) * last
                val change = t.optString("priceChangePercent", "0").toDoubleOrNull() ?: Double.NaN

                val tick = MarketTick(
                    symbol = s,
                    price = last,
                    high24h = high,
                    low24h = low,
                    volume24h = quoteVol,
                    change24h = change,
                    timestamp = now
                )

                map[s] = tick
                map[s.lowercase()] = tick

                // Also map with underscore and alternative quote names for compatibility
                if (s.endsWith("IDR")) {
                    val base = s.removeSuffix("IDR")
                    map["${base}_IDR"] = tick
                    map["${base.lowercase()}_idr"] = tick
                    map["${base}BIDR"] = tick
                    map["${base}_BIDR"] = tick
                    map["${base.lowercase()}_bidr"] = tick
                } else if (s.endsWith("BIDR")) {
                    val base = s.removeSuffix("BIDR")
                    if (!map.containsKey("${base}IDR")) {
                        map["${base}IDR"] = tick
                        map["${base}_IDR"] = tick
                        map["${base.lowercase()}_idr"] = tick
                    }
                    map["${base}_BIDR"] = tick
                    map["${base.lowercase()}_bidr"] = tick
                } else if (s.endsWith("USDT")) {
                    val base = s.removeSuffix("USDT")
                    map["${base}_USDT"] = tick
                    map["${base.lowercase()}_usdt"] = tick
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun fetchAccountBalanceDetails(apiKey: String, secretKey: String): Pair<Map<String, Double>?, String> = withContext(Dispatchers.IO) {
        val (balances, msg) = TokocryptoTradeApi.getAccount(apiKey, secretKey)
        if (balances != null) {
            val combined = mutableMapOf<String, Double>()
            balances.free.forEach { (asset, amount) ->
                if (amount > 0.00000001 || asset == "bidr" || asset == "idr" || asset == "usdt") {
                    combined[asset] = amount
                }
            }
            Pair(combined, "Koneksi Tokocrypto TAPI Berhasil (${combined.size} aset terdeteksi).")
        } else {
            Pair(null, msg)
        }
    }

    suspend fun fetchPublicIp(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://api.ipify.org").build()
            client.newCall(request).execute().use { resp ->
                resp.body?.string()?.trim() ?: "Gagal mendapatkan IP"
            }
        } catch (_: Exception) {
            "Gagal mengecek IP"
        }
    }

    suspend fun fetchAccountBalance(apiKey: String, secretKey: String): Map<String, Double>? {
        return fetchAccountBalanceDetails(apiKey, secretKey).first
    }

    suspend fun placeTradeOrder(
        apiKey: String,
        secretKey: String,
        pair: String,
        type: String, // "buy" or "sell"
        price: Long,
        amountIdr: Double
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val side = if (type.equals("buy", true)) TokocryptoTradeApi.OrderSide.BUY else TokocryptoTradeApi.OrderSide.SELL
        val priceDouble = price.toDouble()
        val quantity = if (side == TokocryptoTradeApi.OrderSide.BUY) {
            if (priceDouble > 0) amountIdr / priceDouble else 0.0
        } else {
            amountIdr
        }

        val res = TokocryptoTradeApi.createLimitOrderDetailed(
            apiKey = apiKey,
            secretKey = secretKey,
            symbol = pair,
            side = side,
            price = priceDouble,
            quantity = quantity
        )
        res.success to res.message
    }
}
