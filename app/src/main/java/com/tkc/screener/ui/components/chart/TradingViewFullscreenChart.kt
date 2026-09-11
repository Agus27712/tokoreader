package com.tkc.screener.ui.components.chart

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.tkc.screener.model.TradingPair

/**
 * Fullscreen chart: load official TradingView widget chart page.
 * Data 100% Tokocrypto/Binance Cloud compatible using IDR/USDT quote.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewFullscreenChart(
    pair: TradingPair,
    modifier: Modifier = Modifier
) {
    val chartUrl = remember(pair) {
        val base = pair.baseAsset.uppercase()
        val quote = when {
            pair.quoteAsset.equals("BIDR", ignoreCase = true) -> "IDR"
            pair.quoteAsset.isBlank() -> "IDR"
            else -> pair.quoteAsset.uppercase()
        }
        val symbol = "BINANCE:${base}${quote}"
        "https://s.tradingview.com/widgetembed/?symbol=$symbol&theme=dark&interval=60&style=1&timezone=Asia%2FJakarta&withdateranges=1&hide_side_toolbar=0&allow_symbol_change=1&save_image=0&locale=id"
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.setSupportZoom(true)
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (request?.isForMainFrame == false) return false
                        return !(url.contains("tokocrypto.com") ||
                            url.contains("binance.com") ||
                            url.contains("tradingview.com") ||
                            url.contains("tradingview-widget.com") ||
                            url.contains("tvscdn.com") ||
                            url.startsWith("about:") ||
                            url.startsWith("data:"))
                    }
                }
                loadUrl(chartUrl)
            }
        },
        update = { webView ->
            if (webView.url != chartUrl) {
                webView.loadUrl(chartUrl)
            }
        }
    )
}

