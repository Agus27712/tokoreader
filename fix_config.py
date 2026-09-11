import re
with open("app/src/main/java/com/tkc/screener/config/AppConfiguration.kt", "r") as f:
    content = f.read()

# Replace the duplicate TOKOCRYPTO enum with nothing
duplicate_str = """    TOKOCRYPTO(
        label = "Tokocrypto",
        shortCode = "BIDR",
        defaultQuoteAsset = "BIDR",
        defaultFeeConfig = TradingFeeConfig(
            buyMakerPct = 0.10,
            buyTakerPct = 0.10,
            sellMakerPct = 0.10,
            sellTakerPct = 0.10
        ),
        description = "Pasar Kripto Tokocrypto (Pair BIDR/USDT) dengan orderbook & candle live."
    ),"""

content = content.replace(duplicate_str, "")

with open("app/src/main/java/com/tkc/screener/config/AppConfiguration.kt", "w") as f:
    f.write(content)
