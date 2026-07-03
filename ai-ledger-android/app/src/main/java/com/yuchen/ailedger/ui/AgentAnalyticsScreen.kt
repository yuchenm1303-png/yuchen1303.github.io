package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.AgentAnalyticsViewModel
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentCapabilityAnalytics
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentModelAnalytics
import com.yuchen.ailedger.model.AgentSkillInventory
import com.yuchen.ailedger.model.AgentTaskAnalytics
import com.yuchen.ailedger.model.AssistantUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val AnalyticsMint = Color(0xFF7BE8D2)
private val AnalyticsBlue = Color(0xFF8FB2FF)
private val AnalyticsViolet = Color(0xFFB49BFF)
private val AnalyticsWarm = Color(0xFFFFC58A)
private val AnalyticsDanger = Color(0xFFFF9EAF)

private enum class AnalyticsTab(val label: String) {
    Overview("总览"),
    Tokens("Token"),
    Tasks("任务"),
    Capabilities("能力"),
}

private enum class AnalyticsRange(
    val label: String,
    val days: Long?,
    val heatmapWeeks: Int,
) {
    Weeks12("12 周", 84L, 12),
    HalfYear("半年", 183L, 26),
    Year("一年", 365L, 52),
    All("全部", null, 52),
}

@Immutable
private data class PeriodMetrics(
    val daily: List<AgentDailyActivity>,
    val tasks: List<AgentTaskAnalytics>,
    val totalTokens: Long,
    val providerTokens: Long,
    val estimatedTokens: Long,
    val peakDailyTokens: Long,
    val activeDays: Int,
    val chatCalls: Long,
    val modelCalls: Long,
    val modelFailures: Long,
    val completedTasks: Long,
    val autonomousCompletedTasks: Long,
    val assistedCompletedTasks: Long,
    val failedTasks: Long,
    val pausedTasks: Long,
    val cancelledTasks: Long,
    val budgetExceededTasks: Long,
    val agentModelTurns: Long,
    val executedActions: Long,
    val successfulActions: Long,
    val failedActions: Long,
    val reobservations: Long,
    val rejectedPlans: Long,
    val executionFailures: Long,
    val confirmationRequests: Long,
    val userInputRequests: Long,
    val userTakeovers: Long,
    val taskDurationMs: Long,
) {
    val terminalTasks: Long
        get() = completedTasks + failedTasks + pausedTasks + cancelledTasks + budgetExceededTasks

    val taskSuccessRate: Float
        get() = if (terminalTasks > 0L) completedTasks.toFloat() / terminalTasks.toFloat() else 0f

    val autonomousRate: Float
        get() = if (completedTasks > 0L) autonomousCompletedTasks.toFloat() / completedTasks.toFloat() else 0f

    val modelSuccessRate: Float
        get() = if (modelCalls > 0L) {
            (modelCalls - modelFailures).coerceAtLeast(0L).toFloat() / modelCalls.toFloat()
        } else {
            0f
        }

    val actionSuccessRate: Float
        get() {
            val total = successfulActions + failedActions
            return if (total > 0L) successfulActions.toFloat() / total.toFloat() else 0f
        }

    val interventionCount: Long
        get() = confirmationRequests + userInputRequests + userTakeovers
}

@Immutable
private data class HeatmapCell(
    val column: Int,
    val row: Int,
    val tokens: Long,
    val future: Boolean,
)

@Immutable
private data class HeatmapData(
    val weeks: Int,
    val cells: List<HeatmapCell>,
    val maxTokens: Long,
    val activeDays: Int,
)

@Composable
fun AgentAnalyticsScreen(
    appState: AssistantUiState,
    viewModel: AgentAnalyticsViewModel,
    onBack: () -> Unit,
) {
    val snapshot by viewModel.state.collectAsState()
    val skills by viewModel.skillInventory.collectAsState()
    var tabName by rememberSaveable { mutableStateOf(AnalyticsTab.Overview.name) }
    var rangeName by rememberSaveable { mutableStateOf(AnalyticsRange.Weeks12.name) }
    val tab = remember(tabName) {
        AnalyticsTab.entries.firstOrNull { it.name == tabName } ?: AnalyticsTab.Overview
    }
    val range = remember(rangeName) {
        AnalyticsRange.entries.firstOrNull { it.name == rangeName } ?: AnalyticsRange.Weeks12
    }
    val metrics = remember(snapshot, range) { buildPeriodMetrics(snapshot, range) }

    LaunchedEffect(tab) {
        if (tab == AnalyticsTab.Capabilities) viewModel.ensureSkillInventoryLoaded()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { AnalyticsHeader(appState, onBack) }
        item(key = "range") {
            SegmentedControl(
                options = AnalyticsRange.entries.map { it.name to it.label },
                selectedKey = range.name,
                onSelected = { rangeName = it },
            )
        }
        item(key = "hero") {
            AnalyticsHero(
                metrics = metrics,
                currentStreak = snapshot.totals.currentActiveStreakDays,
                rangeLabel = range.label,
            )
        }
        item(key = "tabs") {
            SegmentedControl(
                options = AnalyticsTab.entries.map { it.name to it.label },
                selectedKey = tab.name,
                onSelected = { tabName = it },
            )
        }

        if (!snapshot.loaded) {
            item(key = "loading") {
                MessageCard(
                    title = "正在准备统计",
                    message = "只在进入本页后读取本地聚合数据，不扫描聊天记录，也不会额外截图。",
                )
            }
        } else {
            when (tab) {
                AnalyticsTab.Overview -> overviewItems(metrics, snapshot, range.heatmapWeeks)
                AnalyticsTab.Tokens -> tokenItems(metrics, snapshot, range.heatmapWeeks)
                AnalyticsTab.Tasks -> taskItems(metrics, snapshot)
                AnalyticsTab.Capabilities -> capabilityItems(snapshot, skills)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.overviewItems(
    metrics: PeriodMetrics,
    snapshot: AgentAnalyticsSnapshot,
    heatmapWeeks: Int,
) {
    item(key = "overview_heatmap") {
        TokenHeatmapCard(snapshot.dailyActivity, heatmapWeeks)
    }
    item(key = "overview_runtime") { RuntimeOverviewCard(metrics) }
    item(key = "overview_insights") {
        InsightsCard(metrics, snapshot.totals.longestTaskDurationMs)
    }
    item(key = "overview_task_title") {
        SectionTitle("最近任务", "最近收口的智能体执行记录")
    }
    if (metrics.tasks.isEmpty()) {
        item(key = "overview_empty") {
            MessageCard("还没有任务记录", "完成一次 GUI Plus 任务后，这里会显示耗时、动作和介入情况。")
        }
    } else {
        items(metrics.tasks.take(3), key = { "overview_${it.taskId}" }) { TaskRow(it) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.tokenItems(
    metrics: PeriodMetrics,
    snapshot: AgentAnalyticsSnapshot,
    heatmapWeeks: Int,
) {
    item(key = "tokens_heatmap") { TokenHeatmapCard(snapshot.dailyActivity, heatmapWeeks) }
    item(key = "tokens_breakdown") { TokenBreakdownCard(metrics) }
    item(key = "models_title") { SectionTitle("模型使用", "累计数据 · 按 Token 总量排序") }
    if (snapshot.modelUsage.isEmpty()) {
        item(key = "models_empty") { MessageCard("暂无模型数据", "下一次模型请求完成后会开始记录。") }
    } else {
        items(snapshot.modelUsage.take(6), key = { "model_${it.modelId}" }) { ModelRow(it) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.taskItems(
    metrics: PeriodMetrics,
    snapshot: AgentAnalyticsSnapshot,
) {
    item(key = "task_performance") {
        TaskPerformanceCard(metrics, snapshot.totals.longestTaskDurationMs)
    }
    item(key = "tasks_title") { SectionTitle("任务记录", "按开始时间倒序，仅加载最近 100 条摘要") }
    if (metrics.tasks.isEmpty()) {
        item(key = "tasks_empty") {
            MessageCard("当前范围没有任务", "切换时间范围，或完成一次智能体任务后再查看。")
        }
    } else {
        items(metrics.tasks.take(20), key = { "task_${it.taskId}" }) { TaskRow(it) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.capabilityItems(
    snapshot: AgentAnalyticsSnapshot,
    skills: AgentSkillInventory,
) {
    item(key = "skills") { SkillInventoryCard(skills) }
    item(key = "capability_title") { SectionTitle("能力使用", "累计工具、功能、动作与应用调用") }
    if (snapshot.capabilityUsage.isEmpty()) {
        item(key = "capability_empty") {
            MessageCard("暂无能力数据", "联网、图片理解、设备动作和已验证应用会在使用后出现。")
        }
    } else {
        items(snapshot.capabilityUsage.take(20), key = { "cap_${it.kind}_${it.key}" }) {
            CapabilityRow(it)
        }
    }
}

@Composable
private fun AnalyticsHeader(appState: AssistantUiState, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(
            quality = appState.quality,
            glassIntensity = appState.glassIntensity,
            motionIntensity = appState.motionIntensity,
            radius = 999,
            modifier = Modifier.width(92.dp).height(40.dp),
            role = GlassRole.Chip,
            onClick = onBack,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "‹ 返回",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "AGENT INSIGHTS",
                color = AnalyticsMint.copy(alpha = 0.76f),
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
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SegmentedControl(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101640).copy(alpha = 0.34f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val selected = key == selectedKey
            val brush = if (selected) {
                Brush.horizontalGradient(
                    listOf(
                        AnalyticsBlue.copy(alpha = 0.24f),
                        AnalyticsViolet.copy(alpha = 0.20f),
                    ),
                )
            } else {
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brush)
                    .clickable(enabled = !selected) { onSelected(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = Color.White.copy(alpha = if (selected) 0.94f else 0.48f),
                    fontSize = 11.5.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AnalyticsHero(
    metrics: PeriodMetrics,
    currentStreak: Int,
    rangeLabel: String,
) {
    FrostInfoGlassPanel(
        radius = 20f,
        backdropAlpha = 1f,
        frostAlpha = 0.09f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(224.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF202D70).copy(alpha = 0.52f),
                            Color(0xFF211B58).copy(alpha = 0.40f),
                            Color(0xFF0E3851).copy(alpha = 0.32f),
                        ),
                    ),
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = AnalyticsViolet.copy(alpha = 0.09f),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width * 0.88f, size.height * 0.08f),
                )
                drawCircle(
                    color = AnalyticsMint.copy(alpha = 0.07f),
                    radius = size.minDimension * 0.34f,
                    center = Offset(size.width * 0.08f, size.height * 0.94f),
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 19.dp, vertical = 17.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "$rangeLabel 活动",
                        color = AnalyticsMint.copy(alpha = 0.76f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        compactNumber(metrics.totalTokens),
                        color = Color.White,
                        fontSize = 42.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "累计 Token",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroMetric("活跃天数", metrics.activeDays.toString(), Modifier.weight(1f))
                    HeroMetric("完成任务", metrics.completedTasks.toString(), Modifier.weight(1f))
                    HeroMetric("自主完成", percent(metrics.autonomousRate), Modifier.weight(1f))
                    HeroMetric("连续活跃", "$currentStreak 天", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 9.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 8.5.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TokenHeatmapCard(daily: List<AgentDailyActivity>, weeks: Int) {
    val heatmap = remember(daily, weeks) { buildHeatmap(daily, weeks) }
    val scrollState = rememberScrollState()
    val cellSize = 11.dp
    val gap = 3.dp
    val chartWidth = (cellSize + gap) * heatmap.weeks - gap
    val chartHeight = (cellSize + gap) * 7 - gap

    LaunchedEffect(scrollState.maxValue, heatmap.weeks) {
        if (scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
    }

    FrostCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Token 活动热力图", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(
                    "${heatmap.weeks} 周 · ${heatmap.activeDays} 个活跃日 · 每格一天",
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            HeatmapLegend()
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.width(18.dp).height(chartHeight),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("一", "", "三", "", "五", "", "日").forEach { label ->
                    Text(label, color = Color.White.copy(alpha = 0.34f), fontSize = 8.sp, lineHeight = 11.sp)
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(scrollState)) {
                Canvas(Modifier.width(chartWidth).height(chartHeight)) {
                    val cellPx = cellSize.toPx()
                    val gapPx = gap.toPx()
                    val maxValue = heatmap.maxTokens.coerceAtLeast(1L).toFloat()
                    heatmap.cells.forEach { cell ->
                        val ratio = (cell.tokens.toFloat() / maxValue).coerceIn(0f, 1f)
                        val color = when {
                            cell.future -> Color.White.copy(alpha = 0.025f)
                            cell.tokens <= 0L -> Color.White.copy(alpha = 0.065f)
                            ratio < 0.2f -> AnalyticsBlue.copy(alpha = 0.28f)
                            ratio < 0.45f -> AnalyticsBlue.copy(alpha = 0.52f)
                            ratio < 0.72f -> AnalyticsViolet.copy(alpha = 0.72f)
                            else -> AnalyticsMint.copy(alpha = 0.94f)
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
        if (heatmap.maxTokens <= 0L) {
            Spacer(Modifier.height(10.dp))
            Text(
                "从下一次模型调用开始积累，旧版本历史不会被推测补写。",
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun HeatmapLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("少", color = Color.White.copy(alpha = 0.34f), fontSize = 8.sp)
        listOf(
            Color.White.copy(alpha = 0.07f),
            AnalyticsBlue.copy(alpha = 0.28f),
            AnalyticsBlue.copy(alpha = 0.52f),
            AnalyticsViolet.copy(alpha = 0.72f),
            AnalyticsMint.copy(alpha = 0.94f),
        ).forEach { color ->
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
        Text("多", color = Color.White.copy(alpha = 0.34f), fontSize = 8.sp)
    }
}

@Composable
private fun RuntimeOverviewCard(metrics: PeriodMetrics) {
    FrostCard {
        CardTitle("运行概览", "选定时间范围内的真实聚合")
        Spacer(Modifier.height(13.dp))
        MetricGrid(
            listOf(
                Triple("对话", compactNumber(metrics.chatCalls), AnalyticsBlue),
                Triple("模型调用", compactNumber(metrics.modelCalls), AnalyticsViolet),
                Triple("规划轮次", compactNumber(metrics.agentModelTurns), AnalyticsMint),
                Triple("执行动作", compactNumber(metrics.executedActions), AnalyticsWarm),
                Triple("重新观察", compactNumber(metrics.reobservations), AnalyticsBlue),
                Triple("用户介入", compactNumber(metrics.interventionCount), AnalyticsViolet),
            ),
        )
    }
}

@Composable
private fun InsightsCard(metrics: PeriodMetrics, longestTaskDurationMs: Long) {
    val insights = remember(metrics, longestTaskDurationMs) { buildInsights(metrics, longestTaskDurationMs) }
    FrostCard {
        CardTitle("活动洞察", "只根据已记录事实生成，不做能力猜测")
        Spacer(Modifier.height(10.dp))
        insights.forEachIndexed { index, text ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .size(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(listOf(AnalyticsMint, AnalyticsBlue, AnalyticsViolet)[index % 3]),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun TokenBreakdownCard(metrics: PeriodMetrics) {
    val total = metrics.totalTokens.coerceAtLeast(1L).toFloat()
    FrostCard {
        CardTitle("Token 构成", "真实 usage 与本地保守估算分开显示")
        Spacer(Modifier.height(14.dp))
        ProgressMetric(
            "Provider 真实 Token",
            metrics.providerTokens,
            (metrics.providerTokens.toFloat() / total).coerceIn(0f, 1f),
            AnalyticsMint,
        )
        Spacer(Modifier.height(12.dp))
        ProgressMetric(
            "Estimated 估算 Token",
            metrics.estimatedTokens,
            (metrics.estimatedTokens.toFloat() / total).coerceIn(0f, 1f),
            AnalyticsViolet,
        )
        Spacer(Modifier.height(14.dp))
        MetricGrid(
            listOf(
                Triple("峰值日", compactNumber(metrics.peakDailyTokens), AnalyticsWarm),
                Triple("活跃日", metrics.activeDays.toString(), AnalyticsBlue),
                Triple("调用成功", percent(metrics.modelSuccessRate), AnalyticsMint),
            ),
        )
    }
}

@Composable
private fun ProgressMetric(label: String, value: Long, progress: Float, tone: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                label,
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(compactNumber(value), color = tone, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.07f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(tone.copy(alpha = 0.72f)),
            )
        }
    }
}

@Composable
private fun TaskPerformanceCard(metrics: PeriodMetrics, longestTaskDurationMs: Long) {
    FrostCard {
        CardTitle("任务效率", "成功、自主性与恢复情况")
        Spacer(Modifier.height(13.dp))
        MetricGrid(
            listOf(
                Triple("成功率", percent(metrics.taskSuccessRate), AnalyticsMint),
                Triple("自主完成", percent(metrics.autonomousRate), AnalyticsBlue),
                Triple("动作成功", percent(metrics.actionSuccessRate), AnalyticsViolet),
            ),
        )
        Spacer(Modifier.height(10.dp))
        MetricPair("完成任务", metrics.completedTasks.toString(), "介入后完成", metrics.assistedCompletedTasks.toString())
        MetricPair("执行失败", metrics.executionFailures.toString(), "计划拒绝", metrics.rejectedPlans.toString())
        MetricPair("累计耗时", duration(metrics.taskDurationMs), "最长任务", duration(longestTaskDurationMs))
    }
}

@Composable
private fun SkillInventoryCard(skills: AgentSkillInventory) {
    FrostCard {
        CardTitle("Skill 资产", "操作学习形成的长期可复用能力")
        Spacer(Modifier.height(13.dp))
        MetricGrid(
            listOf(
                Triple("全部 Skill", skills.totalSkills.toString(), AnalyticsBlue),
                Triple("可用", skills.usableSkills.toString(), AnalyticsMint),
                Triple("待审核", skills.reviewSkills.toString(), AnalyticsWarm),
            ),
        )
        Spacer(Modifier.height(10.dp))
        MetricPair("已验证", skills.verifiedSkills.toString(), "已批准", skills.approvedSkills.toString())
        MetricPair("演示次数", skills.demonstrations.toString(), "覆盖应用", skills.scopedApps.toString())
        MetricPair("运行次数", skills.totalRuns.toString(), "成功运行", skills.successfulRuns.toString())
    }
}

@Composable
private fun ModelRow(model: AgentModelAnalytics) {
    FrostCard(14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Badge(model.displayName.take(1).uppercase(), AnalyticsViolet)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    model.displayName,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${model.calls} 次调用 · ${compactNumber(model.totalTokens)} Token",
                    color = Color.White.copy(alpha = 0.44f),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            val successRate = if (model.calls > 0L) {
                (model.calls - model.failures).coerceAtLeast(0L).toFloat() / model.calls.toFloat()
            } else {
                0f
            }
            Text(percent(successRate), color = AnalyticsMint, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CapabilityRow(capability: AgentCapabilityAnalytics) {
    val total = capability.successes + capability.failures
    val successRate = if (total > 0L) capability.successes.toFloat() / total.toFloat() else 0f
    val tone = capabilityTone(capability.kind)
    FrostCard(14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Badge(capabilitySymbol(capability.kind), tone)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    capability.displayName,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${capabilityKind(capability.kind)} · ${capability.uses} 次使用",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            Text(
                if (total > 0L) percent(successRate) else "累计",
                color = tone,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun TaskRow(task: AgentTaskAnalytics) {
    val tone = statusTone(task.status)
    FrostCard(14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        task.goal.ifBlank { "未命名智能体任务" },
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        taskTime(task.startedAtMillis),
                        color = Color.White.copy(alpha = 0.36f),
                        fontSize = 9.5.sp,
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(tone.copy(alpha = 0.13f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(statusLabel(task.status), color = tone, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinyMetric("耗时", duration(task.durationMs), Modifier.weight(1f))
                TinyMetric("动作", task.executedActions.toString(), Modifier.weight(1f))
                TinyMetric("Token", compactNumber(task.totalTokens), Modifier.weight(1f))
                TinyMetric("介入", task.interventionCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FrostCard(
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.082f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF111642).copy(alpha = 0.24f))
                .padding(padding),
            content = content,
        )
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.43f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp)
    }
}

@Composable
private fun MessageCard(title: String, message: String) {
    FrostCard {
        Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text(message, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun MetricGrid(values: List<Triple<String, String, Color>>) {
    values.chunked(3).forEachIndexed { index, rowValues ->
        if (index > 0) Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowValues.forEach { (label, value, tone) ->
                CompactMetric(label, value, tone, Modifier.weight(1f))
            }
            repeat(3 - rowValues.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun CompactMetric(label: String, value: String, tone: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(tone.copy(alpha = 0.085f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value,
            color = tone,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, color = Color.White.copy(alpha = 0.40f), fontSize = 8.5.sp, maxLines = 1)
    }
}

@Composable
private fun MetricPair(leftLabel: String, leftValue: String, rightLabel: String, rightValue: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(leftLabel, color = Color.White.copy(alpha = 0.46f), fontSize = 10.5.sp, modifier = Modifier.weight(1f))
        Text(leftValue, color = Color.White.copy(alpha = 0.84f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(18.dp))
        Text(rightLabel, color = Color.White.copy(alpha = 0.46f), fontSize = 10.5.sp, modifier = Modifier.weight(1f))
        Text(rightValue, color = Color.White.copy(alpha = 0.84f), fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TinyMetric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, color = Color.White.copy(alpha = 0.34f), fontSize = 7.5.sp, maxLines = 1)
    }
}

@Composable
private fun Badge(text: String, tone: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tone.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = tone, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun buildPeriodMetrics(snapshot: AgentAnalyticsSnapshot, range: AnalyticsRange): PeriodMetrics {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val cutoff = range.days?.let { today.minusDays((it - 1L).coerceAtLeast(0L)) }
    val cutoffKey = cutoff?.toString()
    val daily = if (cutoffKey == null) snapshot.dailyActivity else snapshot.dailyActivity.filter { it.dateKey >= cutoffKey }
    val cutoffMillis = cutoff?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    val tasks = if (cutoffMillis == null) snapshot.recentTasks else snapshot.recentTasks.filter { it.startedAtMillis >= cutoffMillis }

    return PeriodMetrics(
        daily = daily,
        tasks = tasks,
        totalTokens = daily.safeSum { it.totalTokens },
        providerTokens = daily.safeSum { it.providerTokens },
        estimatedTokens = daily.safeSum { it.estimatedTokens },
        peakDailyTokens = daily.maxOfOrNull { it.totalTokens } ?: 0L,
        activeDays = daily.count { it.totalTokens > 0L || it.chatCalls > 0L || it.agentTasks > 0L || it.executedActions > 0L },
        chatCalls = daily.safeSum { it.chatCalls },
        modelCalls = daily.safeSum { it.modelCalls },
        modelFailures = daily.safeSum { it.modelFailures },
        completedTasks = daily.safeSum { it.completedTasks },
        autonomousCompletedTasks = daily.safeSum { it.autonomousCompletedTasks },
        assistedCompletedTasks = daily.safeSum { it.assistedCompletedTasks },
        failedTasks = daily.safeSum { it.failedTasks },
        pausedTasks = daily.safeSum { it.pausedTasks },
        cancelledTasks = daily.safeSum { it.cancelledTasks },
        budgetExceededTasks = daily.safeSum { it.budgetExceededTasks },
        agentModelTurns = daily.safeSum { it.agentModelTurns },
        executedActions = daily.safeSum { it.executedActions },
        successfulActions = daily.safeSum { it.successfulActions },
        failedActions = daily.safeSum { it.failedActions },
        reobservations = daily.safeSum { it.reobservations },
        rejectedPlans = daily.safeSum { it.rejectedPlans },
        executionFailures = daily.safeSum { it.executionFailures },
        confirmationRequests = daily.safeSum { it.confirmationRequests },
        userInputRequests = daily.safeSum { it.userInputRequests },
        userTakeovers = daily.safeSum { it.userTakeovers },
        taskDurationMs = daily.safeSum { it.taskDurationMs },
    )
}

private fun buildHeatmap(daily: List<AgentDailyActivity>, requestedWeeks: Int): HeatmapData {
    val weeks = requestedWeeks.coerceIn(1, 52)
    val today = LocalDate.now(ZoneId.systemDefault())
    val endSunday = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val startMonday = endSunday.minusDays(weeks * 7L - 1L)
    val values = daily.associate { it.dateKey to it.totalTokens.coerceAtLeast(0L) }
    val cells = ArrayList<HeatmapCell>(weeks * 7)
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
        cells += HeatmapCell(index / 7, index % 7, value, future)
    }
    return HeatmapData(weeks, cells, maxTokens, activeDays)
}

private fun buildInsights(metrics: PeriodMetrics, longestTaskDurationMs: Long): List<String> {
    if (metrics.totalTokens <= 0L && metrics.terminalTasks <= 0L) {
        return listOf(
            "当前范围还没有智能体活动，统计会从下一次真实模型调用开始积累。",
            "Token 热力图不会根据旧聊天内容倒推，因此不会出现伪造的历史活跃记录。",
            "统计写入采用低频聚合，不会持续扫描页面、节点或聊天记录。",
        )
    }
    val providerShare = if (metrics.totalTokens > 0L) {
        metrics.providerTokens.toFloat() / metrics.totalTokens.toFloat()
    } else {
        0f
    }
    return listOf(
        "${metrics.activeDays} 个活跃日累计 ${compactNumber(metrics.totalTokens)} Token，其中 ${percent(providerShare)} 来自供应商真实 usage。",
        if (metrics.completedTasks > 0L) {
            "完成 ${metrics.completedTasks} 个任务，自主完成率 ${percent(metrics.autonomousRate)}，介入后完成 ${metrics.assistedCompletedTasks} 个。"
        } else {
            "当前范围还没有完成任务，模型调用和 Token 活动仍会正常记录。"
        },
        "动作成功率 ${percent(metrics.actionSuccessRate)}，累计重新观察 ${metrics.reobservations} 次；历史最长任务 ${duration(longestTaskDurationMs)}。",
    )
}

private inline fun List<AgentDailyActivity>.safeSum(selector: (AgentDailyActivity) -> Long): Long {
    var total = 0L
    forEach { item ->
        val value = selector(item).coerceAtLeast(0L)
        total = if (Long.MAX_VALUE - total < value) Long.MAX_VALUE else total + value
    }
    return total
}

private fun compactNumber(value: Long): String {
    val safe = value.coerceAtLeast(0L)
    return when {
        safe >= 100_000_000L -> oneDecimal(safe / 100_000_000.0) + " 亿"
        safe >= 10_000L -> oneDecimal(safe / 10_000.0) + " 万"
        else -> safe.toString()
    }
}

private fun oneDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun percent(value: Float): String = "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%"

private fun duration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return when {
        seconds >= 3_600L -> {
            val hours = seconds / 3_600L
            val minutes = (seconds % 3_600L) / 60L
            if (minutes > 0L) "${hours}h ${minutes}m" else "${hours}h"
        }
        seconds >= 60L -> "${seconds / 60L}m ${seconds % 60L}s"
        else -> "${seconds}s"
    }
}

private fun taskTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "时间未知"
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(timestampMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val prefix = when (dateTime.toLocalDate()) {
        today -> "今天"
        today.minusDays(1L) -> "昨天"
        else -> "${dateTime.monthValue}月${dateTime.dayOfMonth}日"
    }
    return "%s %02d:%02d".format(prefix, dateTime.hour, dateTime.minute)
}

private fun statusLabel(status: String): String = when (status.lowercase()) {
    "completed" -> "已完成"
    "failed" -> "失败"
    "paused" -> "已暂停"
    "cancelled", "canceled" -> "已停止"
    "budget_exceeded" -> "达到上限"
    "interrupted" -> "意外中断"
    "running" -> "执行中"
    else -> status.ifBlank { "未知" }
}

private fun statusTone(status: String): Color = when (status.lowercase()) {
    "completed" -> AnalyticsMint
    "running" -> AnalyticsBlue
    "paused", "interrupted" -> AnalyticsWarm
    else -> AnalyticsDanger
}

private fun capabilityKind(kind: String): String = when (kind.lowercase()) {
    "feature" -> "功能"
    "tool" -> "工具"
    "action" -> "动作"
    "app" -> "应用"
    else -> "能力"
}

private fun capabilitySymbol(kind: String): String = when (kind.lowercase()) {
    "feature" -> "F"
    "tool" -> "T"
    "action" -> "A"
    "app" -> "APP"
    else -> "AI"
}

private fun capabilityTone(kind: String): Color = when (kind.lowercase()) {
    "feature" -> AnalyticsMint
    "tool" -> AnalyticsBlue
    "action" -> AnalyticsViolet
    "app" -> AnalyticsWarm
    else -> Color.White
}
