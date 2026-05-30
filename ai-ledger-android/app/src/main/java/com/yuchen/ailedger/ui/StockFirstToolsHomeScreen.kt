package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState

@Composable
fun StockFirstToolsHomeScreen(
    state: AssistantUiState,
    onOpenTool: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item { StockToolsHeader() }
        item { StockMarketHeroEntry(state, onOpenTool) }
        item { StockToolsQuickRow(state, onOpenTool) }
        item { StockToolEntryCard("股票行情", "A股首页、热度榜、龙虎榜、板块和自选", state) { onOpenTool(STOCK_MARKET_TOOL_TITLE) } }
        item { StockToolEntryCard("账单中心", "手动记账、预算、分类和最近明细", state) { onOpenTool("账单中心") } }
        item { StockToolEntryCard("数据统计", "按周、月、年查看趋势", state) { onOpenTool("数据统计") } }
        item { StockToolEntryCard("提醒闹钟", "创建提醒和闹钟", state) { onOpenTool("提醒闹钟") } }
        item { StockToolEntryCard("应用控制", "打开微信、支付宝等应用", state) { onOpenTool("应用控制") } }
        item { StockToolEntryCard("快捷指令", "保存常用任务", state) { onOpenTool("快捷指令") } }
    }
}

@Composable
private fun StockToolsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("TOOLS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text("功能", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
        Text("把行情、记账和常用工具整理成可以执行的入口。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StockMarketHeroEntry(state: AssistantUiState, onOpenTool: (String) -> Unit) {
    OpenGlShellGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 1.03f,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = Modifier.fillMaxWidth().height(198.dp),
        mood = OpenGlShellMood.Hero,
        onClick = { onOpenTool(STOCK_MARKET_TOOL_TITLE) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("市场入口", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("股票行情", color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("A股首页、自选、热榜、龙虎榜、板块和资金流", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FrostInfoGlassPanel(
                radius = 20f,
                backdropAlpha = 1f,
                frostAlpha = 0f,
                dimAlpha = 0f,
                modifier = Modifier.fillMaxWidth().height(68.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StockHeroMetric("市场", "A股", Modifier.weight(1f))
                    StockHeroMetric("上证", "3048.03", Modifier.weight(1f))
                    StockHeroMetric("热点", "龙虎榜", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StockHeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(label, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StockToolsQuickRow(state: AssistantUiState, onOpenTool: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StockQuickPill("股票", "行情", STOCK_MARKET_TOOL_TITLE, state, Modifier.weight(1f), onOpenTool)
        StockQuickPill("账单", "明细", "账单中心", state, Modifier.weight(1f), onOpenTool)
        StockQuickPill("提醒", "待接入", "提醒闹钟", state, Modifier.weight(1f), onOpenTool)
    }
}

@Composable
private fun StockQuickPill(title: String, subtitle: String, target: String, state: AssistantUiState, modifier: Modifier, onOpenTool: (String) -> Unit) {
    PressableGlass(
        state.quality,
        state.glassIntensity * if (target == STOCK_MARKET_TOOL_TITLE) 1.03f else 0.92f,
        state.motionIntensity,
        22,
        modifier.height(62.dp),
        if (target == STOCK_MARKET_TOOL_TITLE) GlassRole.Floating else GlassRole.Chip,
        onClick = { onOpenTool(target) }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun StockToolEntryCard(title: String, subtitle: String, state: AssistantUiState, onClick: () -> Unit) {
    PressableGlass(
        state.quality,
        state.glassIntensity * if (title == "股票行情") 1.02f else 0.92f,
        state.motionIntensity,
        24,
        Modifier.fillMaxWidth().height(76.dp),
        if (title == "股票行情") GlassRole.Floating else GlassRole.Card,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text(if (title == "股票行情") "股" else title.take(1), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("进入", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
