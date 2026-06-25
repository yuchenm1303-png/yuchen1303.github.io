package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockDetailUiState
import com.yuchen.ailedger.model.StockIndexSnapshot
import com.yuchen.ailedger.model.StockInformationItem
import com.yuchen.ailedger.model.StockMarketBoard
import com.yuchen.ailedger.model.StockMetric
import com.yuchen.ailedger.model.StockModuleMeta
import com.yuchen.ailedger.model.StockModuleStatus
import com.yuchen.ailedger.model.StockRankItem
import com.yuchen.ailedger.model.StockSectorSnapshot
import com.yuchen.ailedger.model.StockTone
import com.yuchen.ailedger.model.displayText

private val HomeQuickActions = listOf("自选", "热榜", "板块", "资金", "异动", "新闻", "研报", "预警")
private val RiseRed = Color(0xFFFF8F8F)
private val FallGreen = Color(0xFF80F7B4)
private val Aqua = Color(0xFF8DF9EA)
private val WarningYellow = Color(0xFFFFD36E)
private val SectionLine = Color.White.copy(alpha = 0.085f)
private val MarketOverviewPanelHeight = 520.dp
private val MarketContentPanelHeight = 640.dp
private val MarketStatusPanelHeight = 390.dp
private val StockDetailCorePanelHeight = 980.dp
private val StockDetailInfoPanelHeight = 650.dp

@Composable
fun AStockMarketScreenV2(
    state: AssistantUiState,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    val viewModel: StockMarketViewModel = viewModel()
    val ui by viewModel.uiState.collectAsState()

    if (ui.showDetail) {
        StockDetailPage(
            appState = state,
            ui = ui,
            onBack = viewModel::backToHome,
            onRefresh = viewModel::refreshCurrent,
            onSelectTab = viewModel::selectTab,
            onAction = viewModel::handleAction,
            onOpenAssistant = onOpenAssistant
        )
    } else {
        StockHomePage(
            appState = state,
            ui = ui,
            onBack = onBack,
            onRefresh = viewModel::refreshHome,
            onQueryChange = viewModel::updateQuery,
            onSearch = viewModel::searchAndOpen,
            onOpenDetail = viewModel::openDetail,
            onOpenCode = viewModel::openCode,
            onSelectHomeAction = viewModel::selectHomeAction,
            onOpenAssistant = onOpenAssistant
        )
    }
}

@Composable
private fun StockHomePage(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenCode: (String) -> Unit,
    onSelectHomeAction: (String) -> Unit,
    onOpenAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { HomeHeader(appState, ui, onBack, onRefresh) }
        item {
            StockParentGlassPanel(appState, Modifier.height(MarketOverviewPanelHeight)) {
                TopSearchBar(appState, ui, onQueryChange, onSearch)
                SectionDivider()
                MarketIndexSection(ui)
                SectionDivider()
                MarketBreadthSection(ui, onOpenDetail)
            }
        }
        item {
            StockParentGlassPanel(appState, Modifier.height(MarketContentPanelHeight)) {
                Section("市场数据", "真实榜单、板块与资金排序")
                HomeToolGrid(appState, ui.selectedHomeAction, onSelectHomeAction)
                SectionDivider()
                HomeToolContent(ui, onOpenCode)
            }
        }
        item {
            StockParentGlassPanel(appState, Modifier.height(MarketStatusPanelHeight)) {
                HomeDataStatusSection(ui, onOpenAssistant)
            }
        }
    }
}

@Composable
private fun StockDetailPage(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (String) -> Unit,
    onAction: (String) -> Unit,
    onOpenAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { DetailTopBar(appState, ui, onBack, onRefresh) }
        item {
            StockParentGlassPanel(appState, Modifier.height(StockDetailCorePanelHeight)) {
                DetailQuoteHeaderSection(ui.stock)
                SectionDivider()
                DetailMetricTickerSection(ui.stock)
                SectionDivider()
                StockProfessionalTerminalV2(appState, ui, onSelectTab)
                DepthStatusLine(ui)
                SectionDivider()
                DetailActionBar(appState, ui, onAction)
            }
        }
        item {
            StockParentGlassPanel(appState, Modifier.height(StockDetailInfoPanelHeight)) {
                DetailDecisionSection(ui, onOpenAssistant)
            }
        }
    }
}

@Composable
private fun HomeHeader(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StockButton(appState, "‹ 首页", Modifier.width(92.dp).height(42.dp), onBack)
            Spacer(Modifier.weight(1f))
            Text(
                "市场总览 · A股",
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            StockIconButton(appState, if (ui.loading || ui.marketLoading) "…" else "⟳", onRefresh)
        }
        Text(
            "A-SHARE MARKET",
            color = Aqua.copy(alpha = 0.72f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            "市场概览",
            color = Color.White,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            statusText(ui),
            color = statusColor(ui),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailTopBar(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StockButton(appState, "‹ 首页", Modifier.width(92.dp).height(42.dp), onBack)
        Spacer(Modifier.weight(1f))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                ui.stock.quote.name,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${ui.stock.quote.code} · ${ui.stock.quote.market}",
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        StockIconButton(appState, if (ui.loading || ui.kLineLoading) "…" else "⟳", onRefresh)
    }
}

@Composable
private fun StockParentGlassPanel(
    appState: AssistantUiState,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    GlassPanel(
        quality = appState.quality,
        glassIntensity = appState.glassIntensity * 0.92f,
        motionIntensity = appState.motionIntensity,
        radius = 30,
        modifier = modifier.fillMaxWidth(),
        role = GlassRole.Card
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun TopSearchBar(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("⌕", color = Aqua, fontSize = 19.sp, fontWeight = FontWeight.Black)
        BasicTextField(
            value = ui.query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Black
            ),
            cursorBrush = SolidColor(Color.White.copy(alpha = 0.92f)),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (ui.query.isBlank()) {
                        Text(
                            "搜索股票代码或名称",
                            color = Color.White.copy(alpha = 0.38f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    inner()
                }
            }
        )
        StockButton(
            appState,
            if (ui.loading) "连接" else "搜索",
            Modifier.width(70.dp).height(38.dp),
            onSearch,
            active = true
        )
    }
}

@Composable
private fun MarketIndexSection(ui: StockMarketUiState) {
    Section("主要指数", "10 个沪深京核心指数 · 横向滑动")
    val indices = ui.marketHome.indices
    if (indices.isEmpty()) {
        DataEmptyLine(
            if (ui.marketLoading) "正在加载真实指数" else "指数数据暂不可用",
            ui.marketLoading
        )
        return
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(88.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(indices, key = { "${it.name}-${it.value}" }) { item ->
            IndexCard(item)
        }
    }
}

@Composable
private fun IndexCard(item: StockIndexSnapshot) {
    Column(
        Modifier.width(104.dp).height(82.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            item.name,
            color = Color.White.copy(alpha = 0.52f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(5.dp))
        Text(
            item.value,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            item.changePercent,
            color = quoteColor(item.isRising),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun MarketBreadthSection(ui: StockMarketUiState, onOpenDetail: () -> Unit) {
    val breadth = ui.marketHome.marketBreadth
    val sentiment = ui.marketHome.sentiment
    Section("市场宽度", "全市场涨跌分布与派生情绪温度")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile("上涨", breadth.upCount?.toString() ?: "--", RiseRed, Modifier.weight(1f))
        MetricTile("下跌", breadth.downCount?.toString() ?: "--", FallGreen, Modifier.weight(1f))
        MetricTile("涨停", breadth.limitUpCount?.toString() ?: "--", RiseRed, Modifier.weight(1f))
        MetricTile("跌停", breadth.limitDownCount?.toString() ?: "--", FallGreen, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile("红盘率", percentText(breadth.redRate), Color.White, Modifier.weight(1f))
        MetricTile("赚钱效应", percentText(breadth.moneyMakingEffect), Aqua, Modifier.weight(1f))
        MetricTile("情绪温度", temperatureText(sentiment.temperature), sentimentColor(sentiment.temperature), Modifier.weight(1f))
    }
    Row(
        Modifier.fillMaxWidth().height(42.dp).clickable(onClick = onOpenDetail),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "全市场成交额",
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(
            breadth.marketAmount,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun HomeToolGrid(
    appState: AssistantUiState,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeQuickActions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { action ->
                    StockButton(
                        appState,
                        action,
                        Modifier.weight(1f).height(42.dp),
                        { onSelect(action) },
                        active = selected == action
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeToolContent(ui: StockMarketUiState, onOpenCode: (String) -> Unit) {
    when (ui.selectedHomeAction) {
        "自选" -> WatchListContent(ui, onOpenCode)
        "热榜" -> BoardGroupContent(
            ui.marketHome.boards.filterNot { it.title.contains("主力") }.take(3),
            "真实行情榜单",
            ui.marketHome.popularityMeta,
            onOpenCode
        )
        "板块" -> SectorContent(ui.marketHome.sectors)
        "资金" -> BoardGroupContent(
            ui.marketHome.boards.filter { it.title.contains("主力") },
            "主力资金排序",
            StockModuleMeta(status = if (ui.marketHome.boards.any { it.title.contains("主力") }) StockModuleStatus.Ok else StockModuleStatus.Unavailable),
            onOpenCode
        )
        "异动" -> ModuleUnavailableContent("交易异动", ui.marketHome.limitUpMeta)
        "新闻" -> InformationContent("市场新闻", ui.marketHome.marketNews, ui.marketHome.marketNewsMeta)
        "研报" -> ModuleUnavailableContent("机构研报", ui.slowData.researchMeta)
        "预警" -> DataEmptyLine("价格预警属于本地功能，当前尚未配置预警条件", false)
    }
}

@Composable
private fun WatchListContent(ui: StockMarketUiState, onOpenCode: (String) -> Unit) {
    Section("我的自选", "自选列表只使用用户本地选择，不再注入固定股票")
    if (!ui.isWatched) {
        DataEmptyLine("尚未添加自选股", false)
        return
    }
    val quote = ui.stock.quote
    Row(
        Modifier.fillMaxWidth().height(50.dp).clickable { onOpenCode(quote.code) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(quote.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(quote.code, color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp)
        }
        Text(quote.price, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(
            quote.changePercent,
            color = quoteColor(quote.isRising),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun BoardGroupContent(
    boards: List<StockMarketBoard>,
    title: String,
    fallbackMeta: StockModuleMeta,
    onOpenCode: (String) -> Unit
) {
    Section(title, "不同榜单使用各自真实排序字段")
    if (boards.isEmpty()) {
        ModuleStatusLine(fallbackMeta)
        return
    }
    boards.forEachIndexed { boardIndex, board ->
        Text(
            board.title,
            color = if (boardIndex == 0) Aqua else Color.White.copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        board.items.take(if (boards.size == 1) 8 else 3).forEachIndexed { index, item ->
            RankRow(index + 1, item, onOpenCode)
        }
        if (boardIndex != boards.lastIndex) SectionDivider()
    }
}

@Composable
private fun SectorContent(sectors: List<StockSectorSnapshot>) {
    Section("行业板块", "真实行业涨幅、涨跌家数与领涨股")
    if (sectors.isEmpty()) {
        DataEmptyLine("板块数据暂不可用", false)
        return
    }
    sectors.take(8).forEach { sector ->
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    sector.sectorName,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    buildString {
                        append("涨 ${sector.upCount ?: "--"} · 跌 ${sector.downCount ?: "--"}")
                        if (sector.leaderName.isNotBlank()) append(" · ${sector.leaderName}")
                    },
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                sector.mainInflow.ifBlank { sector.amount },
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 10.sp,
                modifier = Modifier.width(72.dp),
                textAlign = TextAlign.End,
                maxLines = 1
            )
            Text(
                sector.changePercent,
                color = quoteColor(!sector.changePercent.startsWith("-")),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(64.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun InformationContent(
    title: String,
    items: List<StockInformationItem>,
    meta: StockModuleMeta
) {
    Section(title, "只展示后端确认的真实内容")
    if (items.isEmpty()) {
        ModuleStatusLine(meta)
        return
    }
    items.take(8).forEach { item ->
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(
                item.title,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(item.source, item.publishTime).filter { it.isNotBlank() }.joinToString(" · "),
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ModuleUnavailableContent(title: String, meta: StockModuleMeta) {
    Section(title, "没有稳定真实数据源时不会生成模板数据")
    ModuleStatusLine(meta)
}

@Composable
private fun HomeDataStatusSection(ui: StockMarketUiState, onOpenAssistant: () -> Unit) {
    Section("数据覆盖状态", "真实可用与暂不可用模块分开显示")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusMetric("指数", ui.marketHome.marketBreadth.meta.status, Modifier.weight(1f))
        StatusMetric("情绪", ui.marketHome.sentiment.meta.status, Modifier.weight(1f))
        StatusMetric("新闻", ui.marketHome.marketNewsMeta.status, Modifier.weight(1f))
        StatusMetric("人气", ui.marketHome.popularityMeta.status, Modifier.weight(1f))
    }
    SectionDivider()
    Text(
        "公告、新闻、研报、财务、人气、龙虎榜等接口已预留；当前没有稳定真实源的模块显示不可用，不再展示本地模板。",
        color = Color.White.copy(alpha = 0.62f),
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
    SectionDivider()
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onOpenAssistant),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("AI 看盘", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(
            ui.stock.aiSummary,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailQuoteHeaderSection(stock: StockDetailUiState) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stock.quote.name.ifBlank { "个股详情" },
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${stock.quote.code} · ${stock.quote.market}",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                stock.dataSourceLabel,
                color = Aqua.copy(alpha = 0.70f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stock.quote.price.ifBlank { "--" },
                color = quoteColor(stock.quote.isRising),
                fontSize = 39.sp,
                lineHeight = 45.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "${stock.quote.changeAmount}  ${stock.quote.changePercent}",
                color = quoteColor(stock.quote.isRising),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "成交额 ${stock.quote.amount}",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun DetailMetricTickerSection(stock: StockDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactMetricCell("高", stock.quote.high, RiseRed, Modifier.weight(1f))
            CompactMetricCell("低", stock.quote.low, FallGreen, Modifier.weight(1f))
            CompactMetricCell("开", stock.quote.open, Color.White, Modifier.weight(1f))
            CompactMetricCell("昨收", formatPreviousClose(stock.quote.previousClose), Color.White, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactMetricCell("市值", stock.quote.totalMarketValue, Color.White, Modifier.weight(1f))
            CompactMetricCell("流通", stock.quote.floatMarketValue, Color.White, Modifier.weight(1f))
            CompactMetricCell("换手", stock.quote.turnoverRate, Color.White, Modifier.weight(1f))
            CompactMetricCell("量比", stock.quote.volumeRatio, quoteColor(stock.quote.isRising), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactMetricCell("市盈TTM", stock.quote.peTtm, Color.White, Modifier.weight(1f))
            CompactMetricCell("市净率", stock.quote.pb, Color.White, Modifier.weight(1f))
            CompactMetricCell("主力", stock.moneyFlow.mainInflow, flowColor(stock.moneyFlow.mainInflow), Modifier.weight(1f))
            CompactMetricCell("人气", stock.quote.popularityRank, Aqua, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DepthStatusLine(ui: StockMarketUiState) {
    val depth = ui.depthState
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "五档盘口",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            depth.status.displayText(),
            color = statusTone(depth.status),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (depth.isDerived) "推导数据" else "仅真实上游",
            color = if (depth.isDerived) WarningYellow else Aqua.copy(alpha = 0.64f),
            fontSize = 9.sp
        )
    }
}

@Composable
private fun DetailActionBar(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onAction: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("加自选", "预警", "诊股", "买入", "卖出").forEach { action ->
            StockButton(
                appState = appState,
                text = if (action == "加自选" && ui.isWatched) "已自选" else action,
                modifier = Modifier.weight(1f).height(42.dp),
                onClick = { onAction(if (action == "买入" || action == "卖出") "交易" else action) },
                active = ui.activeAction == action || (action == "买入" && ui.activeAction == "交易")
            )
        }
    }
}

@Composable
private fun DetailDecisionSection(ui: StockMarketUiState, onOpenAssistant: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onOpenAssistant),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Section("AI 看盘摘要", "点击继续追问")
            Text(
                ui.stock.aiSummary,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        SectionDivider()
        MoneyFlowSection(ui.stock)
        SectionDivider()
        SlowModuleStatusSection(ui)
        SectionDivider()
        InformationContent("个股资讯", ui.slowData.news, ui.slowData.newsMeta)
    }
}

@Composable
private fun MoneyFlowSection(stock: StockDetailUiState) {
    Section("资金流向", "主力、大单、中单与小单结构")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile("主力", stock.moneyFlow.mainInflow, flowColor(stock.moneyFlow.mainInflow), Modifier.weight(1f))
        MetricTile("超大单", stock.moneyFlow.superLargeOrder, flowColor(stock.moneyFlow.superLargeOrder), Modifier.weight(1f))
        MetricTile("大单", stock.moneyFlow.largeOrder, flowColor(stock.moneyFlow.largeOrder), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile("中单", stock.moneyFlow.mediumOrder, flowColor(stock.moneyFlow.mediumOrder), Modifier.weight(1f))
        MetricTile("小单", stock.moneyFlow.smallOrder, flowColor(stock.moneyFlow.smallOrder), Modifier.weight(1f))
        MetricTile("状态", if (stock.moneyFlow.mainInflow == "--") "暂无" else "实时快照", Aqua, Modifier.weight(1f))
    }
}

@Composable
private fun SlowModuleStatusSection(ui: StockMarketUiState) {
    Section("慢数据覆盖", if (ui.slowDataLoading) "正在检查真实数据源" else "未接真实源的模块明确显示不可用")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusMetric("资料", ui.slowData.profileMeta.status, Modifier.weight(1f))
        StatusMetric("财务", ui.slowData.financialsMeta.status, Modifier.weight(1f))
        StatusMetric("公告", ui.slowData.announcementsMeta.status, Modifier.weight(1f))
        StatusMetric("研报", ui.slowData.researchMeta.status, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusMetric("人气", ui.slowData.popularityMeta.status, Modifier.weight(1f))
        StatusMetric("股东", ui.slowData.shareholdersMeta.status, Modifier.weight(1f))
        StatusMetric("解禁", ui.slowData.unlocksMeta.status, Modifier.weight(1f))
        StatusMetric("分红", ui.slowData.dividendsMeta.status, Modifier.weight(1f))
    }
}

@Composable
private fun RankRow(rank: Int, item: StockRankItem, onOpenCode: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(42.dp).clickable(enabled = item.code.length == 6) { onOpenCode(item.code) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            rank.toString(),
            color = Aqua.copy(alpha = 0.88f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(22.dp),
            textAlign = TextAlign.Center
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(item.code, color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp)
        }
        Text(
            item.value,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 11.sp,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            item.changePercent,
            color = quoteColor(item.isRising),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(62.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ModuleStatusLine(meta: StockModuleMeta) {
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            meta.status.displayText(),
            color = statusTone(meta.status),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.weight(1f))
        Text(
            meta.source.ifBlank { "未接稳定真实数据源" },
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusMetric(label: String, status: StockModuleStatus, modifier: Modifier) {
    Column(modifier.height(50.dp), verticalArrangement = Arrangement.Center) {
        Text(label, color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(status.displayText(), color = statusTone(status), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun DataEmptyLine(text: String, loading: Boolean) {
    Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            if (loading) "正在加载…" else text,
            color = if (loading) Aqua.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.46f),
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SectionLine))
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier.height(54.dp), verticalArrangement = Arrangement.Center) {
        Text(label, color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            value.ifBlank { "--" },
            color = color.copy(alpha = 0.94f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactMetricCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier.height(54.dp), verticalArrangement = Arrangement.Center) {
        Text(label, color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            value.ifBlank { "--" },
            color = color.copy(alpha = 0.94f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StockButton(
    appState: AssistantUiState,
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
    active: Boolean = false
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
    val intensity = appState.glassIntensity.coerceIn(0.45f, 1.20f)
    val fillAlpha = (if (active) 0.20f else 0.075f) * intensity
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = fillAlpha), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (active) 0.98f else 0.76f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StockIconButton(appState: AssistantUiState, text: String, onClick: () -> Unit) {
    StockButton(appState, text, Modifier.width(46.dp).height(42.dp), onClick)
}

private fun quoteColor(isRising: Boolean): Color = if (isRising) RiseRed else FallGreen

private fun toneColor(tone: StockTone): Color = when (tone) {
    StockTone.Rising -> RiseRed
    StockTone.Falling -> FallGreen
    StockTone.Neutral -> Color.White
}

private fun flowColor(text: String): Color = when {
    text.isBlank() || text == "--" -> Color.White.copy(alpha = 0.62f)
    text.contains("-") -> FallGreen
    else -> RiseRed
}

private fun statusTone(status: StockModuleStatus): Color = when (status) {
    StockModuleStatus.Ok -> Aqua
    StockModuleStatus.Partial -> WarningYellow
    StockModuleStatus.Stale -> WarningYellow
    StockModuleStatus.Empty -> Color.White.copy(alpha = 0.50f)
    StockModuleStatus.Unavailable -> Color.White.copy(alpha = 0.42f)
}

private fun sentimentColor(value: Double?): Color = when {
    value == null -> Color.White.copy(alpha = 0.50f)
    value >= 60.0 -> RiseRed
    value < 35.0 -> FallGreen
    else -> WarningYellow
}

private fun statusText(ui: StockMarketUiState): String = when {
    ui.loading -> "正在连接个股行情"
    ui.marketLoading -> "正在同步指数、宽度、榜单与板块"
    ui.requestMessage != null -> ui.requestMessage
    else -> "真实市场数据 · 20 秒刷新"
}

private fun statusColor(ui: StockMarketUiState): Color =
    if (ui.requestMessage != null) WarningYellow else Color.White.copy(alpha = 0.58f)

private fun percentText(value: Double?): String = value?.let { String.format("%.2f%%", it) } ?: "--"

private fun temperatureText(value: Double?): String = value?.let { String.format("%.0f", it) } ?: "--"

private fun formatPreviousClose(value: Float): String = if (value > 0f) String.format("%.2f", value) else "--"
