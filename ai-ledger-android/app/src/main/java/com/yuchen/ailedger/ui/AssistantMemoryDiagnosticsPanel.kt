package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.data.AssistantMemoryDiagnosticItem
import com.yuchen.ailedger.data.AssistantMemoryDiagnosticRecord
import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import com.yuchen.ailedger.data.AssistantMemoryDiagnosticsState
import com.yuchen.ailedger.model.AssistantUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DIAGNOSTIC_VISIBLE_HISTORY = 12

/**
 * 设置页逐轮记忆排障面板。
 * 只绘制普通 Compose 卡片和 GlassRole.Chip，不接入 OpenGL registry 或 geometry sync。
 */
@Composable
internal fun AssistantMemoryDiagnosticsPanel(
    state: AssistantUiState,
    diagnosticsState: AssistantMemoryDiagnosticsState,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val records = diagnosticsState.records
    val latest = diagnosticsState.latest
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DiagnosticSurface {
            if (latest == null) {
                Text(
                    "发送一条普通聊天后，这里会保存这一轮从请求、候选召回、过滤、重排到最终注入的完整链路。",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "部署 V176 后端后会出现逐条候选追踪；旧后端仍可显示状态和候选数量。",
                    color = Color(0xFFFFD27A).copy(alpha = 0.64f),
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                LatestDiagnosticSummary(latest)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            DiagnosticAction(
                title = "复制最新诊断",
                subtitle = "直接粘贴给开发排障",
                state = state,
                enabled = latest != null,
                modifier = Modifier.weight(1f),
            ) {
                clipboard.setText(AnnotatedString(AssistantMemoryDiagnostics.latestReport()))
                Toast.makeText(context, "已复制最新一轮记忆诊断", Toast.LENGTH_SHORT).show()
            }
            DiagnosticAction(
                title = "复制全部诊断",
                subtitle = "最近 ${records.size} 轮完整报告",
                state = state,
                enabled = records.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                clipboard.setText(AnnotatedString(AssistantMemoryDiagnostics.fullReport()))
                Toast.makeText(context, "已复制全部记忆诊断", Toast.LENGTH_SHORT).show()
            }
        }

        if (records.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                DiagnosticAction(
                    title = if (showHistory) "收起逐轮记录" else "查看逐轮记录",
                    subtitle = "保留最近 ${records.size} 轮",
                    state = state,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                ) { showHistory = !showHistory }
                DiagnosticAction(
                    title = if (confirmClear) "确认清空诊断" else "清空诊断",
                    subtitle = if (confirmClear) "再次点击执行" else "不会删除长期记忆",
                    state = state,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                ) {
                    if (confirmClear) {
                        AssistantMemoryDiagnostics.clear()
                        confirmClear = false
                        showHistory = false
                        Toast.makeText(context, "逐轮诊断已清空", Toast.LENGTH_SHORT).show()
                    } else {
                        confirmClear = true
                    }
                }
            }
        }

        if (showHistory) {
            records.take(DIAGNOSTIC_VISIBLE_HISTORY).forEachIndexed { index, record ->
                DiagnosticHistoryCard(index + 1, record)
            }
            if (records.size > DIAGNOSTIC_VISIBLE_HISTORY) {
                Text(
                    "界面仅展开最近 $DIAGNOSTIC_VISIBLE_HISTORY 轮；“复制全部诊断”仍包含全部 ${records.size} 轮。",
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LatestDiagnosticSummary(record: AssistantMemoryDiagnosticRecord) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                record.prompt.ifBlank { "最近一轮聊天" },
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${diagnosticTime(record.createdAtMillis)} · ${record.backendVersion.ifBlank { "后端版本未返回" }}",
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DiagnosticStatusChip(record.statusLabel, record.memoryUsed, record.error.isNotBlank())
    }

    DiagnosticMetricRow(
        first = "Anchor" to record.anchorCandidateCount.toString(),
        second = "动态候选" to record.dynamicCandidateCount.toString(),
        third = "最终注入" to record.itemCount.toString(),
    )
    DiagnosticMetricRow(
        first = "Embedding" to record.embeddingStatus.ifBlank { "未返回" },
        second = "检索" to record.retrievalStatus.ifBlank { "未返回" },
        third = "重排" to record.rerankStatus.ifBlank { "未返回" },
    )

    Text(
        "请求 ${record.requestMode} · 状态 ${record.memoryStatus} · 总耗时 ${record.totalMs}ms",
        color = Color.White.copy(alpha = 0.48f),
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold,
    )
    if (!record.traceAvailable) {
        Text(
            "当前响应没有逐条候选 trace。部署 V176 后端后，可精确看到每条记忆在哪一步被找到或淘汰。",
            color = Color(0xFFFFD27A).copy(alpha = 0.72f),
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    if (record.error.isNotBlank()) {
        Text(
            "错误：${record.error}",
            color = Color(0xFFFFB4B4).copy(alpha = 0.88f),
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    if (record.selectedItems.isNotEmpty()) {
        Text(
            "实际注入：${record.selectedItems.joinToString("；") { it.content.ifBlank { it.id } }}",
            color = Color(0xFF8DF9EA).copy(alpha = 0.72f),
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DiagnosticHistoryCard(index: Int, record: AssistantMemoryDiagnosticRecord) {
    var expanded by rememberSaveable(record.requestId) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(Color.White.copy(alpha = 0.050f))
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { expanded = !expanded }
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "$index · ${record.prompt.ifBlank { "空请求" }}",
                    color = Color.White.copy(alpha = 0.84f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${diagnosticTime(record.createdAtMillis)} · ${record.statusLabel} · 候选 ${record.candidateCount}",
                    color = Color.White.copy(alpha = 0.36f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                if (expanded) "收起" else "详情",
                color = Color(0xFF8DF9EA).copy(alpha = 0.78f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }
        if (expanded) {
            DiagnosticDetailLine("请求 ID", record.requestId)
            DiagnosticDetailLine("请求模式", "${record.requestMode} / enabled=${record.requestEnabled}")
            DiagnosticDetailLine("状态", "${record.memoryStatus} / degraded=${record.degraded}")
            DiagnosticDetailLine("Gate", "${record.gateStatus} / ${record.budgetLevel}")
            DiagnosticDetailLine("Embedding", record.embeddingStatus)
            DiagnosticDetailLine("检索", record.retrievalStatus)
            DiagnosticDetailLine("重排", record.rerankStatus)
            DiagnosticDetailLine(
                "候选数",
                "anchor=${record.anchorCandidateCount}, dynamic=${record.dynamicCandidateCount}, afterFilter=${record.candidateCount}",
            )
            DiagnosticDetailLine("过滤", "history=${record.filteredHistoryCount}, sensitive=${record.filteredSensitiveCount}")
            DiagnosticDetailLine("耗时", "${record.totalMs}ms ${record.stageTimings.entries.joinToString { "${it.key}=${it.value}ms" }}")
            if (record.error.isNotBlank()) DiagnosticDetailLine("错误", record.error, error = true)
            DiagnosticCandidateGroup("动态候选", record.dynamicCandidates)
            DiagnosticCandidateGroup("最终选择", record.selectedItems)
        }
    }
}

@Composable
private fun DiagnosticCandidateGroup(title: String, items: List<AssistantMemoryDiagnosticItem>) {
    Text(
        "$title（${items.size}）",
        color = Color.White.copy(alpha = 0.66f),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Black,
    )
    if (items.isEmpty()) {
        Text("无", color = Color.White.copy(alpha = 0.30f), fontSize = 9.5.sp)
        return
    }
    items.take(8).forEach { item ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(Color.Black.copy(alpha = 0.10f))
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "${item.stage} · ${item.disposition.ifBlank { "unknown" }} · score=${item.retrievalScore}",
                color = if (item.disposition == "selected") {
                    Color(0xFF8DF9EA).copy(alpha = 0.76f)
                } else {
                    Color.White.copy(alpha = 0.42f)
                },
                fontSize = 8.8.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                item.content.ifBlank { item.id },
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            val reason = item.dispositionReason.ifBlank { item.selectionReason.ifBlank { item.retrievalReason } }
            if (reason.isNotBlank()) {
                Text(
                    reason,
                    color = Color.White.copy(alpha = 0.32f),
                    fontSize = 8.5.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticDetailLine(label: String, value: String, error: Boolean = false) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.36f),
            fontSize = 9.2.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.28f),
        )
        Text(
            value,
            color = if (error) Color(0xFFFFB4B4).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.64f),
            fontSize = 9.2.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.72f),
        )
    }
}

@Composable
private fun DiagnosticMetricRow(
    first: Pair<String, String>,
    second: Pair<String, String>,
    third: Pair<String, String>,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(first, second, third).forEach { value ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.045f))
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    value.first,
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    value.second,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticStatusChip(text: String, active: Boolean, error: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    error -> Color(0xFFFF8F8F).copy(alpha = 0.14f)
                    active -> Color(0xFF71EADD).copy(alpha = 0.14f)
                    else -> Color.White.copy(alpha = 0.055f)
                }
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            color = when {
                error -> Color(0xFFFFB4B4).copy(alpha = 0.88f)
                active -> Color(0xFF8DF9EA).copy(alpha = 0.86f)
                else -> Color.White.copy(alpha = 0.48f)
            },
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun DiagnosticSurface(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.070f),
                        Color.White.copy(alpha = 0.045f),
                    )
                )
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun DiagnosticAction(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (enabled) 1f else 0.68f,
        motionIntensity = 0f,
        radius = 22,
        modifier = modifier.height(58.dp),
        role = GlassRole.Chip,
        onClick = { if (enabled) onClick() },
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                color = Color.White.copy(alpha = if (enabled) 0.92f else 0.42f),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = if (enabled) 0.46f else 0.26f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun diagnosticTime(value: Long): String {
    if (value <= 0L) return "未知时间"
    return SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
}
