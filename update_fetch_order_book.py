import re

with open("app/src/main/java/com/tkc/screener/service/TokocryptoMarketService.kt", "r") as f:
    content = f.read()

old_fetch_order_book = """    suspend fun fetchOrderBook(symbol: String, limit: Int = 15): Pair<List<OrderBookItem>, List<OrderBookItem>> =
        withContext(Dispatchers.IO) {
            try {
                val binanceSymbol = toDepthPairId(symbol)
                val reqLimit = limit.coerceIn(5, 50)
                val body = get("https://api.binance.com/api/v3/depth?symbol=$binanceSymbol&limit=$reqLimit")
                    ?: return@withContext emptyList<OrderBookItem>() to emptyList()
                val j = JSONObject(body)"""

new_fetch_order_book = """    suspend fun fetchOrderBook(symbol: String, limit: Int = 15): Pair<List<OrderBookItem>, List<OrderBookItem>> =
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
                
                val j = dataObj"""

content = content.replace(old_fetch_order_book, new_fetch_order_book)

with open("app/src/main/java/com/tkc/screener/service/TokocryptoMarketService.kt", "w") as f:
    f.write(content)

