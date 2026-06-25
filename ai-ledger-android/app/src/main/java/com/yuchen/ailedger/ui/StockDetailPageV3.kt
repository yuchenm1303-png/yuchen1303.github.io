package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.yuchen.ailedger.StockMarketUiState
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.StockInformationItem
import com.yuchen.ailedger.model.StockModuleMeta
import com.yuchen.ailedger.model.StockModuleStatus
import com.yuchen.ailedger.model.displayText

private val DetailRise = Color(0xFFFF8F8F)
private val DetailFall = Color(0xFF80F7B4)
private val DetailAqua = Color(0xFF8DF9EA)
private val DetailYellow = Color(0xFFFFD36E)
private val DetailTileFill = Color.White.copy(alpha = 0.045f)
private val DetailTileBorder = Color.White.copy(alpha = 0.095f)
private val DetailDivider = Color.White.copy(alpha = 0.085f)
private val DetailTileShape = RoundedCornerShape(18.dp)
private val DetailPillShape = RoundedCornerShape(999.dp)

private val DetailQuotePanelHeight = 258.dp
private val DetailTimeTerminalHeight = 604.dp
private val DetailKTerminalHeight = 666.dp
private val DetailActionPanelHeight = 82.dp
private val DetailDecisionPanelHeight = 454.dp
private val DetailFeedPanelHeight = 354.dp

@Composable
internal fun StockDetailPageV3(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (String) -> Unit,
    onAction: (String) -> Unit,
    onOpenAssistant: () -> Unit
) {
    val isTimeShare = ui.selectedTab == "分时" || ui.selectedTab == "五日"
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { DetailTopBarV3(appState, ui, onBack, onRefresh) }
        item {
            DetailGlassPanelV3(appState, Modifier.height(DetailQuotePanelHeight)) {
                DetailQuoteHeroV3(ui)
            }
        }
        item {
            DetailGlassPanelV3(
                appState,
                Modifier.height(if (isTimeShare) DetailTimeTerminalHeight else DetailKTerminalHeight)
            ) {
                if (isTimeShare) {
                    StockProfessionalTerminalV4(
                        appState = appState,
                        ui = ui,
                        onSelectTab = onSelectTab
                    )
                } else {
                    StockProfessionalTerminalV3(
                        appState = appState,
                        ui = ui,
                        onSelectTab = onSelectTab
                    )
                }
            }
        }
        item {
            DetailGlassPanelV3(appState, Modifier.height(DetailActionPanelHeight)) {
                DetailActionStripV3(appState, ui, onAction)
            }
        }
        item {
            DetailGlassPanelV3(appState, Modifier.height(DetailDecisionPanelHeight)) {
                DetailDecisionPanelV3(ui, onOpenAssistant)
            }
        }
        item {
            DetailGlassPanelV3(appState, Modifier.height(DetailFeedPanelHeight)) {
                DetailFeedPanelV3(ui)
            }
        }
    }
}

@Composable
private fun DetailTopBarV3(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailPillButtonV3(
            text = "‹ 首页",
            active = false,
            modifier = Modifier.width(92.dp).height(42.dp),
            appState = appState,
            onClick = onBack
        )
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "个股行情",
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                listOf(ui.stock.quote.code, ui.stock.quote.market)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = DetailAqua.copy(alpha = 0.58f),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.weight(1f))
        DetailPillButtonV3(
            text = if (ui.loading || ui.kLineLoading) "…" else "⟳",
            active = false,
            modifier = Modifier.width(46.dp).height(42.dp),
            appState = appState,
            onClick = onRefresh
        )
    }
}

@Composable
private fun DetailGlassPanelV3(
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
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun DetailQuoteHeroV3(ui: StockMarketUiState) {
    val quote = ui.stock.quote
    val tone = if (quote.isRising) DetailRise else DetailFall
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                quote.name.ifBlank { "个股详情" },
                color = Color.White,
                fontSize = 29.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(quote.code, quote.market).filter { it.isNotBlank() }.joinToString(" · "),
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                ui.stock.dataSourceLabel,
                color = DetailAqua.copy(alpha = 0.68f),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                quote.price.ifBlank { "--" },
                color = tone,
                fontSize = 40.sp,
                lineHeight = 45.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                "${quote.changeAmount}  ${quote.changePercent}",
                color = tone,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                "成交额 ${quote.amount}",
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 9.sp,
                lineHeight = 12.sp
            )
        }
    }
    DetailDividerV3()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        DetailQuoteMetricV3("高", quote.high, DetailRise, Modifier.weight(1f))
        DetailQuoteMetricV3("低", quote.low, DetailFall, Modifier.weight(1f))
        DetailQuoteMetricV3("开", quote.open, Color.White, Modifier.weight(1f))
        DetailQuoteMetricV3("昨收", formatFloatV3(quote.previousClose), Color.White, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        DetailQuoteMetricV3("成交额", quote.amount, Color.White, Modifier.weight(1f))
        DetailQuoteMetricV3("换手", quote.turnoverRate, Color.White, Modifier.weight(1f))
        DetailQuoteMetricV3("量比", quote.volumeRatio, tone, Modifier.weight(1f))
        DetailQuoteMetricV3("总市值", quote.totalMarketValue, Color.White, Modifier.weight(1f))
    }
}

@Composable
private fun DetailQuoteMetricV3(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .height(56.dp)
            .background(DetailTileFill, DetailTileShape)
            .border(1.dp, color.copy(alpha = 0.10f), DetailTileShape)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            value.ifBlank { "--" },
            color = color.copy(alpha = 0.96f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailActionStripV3(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    onAction: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("加自选", "预警", "诊股", "买入", "卖出").forEach { action ->
            val display = if (action == "加自选" && ui.isWatched) "已自选" else action
            val routeAction = if (action == "买入" || action == "卖出") "交易" else action
            DetailPillButtonV3(
                text = display,
                active = ui.activeAction == action ||
                    (action == "加自选" && ui.isWatched) ||
                    ((action == "买入" || action == "卖出") && ui.activeAction == "交易"),
                modifier = Modifier.weight(1f).height(46.dp),
                appState = appState,
                onClick = { onAction(routeAction) }
            )
        }
    }
}

@Composable
private fun DetailDecisionPanelV3(
    ui: StockMarketUiState,
    onOpenAssistant: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAssistant),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DetailSectionTitleV3("AI 看盘摘要", "结合真实分时、盘口、资金与慢数据")
            Text(
                ui.stock.aiSummary,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        DetailDividerV3()
        DetailSectionTitleV3("资金流向", "主力与大中小单结构")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DetailInfoMetricV3("主力", ui.stock.moneyFlow.mainInflow, moneyToneV3(ui.stock.moneyFlow.mainInflow), Modifier.weight(1f))
            DetailInfoMetricV3("超大单", ui.stock.moneyFlow.superLargeOrder, moneyToneV3(ui.stock.moneyFlow.superLargeOrder), Modifier.weight(1f))
            DetailInfoMetricV3("大单", ui.stock.moneyFlow.largeOrder, moneyToneV3(ui.stock.moneyFlow.largeOrder), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DetailInfoMetricV3("中单", ui.stock.moneyFlow.mediumOrder, moneyToneV3(ui.stock.moneyFlow.mediumOrder), Modifier.weight(1f))
            DetailInfoMetricV3("小单", ui.stock.moneyFlow.smallOrder, moneyToneV3(ui.stock.moneyFlow.smallOrder), Modifier.weight(1f))
            DetailInfoMetricV3("状态", if (ui.stock.moneyFlow.mainInflow == "--") "暂无" else "真实快照", DetailAqua, Modifier.weight(1f))
        }
        DetailDividerV3()
        DetailSectionTitleV3("估值与覆盖", "估值指标和慢数据接入状态")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DetailInfoMetricV3("市盈TTM", ui.stock.quote.peTtm, Color.White, Modifier.weight(1f))
            DetailInfoMetricV3("市净率", ui.stock.quote.pb, Color.White, Modifier.weight(1f))
            DetailInfoMetricV3("流通市值", ui.stock.quote.floatMarketValue, Color.White, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DetailStatusMetricV3("资料", ui.slowData.profileMeta.status, Modifier.weight(1f))
            DetailStatusMetricV3("财务", ui.slowData.financialsMeta.status, Modifier.weight(1f))
            DetailStatusMetricV3("公告", ui.slowData.announcementsMeta.status, Modifier.weight(1f))
            DetailStatusMetricV3("研报", ui.slowData.researchMeta.status, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailFeedPanelV3(ui: StockMarketUiState) {
    var selected by remember(ui.stock.quote.code) { mutableStateOf("公告") }
    val options = listOf("公告", "新闻", "研报")
    DetailSectionTitleV3("资讯与事件", "真实内容优先；没有稳定数据源时明确不可用")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(
                        Color.White.copy(alpha = if (selected == option) 0.16f else 0.055f),
                        DetailPillShape
                    )
                    .clickable { selected = option },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option,
                    color = Color.White.copy(alpha = if (selected == option) 0.96f else 0.60f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
    DetailDividerV3()
    val (items, meta) = when (selected) {
        "公告" -> ui.slowData.announcements to ui.slowData.announcementsMeta
        "新闻" -> ui.slowData.news to ui.slowData.newsMeta
        else -> ui.slowData.research to ui.slowData.researchMeta
    }
    DetailFeedListV3(items, meta)
}

@Composable
private fun DetailFeedListV3(
    items: List<StockInformationItem>,
    meta: StockModuleMeta
) {
    if (items.isEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                meta.status.displayText(),
                color = statusToneV3(meta.status),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            Text(
                meta.source.ifBlank { "未接稳定真实数据源" },
                color = Color.White.copy(alpha = 0.36f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }
    items.take(5).forEach { item ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
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
                color = Color.White.copy(alpha = 0.36f),
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailInfoMetricV3(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .height(54.dp)
            .background(DetailTileFill, DetailTileShape)
            .border(1.dp, color.copy(alpha = 0.08f), DetailTileShape)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.38f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(
            value.ifBlank { "--" },
            color = color.copy(alpha = 0.94f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailStatusMetricV3(
    label: String,
    status: StockModuleStatus,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .height(50.dp)
            .background(DetailTileFill, DetailTileShape)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.36f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(
            status.displayText(),
            color = statusToneV3(status),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailSectionTitleV3(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailDividerV3() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DetailDivider))
}

@Composable
private fun DetailPillButtonV3(
    text: String,
    active: Boolean,
    modifier: Modifier,
    appState: AssistantUiState,
    onClick: () -> Unit
) {
    val intensity = appState.glassIntensity.coerceIn(0.45f, 1.20f)
    Box(
        modifier = modifier
            .background(
                Color.White.copy(alpha = (if (active) 0.18f else 0.07f) * intensity),
                DetailPillShape
            )
            .border(
                1.dp,
                (if (active) DetailAqua else Color.White).copy(alpha = if (active) 0.20f else 0.07f),
                DetailPillShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (active) 0.98f else 0.76f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatFloatV3(value: Float): String = if (value > 0f) String.format("%.2f", value) else "--"

private fun moneyToneV3(text: String): Color = when {
    text.isBlank() || text == "--" -> Color.White.copy(alpha = 0.58f)
    text.startsWith("-") -> DetailFall
    else -> DetailRise
}

private fun statusToneV3(status: StockModuleStatus): Color = when (status) {
    StockModuleStatus.Ok -> DetailAqua
    StockModuleStatus.Partial, StockModuleStatus.Stale -> DetailYellow
    StockModuleStatus.Empty -> Color.White.copy(alpha = 0.50f)
    StockModuleStatus.Unavailable -> Color.White.copy(alpha = 0.40f)
}
