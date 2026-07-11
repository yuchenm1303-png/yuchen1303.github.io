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

private val STOCK_PRICE_ALIASES = setOf(
    "price", "regularmarketprice", "currentprice", "last", "latest", "latestprice", "close",
    "股价", "价格", "现价", "最新价", "收盘价"
)
private val STOCK_CHANGE_ALIASES = setOf(
    "change", "regularmarketchange", "netchange", "涨跌", "涨跌额", "变动"
)
private val STOCK_PERCENT_ALIASES = setOf(
    "changepercent", "regularmarketchangepercent", "percent", "percentchange", "changepct", "pct",
    "涨跌幅", "涨幅", "跌幅"
)
private val STOCK_CURRENCY_ALIASES = setOf("currency", "币种", "货币")
private val STOCK_EXCHANGE_ALIASES = setOf("exchange", "market", "交易所", "市场")
private val STOCK_STATUS_ALIASES = setOf("marketstatus", "marketstate", "status", "状态", "市场状态")
private val STOCK_SYMBOL_ALIASES = setOf("symbol", "ticker", "代码", "股票代码")
private val STOCK_USED_LABELS = STOCK_PRICE_ALIASES +
    STOCK_CHANGE_ALIASES +
    STOCK_PERCENT_ALIASES +
    STOCK_CURRENCY_ALIASES +
    STOCK_EXCHANGE_ALIASES +
    STOCK_STATUS_ALIASES +
    STOCK_SYMBOL_ALIASES
private val STOCK_DETECTION_LABELS = setOf(
    "股价", "股票代码", "最新价", "现价", "涨跌幅", "regularmarketprice", "regularmarketchangepercent"
)

@Composable
fun MessageDataCards(message: ChatMessage) {
    if (
        message.contentBlocks.isEmpty() &&
        message.structuredData == null &&
        message.webSources.isEmpty()
    ) return

    var webPreviewSource by remember(message.id) { mutableStateOf<WebPreviewSource?>(null) }
    val onOpenUrl = remember(message.id) {
        { title: String, url: String ->
            webPreviewSource = WebPreviewSource(
                title = title.ifBlank { "链接" },
                url = url,
                domain = url.substringAfter("://").substringBefore('/'),
            )
        }
    }
    val onOpenSource = remember(message.id) {
        { source: WebPreviewSource -> webPreviewSource = source }
    }
    val onDismissPreview = remember(message.id) {
        { webPreviewSource = null }
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        MessageContentBlockList(
            blocks = message.contentBlocks,
            onOpenUrl = onOpenUrl,
        )
        message.structuredData?.let { data ->
            if (data.isChatStickerData()) MessageStickerV1(data) else StructuredDataCardView(data)
        }
        if (message.webSources.isNotEmpty()) {
            WebSourcesCard(
                sources = message.webSources,
                provider = message.searchProvider,
                onOpenSource = onOpenSource
            )
        }
    }

    InAppWebBrowserOverlay(
        target = webPreviewSource,
        onDismiss = onDismissPreview
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
    val meta = remember(quote.symbolLine, quote.timestamp) {
        joinNonBlank(quote.symbolLine, quote.timestamp, " · ")
    }

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

            MetricGrid(quote.secondaryMetrics)

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
    val meta = remember(data.subtitle, data.timestamp) {
        joinNonBlank(data.subtitle.orEmpty(), data.timestamp.orEmpty(), " · ")
    }
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

            MetricGrid(data.metrics)

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
private fun MetricGrid(metrics: List<StructuredMetric>) {
    var index = 0
    while (index < metrics.size) {
        val first = metrics[index]
        val second = metrics.getOrNull(index + 1)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            MetricPill(first, Modifier.weight(1f))
            if (second != null) {
                MetricPill(second, Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f))
            }
        }
        index += 2
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
    val previewCount = if (expanded) sources.size else minOf(2, sources.size)
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

            for (index in 0 until previewCount) {
                val source = sources[index]
                val onOpen = remember(source, index, onOpenSource) {
                    {
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
                }
                WebSourceRow(
                    index = index + 1,
                    source = source,
                    expanded = expanded,
                    onOpen = onOpen
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
    val meta = remember(source.domain, source.publishedAt) {
        joinNonBlank(source.domain, source.publishedAt.orEmpty(), " · ")
    }
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(metric.displayLabel, color = Color.White.copy(alpha = 0.44f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = metric.displayValue,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        metric.displayDetail?.let {
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

private data class NormalizedMetric(
    val metric: StructuredMetric,
    val key: String
)

private class StockMetricLookup(metrics: List<StructuredMetric>) {
    private val normalized = metrics.map { metric ->
        NormalizedMetric(metric = metric, key = normalizeMetricKey(metric.label))
    }

    fun value(aliases: Set<String>): String? {
        for (entry in normalized) {
            val key = entry.key
            if (key in aliases || aliases.any { alias -> key.contains(alias) || alias.contains(key) }) {
                val value = entry.metric.displayValue
                if (value.isNotBlank() && value != "--") return value
            }
        }
        return null
    }

    fun secondaryMetrics(limit: Int): List<StructuredMetric> {
        if (limit <= 0) return emptyList()
        val result = ArrayList<StructuredMetric>(minOf(limit, normalized.size))
        for (entry in normalized) {
            if (entry.key !in STOCK_USED_LABELS) {
                result += entry.metric
                if (result.size >= limit) break
            }
        }
        return result
    }
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
            val lookup = StockMetricLookup(data.metrics)
            val price = lookup.value(STOCK_PRICE_ALIASES)
            val change = lookup.value(STOCK_CHANGE_ALIASES)
            val percent = lookup.value(STOCK_PERCENT_ALIASES)
            val currency = lookup.value(STOCK_CURRENCY_ALIASES)
            val exchange = lookup.value(STOCK_EXCHANGE_ALIASES)
            val status = lookup.value(STOCK_STATUS_ALIASES)
            val symbol = lookup.value(STOCK_SYMBOL_ALIASES) ?: data.subtitle.orEmpty()
            val tone = StockChangeTone.from(change = change, percent = percent)
            val changeLine = joinNonBlank(change.orEmpty(), percent.orEmpty(), "  ").ifBlank { "--" }
            val priceLine = buildPriceLine(price = price, currency = currency)
            val symbolLine = joinDistinctNonBlank(symbol, exchange.orEmpty(), " · ")
            return StockQuoteUi(
                name = data.title.ifBlank { "股票行情" },
                symbolLine = symbolLine,
                timestamp = data.timestamp.orEmpty(),
                priceLine = priceLine,
                changeLine = changeLine,
                marketStatus = status.orEmpty(),
                changeTone = tone,
                secondaryMetrics = lookup.secondaryMetrics(limit = 6)
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
            val text = joinNonBlank(change.orEmpty(), percent.orEmpty(), " ").lowercase()
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
    return metrics.any { metric -> normalizeMetricKey(metric.label) in STOCK_DETECTION_LABELS }
}

private fun buildPriceLine(price: String?, currency: String?): String {
    val cleanPrice = price?.takeIf { it.isNotBlank() } ?: "--"
    val cleanCurrency = currency?.takeIf { it.isNotBlank() } ?: return cleanPrice
    return if (cleanPrice.contains(cleanCurrency, ignoreCase = true)) cleanPrice else "$cleanPrice $cleanCurrency"
}

private fun joinNonBlank(first: String, second: String, separator: String): String {
    val cleanFirst = first.takeIf { it.isNotBlank() }
    val cleanSecond = second.takeIf { it.isNotBlank() }
    return when {
        cleanFirst != null && cleanSecond != null -> "$cleanFirst$separator$cleanSecond"
        cleanFirst != null -> cleanFirst
        cleanSecond != null -> cleanSecond
        else -> ""
    }
}

private fun joinDistinctNonBlank(first: String, second: String, separator: String): String {
    val cleanFirst = first.trim()
    val cleanSecond = second.trim()
    return when {
        cleanFirst.isNotBlank() && cleanSecond.isNotBlank() && cleanFirst != cleanSecond -> "$cleanFirst$separator$cleanSecond"
        cleanFirst.isNotBlank() -> cleanFirst
        cleanSecond.isNotBlank() -> cleanSecond
        else -> ""
    }
}

private fun normalizeMetricKey(value: String): String = buildString(value.length) {
    value.lowercase().forEach { char ->
        when (char) {
            ' ', '_', '-', ':', '：', '%', '（', '）', '(', ')' -> Unit
            else -> append(char)
        }
    }
}

private fun typeLabel(type: String): String = when (type.lowercase()) {
    "stock" -> "股票"
    "weather" -> "天气"
    "exchange_rate", "rate", "currency" -> "汇率"
    "sports" -> "比赛"
    else -> "实时"
}
