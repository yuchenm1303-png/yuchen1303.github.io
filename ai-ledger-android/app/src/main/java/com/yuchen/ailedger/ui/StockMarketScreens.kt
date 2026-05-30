package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState

@Composable
fun AStockMarketScreenV2(state: AssistantUiState, onBack: () -> Unit, onOpenAssistant: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item { AStockTopBar(state, onBack) }
        item { AStockQuotePanel(state) }
        item { AStockTabs(state) }
        item { AStockMinuteChart(state) }
        item { AStockOrderBook(state) }
        item { AStockTradeAndFlow(state) }
        item { AStockInfoPanel(state) }
        item { AStockAiPanel(state, onOpenAssistant) }
    }
}

@Composable
private fun AStockTopBar(state: AssistantUiState, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PressableGlass(state.quality, state.glassIntensity * 0.95f, state.motionIntensity, 999, Modifier.height(38.dp), GlassRole.Chip, onClick = onBack) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("‹ 返回", color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("A-SHARE", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("股票行情", color = Color.White, fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.Black)
            Text("A股专业看盘、盘口、资金和 AI 摘要", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun AStockQuotePanel(state: AssistantUiState) {
    OpenGlShellGlass(state.quality, state.glassIntensity * 1.04f, state.motionIntensity, 30, Modifier.fillMaxWidth().height(196.dp), OpenGlShellMood.Summary) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("华电能源", color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("600396 · 沪A · Level-2 骨架", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text("＋自选", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(0.92f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("20.03", color = RiseRed, fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
                    Text("+0.77   +4.00%", color = RiseRed, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        QuoteMetric("高", "20.80", RiseRed, Modifier.weight(1f)); QuoteMetric("低", "19.18", FallGreen, Modifier.weight(1f)); QuoteMetric("开", "19.18", FallGreen, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        QuoteMetric("市值", "294.98亿", Color.White, Modifier.weight(1f)); QuoteMetric("量比", "1.10", RiseRed, Modifier.weight(1f)); QuoteMetric("换手", "20.34%", Color.White, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        QuoteMetric("市盈", "72.45", Color.White, Modifier.weight(1f)); QuoteMetric("成交额", "60.17亿", Color.White, Modifier.weight(1f)); QuoteMetric("人气", "9/4284", Color.White, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = color.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AStockTabs(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.92f, state.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("分时", "日K", "周K", "月K", "五日", "更多").forEachIndexed { index, label ->
                GlassPanel(state.quality, state.glassIntensity * if (index == 0) 1.02f else 0.78f, state.motionIntensity, 999, Modifier.weight(1f).height(34.dp), if (index == 0) GlassRole.Floating else GlassRole.Chip) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = Color.White.copy(alpha = if (index == 0) 0.94f else 0.48f), fontSize = 12.sp, fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
private fun AStockMinuteChart(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("分时走势", "白线价格 · 黄线均价 · 下方成交量")
            Canvas(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                val prices = listOf(19.18f, 19.18f, 20.52f, 21.19f, 20.86f, 20.72f, 20.35f, 20.18f, 20.42f, 20.09f, 20.01f, 20.03f)
                val avgs = listOf(19.18f, 19.44f, 19.82f, 20.01f, 20.11f, 20.14f, 20.12f, 20.11f, 20.10f, 20.09f, 20.09f, 20.09f)
                val volumes = listOf(0.12f, 0.25f, 0.82f, 1f, 0.74f, 0.42f, 0.36f, 0.32f, 0.50f, 0.44f, 0.38f, 0.34f)
                val minValue = 17.33f; val maxValue = 21.19f; val baseValue = 19.26f
                val range = (maxValue - minValue).coerceAtLeast(0.01f)
                val left = 6.dp.toPx(); val right = size.width - 6.dp.toPx(); val top = 10.dp.toPx(); val bottom = size.height * 0.70f
                val volumeTop = bottom + 16.dp.toPx(); val volumeBottom = size.height - 8.dp.toPx()
                repeat(4) { i -> val y = top + (bottom - top) * i / 3f; drawLine(Color.White.copy(alpha = 0.12f), Offset(left, y), Offset(right, y), 1.dp.toPx(), cap = StrokeCap.Round) }
                repeat(5) { i -> val x = left + (right - left) * i / 4f; drawLine(Color.White.copy(alpha = 0.12f), Offset(x, top), Offset(x, volumeBottom), 1.dp.toPx(), cap = StrokeCap.Round) }
                val baseY = bottom - (baseValue - minValue) / range * (bottom - top)
                drawLine(Color.White.copy(alpha = 0.25f), Offset(left, baseY), Offset(right, baseY), 1.dp.toPx(), cap = StrokeCap.Round)
                fun point(index: Int, value: Float): Offset = Offset(left + (right - left) * index / prices.lastIndex.coerceAtLeast(1).toFloat(), bottom - (value - minValue) / range * (bottom - top))
                val pricePath = Path(); prices.forEachIndexed { i, v -> val p = point(i, v); if (i == 0) pricePath.moveTo(p.x, p.y) else pricePath.lineTo(p.x, p.y) }
                val avgPath = Path(); avgs.forEachIndexed { i, v -> val p = point(i, v); if (i == 0) avgPath.moveTo(p.x, p.y) else avgPath.lineTo(p.x, p.y) }
                drawPath(pricePath, Color.White.copy(alpha = 0.92f), style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
                drawPath(avgPath, Color(0xFFFFC857), style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round))
                volumes.forEachIndexed { i, volume ->
                    val barWidth = (right - left) / prices.size * 0.52f
                    val x = left + (right - left) * i / prices.lastIndex.coerceAtLeast(1).toFloat()
                    val barHeight = (volumeBottom - volumeTop) * volume.coerceIn(0f, 1f)
                    val color = if (i > 0 && prices[i] >= prices[i - 1]) RiseRed else FallGreen
                    drawRoundRect(color.copy(alpha = 0.62f), Offset(x - barWidth / 2f, volumeBottom - barHeight), Size(barWidth, barHeight), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("09:30", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("11:30 / 13:00", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("15:00", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AStockOrderBook(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("十档盘口", "卖盘在上，买盘在下")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { listOf("卖10 20.13 128", "卖9 20.12 95", "卖8 20.11 165", "卖7 20.10 1128", "卖6 20.09 294", "卖5 20.08 480", "卖4 20.07 185", "卖3 20.06 872", "卖2 20.05 2348", "卖1 20.04 2735").forEach { OrderRow(it, true) } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { listOf("买1 20.03 5923", "买2 20.02 2209", "买3 20.01 2272", "买4 20.00 6464", "买5 19.99 775", "买6 19.98 1155", "买7 19.97 400", "买8 19.96 502", "买9 19.95 441", "买10 19.94 318").forEach { OrderRow(it, false) } }
            }
        }
    }
}

@Composable
private fun OrderRow(text: String, ask: Boolean) { Text(text, color = if (ask) RiseRed else FallGreen, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1) }

@Composable
private fun AStockTradeAndFlow(state: AssistantUiState) { InfoCard(state, "成交与资金", "逐笔成交、主力净流入和大单结构", listOf("现手 51756    总量 44763万手    委比 +18.6%", "15:00  20.03  44763  收盘", "14:59  20.04  2735  主动", "主力净流入 +1.28亿    超大单 +0.46亿    大单 +0.82亿")) }
@Composable
private fun AStockInfoPanel(state: AssistantUiState) { InfoCard(state, "资料与消息", "财务、公告、新闻和研报入口", listOf("流通市值 294.98亿    市净率 3.18    每股收益 0.28", "公司资料 / 公告 / 新闻 / 研报")) }
@Composable
private fun AStockAiPanel(state: AssistantUiState, onOpenAssistant: () -> Unit) { PressableGlass(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card, onClick = onOpenAssistant) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Section("AI 看盘摘要", "根据分时、量能、盘口和资金流整理") ; Text("当前示例股冲高后回落，价格仍高于昨收线；成交量明显放大，盘口买一挂单较厚。接入真实行情后，这里会自动生成摘要。", color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp, lineHeight = 18.sp) } } }

@Composable
private fun InfoCard(state: AssistantUiState, title: String, subtitle: String, rows: List<String>) { GlassPanel(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Section(title, subtitle); rows.forEach { Text(it, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold) } } } }
@Composable
private fun Section(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black); Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp, lineHeight = 16.sp) } }

private val RiseRed = Color(0xFFFF4D5D)
private val FallGreen = Color(0xFF41D873)
