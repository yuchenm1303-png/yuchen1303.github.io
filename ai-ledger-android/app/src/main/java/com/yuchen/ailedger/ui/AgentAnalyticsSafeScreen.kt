package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.AgentAnalyticsViewModel
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentCapabilityAnalytics
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentModelAnalytics
import com.yuchen.ailedger.model.AgentSkillInventory
import com.yuchen.ailedger.model.AgentTaskAnalytics
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val SafeAnalyticsBlue = Color(0xFF8FB2FF)
private val SafeAnalyticsViolet = Color(0xFFB49BFF)
private val SafeAnalyticsMint = Color(0xFF7BE8D2)
private val SafeAnalyticsWarm = Color(0xFFFFC58A)
private val SafeAnalyticsDanger = Color(0xFFFF9EAF)
private val SafeAnalyticsCard = Color(0xFF11163A).copy(alpha = 0.74f)

private enum class SafeAnalyticsTab(val label: String) {
    Overview("总览"),
    Tokens("Token"),
    Tasks("任务"),
    Capabilities("能力"),
}

private data class SafeHeatmapCell(
    val column: Int,
    val row: Int,
    val tokens: Long,
    val future: Boolean,
)

private data class SafeHeatmapData(
    val weeks: Int,
    val cells: List<SafeHeatmapCell>,
    val maxTokens: Long,
    val activeDays: Int,
)

/**
 * 完全绕开 OpenGL、普通玻璃 registry 和 Frost 批处理的统计页。
 * 页面只绘制标准 Compose 背景、文字与 Canvas，作为稳定且极轻量的正式入口。
 */
@Composable
internal fun AgentAnalyticsSafeScreen(
    viewModel: AgentAnalyticsViewModel,
    onBack: () -> Unit,
) {
    val snapshot by viewModel.state.collectAsState()
    val skills by viewModel.skillInventory.collectAsState()
    var tabName by rememberSaveable { mutableStateOf(SafeAnalyticsTab.Overview.name) }
    val tab = remember(tabName) {
        SafeAnalyticsTab.entries.firstOrNull { it.name == tabName } ?: SafeAnalyticsTab.Overview
    }

    LaunchedEffect(tab) {
        if (tab == SafeAnalyticsTab.Capabilities) viewModel.ensureSkillInventoryLoaded()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "safe_header") {
            SafeAnalyticsHeader(onBack)
        }
        item(key = "safe_tabs") {
            SafeAnalyticsTabs(
                selected = tab,
                onSelected = { tabName = it.name },
            )
        }

        if (!snapshot.loaded) {
            item(key = "safe_loading") {
                SafeAnalyticsCard {
                    Text(
                        "正在读取统计",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "仅执行一次本地聚合查询，不持续观察数据库。",
                        color = Color.White.copy(alpha = 0.52f),
                        fontSize = 11.sp,
                    )
                }
            }
        } else {
            when (tab) {
                SafeAnalyticsTab.Overview -> {
                    item(key = "safe_summary") { SafeSummaryCard(snapshot) }
                    item(key = "safe_heatmap") { SafeTokenHeatmap(snapshot.dailyActivity) }
                    item(key = "safe_runtime") { SafeRuntimeCard(snapshot) }
                    item(key = "safe_recent_title") { SafeSectionTitle("最近任务", "本机最近执行摘要") }
                    if (snapshot.recentTasks.isEmpty()) {
                        item(key = "safe_recent_empty") { SafeEmptyCard("还没有智能体任务记录") }
                    } else {
                        items(snapshot.recentTasks.take(5), key = { "safe_overview_${it.taskId}" }) {
                            SafeTaskRow(it)
                        }
                    }
                }

                SafeAnalyticsTab.Tokens -> {
                    item(key = "safe_token_summary") { SafeTokenSummaryCard(snapshot) }
                    item(key = "safe_token_heatmap") { SafeTokenHeatmap(snapshot.dailyActivity) }
                    item(key = "safe_models_title") { SafeSectionTitle("模型使用", "按累计 Token 排序") }
                    if (snapshot.modelUsage.isEmpty()) {
                        item(key = "safe_models_empty") { SafeEmptyCard("暂无模型使用数据") }
                    } else {
                        items(snapshot.modelUsage.take(12), key = { "safe_model_${it.modelId}" }) {
                            SafeModelRow(it)
                        }
                    }
                }

                SafeAnalyticsTab.Tasks -> {
                    item(key = "safe_task_summary") { SafeTaskSummaryCard(snapshot) }
                    item(key = "safe_tasks_title") { SafeSectionTitle("任务记录", "最多显示最近 100 条") }
                    if (snapshot.recentTasks.isEmpty()) {
                        item(key = "safe_tasks_empty") { SafeEmptyCard("暂无任务记录") }
                    } else {
                        items(snapshot.recentTasks, key = { "safe_task_${it.taskId}" }) {
                            SafeTaskRow(it)
                        }
                    }
                }

                SafeAnalyticsTab.Capabilities -> {
                    item(key = "safe_skill") { SafeSkillCard(skills) }
                    item(key = "safe_capability_title") { SafeSectionTitle("能力使用", "工具、功能、动作与应用") }
                    if (snapshot.capabilityUsage.isEmpty()) {
                        item(key = "safe_capability_empty") { SafeEmptyCard("暂无能力使用数据") }
                    } else {
                        items(
                            snapshot.capabilityUsage.take(40),
                            key = { "safe_capability_${it.kind}_${it.key}" },
                        ) {
                            SafeCapabilityRow(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeAnalyticsHeader(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .width(92.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "‹ 返回",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "AGENT INSIGHTS",
                color = SafeAnalyticsMint.copy(alpha = 0.78f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "智能体统计",
                color = Color.White,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Token 活动、任务效率、自主性与能力成长",
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SafeAnalyticsTabs(
    selected: SafeAnalyticsTab,
    onSelected: (SafeAnalyticsTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SafeAnalyticsTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (active) SafeAnalyticsViolet.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.055f),
                    )
                    .border(
                        1.dp,
                        if (active) SafeAnalyticsViolet.copy(alpha = 0.30f)
                        else Color.White.copy(alpha = 0.07f),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = !active) { onSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    color = Color.White.copy(alpha = if (active) 0.94f else 0.52f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun SafeSummaryCard(snapshot: AgentAnalyticsSnapshot) {
    SafeAnalyticsCard(
        brush = Brush.linearGradient(
            listOf(
                Color(0xFF202D70).copy(alpha = 0.72f),
                Color(0xFF211B58).copy(alpha = 0.62f),
                Color(0xFF0E3851).copy(alpha = 0.54f),
            ),
        ),
    ) {
        Text(
            compactSafeNumber(snapshot.totals.totalTokens),
            color = Color.White,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            "累计 Token",
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        SafeMetricLine("活跃连续", "${snapshot.totals.currentActiveStreakDays} 天", SafeAnalyticsMint)
        SafeMetricLine("完成任务", snapshot.totals.completedTasks.toString(), SafeAnalyticsBlue)
        SafeMetricLine("自主完成率", safePercent(snapshot.totals.autonomousCompletionRate), SafeAnalyticsViolet)
        SafeMetricLine("执行动作", compactSafeNumber(snapshot.totals.executedActions), SafeAnalyticsWarm)
    }
}

@Composable
private fun SafeRuntimeCard(snapshot: AgentAnalyticsSnapshot) {
    SafeAnalyticsCard {
        SafeCardTitle("运行概览", "本地与账号每日聚合")
        Spacer(Modifier.height(10.dp))
        SafeMetricLine("对话请求", compactSafeNumber(snapshot.totals.chatCalls), SafeAnalyticsBlue)
        SafeMetricLine("模型调用", compactSafeNumber(snapshot.totals.modelCalls), SafeAnalyticsViolet)
        SafeMetricLine("规划轮次", compactSafeNumber(snapshot.totals.agentModelTurns), SafeAnalyticsMint)
        SafeMetricLine("任务总数", compactSafeNumber(snapshot.totals.agentTasks), SafeAnalyticsWarm)
        SafeMetricLine("累计任务时长", safeDuration(snapshot.totals.totalTaskDurationMs), SafeAnalyticsBlue)
        SafeMetricLine("最长任务", safeDuration(snapshot.totals.longestTaskDurationMs), SafeAnalyticsViolet)
    }
}

@Composable
private fun SafeTokenSummaryCard(snapshot: AgentAnalyticsSnapshot) {
    SafeAnalyticsCard {
        SafeCardTitle("Token 构成", "真实 usage 与本地估算分开显示")
        Spacer(Modifier.height(10.dp))
        SafeMetricLine("Provider 真实", compactSafeNumber(snapshot.totals.providerTokens), SafeAnalyticsMint)
        SafeMetricLine("Estimated 估算", compactSafeNumber(snapshot.totals.estimatedTokens), SafeAnalyticsViolet)
        SafeMetricLine("峰值日", compactSafeNumber(snapshot.totals.peakDailyTokens), SafeAnalyticsWarm)
        SafeMetricLine("累计总量", compactSafeNumber(snapshot.totals.totalTokens), SafeAnalyticsBlue)
    }
}

@Composable
private fun SafeTaskSummaryCard(snapshot: AgentAnalyticsSnapshot) {
    SafeAnalyticsCard {
        SafeCardTitle("任务效率", "成功率、自主性与耗时")
        Spacer(Modifier.height(10.dp))
        SafeMetricLine("任务成功率", safePercent(snapshot.totals.taskSuccessRate), SafeAnalyticsMint)
        SafeMetricLine("自主完成率", safePercent(snapshot.totals.autonomousCompletionRate), SafeAnalyticsBlue)
        SafeMetricLine("完成任务", snapshot.totals.completedTasks.toString(), SafeAnalyticsViolet)
        SafeMetricLine("介入后完成", snapshot.totals.assistedCompletedTasks.toString(), SafeAnalyticsWarm)
        SafeMetricLine("累计耗时", safeDuration(snapshot.totals.totalTaskDurationMs), SafeAnalyticsBlue)
    }
}

@Composable
private fun SafeSkillCard(skills: AgentSkillInventory) {
    SafeAnalyticsCard {
        SafeCardTitle("Skill 资产", "操作学习形成的本机可复用能力")
        Spacer(Modifier.height(10.dp))
        SafeMetricLine("全部 Skill", skills.totalSkills.toString(), SafeAnalyticsBlue)
        SafeMetricLine("可用", skills.usableSkills.toString(), SafeAnalyticsMint)
        SafeMetricLine("待审核", skills.reviewSkills.toString(), SafeAnalyticsWarm)
        SafeMetricLine("覆盖应用", skills.scopedApps.toString(), SafeAnalyticsViolet)
        SafeMetricLine("运行次数", skills.totalRuns.toString(), SafeAnalyticsBlue)
        SafeMetricLine("成功运行", skills.successfulRuns.toString(), SafeAnalyticsMint)
    }
}

@Composable
private fun SafeTokenHeatmap(daily: List<AgentDailyActivity>) {
    val heatmap = remember(daily) { buildSafeHeatmap(daily, 12) }
    val cellSize = 11.dp
    val gap = 3.dp
    val chartWidth = (cellSize + gap) * heatmap.weeks - gap
    val chartHeight = (cellSize + gap) * 7 - gap

    SafeAnalyticsCard {
        SafeCardTitle(
            "Token 活动热力图",
            "${heatmap.weeks} 周 · ${heatmap.activeDays} 个活跃日 · 每格一天",
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(chartWidth).height(chartHeight)) {
                val cellPx = cellSize.toPx()
                val gapPx = gap.toPx()
                val maxValue = heatmap.maxTokens.coerceAtLeast(1L).toFloat()
                heatmap.cells.forEach { cell ->
                    val ratio = (cell.tokens.toFloat() / maxValue).coerceIn(0f, 1f)
                    val color = when {
                        cell.future -> Color.White.copy(alpha = 0.025f)
                        cell.tokens <= 0L -> Color.White.copy(alpha = 0.065f)
                        ratio < 0.2f -> SafeAnalyticsBlue.copy(alpha = 0.30f)
                        ratio < 0.45f -> SafeAnalyticsBlue.copy(alpha = 0.54f)
                        ratio < 0.72f -> SafeAnalyticsViolet.copy(alpha = 0.74f)
                        else -> SafeAnalyticsMint.copy(alpha = 0.94f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(
                            cell.column * (cellPx + gapPx),
                            cell.row * (cellPx + gapPx),
                        ),
                        size = Size(cellPx, cellPx),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun SafeTaskRow(task: AgentTaskAnalytics) {
    SafeAnalyticsCard {
        Text(
            task.goal.ifBlank { "未命名智能体任务" },
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            safeStatusLabel(task.status),
            color = safeStatusTone(task.status),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        SafeMetricLine("耗时", safeDuration(task.durationMs), SafeAnalyticsBlue)
        SafeMetricLine("动作", task.executedActions.toString(), SafeAnalyticsMint)
        SafeMetricLine("Token", compactSafeNumber(task.totalTokens), SafeAnalyticsViolet)
        SafeMetricLine("用户介入", task.interventionCount.toString(), SafeAnalyticsWarm)
    }
}

@Composable
private fun SafeModelRow(model: AgentModelAnalytics) {
    SafeAnalyticsCard {
        Text(
            model.displayName,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        SafeMetricLine("调用次数", model.calls.toString(), SafeAnalyticsBlue)
        SafeMetricLine("累计 Token", compactSafeNumber(model.totalTokens), SafeAnalyticsViolet)
        val successRate = if (model.calls > 0L) {
            (model.calls - model.failures).coerceAtLeast(0L).toFloat() / model.calls.toFloat()
        } else {
            0f
        }
        SafeMetricLine("调用成功率", safePercent(successRate), SafeAnalyticsMint)
    }
}

@Composable
private fun SafeCapabilityRow(capability: AgentCapabilityAnalytics) {
    SafeAnalyticsCard {
        Text(
            capability.displayName,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        SafeMetricLine("类型", safeCapabilityKind(capability.kind), safeCapabilityTone(capability.kind))
        SafeMetricLine("使用次数", capability.uses.toString(), SafeAnalyticsBlue)
        val resultTotal = capability.successes + capability.failures
        val rate = if (resultTotal > 0L) capability.successes.toFloat() / resultTotal.toFloat() else 0f
        SafeMetricLine("成功率", if (resultTotal > 0L) safePercent(rate) else "累计", SafeAnalyticsMint)
    }
}

@Composable
private fun SafeAnalyticsCard(
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    content: @Composable Column.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (brush != null) Modifier.background(brush)
                else Modifier.background(SafeAnalyticsCard),
            )
            .border(1.dp, Color.White.copy(alpha = 0.075f), shape)
            .padding(horizontal = 17.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        content = content,
    )
}

@Composable
private fun SafeCardTitle(title: String, subtitle: String) {
    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(2.dp))
    Text(
        subtitle,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
    )
}

@Composable
private fun SafeMetricLine(label: String, value: String, tone: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp)
        Text(value, color = tone, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SafeSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp)
    }
}

@Composable
private fun SafeEmptyCard(message: String) {
    SafeAnalyticsCard {
        Text(message, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
    }
}

private fun buildSafeHeatmap(daily: List<AgentDailyActivity>, requestedWeeks: Int): SafeHeatmapData {
    val weeks = requestedWeeks.coerceIn(1, 52)
    val today = LocalDate.now(ZoneId.systemDefault())
    val endSunday = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val startMonday = endSunday.minusDays(weeks * 7L - 1L)
    val values = daily.associate { it.dateKey to it.totalTokens.coerceAtLeast(0L) }
    val cells = ArrayList<SafeHeatmapCell>(weeks * 7)
    var maxTokens = 0L
    var activeDays = 0

    repeat(weeks * 7) { index ->
        val date = startMonday.plusDays(index.toLong())
        val value = values[date.toString()] ?: 0L
        val future = date.isAfter(today)
        if (!future) {
            maxTokens = maxOf(maxTokens, value)
            if (value > 0L) activeDays += 1
        }
        cells += SafeHeatmapCell(index / 7, index % 7, value, future)
    }
    return SafeHeatmapData(weeks, cells, maxTokens, activeDays)
}

private fun compactSafeNumber(value: Long): String {
    val safe = value.coerceAtLeast(0L)
    return when {
        safe >= 100_000_000L -> safeOneDecimal(safe / 100_000_000.0) + " 亿"
        safe >= 10_000L -> safeOneDecimal(safe / 10_000.0) + " 万"
        else -> safe.toString()
    }
}

private fun safeOneDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun safePercent(value: Float): String =
    "${(value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f).times(100f).roundToInt()}%"

private fun safeDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return when {
        seconds >= 3_600L -> "${seconds / 3_600L}h ${(seconds % 3_600L) / 60L}m"
        seconds >= 60L -> "${seconds / 60L}m ${seconds % 60L}s"
        else -> "${seconds}s"
    }
}

private fun safeStatusLabel(status: String): String = when (status.lowercase()) {
    "completed" -> "已完成"
    "failed" -> "失败"
    "paused" -> "已暂停"
    "cancelled", "canceled" -> "已停止"
    "budget_exceeded" -> "达到上限"
    "interrupted" -> "意外中断"
    "running" -> "执行中"
    else -> status.ifBlank { "未知" }
}

private fun safeStatusTone(status: String): Color = when (status.lowercase()) {
    "completed" -> SafeAnalyticsMint
    "running" -> SafeAnalyticsBlue
    "paused", "interrupted" -> SafeAnalyticsWarm
    else -> SafeAnalyticsDanger
}

private fun safeCapabilityKind(kind: String): String = when (kind.lowercase()) {
    "feature" -> "功能"
    "tool" -> "工具"
    "action" -> "动作"
    "app" -> "应用"
    else -> "能力"
}

private fun safeCapabilityTone(kind: String): Color = when (kind.lowercase()) {
    "feature" -> SafeAnalyticsMint
    "tool" -> SafeAnalyticsBlue
    "action" -> SafeAnalyticsViolet
    "app" -> SafeAnalyticsWarm
    else -> Color.White
}
