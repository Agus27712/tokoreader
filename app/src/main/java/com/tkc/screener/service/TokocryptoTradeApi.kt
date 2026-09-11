package com.tkc.screener.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.Inet4Address
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Tokocrypto Open API (TAPI) Execution Layer.
 * Mendukung autentikasi HMAC-SHA256 untuk endpoint Open API Tokocrypto (/open/v1) & Binance Cloud (/api/v3).
 */
object TokocryptoTradeApi {
    private const val TOKOCRYPTO_BASE_URL = "https://www.tokocrypto.com"
    private const val BINANCE_BASE_URL = "https://api.binance.com"
    private const val RECV_WINDOW_MS = 10_000L

    private val client = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                val ipv4 = addresses.filterIsInstance<Inet4Address>()
                return if (ipv4.isNotEmpty()) ipv4 else addresses
            }
        })
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun hmacSha256(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.trim().toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun encodeQuery(params: LinkedHashMap<String, String>): String =
        params.entries.joinToString("&") { "${it.key}=${it.value}" }

    fun toTradeSymbol(symbol: String): String =
        TokocryptoMarketService.toDepthPairId(symbol).lowercase()

    fun toOrderSymbol(symbol: String): String =
        TokocryptoMarketService.toDepthPairId(symbol).uppercase()

    fun toTokocryptoSymbol(symbol: String): String =
        TokocryptoMarketService.toTokocryptoOpenPair(symbol)

    private suspend fun serverTimeMs(): Long = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$TOKOCRYPTO_BASE_URL/open/v1/common/time")
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val raw = if (json.has("timestamp")) json.optLong("timestamp", 0L)
                          else if (json.has("data")) json.optLong("data", 0L)
                          else 0L
                if (raw > 0L) return@withContext raw
            }
        } catch (_: Exception) { }

        try {
            val req = Request.Builder()
                .url("$BINANCE_BASE_URL/api/v3/time")
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val raw = json.optLong("serverTime", 0L)
                if (raw > 0L) return@withContext raw
            }
        } catch (_: Exception) { }

        System.currentTimeMillis()
    }

    private suspend fun signedRequest(
        baseUrl: String,
        apiKey: String,
        secretKey: String,
        method: String,
        path: String,
        params: LinkedHashMap<String, String>
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val payloadString = encodeQuery(params)
        val sign = hmacSha256(secretKey, payloadString)
        val fullUrl = "$baseUrl$path"

        val request = when (method.uppercase()) {
            "GET" -> Request.Builder()
                .url("$fullUrl?$payloadString&signature=$sign")
                .get()
                .header("X-MBX-APIKEY", apiKey.trim())
                .header("Accept", "application/json")
                .build()
            "DELETE" -> Request.Builder()
                .url("$fullUrl?$payloadString&signature=$sign")
                .delete()
                .header("X-MBX-APIKEY", apiKey.trim())
                .header("Accept", "application/json")
                .build()
            "POST" -> {
                val formBody = FormBody.Builder()
                params.forEach { (key, value) -> formBody.add(key, value) }
                formBody.add("signature", sign)

                Request.Builder()
                    .url(fullUrl)
                    .post(formBody.build())
                    .header("X-MBX-APIKEY", apiKey.trim())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()
            }
            else -> return@withContext false to "Unsupported HTTP method: $method"
        }

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                val json = try { JSONObject(responseBody) } catch (_: Exception) { null }
                val code = json?.optInt("code", 0) ?: 0
                val hasErrorCode = json != null && json.has("code") && code != 0
                if (response.isSuccessful && !hasErrorCode) {
                    true to responseBody
                } else {
                    val errorMsg = mapError(json, responseBody.ifBlank { response.message })
                    Timber.w("Tokocrypto API Request Failed: $path | $errorMsg")
                    false to errorMsg
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Tokocrypto API network error")
            false to "Tokocrypto API network error: ${e.localizedMessage}"
        }
    }

    private fun mapError(json: JSONObject?, fallback: String): String {
        val code = json?.optInt("code", 0) ?: 0
        val msg = json?.optString("msg", fallback).orEmpty()
        return when (code) {
            -1002, -2014 -> "API Key tidak valid atau belum diisi."
            -1021 -> "Timestamp invalid. Mohon sinkronkan jam perangkat Anda."
            -1022 -> "Signature invalid. Secret Key salah."
            -1121 -> "Invalid symbol. Pasangan koin tidak ditemukan di Tokocrypto."
            -2015 -> if (msg.isNotBlank()) "Akses ditolak: $msg. Pastikan IP Whitelist dan Trade Permission di Tokocrypto sudah aktif."
                     else "Akses ditolak (2015). Pastikan IP Whitelist dan Trade Permission di Tokocrypto sudah aktif."
            -1003 -> "Terlalu banyak permintaan (Rate limit). Harap tunggu sejenak."
            -2010 -> "Saldo tidak mencukupi untuk melakukan order ini."
            else -> "Error Tokocrypto [$code]: $msg | $fallback"
        }
    }

    data class TokocryptoBalances(
        val total: Map<String, Double>,
        val free: Map<String, Double>,
        val locked: Map<String, Double>
    )

    data class OrderResult(
        val success: Boolean,
        val message: String,
        val orderId: String = "",
        val clientOrderId: String = "",
        val executedQty: Double = 0.0,
        val origQty: Double = 0.0,
        val status: String = ""
    )

    enum class OrderSide { BUY, SELL }
    enum class OrderType { LIMIT, MARKET }

    suspend fun getAccount(apiKey: String, secretKey: String): Pair<TokocryptoBalances?, String> {
        if (apiKey.isBlank() || secretKey.isBlank()) return null to "API Key / Secret Key Tokocrypto belum diisi."
        val timestamp = serverTimeMs()
        val params = linkedMapOf(
            "timestamp" to timestamp.toString(),
            "recvWindow" to RECV_WINDOW_MS.toString()
        )

        // Coba Tokocrypto OpenAPI v1 spot asset
        var (ok, raw) = signedRequest(TOKOCRYPTO_BASE_URL, apiKey, secretKey, "GET", "/open/v1/account/spot/asset", params)
        if (ok) {
            try {
                val json = JSONObject(raw)
                val dataArr = json.optJSONArray("data")
                if (dataArr != null) {
                    val totalMap = mutableMapOf<String, Double>()
                    val freeMap = mutableMapOf<String, Double>()
                    val lockedMap = mutableMapOf<String, Double>()
                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.optJSONObject(i) ?: continue
                        val asset = item.optString("asset", "").lowercase()
                        if (asset.isBlank()) continue
                        val free = item.optString("free", "0").toDoubleOrNull() ?: 0.0
                        val locked = item.optString("locked", "0").toDoubleOrNull() ?: 0.0
                        freeMap[asset] = free
                        lockedMap[asset] = locked
                        totalMap[asset] = free + locked
                    }
                    return TokocryptoBalances(totalMap, freeMap, lockedMap) to "OK"
                }
            } catch (_: Exception) { }
        }

        // Fallback ke Binance Cloud account endpoint
        val (ok2, raw2) = signedRequest(BINANCE_BASE_URL, apiKey, secretKey, "GET", "/api/v3/account", params)
        if (ok2) {
            try {
                val json = JSONObject(raw2)
                val balancesArr = json.optJSONArray("balances")
                if (balancesArr != null) {
                    val totalMap = mutableMapOf<String, Double>()
                    val freeMap = mutableMapOf<String, Double>()
                    val lockedMap = mutableMapOf<String, Double>()
                    for (i in 0 until balancesArr.length()) {
                        val item = balancesArr.optJSONObject(i) ?: continue
                        val asset = item.optString("asset", "").lowercase()
                        if (asset.isBlank()) continue
                        val free = item.optString("free", "0").toDoubleOrNull() ?: 0.0
                        val locked = item.optString("locked", "0").toDoubleOrNull() ?: 0.0
                        freeMap[asset] = free
                        lockedMap[asset] = locked
                        totalMap[asset] = free + locked
                    }
                    return TokocryptoBalances(totalMap, freeMap, lockedMap) to "OK"
                }
            } catch (e: Exception) {
                return null to "Gagal parsing saldo akun: ${e.localizedMessage}"
            }
        }

        return null to (if (!ok) raw else raw2)
    }

    suspend fun createLimitOrderDetailed(
        apiKey: String,
        secretKey: String,
        symbol: String,
        side: OrderSide,
        price: Double,
        quantity: Double,
        clientOrderId: String? = null
    ): OrderResult {
        if (apiKey.isBlank() || secretKey.isBlank()) return OrderResult(false, "API Key / Secret Key Tokocrypto belum diisi.")
        if (price <= 0.0 || quantity <= 0.0) return OrderResult(false, "Harga atau kuantitas order tidak valid.")

        val timestamp = serverTimeMs()
        val pairOpen = toTokocryptoSymbol(symbol)
        val sideInt = if (side == OrderSide.BUY) "0" else "1"

        val params = linkedMapOf(
            "symbol" to pairOpen,
            "side" to sideInt,
            "type" to "1", // 1 = LIMIT
            "price" to "%.8f".format(java.util.Locale.US, price).trimEnd('0').trimEnd('.'),
            "quantity" to "%.8f".format(java.util.Locale.US, quantity).trimEnd('0').trimEnd('.'),
            "timestamp" to timestamp.toString(),
            "recvWindow" to RECV_WINDOW_MS.toString()
        )
        if (!clientOrderId.isNullOrBlank()) {
            params["clientId"] = clientOrderId
        }

        var (ok, raw) = signedRequest(TOKOCRYPTO_BASE_URL, apiKey, secretKey, "POST", "/open/v1/orders", params)
        if (!ok) {
            // Fallback ke Binance Cloud format
            val binanceSymbol = toOrderSymbol(symbol)
            val binanceParams = linkedMapOf(
                "symbol" to binanceSymbol,
                "side" to side.name,
                "type" to "LIMIT",
                "timeInForce" to "GTC",
                "price" to "%.8f".format(java.util.Locale.US, price).trimEnd('0').trimEnd('.'),
                "quantity" to "%.8f".format(java.util.Locale.US, quantity).trimEnd('0').trimEnd('.'),
                "timestamp" to timestamp.toString(),
                "recvWindow" to RECV_WINDOW_MS.toString()
            )
            if (!clientOrderId.isNullOrBlank()) {
                binanceParams["newClientOrderId"] = clientOrderId
            }
            val (ok2, raw2) = signedRequest(BINANCE_BASE_URL, apiKey, secretKey, "POST", "/api/v3/order", binanceParams)
            ok = ok2
            raw = raw2
        }

        if (!ok) return OrderResult(false, raw)

        return try {
            val json = JSONObject(raw)
            val data = if (json.has("data")) json.optJSONObject("data") ?: json else json
            val orderId = data.optString("orderId", data.optString("order_id", ""))
            val resClientId = data.optString("clientOrderId", clientOrderId.orEmpty())
            val execQty = data.optString("executedQty", "0").toDoubleOrNull() ?: 0.0
            val origQty = data.optString("origQty", quantity.toString()).toDoubleOrNull() ?: quantity
            val status = data.optString("status", "NEW")
            OrderResult(true, "Order berhasil dibuat! ID: $orderId", orderId, resClientId, execQty, origQty, status)
        } catch (e: Exception) {
            OrderResult(true, "Order berhasil dibuat", "", clientOrderId.orEmpty(), 0.0, quantity, "NEW")
        }
    }

    suspend fun createLimitOrderDetailed(
        apiKey: String,
        secretKey: String,
        symbol: String,
        side: String,
        price: Double,
        quantity: Double,
        clientOrderId: String? = null
    ): OrderResult {
        val orderSide = if (side.equals("buy", ignoreCase = true)) OrderSide.BUY else OrderSide.SELL
        return createLimitOrderDetailed(apiKey, secretKey, symbol, orderSide, price, quantity, clientOrderId)
    }

    suspend fun createLimitOrder(
        apiKey: String,
        secretKey: String,
        pair: String,
        side: String,
        price: Double,
        quantity: Double,
        clientOrderId: String? = null
    ): Pair<Boolean, String> {
        val res = createLimitOrderDetailed(apiKey, secretKey, pair, side, price, quantity, clientOrderId)
        return res.success to res.message
    }

    suspend fun createMarketOrderDetailed(
        apiKey: String,
        secretKey: String,
        symbol: String,
        side: OrderSide,
        quantity: Double = 0.0,
        quoteOrderQty: Double = 0.0
    ): OrderResult {
        if (apiKey.isBlank() || secretKey.isBlank()) return OrderResult(false, "API Key / Secret Key Tokocrypto belum diisi.")
        val timestamp = serverTimeMs()
        val pairOpen = toTokocryptoSymbol(symbol)
        val sideInt = if (side == OrderSide.BUY) "0" else "1"

        val params = linkedMapOf(
            "symbol" to pairOpen,
            "side" to sideInt,
            "type" to "2", // 2 = MARKET
            "timestamp" to timestamp.toString(),
            "recvWindow" to RECV_WINDOW_MS.toString()
        )
        if (quantity > 0.0) {
            params["quantity"] = "%.8f".format(java.util.Locale.US, quantity).trimEnd('0').trimEnd('.')
        }

        val (ok, raw) = signedRequest(TOKOCRYPTO_BASE_URL, apiKey, secretKey, "POST", "/open/v1/orders", params)
        if (!ok) return OrderResult(false, raw)

        return try {
            val json = JSONObject(raw)
            val data = if (json.has("data")) json.optJSONObject("data") ?: json else json
            val orderId = data.optString("orderId", "")
            OrderResult(true, "Market Order berhasil dikirim! ID: $orderId", orderId)
        } catch (_: Exception) {
            OrderResult(true, "Market Order terkirim")
        }
    }

    suspend fun cancelOrder(
        apiKey: String,
        secretKey: String,
        symbol: String,
        orderId: String
    ): Pair<Boolean, String> {
        if (apiKey.isBlank() || secretKey.isBlank()) return false to "API Key / Secret Key Tokocrypto belum diisi."
        val timestamp = serverTimeMs()
        val params = linkedMapOf(
            "symbol" to toTokocryptoSymbol(symbol),
            "orderId" to orderId,
            "timestamp" to timestamp.toString(),
            "recvWindow" to RECV_WINDOW_MS.toString()
        )
        val (ok, raw) = signedRequest(TOKOCRYPTO_BASE_URL, apiKey, secretKey, "POST", "/open/v1/orders/cancel", params)
        if (ok) return true to "Order $orderId berhasil dibatalkan."

        // Fallback
        val binanceParams = linkedMapOf(
            "symbol" to toOrderSymbol(symbol),
            "orderId" to orderId,
            "timestamp" to timestamp.toString(),
            "recvWindow" to RECV_WINDOW_MS.toString()
        )
        val (ok2, raw2) = signedRequest(BINANCE_BASE_URL, apiKey, secretKey, "DELETE", "/api/v3/order", binanceParams)
        return ok2 to (if (ok2) "Order $orderId berhasil dibatalkan." else raw2)
    }

    suspend fun getOrder(
        apiKey: String,
        secretKey: String,
        symbol: String,
        orderId: String? = null,
        clientOrderId: String? = null
    ): OrderResult {
        if (apiKey.isBlank() || secretKey.isBlank()) return OrderResult(false, "API Key / Secret Key Tokocrypto belum diisi.")
        val timestamp = serverTimeMs()
        val params = linkedMapOf(
            "timestamp" to timestamp.toString(),
            "recvWindow" to RECV_WINDOW_MS.toString()
        )
        if (!orderId.isNullOrBlank() && orderId != "0") {
            params["orderId"] = orderId
        }
        if (!clientOrderId.isNullOrBlank()) {
            params["clientId"] = clientOrderId
        }

        val (ok, raw) = signedRequest(TOKOCRYPTO_BASE_URL, apiKey, secretKey, "GET", "/open/v1/orders/detail", params)
        if (!ok) {
            // Fallback binance get order
            val binanceParams = linkedMapOf(
                "symbol" to toOrderSymbol(symbol),
                "timestamp" to timestamp.toString(),
                "recvWindow" to RECV_WINDOW_MS.toString()
            )
            if (!orderId.isNullOrBlank() && orderId != "0") {
                binanceParams["orderId"] = orderId
            }
            if (!clientOrderId.isNullOrBlank()) {
                binanceParams["origClientOrderId"] = clientOrderId
            }
            val (ok2, raw2) = signedRequest(BINANCE_BASE_URL, apiKey, secretKey, "GET", "/api/v3/order", binanceParams)
            if (!ok2) return OrderResult(false, raw2)
            return parseOrderResult(raw2, orderId.orEmpty())
        }

        return parseOrderResult(raw, orderId.orEmpty())
    }

    private fun parseOrderResult(raw: String, fallbackId: String): OrderResult {
        return try {
            val json = JSONObject(raw)
            val data = if (json.has("data")) json.optJSONObject("data") ?: json else json
            val id = data.optString("orderId", fallbackId)
            val clientOrderId = data.optString("clientOrderId", "")
            val execQty = data.optString("executedQty", "0").toDoubleOrNull() ?: 0.0
            val origQty = data.optString("origQty", "0").toDoubleOrNull() ?: 0.0
            val status = data.optString("status", "")
            OrderResult(true, "OK", id, clientOrderId, execQty, origQty, status)
        } catch (e: Exception) {
            OrderResult(false, "Gagal parsing status order: ${e.localizedMessage}")
        }
    }

    suspend fun openOrders(
        apiKey: String,
        secretKey: String,
        symbol: String = ""
    ): Pair<Boolean, String> {
        if (apiKey.isBlank() || secretKey.isBlank()) return false to "API Key / Secret Key Tokocrypto belum diisi."
        val timestamp = serverTimeMs()
        val params = linkedMapOf(
            "timestamp" to timestamp.toString(),
            "recvWindow" to RECV_WINDOW_MS.toString()
        )
        if (symbol.isNotBlank()) {
            params["symbol"] = toTokocryptoSymbol(symbol)
        }

        val (ok, raw) = signedRequest(TOKOCRYPTO_BASE_URL, apiKey, secretKey, "GET", "/open/v1/orders/open", params)
        if (ok) return true to raw

        // Fallback Binance
        val binanceParams = linkedMapOf(
            "timestamp" to timestamp.toString(),
            "recvWindow" to RECV_WINDOW_MS.toString()
        )
        if (symbol.isNotBlank()) {
            binanceParams["symbol"] = toOrderSymbol(symbol)
        }
        val (ok2, raw2) = signedRequest(BINANCE_BASE_URL, apiKey, secretKey, "GET", "/api/v3/openOrders", binanceParams)
        return ok2 to (if (ok2) raw2 else raw)
    }

    suspend fun getMyTrades(
        apiKey: String,
        secretKey: String,
        symbol: String,
        startTime: Long = 0L,
        endTime: Long = 0L,
        limit: Int = 50
    ): Pair<List<TradeResult>?, String> {
        if (apiKey.isBlank() || secretKey.isBlank()) return null to "API Key / Secret Key Tokocrypto belum diisi."
        val timestamp = serverTimeMs()
        val params = linkedMapOf(
            "symbol" to toTokocryptoSymbol(symbol),
            "limit" to limit.toString(),
            "timestamp" to timestamp.toString(),
            "recvWindow" to RECV_WINDOW_MS.toString()
        )
        if (startTime > 0) params["startTime"] = startTime.toString()
        if (endTime > 0) params["endTime"] = endTime.toString()

        val (ok, raw) = signedRequest(TOKOCRYPTO_BASE_URL, apiKey, secretKey, "GET", "/open/v1/orders/trades", params)
        if (!ok) return null to raw

        return try {
            val json = JSONObject(raw)
            val arr = json.optJSONArray("data") ?: json.optJSONArray("list") ?: JSONArray()
            val list = mutableListOf<TradeResult>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                list.add(
                    TradeResult(
                        id = item.optString("id", i.toString()),
                        orderId = item.optString("orderId", ""),
                        price = item.optString("price", "0").toDoubleOrNull() ?: 0.0,
                        qty = item.optString("qty", "0").toDoubleOrNull() ?: 0.0,
                        quoteQty = item.optString("quoteQty", "0").toDoubleOrNull() ?: 0.0,
                        isBuyer = item.optBoolean("isBuyer", true),
                        time = item.optLong("time", System.currentTimeMillis())
                    )
                )
            }
            list to "OK"
        } catch (e: Exception) {
            null to "Gagal membaca trades: ${e.localizedMessage}"
        }
    }

    data class TradeResult(
        val id: String,
        val orderId: String,
        val price: Double,
        val qty: Double,
        val quoteQty: Double,
        val isBuyer: Boolean,
        val time: Long
    )

    suspend fun myTrades(apiKey: String, secretKey: String, pair: String, limit: Int = 50): Pair<Boolean, String> {
        val (trades, msg) = getMyTrades(apiKey, secretKey, pair, limit = limit)
        if (trades != null) {
            val jsonArr = JSONArray()
            trades.forEach { t ->
                val obj = JSONObject()
                obj.put("id", t.id)
                obj.put("type", if (t.isBuyer) "buy" else "sell")
                obj.put("price", t.price)
                obj.put("qty", t.qty)
                obj.put("time", t.time)
                jsonArr.put(obj)
            }
            return true to jsonArr.toString()
        }
        return false to msg
    }

    fun parseTradesList(raw: String): List<JSONObject> {
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                list.add(item)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun tradeIdOf(trade: JSONObject): String = trade.optString("id", "")
    fun isBuyerOf(trade: JSONObject): Boolean = trade.optString("type", "buy").equals("buy", true)
    fun tradePriceOf(trade: JSONObject): Double = trade.optDouble("price", 0.0)
    fun tradeQtyOf(trade: JSONObject): Double = trade.optDouble("qty", 0.0)
    fun tradeTimeMs(trade: JSONObject): Long = trade.optLong("time", System.currentTimeMillis())
}
