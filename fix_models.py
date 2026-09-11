with open("app/src/main/java/com/tkc/screener/model/TradingViewModels.kt", "r") as f:
    content = f.read()

import re

# Remove the duplicate val tokocryptoPair: String = tokocryptoPair
content = re.sub(r'val tokocryptoPair:\s*String\s*=\s*tokocryptoPair,?\n', '', content)

# Remove val POPULAR_TOKOCRYPTO_PAIRS = POPULAR_TOKOCRYPTO_PAIRS
content = re.sub(r'val POPULAR_TOKOCRYPTO_PAIRS\s*=\s*POPULAR_TOKOCRYPTO_PAIRS\n', '', content)

# Remove effectiveTokocryptoPair that just calls itself
content = re.sub(r'fun effectiveTokocryptoPair\(\):\s*String\s*=\s*effectiveTokocryptoPair\(\)\n', '', content)

# Remove the redundant else if in effectiveTokocryptoPair
content = content.replace("else if (tokocryptoPair.isNotBlank()) tokocryptoPair\n", "")

with open("app/src/main/java/com/tkc/screener/model/TradingViewModels.kt", "w") as f:
    f.write(content)

