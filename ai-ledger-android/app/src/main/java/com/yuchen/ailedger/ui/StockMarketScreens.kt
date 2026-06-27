package com.yuchen.ailedger.ui

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.StockMarketUiState
import com.yuchen.ailedger.StockMarketViewModel
import com.yuchen.ailedger.StockNativePageUiState
import com.yuchen.ailedger.StockNativePageViewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockIndexSnapshot
import com.yuchen.ailedger.model.StockMarketBoard
import com.yuchen.ailedger.model.StockNativeHotType
import com.yuchen.ailedger.model.StockNativeRankingType
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockSectorSnapshot
import org.json.JSONArray
import org.json.JSONObject

private val NativeHomeActions = listOf("自选", "热榜", "板块", "资金", "异动", "热点", "研报", "预警")
private const val NativeWatchPreferences = "stock_native_watchlist"
private const val NativeWatchKey = "items"

private sealed interface StockNativeRoute {
    data object Home : StockNativeRoute
    data class Ranking(val type: StockNativeRankingType) : StockNativeRoute
    data class Hot(val type: StockNativeHotType) : StockNativeRoute
    data class Sector(val code: String) : StockNativeRoute
    data class Index(val code: String) : StockNativeRoute
    data class Detail(val code: String, val startInCommunity: Boolean = false) : StockNativeRoute
    data class Post(val code: String, val postId: String) : StockNativeRoute
}

private data class NativeWatchEntry(
    val code: String,
    val name: String,
    val market: String
)

@Composable
fun AStockMarketScreenV2(
    state: AssistantUiState,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    val marketViewModel: StockMarketViewModel = viewModel()
    val nativeViewModel: StockNativePageViewModel = viewModel()
    val marketUi by marketViewModel.uiState.collectAsState()
    val nativeUi by nativeViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var route by remember { mutableStateOf<StockNativeRoute>(StockNativeRoute.Home) }
    val routeStack = remember { mutableStateListOf<StockNativeRoute>() }
    var watchlist by remember { mutableStateOf(loadNativeWatchlist(context)) }

    fun navigate(next: StockNativeRoute) {
        if (next == route) return
        routeStack.add(route)
        route = next
    }

    fun openStock(code: String, startInCommunity: Boolean = false) {
        marketViewModel.openCode(code)
        navigate(StockNativeRoute.Detail(code, startInCommunity))
    }

    fun navigateBack() {
        when (route) {
            is StockNativeRoute.Detail -> marketViewModel.backToHome()
            else -> Unit
        }
        route = if (routeStack.isNotEmpty()) routeStack.removeAt(routeStack.lastIndex) else StockNativeRoute.Home
    }

    fun toggleWatch() {
        val quote = marketUi.stock.quote
        if (quote.code.length != 6) return
        watchlist = if (watchlist.any { it.code == quote.code }) {
            watchlist.filterNot { it.code == quote.code }
        } else {
            listOf(NativeWatchEntry(quote.code, quote.name.ifBlank { quote.code }, quote.market)) + watchlist
        }
        saveNativeWatchlist(context, watchlist)
    }

    BackHandler {
        if (route == StockNativeRoute.Home) onBack() else navigateBack()
    }

    when (val current = route) {
        StockNativeRoute.Home -> StockNativeHomeScreen(
            appState = state,
            marketUi = marketUi,
            nativeUi = nativeUi,
            watchlist = watchlist,
            onBack = onBack,
            onRefresh = marketViewModel::refreshHome,
            onQueryChange = marketViewModel::updateQuery,
            onSearch = {
                val query = marketUi.query.trim()
                if (query.isNotBlank()) openStock(query)
            },
            onOpenStock = ::openStock,
            onRemoveWatch = { code ->
                watchlist = watchlist.filterNot { it.code == code }
                saveNativeWatchlist(context, watchlist)
            },
            onSelectAction = { action ->
                marketViewModel.selectHomeAction(action)
                if (action == "热点") nativeViewModel.loadHot(StockNativeHotType.Popularity)
            },
            onOpenRanking = { type ->
                nativeViewModel.loadRanking(type)
                navigate(StockNativeRoute.Ranking(type))
            },
            onOpenHot = { type ->
                nativeViewModel.loadHot(type)
                navigate(StockNativeRoute.Hot(type))
            },
            onLoadConcept = nativeViewModel::loadConceptSectors,
            onOpenSector = { code ->
                nativeViewModel.loadSector(code)
                navigate(StockNativeRoute.Sector(code))
            },
            onOpenIndex = { code ->
                nativeViewModel.loadIndex(code)
                navigate(StockNativeRoute.Index(code))
            },
            onOpenAssistant = onOpenAssistant
        )

        is StockNativeRoute.Ranking -> StockNativeRankingScreen(
            ui = nativeUi,
            type = current.type,
            onBack = ::navigateBack,
            onRefresh = { nativeViewModel.loadRanking(current.type, true) },
            onSelectType = { type -> nativeViewModel.loadRanking(type) },
            onOpenStock = ::openStock
        )

        is StockNativeRoute.Hot -> StockNativeHotScreen(
            ui = nativeUi,
            type = current.type,
            onBack = ::navigateBack,
            onRefresh = { nativeViewModel.loadHot(current.type, true) },
            onSelectType = { type -> nativeViewModel.loadHot(type) },
            onOpenStock = ::openStock
        )

        is StockNativeRoute.Sector -> StockNativeSectorScreen(
            ui = nativeUi,
            code = current.code,
            onBack = ::navigateBack,
            onRefresh = { nativeViewModel.loadSector(current.code, true) },
            onSelectTab = nativeViewModel::selectSectorTab,
            onLoadMore = nativeViewModel::loadMoreSectorConstituents,
            onOpenSector = { code ->
                nativeViewModel.loadSector(code, true)
                route = StockNativeRoute.Sector(code)
            },
            onOpenStock = ::openStock
        )

        is StockNativeRoute.Index -> StockNativeIndexScreen(
            ui = nativeUi,
            code = current.code,
            onBack = ::navigateBack,
            onRefresh = { nativeViewModel.loadIndex(current.code, true) },
            onOpenIndex = { code ->
                nativeViewModel.loadIndex(code, true)
                route = StockNativeRoute.Index(code)
            }
        )

        is StockNativeRoute.Detail -> StockNativeDetailScreen(
            appState = state,
            marketUi = marketUi,
            nativeUi = nativeUi,
            startInCommunity = current.startInCommunity,
            isWatched = watchlist.any { it.code == marketUi.stock.quote.code },
            onBack = ::navigateBack,
            onRefresh = marketViewModel::refreshCurrent,
            onToggleWatch = ::toggleWatch,
            onSelectTab = marketViewModel::selectTab,
            onLoadCommunity = { reset -> nativeViewModel.loadDiscussions(marketUi.stock.quote.code, reset) },
            onLoadMoreCommunity = { nativeViewModel.loadDiscussions(marketUi.stock.quote.code, false) },
            onOpenPost = { postId ->
                val code = marketUi.stock.quote.code
                nativeViewModel.loadPost(code, postId)
                navigate(StockNativeRoute.Post(code, postId))
            }
        )

        is StockNativeRoute.Post -> StockNativePostScreen(
            ui = nativeUi,
            code = current.code,
            postId = current.postId,
            onBack = ::navigateBack,
            onRefresh = { nativeViewModel.loadPost(current.code, current.postId, true) },
            onLoadComments = nativeViewModel::loadComments
        )
    }
}

@Composable
private fun StockNativeHomeScreen(
    appState: AssistantUiState,
    marketUi: StockMarketUiState,
    nativeUi: StockNativePageUiState,
    watchlist: List<NativeWatchEntry>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
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
    LaunchedEffect(selectedAction) {
        if (selectedAction == "热点") onOpenHotPreview(nativeUi, onOpenHot = {}, load = { onLoadConcept(false) })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StockNativePageHeader(
                    label = "市场总览 · A股",
                    onBack = onBack,
                    onRefresh = onRefresh,
                    loading = marketUi.marketLoading
                )
                Text("A-SHARE MARKET", color = StockAqua.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text("市场概览", color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
                Text(
                    when {
                        marketUi.marketLoading -> "正在同步指数、宽度、榜单与板块"
                        !marketUi.requestMessage.isNullOrBlank() -> marketUi.requestMessage
                        else -> "真实市场数据 · 20 秒刷新"
                    },
                    color = if (marketUi.requestMessage.isNullOrBlank()) Color.White.copy(alpha = 0.48f) else StockYellow,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 30.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    NativeStockSearchBar(marketUi.query, marketUi.loading, onQueryChange, onSearch)
                    StockDivider()
                    StockSectionTitle("主要指数", "点击进入共用指数详情页 · 左右滑动查看更多")
                    NativeIndexRow(marketUi, onOpenIndex)
                    StockDivider()
                    StockSectionTitle("市场宽度", "涨跌结构、赚钱效应与市场热度")
                    NativeBreadthSection(marketUi)
                }
            }
        }

        item {
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 30.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StockSectionTitle("市场数据", "实时榜单、行业板块、概念板块、资金与个股热点")
                    NativeHomeActionGrid(selectedAction, onSelectAction)
                    StockDivider()
                    when (selectedAction) {
                        "自选" -> NativeWatchlistContent(watchlist, onOpenStock, onRemoveWatch)
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
                            subtitle = "每个榜单都有独立详情入口，首页仅展示第一名预览",
                            onOpenRanking = onOpenRanking,
                            onOpenStock = { onOpenStock(it, false) }
                        )
                        "资金" -> NativeRankingOverview(
                            types = listOf(StockNativeRankingType.MainInflow, StockNativeRankingType.MainOutflow),
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
            StockNativeGlassPanel(Modifier.fillMaxWidth(), radius = 30.dp) {
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
                        color = Color.White.copy(alpha = 0.52f),
                        fontSize = 9.sp,
                        lineHeight = 14.sp
                    )
                    StockDivider()
                    Column(
                        Modifier.fillMaxWidth().clickable(onClick = onOpenAssistant),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("AI 看盘", color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Text(
                            marketUi.stock.aiSummary,
                            color = Color.White.copy(alpha = 0.58f),
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
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
    Row(Modifier.fillMaxWidth().height(45.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(17.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(17.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("⌕", color = StockAqua, fontSize = 17.sp, fontWeight = FontWeight.Black)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black),
                cursorBrush = SolidColor(StockAqua),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) Text("股票代码或名称", color = Color.White.copy(alpha = 0.32f), fontSize = 11.sp)
                        inner()
                    }
                }
            )
        }
        StockNativePill(
            text = if (loading) "连接中" else "搜索",
            active = true,
            modifier = Modifier.width(76.dp).fillMaxSize(),
            onClick = onSearch
        )
    }
}

@Composable
private fun NativeIndexRow(ui: StockMarketUiState, onOpenIndex: (String) -> Unit) {
    if (ui.marketHome.indices.isEmpty()) {
        StockLoadingOrError(ui.marketLoading, ui.requestMessage, "指数数据暂不可用", Modifier.height(86.dp))
        return
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(88.dp),
        contentPadding = PaddingValues(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ui.marketHome.indices, key = { it.name }) { item ->
            val tone = if (item.isRising) StockRise else StockFall
            Column(
                Modifier
                    .width(124.dp)
                    .height(86.dp)
                    .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(18.dp))
                    .border(1.dp, tone.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                    .clickable { indexCode(item)?.let(onOpenIndex) }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(item.name, color = Color.White.copy(alpha = 0.58f), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text(if (item.isRising) "↑" else "↓", color = tone, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
                Text(item.value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.changePercent, color = tone, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun NativeBreadthSection(ui: StockMarketUiState) {
    val breadth = ui.marketHome.marketBreadth
    val sentiment = ui.marketHome.sentiment
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        StockMetricTile("上涨", stockCount(breadth.upCount), StockRise, Modifier.weight(1f), true)
        StockMetricTile("下跌", stockCount(breadth.downCount), StockFall, Modifier.weight(1f), true)
        StockMetricTile("涨停", stockCount(breadth.limitUpCount), StockRise, Modifier.weight(1f), true)
        StockMetricTile("跌停", stockCount(breadth.limitDownCount), StockFall, Modifier.weight(1f), true)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        StockMetricTile("红盘率", stockPercent(breadth.redRate), Color.White, Modifier.weight(1f))
        StockMetricTile("赚钱效应", stockPercent(breadth.moneyMakingEffect), StockAqua, Modifier.weight(1f))
        StockMetricTile("情绪温度", sentiment.temperature?.let { String.format("%.0f", it) } ?: "--", StockAqua, Modifier.weight(1f))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("全市场成交额", color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("沪深京实时汇总", color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(breadth.marketAmount, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun NativeHomeActionGrid(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NativeHomeActions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { action ->
                    StockNativePill(
                        text = action,
                        active = action == selected,
                        modifier = Modifier.weight(1f).height(42.dp),
                        onClick = { onSelect(action) }
                    )
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(17.dp))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(17.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clickable { onOpenRanking(type) }.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(type.title, color = Color.White.copy(alpha = 0.93f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(type.subtitle, color = Color.White.copy(alpha = 0.34f), fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("进入榜单 ›", color = StockAqua.copy(alpha = 0.82f), fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        if (top != null) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).clickable { onOpenStock(top.code) }.padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("1", color = StockAqua, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(25.dp), textAlign = TextAlign.Center)
                Column(Modifier.weight(1f)) {
                    Text(top.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("${top.code} · ${top.value}", color = Color.White.copy(alpha = 0.32f), fontSize = 7.sp)
                }
                Text(top.changePercent, color = if (top.isRising) StockRise else StockFall, fontSize = 10.sp, fontWeight = FontWeight.Black)
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
    StockSectionTitle("板块排行", "行业与概念分开呈现，点击任意板块进入详情", "${sectors.size.takeIf { it > 0 } ?: "--"} 个板块")
    Row(
        Modifier.fillMaxWidth().height(42.dp).background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(17.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StockNativePill("行业板块", sectorType == "industry", Modifier.weight(1f).fillMaxSize()) { onSelectType("industry") }
        StockNativePill("概念板块", sectorType == "concept", Modifier.weight(1f).fillMaxSize()) { onSelectType("concept") }
    }
    if (sectors.isEmpty()) {
        StockLoadingOrError(conceptLoading && sectorType == "concept", if (sectorType == "concept") conceptError else null, "板块数据暂不可用")
    } else {
        sectors.take(10).forEach { sector ->
            Row(
                Modifier.fillMaxWidth().height(51.dp).clickable { onOpenSector(sector.sectorCode) }.padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(sector.sectorName, color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(
                        "${if (sectorType == "concept") "概念" else "行业"} · 涨 ${sector.upCount ?: "--"} · 跌 ${sector.downCount ?: "--"}${sector.leaderName.takeIf { it.isNotBlank() }?.let { " · 领涨 $it" } ?: ""}",
                        color = Color.White.copy(alpha = 0.32f),
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(sector.mainInflow.ifBlank { sector.amount.ifBlank { "--" } }, color = stockFlowTone(sector.mainInflow), fontSize = 9.sp, modifier = Modifier.width(72.dp), textAlign = TextAlign.End, maxLines = 1)
                Text(sector.changePercent, color = stockTone(sector.changePercent), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
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
    StockSectionTitle("实时热点", "东方财富个股人气榜 · 约10分钟更新", "${ui.hotSnapshot.items.size.takeIf { it > 0 } ?: "--"} 只")
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Brush.horizontalGradient(listOf(StockAqua.copy(alpha = 0.10f), Color(0xFF7C8FFF).copy(alpha = 0.10f))), RoundedCornerShape(17.dp))
            .border(1.dp, StockAqua.copy(alpha = 0.13f), RoundedCornerShape(17.dp))
            .clickable { onOpenHot(StockNativeHotType.Popularity) }
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("个股人气榜与飙升榜", color = Color.White.copy(alpha = 0.94f), fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("股票软件内部热度，不是普通新闻热搜", color = Color.White.copy(alpha = 0.34f), fontSize = 7.sp)
        }
        Text("进入热点榜 ›", color = StockAqua, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
    if (ui.hotSnapshot.items.isEmpty()) {
        StockLoadingOrError(ui.hotLoading, ui.hotError, "热点榜按需加载")
    } else {
        ui.hotSnapshot.items.take(6).forEach { item ->
            Row(
                Modifier.fillMaxWidth().height(52.dp).clickable { onOpenStock(item.code, false) }.padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.rank.toString(), color = StockAqua, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(26.dp), textAlign = TextAlign.Center)
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(listOf(item.code, item.industry).filter { it.isNotBlank() }.joinToString(" · "), color = Color.White.copy(alpha = 0.32f), fontSize = 7.sp)
                }
                Text(item.price, color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp, modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
                Column(Modifier.width(65.dp), horizontalAlignment = Alignment.End) {
                    Text("#${item.currentRank}", color = StockAqua.copy(alpha = 0.82f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Text(item.changePercent, color = stockTone(item.changePercent), fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun NativeWatchlistContent(
    watchlist: List<NativeWatchEntry>,
    onOpenStock: (String, Boolean) -> Unit,
    onRemoveWatch: (String) -> Unit
) {
    StockSectionTitle("我的自选", "从个股详情页加入，保存在当前设备", "${watchlist.size} 只")
    if (watchlist.isEmpty()) {
        StockLoadingOrError(false, null, "还没有自选股\n进入任意个股详情，点击“加自选”即可加入")
        return
    }
    watchlist.forEach { item ->
        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).fillMaxSize().clickable { onOpenStock(item.code, false) }.padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(Modifier.size(28.dp).background(StockYellow.copy(alpha = 0.10f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text("★", color = StockYellow, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text("${item.code} · ${item.market.ifBlank { "A股" }}", color = Color.White.copy(alpha = 0.34f), fontSize = 8.sp)
                }
                Text("›", color = StockAqua.copy(alpha = 0.58f), fontSize = 16.sp)
            }
            Box(
                Modifier.size(30.dp).background(Color.White.copy(alpha = 0.045f), StockPillShape).clickable { onRemoveWatch(item.code) },
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = Color.White.copy(alpha = 0.40f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NativeStatusMetric(label: String, available: Boolean, modifier: Modifier) {
    Column(modifier.height(48.dp), verticalArrangement = Arrangement.Center) {
        Text(label, color = Color.White.copy(alpha = 0.34f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(if (available) "实时" else "不可用", color = if (available) StockAqua else Color.White.copy(alpha = 0.42f), fontSize = 9.sp, fontWeight = FontWeight.Black)
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
    item.name.contains("上证") -> "000001"
    item.name.contains("深证") || item.name.contains("深成") -> "399001"
    item.name.contains("创业") -> "399006"
    item.name.contains("沪深300") -> "000300"
    item.name.contains("科创50") -> "000688"
    item.name.contains("A500") -> "000510"
    item.name.contains("上证50") -> "000016"
    item.name.contains("中证500") -> "000905"
    item.name.contains("中证1000") -> "000852"
    item.name.contains("北证50") -> "899050"
    else -> null
}

private fun loadNativeWatchlist(context: Context): List<NativeWatchEntry> = runCatching {
    val raw = context.getSharedPreferences(NativeWatchPreferences, Context.MODE_PRIVATE).getString(NativeWatchKey, "[]") ?: "[]"
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val code = item.optString("code").trim()
            if (code.length != 6) continue
            add(NativeWatchEntry(code, item.optString("name").ifBlank { code }, item.optString("market")))
        }
    }
}.getOrDefault(emptyList())

private fun saveNativeWatchlist(context: Context, items: List<NativeWatchEntry>) {
    val array = JSONArray()
    items.take(300).forEach { item ->
        array.put(JSONObject().put("code", item.code).put("name", item.name).put("market", item.market))
    }
    context.getSharedPreferences(NativeWatchPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString(NativeWatchKey, array.toString())
        .apply()
}

private fun onOpenHotPreview(
    ui: StockNativePageUiState,
    onOpenHot: (StockNativeHotType) -> Unit,
    load: () -> Unit
) {
    if (ui.hotSnapshot.items.isEmpty() && !ui.hotLoading) {
        @Suppress("UNUSED_VARIABLE") val ignored = onOpenHot
        load()
    }
}
