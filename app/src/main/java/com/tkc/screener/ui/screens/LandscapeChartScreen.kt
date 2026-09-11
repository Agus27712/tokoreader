package com.tkc.screener.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tkc.screener.model.MarketConnectionState
import com.tkc.screener.model.Timeframe
import com.tkc.screener.ui.components.MarketEmptyOrErrorState
import com.tkc.screener.ui.components.chart.LightweightChartView
import com.tkc.screener.ui.theme.*
import com.tkc.screener.viewmodel.TradingViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun LandscapeChartScreen(
    viewModel: TradingViewModel,
    onBackToDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val pair by viewModel.selectedPair.collectAsState()
    val timeframe by viewModel.selectedTimeframe.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val candles by viewModel.recentCandles.collectAsState()
    val tick by viewModel.currentTick.collectAsState()
    val signal by viewModel.aiSignalState.collectAsState()

    var showVolume by remember { mutableStateOf(true) }
    var showEma by remember { mutableStateOf(true) }
    var showBb by remember { mutableStateOf(false) }
    var showStoch by remember { mutableStateOf(false) }

    // Force landscape + hide System UI (status bar + nav bar)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    BackHandler { onBackToDetail() }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF131722))) {
        when (val state = connectionState) {
            is MarketConnectionState.ConnectionLost -> MarketEmptyOrErrorState(
                false, true, state.title, state.reason,
                { viewModel.retryConnection() }, Modifier.fillMaxSize()
            )
            is MarketConnectionState.Loading -> MarketEmptyOrErrorState(
                true, false, onRetry = { viewModel.retryConnection() },
                modifier = Modifier.fillMaxSize()
            )
            is MarketConnectionState.Connected -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Landscape Top Control Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(Color(0xFF1E222D))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Back button & Pair title & Live Price
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBackToDetail,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kembali",
                                    tint = TvTextPrimary
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${pair.baseAsset}/${pair.quoteAsset}",
                                color = TvTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(12.dp))
                            val price = tick?.price ?: 0.0
                            val change = tick?.change24h ?: 0.0
                            val isIdr = pair.quoteAsset.equals("IDR", ignoreCase = true)
                            val priceStr = if (isIdr) {
                                "Rp " + NumberFormat.getNumberInstance(Locale("in", "ID")).format(price.toLong())
                            } else {
                                "$%.4f".format(price)
                            }
                            Text(
                                text = priceStr,
                                color = if (change >= 0) TvGreen else TvRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "%+.2f%%".format(change),
                                color = if (change >= 0) TvGreen else TvRed,
                                fontSize = 11.sp
                            )
                        }

                        // Center: Timeframe selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Timeframe.values().forEach { tf ->
                                val isSelected = tf == timeframe
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) TvBlue.copy(alpha = 0.25f) else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (isSelected) TvBlue else Color(0xFF2A2E39),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { viewModel.selectTimeframe(tf) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = tf.label,
                                        color = if (isSelected) TvBlue else TvTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Right: Indicator toggles
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "VOL" to showVolume to { showVolume = !showVolume },
                                "EMA" to showEma to { showEma = !showEma },
                                "BB" to showBb to { showBb = !showBb },
                                "STOCH" to showStoch to { showStoch = !showStoch }
                            ).forEach { (item, toggle) ->
                                val (label, active) = item
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (active) TvGreen.copy(alpha = 0.2f) else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (active) TvGreen else Color(0xFF2A2E39),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { toggle() }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (active) TvGreen else TvTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Main Lightweight Candlestick Chart
                    LightweightChartView(
                        candles = candles,
                        currentPrice = tick?.price ?: 0.0,
                        showVolume = showVolume,
                        showEma = showEma,
                        showBb = showBb,
                        showStochRsi = showStoch,
                        entryPrice = signal.entryPrice,
                        targetPrice1 = signal.targetPrice1,
                        targetPrice2 = signal.targetPrice2,
                        stopLoss = signal.stopLoss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

