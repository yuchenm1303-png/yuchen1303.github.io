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
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.StructuredDataCard
import com.yuchen.ailedger.model.StructuredMetric
import com.yuchen.ailedger.model.WebSource

@Composable
fun MessageDataCards(message: ChatMessage, state: AssistantUiState) {
    if (message.structuredData == null && message.webSources.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        message.structuredData?.let { StructuredDataCardView(it) }
        if (message.webSources.isNotEmpty()) {
            WebSourcesCard(message.webSources, message.searchProvider)
        }
    }

    state.quality.hashCode()
}

@Composable
private fun StructuredDataCardView(data: StructuredDataCard) {
    LightweightDataCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DataDot(Color(0xFF8DF9EA))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(data.title, color = Color.White.copy(alpha = 0.94f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(data.subtitle, data.timestamp).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(typeLabel(data.type), color = Color(0xFF8DF9EA).copy(alpha = 0.78f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }

            data.metrics.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { metric -> MetricPill(metric, Modifier.weight(1f)) }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }

            data.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
                Text(raw, color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WebSourcesCard(sources: List<WebSource>, provider: String?) {
    var expanded by remember(sources) { mutableStateOf(false) }
    val openWebSource = LocalWebSourceOpener.current
    val previewCount = if (expanded) sources.size else 2.coerceAtMost(sources.size)
    val hiddenCount = (sources.size - previewCount).coerceAtLeast(0)

    LightweightDataCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DataDot(Color(0xFF9FD8FF))
                Text("联网来源", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("${sources.size} 条", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                provider?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color(0xFF9FD8FF).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            openWebSource(
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
                Text(source.title.ifBlank { source.domain.ifBlank { "来源 $index" } }, color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(metric.label, color = Color.White.copy(alpha = 0.44f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = metric.value + (metric.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""),
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        metric.detail?.takeIf { it.isNotBlank() }?.let {
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

private fun typeLabel(type: String): String = when (type.lowercase()) {
    "stock" -> "股票"
    "weather" -> "天气"
    "exchange_rate", "rate", "currency" -> "汇率"
    "sports" -> "比赛"
    else -> "实时"
}
