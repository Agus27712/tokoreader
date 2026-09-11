import re

with open("app/src/main/java/com/tkc/screener/service/TokocryptoMarketService.kt", "r") as f:
    content = f.read()

old = """    fun toTokocryptoOpenPair(symbol: String): String {
        val p = toPairId(symbol).uppercase()
        return if (p.contains("_")) p else {
            if (p.endsWith("IDR")) p.dropLast(3) + "_IDR"
            else if (p.endsWith("BIDR")) p.dropLast(4) + "_BIDR"
            else if (p.endsWith("USDT")) p.dropLast(4) + "_USDT"
            else "${p}_IDR"
        }
    }"""

new = """    fun toTokocryptoOpenPair(symbol: String): String {
        val p = toPairId(symbol).uppercase()
        val formatted = if (p.contains("_")) p else {
            if (p.endsWith("IDR")) p.dropLast(3) + "_IDR"
            else if (p.endsWith("BIDR")) p.dropLast(4) + "_BIDR"
            else if (p.endsWith("USDT")) p.dropLast(4) + "_USDT"
            else "${p}_IDR"
        }
        // Tokocrypto open API uses _IDR for IDR pairs, not _BIDR
        return formatted.replace("_BIDR", "_IDR")
    }"""

content = content.replace(old, new)

with open("app/src/main/java/com/tkc/screener/service/TokocryptoMarketService.kt", "w") as f:
    f.write(content)
