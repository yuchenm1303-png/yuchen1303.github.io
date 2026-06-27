package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.StockNativeIndexKlineViewModel
import com.yuchen.ailedger.StockNativePageUiState
import com.yuchen.ailedger.model.StockNativeIndexDetail
import java.util.Locale

@Composable
internal fun StockNativeIndexScreenV2(
    ui: StockNativePageUiState,
    code: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenIndex: (String) -> Unit
) {
    val klineViewModel: StockNativeIndexKlineViewModel = viewModel(key = "native-index-kline-$code")
    val klineUi by klineViewModel.uiState.collectAsState()
    val detail = ui.indexDetail
    var tab by remember(detail?.code) { mutableStateOf("minute") }

    LaunchedEffect(detail?.code) {
        detail?.code?.takeIf { it.isNotBlank() }?.let { klineViewModel.load(it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            StockNativePageHeader(
                label = "指数详情 · A股",
                onBack = onBack,
                onRefresh = {
                    onRefresh()
                    detail?.code?.let { klineViewModel.load(it, true) }
                },
                loading = ui.indexLoading || klineUi.loading
            )
        }
        if (detail == null) {
            item {
                StockNativeGlassPanel(Modifier.fillMaxWidth().height(260.dp)) {
                    StockLoadingOrError(ui.indexLoading, ui.indexError, "指数详情暂不可用", Modifier.fillMaxSize())
                }
            }
            return@LazyColumn
        }
        item { NativeIndexHeroV2(detail) }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("minute" to "分时", "daily" to "日K", "five" to "五日").forEach { (key, label) ->
                            StockNativePill(
                                text = label,
                                active = tab == key,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                fontSize = 9
                            ) {
                                tab = key
                                if (key == "daily" && klineUi.points.isEmpty()) klineViewModel.load(detail.code)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (tab) {
                                "daily" -> "指数日K"
                                "five" -> "五日分时"
                                else -> "指数分时"
                            },
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            when {
                                tab == "daily" && klineUi.loading -> "正在加载历史K线"
                                tab == "daily" -> "${klineUi.points.size}根真实K线"
                                tab == "five" -> "真实五日分钟数据"
                                else -> "实时分时数据"
                            },
                            color = StockAqua.copy(alpha = 0.56f),
                            fontSize = 7.sp
                        )
                    }
                    StockNativeTrendChart(
                        minutePoints = if (tab == "five") detail.fiveDayPoints else detail.minutePoints,
                        klinePoints = klineUi.points,
                        showKline = tab == "daily",
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (tab == "daily") klineUi.points.firstOrNull()?.date.orEmpty() else "09:30",
                            color = Color.White.copy(alpha = 0.30f),
                            fontSize = 7.sp
                        )
                        Text(
                            if (tab == "daily") "${klineUi.points.size}根" else if (tab == "five") "五日" else "11:30 / 13:00",
                            color = Color.White.copy(alpha = 0.30f),
                            fontSize = 7.sp
                        )
                        Text(
                            if (tab == "daily") klineUi.points.lastOrNull()?.date.orEmpty() else "15:00",
                            color = Color.White.copy(alpha = 0.30f),
                            fontSize = 7.sp
                        )
                    }
                    if (tab == "daily" && !klineUi.error.isNullOrBlank()) {
                        Text(klineUi.error.orEmpty(), color = StockYellow.copy(alpha = 0.80f), fontSize = 8.sp)
                    }
                }
            }
        }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    StockSectionTitle("全市场背景", "指数走势结合市场宽度与情绪查看")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StockMetricTile("上涨", stockCount(detail.marketBreadth.upCount), StockRise, Modifier.weight(1f))
                        StockMetricTile("下跌", stockCount(detail.marketBreadth.downCount), StockFall, Modifier.weight(1f))
                        StockMetricTile("红盘率", stockPercent(detail.marketBreadth.redRate), Color.White, Modifier.weight(1f))
                        StockMetricTile(
                            "情绪温度",
                            detail.sentiment.temperature?.let { String.format(Locale.US, "%.0f", it) } ?: "--",
                            StockAqua,
                            Modifier.weight(1f)
                        )
                    }
                    NativeIndexWideRowV2("全市场成交额", "沪深京实时汇总", detail.marketBreadth.marketAmount)
                }
            }
        }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StockSectionTitle("其他主要指数", "点击后共用同一指数详情页")
                    if (detail.relatedIndices.isEmpty()) {
                        StockLoadingOrError(false, null, "其他指数暂不可用")
                    } else {
                        detail.relatedIndices.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { item ->
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .height(66.dp)
                                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                                            .clickable { onOpenIndex(item.code) }
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(item.name, color = Color.White.copy(alpha = 0.90f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                                        Row(Modifier.fillMaxWidth()) {
                                            Text(item.code, color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp, modifier = Modifier.weight(1f))
                                            Text(item.changePercent, color = stockTone(item.changePercent), fontSize = 8.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeIndexHeroV2(detail: StockNativeIndexDetail) {
    val quote = detail.quote
    StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 30.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("A-SHARE INDEX", color = StockAqua.copy(alpha = 0.72f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Text(detail.name.ifBlank { quote.name }, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail.code, color = Color.White.copy(alpha = 0.38f), fontSize = 8.sp)
                }
                Text(
                    detail.dataSourceLabel,
                    color = StockAqua.copy(alpha = 0.52f),
                    fontSize = 7.sp,
                    modifier = Modifier.width(120.dp),
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(quote.price, color = stockTone(quote.changePercent), fontSize = 38.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(10.dp))
                Text("${quote.changeAmount}  ${quote.changePercent}", color = stockTone(quote.changePercent), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("今开" to quote.open, "最高" to quote.high, "最低" to quote.low).forEach { (label, value) ->
                    StockMetricTile(
                        label,
                        value,
                        if (label == "最高") StockRise else if (label == "最低") StockFall else Color.White,
                        Modifier.weight(1f)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StockMetricTile(
                    "昨收",
                    if (quote.previousClose > 0f) String.format(Locale.US, "%.2f", quote.previousClose) else "--",
                    Color.White,
                    Modifier.weight(1f)
                )
                StockMetricTile("成交额", quote.amount, Color.White, Modifier.weight(1f))
                StockMetricTile("成交量", quote.volume, Color.White, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NativeIndexWideRowV2(label: String, subtitle: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(15.dp))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White.copy(alpha = 0.68f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp)
        }
        Text(value.ifBlank { "--" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}
