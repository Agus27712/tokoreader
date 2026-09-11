package com.tkc.screener.viewmodel

fun TradingViewModel.getGroqApiKey() = prefs.groqApiKey
fun TradingViewModel.saveGroqApiKey(key: String) { prefs.groqApiKey = key }
fun TradingViewModel.getGeminiApiKey() = prefs.geminiApiKey
fun TradingViewModel.saveGeminiApiKey(key: String) { prefs.geminiApiKey = key }

fun TradingViewModel.toggleWatchlist(symbol: String) {
    prefs.toggleWatchlist(symbol)
    val newList = prefs.getWatchlist()
    _watchlist.value = newList
    com.tkc.screener.util.MtfCacheManager.updateQueues(newList.toList(), emptyList())
}

fun TradingViewModel.isWatched(symbol: String) = prefs.isInWatchlist(symbol)
