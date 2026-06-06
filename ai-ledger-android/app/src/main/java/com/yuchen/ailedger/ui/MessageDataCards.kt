package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.StructuredDataCard
import com.yuchen.ailedger.model.StructuredMetric
import com.yuchen.ailedger.model.WebSource

@Composable
fun MessageDataCards(message: ChatMessage) {
    if (message.structuredData == null && message.webSources.isEmpty()) return

    var webPreviewSource by remember(message.id) { mutableStateOf<WebPreviewSource?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        message.structuredData?.let { StructuredDataCardView(it) }
        if (message.webSources.isNotEmpty()) {
            WebSourcesCard(
                sources = message.webSources,
                provider = message.searchProvider,
                onOpenSource = { webPreviewSource = it }
            )
        }
    }

    InAppWebBrowserOverlay(
        target = webPreviewSource,
        onDismiss = { webPreviewSource = null }
    )
}

@Composable
private fun StructuredDataCardView(data: StructuredDataCard) {
    if (data.isStockCard()) {
        StockQuoteCard(data)
    } else {
        RealtimeDataCard(data)
    }
}

@Composable
private fun StockQuoteCard(data: StructuredDataCard) {
    val quote = remember(data) { StockQuoteUi.from(data) }
    val accent = quote.changeTone.accent

    LightweightDataCard {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DataDot(accent)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        quote.name,
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOf(quote.symbolLine, quote.timestamp).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            color = Color.White.copy(alpha = 0.48f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                StockStatusPill(text = quote.marketStatus.ifBlank { "股票" }, accent = accent)
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("最新价", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        quote.priceLine,
                        color = Color.White.copy(alpha = 0.96f),
                        fontSize = 27.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StockChangePill(change = quote.changeLine, accent = accent)
            }

            val secondary = quote.secondaryMetrics
            if (secondary.isNotEmpty()) {
                secondary.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { metric -> MetricPill(metric, Modifier.weight(1f)) }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }

            data.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
                Text(
                    raw,
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RealtimeDataCard(data: StructuredDataCard) {
    LightweightDataCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DataDot(Color(0xFF8DF9EA))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        data.title,
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOfNotNull(data.subtitle, data.timestamp).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            color = Color.White.copy(alpha = 0.48f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    typeLabel(data.type),
                    color = Color(0xFF8DF9EA).copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }

            data.metrics.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { metric -> MetricPill(metric, Modifier.weight(1f)) }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }

            data.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
                Text(
                    raw,
                    color = Color.White.copy(alpha = 0.56f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StockStatusPill(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = accent.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StockChangePill(change: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(change, color = accent.copy(alpha = 0.94f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WebSourcesCard(
    sources: List<WebSource>,
    provider: String?,
    onOpenSource: (WebPreviewSource) -> Unit
) {
    var expanded by remember(sources) { mutableStateOf(false) }
    val previewCount = if (expanded) sources.size else 2.coerceAtMost(sources.size)
    val hiddenCount = (sources.size - previewCount).coerceAtLeast(0)

    LightweightDataCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DataDot(Color(0xFF9FD8FF))
                Text("联网来源", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("${sources.size} 条", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                provider?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = Color(0xFF9FD8FF).copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            sources.take(previewCount).forEachIndexed { index, source ->
                WebSourceRow(
                    index = index + 1,
                    source = source,
                    expanded = expanded,
                    onOpen = {
                        val url = source.url.trim()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            onOpenSource(
                                WebPreviewSource(
                                    title = source.title.ifBlank { source.domain.ifBlank { "来源 ${index + 1}" } },
                                    url = url,
                                    domain = source.domain
                                )
                            )
                        }
                    }
                )
            }

            if (hiddenCount > 0) {
                SourceTogglePill(text = "展开全部 ${sources.size} 条来源") {
                    expanded = true
                }
            } else if (expanded && sources.size > 2) {
                SourceTogglePill(text = "收起来源") {
                    expanded = false
                }
            }
        }
    }
}

@Composable
private fun SourceTogglePill(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color(0xFF9FD8FF).copy(alpha = 0.74f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
private fun LightweightDataCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.10f))
    ) {
        content()
    }
}

@Composable
private fun WebSourceRow(index: Int, source: WebSource, expanded: Boolean, onOpen: () -> Unit) {
    val hasUrl = source.url.startsWith("http://") || source.url.startsWith("https://")
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = hasUrl, onClick = onOpen)
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(index.toString(), color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    source.title.ifBlank { source.domain.ifBlank { "来源 $index" } },
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasUrl) Text("打开", color = Color(0xFF9FD8FF).copy(alpha = 0.58f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            val meta = listOf(source.domain, source.publishedAt.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = Color.White.copy(alpha = 0.40f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (source.snippet.isNotBlank()) {
                Text(
                    source.snippet,
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = if (expanded) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MetricPill(metric: StructuredMetric, modifier: Modifier = Modifier) {
    val label = metric.label.orEmpty().ifBlank { "指标" }
    val value = metric.value.orEmpty().ifBlank { "--" }
    val unit = metric.unit.orEmpty()
    val valueText = value + (unit.takeIf { it.isNotBlank() && !value.contains(it) }?.let { " $it" } ?: "")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.44f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = valueText,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        metric.detail.orEmpty().takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color.White.copy(alpha = 0.44f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DataDot(color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.84f))
            .padding(4.dp)
    )
}

private data class StockQuoteUi(
    val name: String,
    val symbolLine: String,
    val timestamp: String,
    val priceLine: String,
    val changeLine: String,
    val marketStatus: String,
    val changeTone: StockChangeTone,
    val secondaryMetrics: List<StructuredMetric>
) {
    companion object {
        fun from(data: StructuredDataCard): StockQuoteUi {
            val price = data.metricValue("price", "regularMarketPrice", "currentPrice", "last", "latest", "latestPrice", "close", "股价", "价格", "现价", "最新价", "收盘价")
            val change = data.metricValue("change", "regularMarketChange", "netChange", "涨跌", "涨跌额", "变动")
            val percent = data.metricValue("changePercent", "regularMarketChangePercent", "percent", "percentChange", "changePct", "pct", "涨跌幅", "涨幅", "跌幅")
            val currency = data.metricValue("currency", "币种", "货币")
            val exchange = data.metricValue("exchange", "market", "交易所", "市场")
            val status = data.metricValue("marketStatus", "marketState", "status", "状态", "市场状态")
            val symbol = data.metricValue("symbol", "ticker", "代码", "股票代码") ?: data.subtitle.orEmpty()
            val tone = StockChangeTone.from(change = change, percent = percent)
            val changeLine = listOfNotNull(change, percent).joinToString("  ").ifBlank { "--" }
            val priceLine = buildPriceLine(price = price, currency = currency)
            val symbolLine = listOf(symbol, exchange).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(" · ")
            val usedLabels = setOf("price", "regularmarketprice", "currentprice", "last", "latest", "latestprice", "close", "股价", "价格", "现价", "最新价", "收盘价", "change", "regularmarketchange", "netchange", "涨跌", "涨跌额", "变动", "changepercent", "regularmarketchangepercent", "percent", "percentchange", "changepct", "pct", "涨跌幅", "涨幅", "跌幅", "currency", "币种", "货币", "symbol", "ticker", "代码", "股票代码", "exchange", "market", "交易所", "市场", "marketstatus", "marketstate", "status", "状态", "市场状态")
            val secondary = data.metrics
                .filterNot { normalizeMetricKey(it.label) in usedLabels }
                .take(6)
            return StockQuoteUi(
                name = data.title.ifBlank { "股票行情" },
                symbolLine = symbolLine,
                timestamp = data.timestamp.orEmpty(),
                priceLine = priceLine,
                changeLine = changeLine,
                marketStatus = status.orEmpty(),
                changeTone = tone,
                secondaryMetrics = secondary
            )
        }
    }
}

private enum class StockChangeTone(val accent: Color) {
    Up(Color(0xFFFFA2A2)),
    Down(Color(0xFF8DF9C2)),
    Flat(Color(0xFF8DF9EA));

    companion object {
        fun from(change: String?, percent: String?): StockChangeTone {
            val text = listOfNotNull(change, percent).joinToString(" ").lowercase()
            return when {
                text.contains("-") || text.contains("跌") || text.contains("▼") || text.contains("down") -> Down
                text.contains("+") || text.contains("涨") || text.contains("▲") || text.contains("up") -> Up
                else -> Flat
            }
        }
    }
}

private fun StructuredDataCard.isStockCard(): Boolean {
    if (type.equals("stock", ignoreCase = true) || type.contains("quote", ignoreCase = true)) return true
    return metrics.any { metric ->
        normalizeMetricKey(metric.label) in setOf("股价", "股票代码", "最新价", "现价", "涨跌幅", "regularmarketprice", "regularmarketchangepercent")
    }
}

private fun StructuredDataCard.metricValue(vararg aliases: String): String? {
    val normalizedAliases = aliases.map { normalizeMetricKey(it) }.toSet()
    return metrics.firstOrNull { metric ->
        val label = normalizeMetricKey(metric.label)
        label in normalizedAliases || normalizedAliases.any { alias -> label.contains(alias) || alias.contains(label) }
    }?.let { metric ->
        val value = metric.value.orEmpty()
        value + (metric.unit?.takeIf { it.isNotBlank() && !value.contains(it) }?.let { " $it" } ?: "")
    }?.takeIf { it.isNotBlank() }
}

private fun buildPriceLine(price: String?, currency: String?): String {
    val cleanPrice = price?.takeIf { it.isNotBlank() } ?: "--"
    val cleanCurrency = currency?.takeIf { it.isNotBlank() } ?: return cleanPrice
    return if (cleanPrice.contains(cleanCurrency, ignoreCase = true)) cleanPrice else "$cleanPrice $cleanCurrency"
}

private fun normalizeMetricKey(value: String): String {
    return value.lowercase()
        .replace(" ", "")
        .replace("_", "")
        .replace("-", "")
        .replace(":", "")
        .replace("：", "")
        .replace("%", "")
        .replace("（", "")
        .replace("）", "")
        .replace("(", "")
        .replace(")", "")
}

private fun typeLabel(type: String): String = when (type.lowercase()) {
    "stock" -> "股票"
    "weather" -> "天气"
    "exchange_rate", "rate", "currency" -> "汇率"
    "sports" -> "比赛"
    else -> "实时"
}
