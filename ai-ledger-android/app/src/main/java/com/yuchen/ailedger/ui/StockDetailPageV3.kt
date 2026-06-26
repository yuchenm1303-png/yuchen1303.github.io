package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private val WebDetailRise = Color(0xFFFF8F8F)
private val WebDetailFall = Color(0xFF80F7B4)
private val WebDetailAqua = Color(0xFF8DF9EA)
private val WebDetailDivider = Color.White.copy(alpha = 0.095f)

/**
 * 股票详情页严格对应 stock-detail-web-preview.html 2026-06-26.16。
 * 页面只保留网页最终版中的紧凑行情卡与全屏自适应图表卡，内部尺寸、间距和信息顺序同步网页。
 */
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
    BackHandler(onBack = onBack)
    @Suppress("UNUSED_VARIABLE")
    val retainedCallbacks = listOf<Any>(onRefresh, onAction, onOpenAssistant)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WebMirrorQuoteCard(
            appState = appState,
            ui = ui,
            modifier = Modifier.fillMaxWidth().height(138.dp)
        )
        WebMirrorGlassCard(
            appState = appState,
            radius = 30,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            StockWebMirrorTerminal(
                appState = appState,
                ui = ui,
                onSelectTab = onSelectTab
            )
        }
    }
}

@Composable
private fun WebMirrorQuoteCard(
    appState: AssistantUiState,
    ui: StockMarketUiState,
    modifier: Modifier
) {
    WebMirrorGlassCard(appState = appState, radius = 26, modifier = modifier) {
        val quote = ui.stock.quote
        val quoteTone = if (quote.isRising) WebDetailRise else WebDetailFall
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = quote.name.ifBlank { "--" },
                    color = Color.White,
                    fontSize = 25.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(quote.code.ifBlank { "------" }, quote.market.ifBlank { "--" }).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
            Text(
                text = ui.stock.dataSourceLabel.ifBlank { "等待真实后端行情" },
                color = WebDetailAqua.copy(alpha = 0.62f),
                fontSize = 8.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(160.dp).padding(top = 3.dp)
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(WebDetailDivider))

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.width(104.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = quote.price.ifBlank { "--" },
                    color = quoteTone,
                    fontSize = 35.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = "${quote.changeAmount.ifBlank { "--" }}  ${quote.changePercent.ifBlank { "--" }}",
                    color = quoteTone,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = "昨收 ${webMirrorPrice(quote.previousClose)}",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    maxLines = 1
                )
            }
            Box(Modifier.width(1.dp).fillMaxSize().background(WebDetailDivider))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WebMirrorMetricRow(
                    listOf(
                        WebMetric("高", quote.high, webPriceTone(quote.high, quote.previousClose)),
                        WebMetric("市值", quote.totalMarketValue, Color.White.copy(alpha = 0.92f)),
                        WebMetric("量比", quote.volumeRatio, if ((quote.volumeRatio.toFloatOrNull() ?: 0f) >= 1f) WebDetailRise else WebDetailFall)
                    )
                )
                WebMirrorMetricRow(
                    listOf(
                        WebMetric("低", quote.low, webPriceTone(quote.low, quote.previousClose)),
                        WebMetric("流通", quote.floatMarketValue, Color.White.copy(alpha = 0.92f)),
                        WebMetric("换", quote.turnoverRate, Color.White.copy(alpha = 0.92f))
                    )
                )
                WebMirrorMetricRow(
                    listOf(
                        WebMetric("开", quote.open, webPriceTone(quote.open, quote.previousClose)),
                        WebMetric("市盈TTM", quote.peTtm, Color.White.copy(alpha = 0.92f)),
                        WebMetric("额", quote.amount, Color.White.copy(alpha = 0.92f))
                    )
                )
            }
        }
    }
}

private data class WebMetric(val label: String, val value: String, val color: Color)

@Composable
private fun WebMirrorMetricRow(metrics: List<WebMetric>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        metrics.forEach { metric ->
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = metric.label,
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = metric.value.ifBlank { "--" },
                    color = metric.color,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun WebMirrorGlassCard(
    appState: AssistantUiState,
    radius: Int,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    GlassPanel(
        quality = appState.quality,
        glassIntensity = appState.glassIntensity * 0.92f,
        motionIntensity = appState.motionIntensity,
        radius = radius,
        modifier = modifier,
        role = GlassRole.Card
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(if (radius == 26) 14.dp else 13.dp),
            verticalArrangement = Arrangement.spacedBy(if (radius == 26) 9.dp else 0.dp)
        ) {
            content()
        }
    }
}

private fun webMirrorPrice(value: Float): String = if (value > 0f) String.format(java.util.Locale.US, "%.2f", value) else "--"

private fun webPriceTone(value: String, previousClose: Float): Color {
    val number = value.replace(",", "").toFloatOrNull() ?: return Color.White.copy(alpha = 0.86f)
    if (previousClose <= 0f) return Color.White.copy(alpha = 0.86f)
    return when {
        number > previousClose -> WebDetailRise
        number < previousClose -> WebDetailFall
        else -> Color.White.copy(alpha = 0.86f)
    }
}
