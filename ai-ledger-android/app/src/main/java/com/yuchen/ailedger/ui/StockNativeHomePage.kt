package com.yuchen.ailedger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.StockMarketUiState
import com.yuchen.ailedger.StockNativePageUiState
import com.yuchen.ailedger.data.StockWatchlistItem
import com.yuchen.ailedger.model.StockIndexSnapshot
import com.yuchen.ailedger.model.StockMarketBoard
import com.yuchen.ailedger.model.StockNativeHotType
import com.yuchen.ailedger.model.StockNativeRankingType
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockSectorSnapshot
import java.util.Locale
import kotlin.math.roundToInt

private val NativeHomeActions = listOf("自选", "热榜", "板块", "资金", "异动", "热点", "研报", "预警")

@Composable
internal fun StockNativeHomeScreen(
    marketUi: StockMarketUiState,
    nativeUi: StockNativePageUiState,
    watchlist: List<StockWatchlistItem>,
    watchlistStatus: String,
    watchlistMessage: String,
    watchlistBusy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshWatchlist: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenStock: (String, Boolean) -> Unit,
    onRemoveWatch: (String) -> Unit,
    onSelectAction: (String) -> Unit,
    onOpenRanking: (StockNativeRankingType) -> Unit,
    onOpenHot: (StockNativeHotType) -> Unit,
    onLoadConcept: (Boolean) -> Unit,
    onOpenSector: (String) -> Unit,
    onOpenIndex: (String) -> Unit,
    onOpenAssistant: () -> Unit
) {
    var sectorType by remember { mutableStateOf("industry") }
    val selectedAction = marketUi.selectedHomeAction

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StockNativePageHeader(
                    label = "市场总览 · A股",
                    onBack = onBack,
                    onRefresh = {
                        onRefresh()
                        onRefreshWatchlist()
                    },
                    loading = marketUi.marketLoading || watchlistBusy
                )
                Text(
                    "A-SHARE MARKET",
                    color = StockAqua.copy(alpha = 0.76f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "市场概览",
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    when {
                        marketUi.marketLoading -> "正在同步指数、宽度、榜单与板块"
                        !marketUi.requestMessage.isNullOrBlank() -> marketUi.requestMessage
                        else -> "真实市场数据 · 20 秒刷新"
                    },
                    color = if (marketUi.requestMessage.isNullOrBlank()) {
                        Color.White.copy(alpha = 0.54f)
                    } else {
                        StockYellow
                    },
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item {
            StockNativeGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = 30.dp,
                contentPadding = 14.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NativeStockSearchBar(marketUi.query, marketUi.loading, onQueryChange, onSearch)
                    StockDivider()
                    StockSectionTitle("主要指数", "点击进入指数详情 · 横向滑动查看更多")
                    NativeIndexRow(marketUi, onOpenIndex)
                    StockDivider()
                    StockSectionTitle("市场宽度", "涨跌结构、赚钱效应与市场热度")
                    NativeBreadthSection(marketUi)
                }
            }
        }

        item {
            StockNativeGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = 30.dp,
                contentPadding = 14.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    StockSectionTitle("市场数据", "实时榜单、板块、资金与个股热点")
                    NativeHomeActionGrid(selectedAction, onSelectAction)
                    StockDivider()
                    when (selectedAction) {
                        "自选" -> NativeWatchlistContent(
                            watchlist = watchlist,
                            status = watchlistStatus,
                            message = watchlistMessage,
                            busy = watchlistBusy,
                            onRefresh = onRefreshWatchlist,
                            onOpenStock = onOpenStock,
                            onRemoveWatch = onRemoveWatch
                        )
                        "热榜" -> NativeRankingOverview(
                            types = listOf(
                                StockNativeRankingType.Gainers,
                                StockNativeRankingType.Losers,
                                StockNativeRankingType.Amount,
                                StockNativeRankingType.Turnover,
                                StockNativeRankingType.VolumeRatio,
                                StockNativeRankingType.Speed
                            ),
                            boards = marketUi.marketHome.boards,
                            title = "实时行情榜单",
                            subtitle = "每个榜单都有独立详情入口，首页展示第一名预览",
                            onOpenRanking = onOpenRanking,
                            onOpenStock = { onOpenStock(it, false) }
                        )
                        "资金" -> NativeRankingOverview(
                            types = listOf(
                                StockNativeRankingType.MainInflow,
                                StockNativeRankingType.MainOutflow
                            ),
                            boards = marketUi.marketHome.boards,
                            title = "主力资金榜单",
                            subtitle = "净流入与净流出分别进入完整榜单",
                            onOpenRanking = onOpenRanking,
                            onOpenStock = { onOpenStock(it, false) }
                        )
                        "板块" -> NativeSectorOverview(
                            sectorType = sectorType,
                            industry = marketUi.marketHome.sectors,
                            concept = nativeUi.conceptSectors,
                            conceptLoading = nativeUi.conceptLoading,
                            conceptError = nativeUi.conceptError,
                            onSelectType = { type ->
                                sectorType = type
                                if (type == "concept") onLoadConcept(false)
                            },
                            onOpenSector = onOpenSector
                        )
                        "热点" -> NativeHotPreview(nativeUi, onOpenHot, onOpenStock)
                        "异动" -> StockLoadingOrError(false, null, "没有稳定真实数据源时不会生成模板数据")
                        "研报" -> StockLoadingOrError(false, null, "机构研报暂未接入稳定真实数据源")
                        "预警" -> StockLoadingOrError(false, null, "价格预警属于本地功能，当前尚未配置预警条件")
                    }
                }
            }
        }

        item {
            StockNativeGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = 30.dp,
                contentPadding = 14.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    StockSectionTitle("数据覆盖状态", "真实可用与暂不可用模块分开显示")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NativeStatusMetric("指数", marketUi.marketHome.indices.isNotEmpty(), Modifier.weight(1f))
                        NativeStatusMetric("宽度", marketUi.marketHome.marketBreadth.meta.hasRealData, Modifier.weight(1f))
                        NativeStatusMetric("情绪", marketUi.marketHome.sentiment.meta.hasRealData, Modifier.weight(1f))
                        NativeStatusMetric("热点", nativeUi.hotSnapshot.items.isNotEmpty(), Modifier.weight(1f))
                    }
                    StockDivider()
                    Text(
                        "普通新闻、研报、公告等没有稳定真实源时继续显示不可用；股票软件内部的人气榜与飙升榜独立展示。",
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 10.sp,
                        lineHeight = 16.sp
                    )
                    StockDivider()
                    NativeInteractiveGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        radius = 19.dp,
                        intensity = 0.84f,
                        role = GlassRole.Card,
                        contentPadding = 13.dp,
                        onClick = onOpenAssistant
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                "AI 看盘",
                                color = Color.White.copy(alpha = 0.94f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                marketUi.stock.aiSummary,
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeStockSearchBar(
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val searchFocusRequester = remember { FocusRequester() }
    Row(
        Modifier.fillMaxWidth().height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NativeInteractiveGlassCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            radius = 20.dp,
            intensity = 0.88f,
            role = GlassRole.Flex,
            contentPadding = 13.dp,
            onClick = { searchFocusRequester.requestFocus() }
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("⌕", color = StockAqua, fontSize = 20.sp, fontWeight = FontWeight.Black)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    ),
                    cursorBrush = SolidColor(StockAqua),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isBlank()) {
                                Text(
                                    "股票代码或名称",
                                    color = Color.White.copy(alpha = 0.36f),
                                    fontSize = 12.sp
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }
        StockNativePill(
            text = if (loading) "连接中" else "搜索",
            active = true,
            modifier = Modifier.width(82.dp).fillMaxHeight(),
            fontSize = 13,
            onClick = onSearch
        )
    }
}

@Composable
private fun NativeInteractiveGlassCard(
    modifier: Modifier = Modifier,
    radius: Dp,
    intensity: Float = 0.82f,
    role: GlassRole = GlassRole.Card,
    contentPadding: Dp = 0.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = LocalStockNativeGlassState.current
    val radiusValue = radius.value.roundToInt().coerceAtLeast(1)
    if (state != null) {
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity * intensity,
            motionIntensity = state.motionIntensity,
            radius = radiusValue,
            modifier = modifier,
            role = role,
            onClick = onClick
        ) {
            Box(Modifier.fillMaxSize().padding(contentPadding)) {
                content()
            }
        }
    } else {
        StockNativeFrostCard(
            modifier = modifier.clickable(onClick = onClick),
            radius = radius,
            frostAlpha = 0.074f,
            contentPadding = contentPadding,
            content = content
        )
    }
}

@Composable
private fun NativeIndexRow(ui: StockMarketUiState, onOpenIndex: (String) -> Unit) {
    if (ui.marketHome.indices.isEmpty()) {
        StockLoadingOrError(
            ui.marketLoading,
            ui.requestMessage,
            "指数数据暂不可用",
            Modifier.height(88.dp)
        )
        return
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(88.dp),
        contentPadding = PaddingValues(horizontal = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ui.marketHome.indices, key = { it.name }) { item ->
            val tone = if (item.isRising) StockRise else StockFall
            val code = indexCode(item)
            val cardContent: @Composable () -> Unit = {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.name,
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (item.isRising) "↑" else "↓",
                            color = tone,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        item.value,
                        color = Color.White,
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.changePercent,
                        color = tone,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (code != null) {
                NativeInteractiveGlassCard(
                    modifier = Modifier.width(118.dp).height(84.dp),
                    radius = 19.dp,
                    intensity = 0.82f,
                    role = GlassRole.Card,
                    contentPadding = 11.dp,
                    onClick = { onOpenIndex(code) },
                    content = cardContent
                )
            } else {
                StockNativeFrostCard(
                    modifier = Modifier.width(118.dp).height(84.dp),
                    radius = 19.dp,
                    frostAlpha = 0.084f,
                    contentPadding = 11.dp,
                    content = cardContent
                )
            }
        }
    }
}

@Composable
private fun NativeBreadthSection(ui: StockMarketUiState) {
    val breadth = ui.marketHome.marketBreadth
    val sentiment = ui.marketHome.sentiment
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NativeCompactMetricTile("上涨", stockCount(breadth.upCount), StockRise, Modifier.weight(1f), true)
            NativeCompactMetricTile("下跌", stockCount(breadth.downCount), StockFall, Modifier.weight(1f), true)
            NativeCompactMetricTile("涨停", stockCount(breadth.limitUpCount), StockRise, Modifier.weight(1f), true)
            NativeCompactMetricTile("跌停", stockCount(breadth.limitDownCount), StockFall, Modifier.weight(1f), true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NativeCompactMetricTile("红盘率", stockPercent(breadth.redRate), Color.White, Modifier.weight(1f))
            NativeCompactMetricTile("赚钱效应", stockPercent(breadth.moneyMakingEffect), StockAqua, Modifier.weight(1f))
            NativeCompactMetricTile(
                "情绪温度",
                sentiment.temperature?.let { String.format(Locale.US, "%.0f", it) } ?: "--",
                StockAqua,
                Modifier.weight(1f)
            )
            NativeCompactMetricTile("成交额", breadth.marketAmount, Color.White, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NativeCompactMetricTile(
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
    prominent: Boolean = false
) {
    StockNativeFrostCard(
        modifier = modifier.height(if (prominent) 52.dp else 50.dp),
        radius = 16.dp,
        frostAlpha = if (prominent) 0.082f else 0.070f,
        contentPadding = 8.dp
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(
                label,
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value.ifBlank { "--" },
                color = tone,
                fontSize = if (prominent) 15.sp else 13.sp,
                lineHeight = if (prominent) 18.sp else 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NativeHomeActionGrid(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        NativeHomeActions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { action ->
                    StockNativePill(
                        action,
                        action == selected,
                        Modifier.weight(1f).height(40.dp),
                        fontSize = 12
                    ) { onSelect(action) }
                }
            }
        }
    }
}

@Composable
private fun NativeRankingOverview(
    types: List<StockNativeRankingType>,
    boards: List<StockMarketBoard>,
    title: String,
    subtitle: String,
    onOpenRanking: (StockNativeRankingType) -> Unit,
    onOpenStock: (String) -> Unit
) {
    StockSectionTitle(title, subtitle, "${types.size} 个入口")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type ->
            val board = boards.firstOrNull { boardMatches(type, it.title) }
            NativeRankingEntry(type, board?.items?.firstOrNull(), onOpenRanking, onOpenStock)
        }
    }
}

@Composable
private fun NativeRankingEntry(
    type: StockNativeRankingType,
    top: StockRankItem?,
    onOpenRanking: (StockNativeRankingType) -> Unit,
    onOpenStock: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        NativeInteractiveGlassCard(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            radius = 18.dp,
            intensity = 0.80f,
            role = GlassRole.Flex,
            onClick = { onOpenRanking(type) }
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        type.title,
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        type.subtitle,
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "进入榜单 ›",
                    color = StockAqua.copy(alpha = 0.90f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        if (top != null) {
            NativeInteractiveGlassCard(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                radius = 17.dp,
                intensity = 0.74f,
                role = GlassRole.Card,
                onClick = { onOpenStock(top.code) }
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "1",
                        color = StockAqua,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.width(26.dp),
                        textAlign = TextAlign.Center
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            top.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${top.code} · ${top.value}",
                            color = Color.White.copy(alpha = 0.40f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        top.changePercent,
                        color = if (top.isRising) StockRise else StockFall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeSectorOverview(
    sectorType: String,
    industry: List<StockSectorSnapshot>,
    concept: List<StockSectorSnapshot>,
    conceptLoading: Boolean,
    conceptError: String?,
    onSelectType: (String) -> Unit,
    onOpenSector: (String) -> Unit
) {
    val sectors = if (sectorType == "concept") concept else industry
    StockSectionTitle(
        "板块排行",
        "行业与概念分开呈现，点击任意板块进入详情",
        "${sectors.size.takeIf { it > 0 } ?: "--"} 个板块"
    )
    Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StockNativePill(
            "行业板块",
            sectorType == "industry",
            Modifier.weight(1f).fillMaxHeight()
        ) { onSelectType("industry") }
        StockNativePill(
            "概念板块",
            sectorType == "concept",
            Modifier.weight(1f).fillMaxHeight()
        ) { onSelectType("concept") }
    }
    if (sectors.isEmpty()) {
        StockLoadingOrError(
            conceptLoading && sectorType == "concept",
            if (sectorType == "concept") conceptError else null,
            "板块数据暂不可用"
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sectors.take(10).forEach { sector ->
                NativeInteractiveGlassCard(
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    radius = 18.dp,
                    intensity = 0.76f,
                    role = GlassRole.Card,
                    contentPadding = 11.dp,
                    onClick = { onOpenSector(sector.sectorCode) }
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                sector.sectorName,
                                color = Color.White.copy(alpha = 0.94f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${if (sectorType == "concept") "概念" else "行业"} · 涨 ${sector.upCount ?: "--"} · 跌 ${sector.downCount ?: "--"}${sector.leaderName.takeIf { it.isNotBlank() }?.let { " · 领涨 $it" } ?: ""}",
                                color = Color.White.copy(alpha = 0.40f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            sector.mainInflow.ifBlank { sector.amount.ifBlank { "--" } },
                            color = stockFlowTone(sector.mainInflow),
                            fontSize = 10.sp,
                            modifier = Modifier.width(76.dp),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            sector.changePercent,
                            color = stockTone(sector.changePercent),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.width(66.dp),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeHotPreview(
    ui: StockNativePageUiState,
    onOpenHot: (StockNativeHotType) -> Unit,
    onOpenStock: (String, Boolean) -> Unit
) {
    StockSectionTitle(
        "实时热点",
        "东方财富个股人气榜 · 约10分钟更新",
        "${ui.hotSnapshot.items.size.takeIf { it > 0 } ?: "--"} 只"
    )
    NativeInteractiveGlassCard(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        radius = 18.dp,
        intensity = 0.84f,
        role = GlassRole.Flex,
        contentPadding = 12.dp,
        onClick = { onOpenHot(StockNativeHotType.Popularity) }
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "个股人气榜与飙升榜",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "股票软件内部热度，不是普通新闻热搜",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("进入热点榜 ›", color = StockAqua, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
    if (ui.hotSnapshot.items.isEmpty()) {
        StockLoadingOrError(ui.hotLoading, ui.hotError, "热点榜按需加载")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.hotSnapshot.items.take(6).forEach { item ->
                NativeInteractiveGlassCard(
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    radius = 17.dp,
                    intensity = 0.72f,
                    role = GlassRole.Card,
                    contentPadding = 10.dp,
                    onClick = { onOpenStock(item.code, false) }
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.rank.toString(),
                            color = StockAqua,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.Center
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name,
                                color = Color.White.copy(alpha = 0.94f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOf(item.code, item.industry).filter { it.isNotBlank() }.joinToString(" · "),
                                color = Color.White.copy(alpha = 0.40f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            item.price,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 10.sp,
                            modifier = Modifier.width(62.dp),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Column(Modifier.width(68.dp), horizontalAlignment = Alignment.End) {
                            Text(
                                "#${item.currentRank}",
                                color = StockAqua.copy(alpha = 0.84f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                item.changePercent,
                                color = stockTone(item.changePercent),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeWatchlistContent(
    watchlist: List<StockWatchlistItem>,
    status: String,
    message: String,
    busy: Boolean,
    onRefresh: () -> Unit,
    onOpenStock: (String, Boolean) -> Unit,
    onRemoveWatch: (String) -> Unit
) {
    StockSectionTitle("我的自选", status, "${watchlist.size} 只")
    StockNativeFrostCard(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        radius = 17.dp,
        frostAlpha = 0.064f,
        contentPadding = 10.dp
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                message,
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StockNativePill(
                text = if (busy) "同步中" else "刷新",
                active = false,
                modifier = Modifier.width(66.dp).height(32.dp),
                fontSize = 9,
                onClick = onRefresh
            )
        }
    }
    if (watchlist.isEmpty()) {
        StockLoadingOrError(busy, null, "还没有自选股\n进入任意个股详情，点击“加自选”即可加入")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        watchlist.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                NativeInteractiveGlassCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    radius = 18.dp,
                    intensity = 0.76f,
                    role = GlassRole.Card,
                    onClick = { onOpenStock(item.code, false) }
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("★", color = StockYellow, fontSize = 15.sp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name,
                                color = Color.White.copy(alpha = 0.94f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${item.code} · ${item.market.ifBlank { "A股" }}",
                                color = Color.White.copy(alpha = 0.40f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text("›", color = StockAqua.copy(alpha = 0.62f), fontSize = 18.sp)
                    }
                }
                StockNativePill(
                    text = "×",
                    active = false,
                    modifier = Modifier.width(42.dp).fillMaxHeight(),
                    fontSize = 14,
                    onClick = { onRemoveWatch(item.code) }
                )
            }
        }
    }
}

@Composable
private fun NativeStatusMetric(label: String, available: Boolean, modifier: Modifier) {
    StockNativeFrostCard(
        modifier = modifier.height(56.dp),
        radius = 16.dp,
        frostAlpha = 0.064f,
        contentPadding = 9.dp
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(
                label,
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (available) "实时" else "不可用",
                color = if (available) StockAqua else Color.White.copy(alpha = 0.46f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun boardMatches(type: StockNativeRankingType, title: String): Boolean = when (type) {
    StockNativeRankingType.Gainers -> title.contains("涨幅")
    StockNativeRankingType.Losers -> title.contains("跌幅")
    StockNativeRankingType.Amount -> title.contains("成交额")
    StockNativeRankingType.Turnover -> title.contains("换手")
    StockNativeRankingType.VolumeRatio -> title.contains("量比")
    StockNativeRankingType.Speed -> title.contains("涨速")
    StockNativeRankingType.MainInflow -> title.contains("净流入")
    StockNativeRankingType.MainOutflow -> title.contains("净流出")
}

private fun indexCode(item: StockIndexSnapshot): String? = when {
    item.name.contains("沪深300") -> "000300"
    item.name.contains("科创50") -> "000688"
    item.name.contains("A500") -> "000510"
    item.name.contains("上证50") -> "000016"
    item.name.contains("中证500") -> "000905"
    item.name.contains("中证1000") -> "000852"
    item.name.contains("北证50") -> "899050"
    item.name.contains("深证") || item.name.contains("深成") -> "399001"
    item.name.contains("创业") -> "399006"
    item.name.contains("上证") -> "000001"
    else -> null
}
