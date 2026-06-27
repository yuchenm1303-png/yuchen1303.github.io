package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.yuchen.ailedger.StockNativePageUiState
import com.yuchen.ailedger.model.StockNativeHotType
import com.yuchen.ailedger.model.StockNativeIndexDetail
import com.yuchen.ailedger.model.StockNativeQuote
import com.yuchen.ailedger.model.StockNativeRankingType
import com.yuchen.ailedger.model.StockNativeSectorDetail
import java.util.Locale

@Composable
internal fun StockNativeRankingScreen(
    ui: StockNativePageUiState,
    type: StockNativeRankingType,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectType: (StockNativeRankingType) -> Unit,
    onOpenStock: (String, Boolean) -> Unit
) {
    val active = ui.rankingType.takeIf { ui.rankingItems.isNotEmpty() || ui.rankingLoading } ?: type
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { StockNativePageHeader("独立榜单 · A股", onBack, onRefresh, ui.rankingLoading) }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 30.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("A-SHARE RANKING", color = StockAqua.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text(active.title, color = Color.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black)
                    Text(active.subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp, lineHeight = 14.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (ui.rankingLoading) "正在刷新真实榜单" else "真实榜单数据 · 20秒刷新", color = Color.White.copy(alpha = 0.38f), fontSize = 8.sp)
                        Text("${ui.rankingItems.size} 只", color = StockAqua, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(38.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(StockNativeRankingType.entries, key = { it.wire }) { ranking ->
                    StockNativePill(
                        text = ranking.title,
                        active = ranking == active,
                        modifier = Modifier.width(if (ranking.title.length >= 6) 112.dp else 88.dp).fillMaxSize(),
                        fontSize = 9,
                        onClick = { onSelectType(ranking) }
                    )
                }
            }
        }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 30.dp, contentPadding = 12.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().height(30.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("排名", color = Color.White.copy(alpha = 0.32f), fontSize = 8.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.Center)
                        Text("股票", color = Color.White.copy(alpha = 0.32f), fontSize = 8.sp, modifier = Modifier.weight(1f))
                        Text(active.metricLabel, color = Color.White.copy(alpha = 0.32f), fontSize = 8.sp, modifier = Modifier.width(76.dp), textAlign = TextAlign.End)
                        Text("涨跌", color = Color.White.copy(alpha = 0.32f), fontSize = 8.sp, modifier = Modifier.width(68.dp), textAlign = TextAlign.End)
                    }
                    StockDivider()
                    if (ui.rankingItems.isEmpty()) {
                        StockLoadingOrError(ui.rankingLoading, ui.rankingError, "榜单暂无可展示股票")
                    } else {
                        ui.rankingItems.forEach { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .clickable { onOpenStock(item.code, false) }
                                    .padding(horizontal = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.width(38.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .width(28.dp)
                                            .height(28.dp)
                                            .background(
                                                if (item.rank <= 3) StockYellow.copy(alpha = 0.13f) else StockAqua.copy(alpha = 0.07f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            item.rank.toString(),
                                            color = if (item.rank <= 3) StockYellow else StockAqua,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, color = Color.White.copy(alpha = 0.93f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOf(item.code, item.industry).filter { it.isNotBlank() }.joinToString(" · "), color = Color.White.copy(alpha = 0.32f), fontSize = 7.sp, maxLines = 1)
                                }
                                Text(item.metric(active), color = Color.White.copy(alpha = 0.80f), fontSize = 9.sp, modifier = Modifier.width(76.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.displayedChange(active), color = stockTone(item.displayedChange(active)), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(68.dp), textAlign = TextAlign.End)
                            }
                            StockDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StockNativeHotScreen(
    ui: StockNativePageUiState,
    type: StockNativeHotType,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectType: (StockNativeHotType) -> Unit,
    onOpenStock: (String, Boolean) -> Unit
) {
    val snapshot = ui.hotSnapshot
    val active = snapshot.type.takeIf { snapshot.items.isNotEmpty() || ui.hotLoading } ?: type
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { StockNativePageHeader("实时热点 · A股", onBack, onRefresh, ui.hotLoading) }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 30.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("A-SHARE HOT", color = StockAqua.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text(active.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text(active.subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp, lineHeight = 14.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StockMetricTile("榜单股票", snapshot.items.size.takeIf { it > 0 }?.let { "${it}只" } ?: "--", StockAqua, Modifier.weight(1f))
                        StockMetricTile("上涨", snapshot.risingCount.toString(), StockRise, Modifier.weight(1f))
                        StockMetricTile("下跌", snapshot.fallingCount.toString(), StockFall, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().height(44.dp).background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(18.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                StockNativeHotType.entries.forEach { hot ->
                    StockNativePill(hot.title, active == hot, Modifier.weight(1f).fillMaxSize(), fontSize = 10) { onSelectType(hot) }
                }
            }
        }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 30.dp, contentPadding = 12.dp) {
                Column {
                    StockSectionTitle("${active.title} TOP ${snapshot.items.size.takeIf { it > 0 } ?: 100}", if (active == StockNativeHotType.Popularity) "当前人气排名与实时行情批量合并展示" else "按较昨日排名变化排序，并显示当前人气名次")
                    StockDivider(Modifier.padding(top = 9.dp))
                    if (snapshot.items.isEmpty()) {
                        StockLoadingOrError(ui.hotLoading, ui.hotError, "热点榜暂无可展示股票")
                    } else {
                        snapshot.items.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().height(60.dp).clickable { onOpenStock(item.code, false) }.padding(horizontal = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.width(38.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(item.rank.toString(), color = if (item.rank <= 3) StockYellow else StockAqua, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, color = Color.White.copy(alpha = 0.93f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                    Text(listOf(item.code, item.industry, item.market).filter { it.isNotBlank() }.joinToString(" · "), color = Color.White.copy(alpha = 0.32f), fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(Modifier.width(68.dp), horizontalAlignment = Alignment.End) {
                                    Text(item.price, color = Color.White.copy(alpha = 0.82f), fontSize = 9.sp)
                                    Text(item.changePercent, color = stockTone(item.changePercent), fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                                Column(Modifier.width(76.dp), horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (active == StockNativeHotType.Popularity) "#${item.currentRank}" else when {
                                            item.rankChange == null -> "--"
                                            item.rankChange > 0 -> "↑ ${item.rankChange}"
                                            item.rankChange < 0 -> "↓ ${-item.rankChange}"
                                            else -> "—"
                                        },
                                        color = when {
                                            active == StockNativeHotType.Popularity -> Color.White.copy(alpha = 0.66f)
                                            (item.rankChange ?: 0) > 0 -> StockRise
                                            (item.rankChange ?: 0) < 0 -> StockFall
                                            else -> Color.White.copy(alpha = 0.58f)
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(if (active == StockNativeHotType.Popularity) item.market else "当前 #${item.currentRank}", color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp)
                                }
                            }
                            StockDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StockNativeSectorScreen(
    ui: StockNativePageUiState,
    code: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenSector: (String) -> Unit,
    onOpenStock: (String, Boolean) -> Unit
) {
    val detail = ui.sectorDetail
    val activeCode = detail?.code ?: code
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { StockNativePageHeader("板块详情 · A股", onBack, onRefresh, ui.sectorLoading) }
        if (detail == null) {
            item { StockNativeGlassPanel(Modifier.fillMaxWidth().height(260.dp)) { StockLoadingOrError(ui.sectorLoading, ui.sectorError, "板块详情暂不可用", Modifier.fillMaxSize()) } }
            return@LazyColumn
        }
        item { SectorHero(detail) }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("minute" to "分时", "daily" to "日K", "weekly" to "周K", "monthly" to "月K").forEach { (tab, label) ->
                            StockNativePill(label, ui.sectorTab == tab, Modifier.weight(1f).fillMaxSize(), fontSize = 9) { onSelectTab(tab) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ui.sectorTab == "minute") "板块分时" else "板块${sectorTabLabel(ui.sectorTab)}", color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        Text(if (ui.sectorTab == "minute") "分时按需刷新" else "历史K线独立缓存", color = StockAqua.copy(alpha = 0.56f), fontSize = 7.sp)
                    }
                    val kline = ui.sectorKlines["$activeCode:${ui.sectorTab}"].orEmpty()
                    StockNativeTrendChart(
                        minutePoints = detail.minutePoints,
                        klinePoints = kline,
                        showKline = ui.sectorTab != "minute",
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (ui.sectorTab == "minute") "09:30" else kline.firstOrNull()?.date.orEmpty(), color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp)
                        Text(if (ui.sectorTab == "minute") "11:30 / 13:00" else "${kline.size}根", color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp)
                        Text(if (ui.sectorTab == "minute") "15:00" else kline.lastOrNull()?.date.orEmpty(), color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp)
                    }
                }
            }
        }
        item { SectorBreadthPanel(detail) }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        StockSectionTitle("成分股", if (ui.sectorConstituentTotal > 0) "已加载 ${ui.sectorConstituents.size}/${ui.sectorConstituentTotal} 只真实成分股" else "按真实涨跌幅排序")
                        Spacer(Modifier.width(8.dp))
                    }
                    if (ui.sectorConstituents.isEmpty()) {
                        StockLoadingOrError(ui.sectorConstituentLoading, ui.sectorError, "成分股暂不可用")
                    } else {
                        ui.sectorConstituents.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().height(52.dp).clickable { onOpenStock(item.code, false) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.rank.toString(), color = StockAqua, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    Text("${item.code} · 额 ${item.amount}", color = Color.White.copy(alpha = 0.31f), fontSize = 7.sp)
                                }
                                Text(item.price, color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                                Text(item.changePercent, color = stockTone(item.changePercent), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                            }
                            StockDivider()
                        }
                    }
                    StockNativePill(
                        text = when {
                            ui.sectorConstituentLoading -> "加载中"
                            ui.sectorHasMore -> "加载更多成分股"
                            else -> "已全部加载"
                        },
                        active = ui.sectorHasMore,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        onClick = { if (ui.sectorHasMore && !ui.sectorConstituentLoading) onLoadMore() }
                    )
                }
            }
        }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StockSectionTitle("其他热门板块", "点击后共用同一板块详情页")
                    if (detail.relatedSectors.isEmpty()) {
                        StockLoadingOrError(false, null, "其他板块暂不可用")
                    } else {
                        detail.relatedSectors.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { sector ->
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .height(68.dp)
                                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                                            .clickable { onOpenSector(sector.code) }
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(sector.name, color = Color.White.copy(alpha = 0.90f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                        Row(Modifier.fillMaxWidth()) {
                                            Text(sector.code, color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp, modifier = Modifier.weight(1f))
                                            Text(sector.changePercent, color = stockTone(sector.changePercent), fontSize = 8.sp, fontWeight = FontWeight.Black)
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
private fun SectorHero(detail: StockNativeSectorDetail) {
    val quote = detail.quote
    StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 30.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("A-SHARE SECTOR", color = StockAqua.copy(alpha = 0.72f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Text(detail.name.ifBlank { quote.name }, color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${detail.code} · ${if (detail.type == "concept") "概念板块" else "行业板块"}", color = Color.White.copy(alpha = 0.38f), fontSize = 8.sp)
                }
                Text(detail.dataSourceLabel, color = StockAqua.copy(alpha = 0.52f), fontSize = 7.sp, textAlign = TextAlign.End, modifier = Modifier.width(120.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(quote.price, color = stockTone(quote.changePercent), fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("${quote.changeAmount}  ${quote.changePercent}", color = stockTone(quote.changePercent), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("实时板块行情", color = Color.White.copy(alpha = 0.31f), fontSize = 7.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    "今开" to quote.open,
                    "最高" to quote.high,
                    "最低" to quote.low,
                    "昨收" to if (quote.previousClose > 0f) String.format(Locale.US, "%.2f", quote.previousClose) else "--",
                    "成交额" to quote.amount,
                    "成交量" to quote.volume
                ).chunked(3).first().forEach { (label, value) -> StockMetricTile(label, value, if (label == "最高") StockRise else if (label == "最低") StockFall else Color.White, Modifier.weight(1f)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    "昨收" to if (quote.previousClose > 0f) String.format(Locale.US, "%.2f", quote.previousClose) else "--",
                    "成交额" to quote.amount,
                    "成交量" to quote.volume
                ).forEach { (label, value) -> StockMetricTile(label, value, Color.White, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SectorBreadthPanel(detail: StockNativeSectorDetail) {
    val breadth = detail.breadth
    StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            StockSectionTitle("板块内部强弱", "上涨、下跌、红盘率、领涨股与资金状态")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StockMetricTile("上涨", stockCount(breadth.upCount), StockRise, Modifier.weight(1f))
                StockMetricTile("下跌", stockCount(breadth.downCount), StockFall, Modifier.weight(1f))
                StockMetricTile("平盘", stockCount(breadth.flatCount), Color.White, Modifier.weight(1f))
                StockMetricTile("红盘率", stockPercent(breadth.redRate), StockAqua, Modifier.weight(1f))
            }
            NativeWideInfoRow("领涨股", breadth.leaderChangePercent, breadth.leaderName.ifBlank { "--" }, stockTone(breadth.leaderChangePercent))
            NativeWideInfoRow("主力净流入", "公开板块资金字段", breadth.mainInflow, stockFlowTone(breadth.mainInflow))
        }
    }
}

@Composable
private fun NativeWideInfoRow(label: String, subtitle: String, value: String, tone: Color) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(15.dp)).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White.copy(alpha = 0.68f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = tone.copy(alpha = 0.72f), fontSize = 7.sp)
        }
        Text(value.ifBlank { "--" }, color = tone, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun StockNativeIndexScreen(
    ui: StockNativePageUiState,
    code: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenIndex: (String) -> Unit
) {
    val detail = ui.indexDetail
    var tab by remember(detail?.code) { mutableStateOf("minute") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { StockNativePageHeader("指数详情 · A股", onBack, onRefresh, ui.indexLoading) }
        if (detail == null) {
            item { StockNativeGlassPanel(Modifier.fillMaxWidth().height(260.dp)) { StockLoadingOrError(ui.indexLoading, ui.indexError, "指数详情暂不可用", Modifier.fillMaxSize()) } }
            return@LazyColumn
        }
        item { IndexHero(detail) }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("minute" to "分时", "daily" to "日K", "five" to "五日").forEach { (key, label) ->
                            StockNativePill(label, tab == key, Modifier.weight(1f).fillMaxSize(), fontSize = 9) { tab = key }
                        }
                    }
                    StockNativeTrendChart(
                        minutePoints = if (tab == "five") detail.fiveDayPoints else detail.minutePoints,
                        klinePoints = detail.kLinePoints,
                        showKline = tab == "daily",
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (tab == "daily") detail.kLinePoints.firstOrNull()?.date.orEmpty() else "09:30", color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp)
                        Text(if (tab == "five") "真实五日分钟数据" else if (tab == "daily") "${detail.kLinePoints.size}根" else "11:30 / 13:00", color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp)
                        Text(if (tab == "daily") detail.kLinePoints.lastOrNull()?.date.orEmpty() else "15:00", color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp)
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
                        StockMetricTile("情绪温度", detail.sentiment.temperature?.let { String.format(Locale.US, "%.0f", it) } ?: "--", StockAqua, Modifier.weight(1f))
                    }
                    NativeWideInfoRow("全市场成交额", "沪深京实时汇总", detail.marketBreadth.marketAmount, Color.White)
                }
            }
        }
        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 28.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StockSectionTitle("其他主要指数", "点击后共用同一指数详情页")
                    detail.relatedIndices.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { item ->
                                Column(
                                    Modifier.weight(1f).height(66.dp).background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp)).clickable { onOpenIndex(item.code) }.padding(10.dp),
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

@Composable
private fun IndexHero(detail: StockNativeIndexDetail) {
    val quote = detail.quote
    StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 30.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("A-SHARE INDEX", color = StockAqua.copy(alpha = 0.72f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Text(detail.name.ifBlank { quote.name }, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text(detail.code, color = Color.White.copy(alpha = 0.38f), fontSize = 8.sp)
                }
                Text(detail.dataSourceLabel, color = StockAqua.copy(alpha = 0.52f), fontSize = 7.sp, modifier = Modifier.width(120.dp), textAlign = TextAlign.End, maxLines = 2)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(quote.price, color = stockTone(quote.changePercent), fontSize = 38.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(10.dp))
                Text("${quote.changeAmount}  ${quote.changePercent}", color = stockTone(quote.changePercent), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("今开" to quote.open, "最高" to quote.high, "最低" to quote.low).forEach { (label, value) -> StockMetricTile(label, value, if (label == "最高") StockRise else if (label == "最低") StockFall else Color.White, Modifier.weight(1f)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StockMetricTile("昨收", if (quote.previousClose > 0f) String.format(Locale.US, "%.2f", quote.previousClose) else "--", Color.White, Modifier.weight(1f))
                StockMetricTile("成交额", quote.amount, Color.White, Modifier.weight(1f))
                StockMetricTile("成交量", quote.volume, Color.White, Modifier.weight(1f))
            }
        }
    }
}

private fun sectorTabLabel(tab: String): String = when (tab) {
    "weekly" -> "周K"
    "monthly" -> "月K"
    else -> "日K"
}
