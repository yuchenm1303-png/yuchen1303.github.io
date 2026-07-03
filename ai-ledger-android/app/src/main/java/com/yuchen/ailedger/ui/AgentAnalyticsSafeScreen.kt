package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.yuchen.ailedger.AgentAnalyticsSyncPhase
import com.yuchen.ailedger.AgentAnalyticsSyncUiState
import com.yuchen.ailedger.AgentAnalyticsViewModel
import com.yuchen.ailedger.data.AgentAnalyticsOwner
import com.yuchen.ailedger.data.SupabaseAccountState
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentCapabilityAnalytics
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentModelAnalytics
import com.yuchen.ailedger.model.AgentSkillInventory
import com.yuchen.ailedger.model.AgentTaskAnalytics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val SafeBlue = Color(0xFF8FB2FF)
private val SafeViolet = Color(0xFFB49BFF)
private val SafeMint = Color(0xFF7BE8D2)
private val SafeWarm = Color(0xFFFFC58A)
private val SafeDanger = Color(0xFFFF9EAF)
private val SafeCardColor = Color(0xFF11163A).copy(alpha = 0.74f)

private enum class SafeTab(val label: String) {
    Overview("总览"), Tokens("Token"), Tasks("任务"), Capabilities("能力")
}

private data class HeatCell(val column: Int, val row: Int, val tokens: Long, val future: Boolean)
private data class HeatData(
    val weeks: Int,
    val cells: List<HeatCell>,
    val maxTokens: Long,
    val activeDays: Int,
)

/** 标准 Compose 实现，不调用或注册任何玻璃/OpenGL 绘制链。 */
@Composable
internal fun AgentAnalyticsSafeScreen(
    viewModel: AgentAnalyticsViewModel,
    onBack: () -> Unit,
) {
    val snapshot by viewModel.state.collectAsState()
    val skills by viewModel.skillInventory.collectAsState()
    val owner by viewModel.owner.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    var tabName by rememberSaveable { mutableStateOf(SafeTab.Overview.name) }
    val tab = remember(tabName) { SafeTab.entries.firstOrNull { it.name == tabName } ?: SafeTab.Overview }

    LaunchedEffect(tab) {
        if (tab == SafeTab.Capabilities) viewModel.ensureSkillInventoryLoaded()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "safe_header") { Header(onBack) }
        item(key = "safe_account") {
            AccountStatusCard(
                accountState = accountState,
                owner = owner,
                syncState = syncState,
                onSync = viewModel::retryCloudSync,
            )
        }
        item(key = "safe_tabs") { Tabs(tab) { tabName = it.name } }

        if (!snapshot.loaded) {
            item(key = "safe_loading") {
                Card {
                    Text("正在读取统计", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
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
                SafeTab.Overview -> {
                    item(key = "safe_summary") { Summary(snapshot) }
                    item(key = "safe_heat") { Heatmap(snapshot.dailyActivity) }
                    item(key = "safe_runtime") {
                        MetricsCard(
                            "运行概览",
                            "本地与账号每日聚合",
                            listOf(
                                Metric("对话请求", number(snapshot.totals.chatCalls), SafeBlue),
                                Metric("模型调用", number(snapshot.totals.modelCalls), SafeViolet),
                                Metric("规划轮次", number(snapshot.totals.agentModelTurns), SafeMint),
                                Metric("任务总数", number(snapshot.totals.agentTasks), SafeWarm),
                                Metric("累计任务时长", duration(snapshot.totals.totalTaskDurationMs), SafeBlue),
                                Metric("最长任务", duration(snapshot.totals.longestTaskDurationMs), SafeViolet),
                            ),
                        )
                    }
                    item(key = "safe_recent_title") { SectionTitle("最近任务", "本机最近执行摘要") }
                    if (snapshot.recentTasks.isEmpty()) {
                        item(key = "safe_recent_empty") { EmptyCard("还没有智能体任务记录") }
                    } else {
                        items(snapshot.recentTasks.take(5), key = { "safe_overview_${it.taskId}" }) { TaskRow(it) }
                    }
                }

                SafeTab.Tokens -> {
                    item(key = "safe_token_summary") {
                        MetricsCard(
                            "Token 构成",
                            "真实 usage 与本地估算分开显示",
                            listOf(
                                Metric("Provider 真实", number(snapshot.totals.providerTokens), SafeMint),
                                Metric("Estimated 估算", number(snapshot.totals.estimatedTokens), SafeViolet),
                                Metric("峰值日", number(snapshot.totals.peakDailyTokens), SafeWarm),
                                Metric("累计总量", number(snapshot.totals.totalTokens), SafeBlue),
                            ),
                        )
                    }
                    item(key = "safe_token_heat") { Heatmap(snapshot.dailyActivity) }
                    item(key = "safe_models_title") { SectionTitle("模型使用", "按累计 Token 排序") }
                    if (snapshot.modelUsage.isEmpty()) {
                        item(key = "safe_models_empty") { EmptyCard("暂无模型使用数据") }
                    } else {
                        items(snapshot.modelUsage.take(12), key = { "safe_model_${it.modelId}" }) { ModelRow(it) }
                    }
                }

                SafeTab.Tasks -> {
                    item(key = "safe_task_summary") {
                        MetricsCard(
                            "任务效率",
                            "成功率、自主性与耗时",
                            listOf(
                                Metric("任务成功率", percent(snapshot.totals.taskSuccessRate), SafeMint),
                                Metric("自主完成率", percent(snapshot.totals.autonomousCompletionRate), SafeBlue),
                                Metric("完成任务", snapshot.totals.completedTasks.toString(), SafeViolet),
                                Metric("介入后完成", snapshot.totals.assistedCompletedTasks.toString(), SafeWarm),
                                Metric("累计耗时", duration(snapshot.totals.totalTaskDurationMs), SafeBlue),
                            ),
                        )
                    }
                    item(key = "safe_tasks_title") { SectionTitle("任务记录", "最多显示最近 100 条") }
                    if (snapshot.recentTasks.isEmpty()) {
                        item(key = "safe_tasks_empty") { EmptyCard("暂无任务记录") }
                    } else {
                        items(snapshot.recentTasks, key = { "safe_task_${it.taskId}" }) { TaskRow(it) }
                    }
                }

                SafeTab.Capabilities -> {
                    item(key = "safe_skills") { SkillCard(skills) }
                    item(key = "safe_cap_title") { SectionTitle("能力使用", "工具、功能、动作与应用") }
                    if (snapshot.capabilityUsage.isEmpty()) {
                        item(key = "safe_cap_empty") { EmptyCard("暂无能力使用数据") }
                    } else {
                        items(
                            snapshot.capabilityUsage.take(40),
                            key = { "safe_cap_${it.kind}_${it.key}" },
                        ) { CapabilityRow(it) }
                    }
                }
            }
        }
    }
}

private data class Metric(val label: String, val value: String, val tone: Color)

@Composable
private fun Header(onBack: () -> Unit) {
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
            Text("‹ 返回", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text("AGENT INSIGHTS", color = SafeMint.copy(alpha = 0.78f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text("智能体统计", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
        Text(
            "Token 活动、任务效率、自主性与能力成长",
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun AccountStatusCard(
    accountState: SupabaseAccountState,
    owner: AgentAnalyticsOwner,
    syncState: AgentAnalyticsSyncUiState,
    onSync: () -> Unit,
) {
    val checking = accountState.loading
    val loggedIn = !checking && accountState.isLoggedIn && !owner.isGuest
    val visiblePhase = if (checking) AgentAnalyticsSyncPhase.Checking else syncState.phase
    val tone = syncTone(visiblePhase)
    val label = syncLabel(visiblePhase)

    Card(
        brush = Brush.linearGradient(
            listOf(
                SafeBlue.copy(alpha = 0.15f),
                SafeViolet.copy(alpha = 0.10f),
                SafeCardColor,
            ),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    when {
                        checking -> "正在确认账号"
                        loggedIn -> owner.email ?: accountState.email ?: "已登录账号"
                        else -> "本机访客统计"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        checking -> "正在读取本机登录状态，不会阻塞统计页面。"
                        loggedIn -> syncState.message
                        else -> "数据只保存在当前设备。登录后会建立独立账号空间并启用跨设备聚合，访客历史不会自动并入账号。"
                    },
                    color = Color.White.copy(alpha = 0.53f),
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(tone.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(label, color = tone, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(10.dp))
        if (loggedIn) {
            MetricLine(Metric("数据空间", "账号独立", SafeMint))
            MetricLine(Metric("云端范围", "仅每日数值聚合", SafeBlue))
            MetricLine(Metric("隐私保护", "任务文本与应用名称仅本机", SafeViolet))
            MetricLine(
                Metric(
                    "上次同步",
                    formatSyncTime(syncState.lastSyncedAtMillis),
                    if (syncState.lastSyncedAtMillis > 0L) SafeMint else Color.White.copy(alpha = 0.54f),
                ),
            )
            if (syncState.remoteDayCount > 0) {
                MetricLine(Metric("其他设备日期", "${syncState.remoteDayCount} 天", SafeWarm))
            }
            Spacer(Modifier.height(8.dp))
            val syncing = visiblePhase == AgentAnalyticsSyncPhase.Syncing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (syncing) Color.White.copy(alpha = 0.055f)
                        else SafeBlue.copy(alpha = 0.15f),
                    )
                    .border(
                        1.dp,
                        if (syncing) Color.White.copy(alpha = 0.07f)
                        else SafeBlue.copy(alpha = 0.22f),
                        RoundedCornerShape(15.dp),
                    )
                    .clickable(enabled = !syncing, onClick = onSync),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (syncing) "正在同步…" else "立即同步",
                    color = if (syncing) Color.White.copy(alpha = 0.42f) else SafeBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        } else if (!checking) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.045f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    "登录入口：聊天页 → 设置 → 账号。未登录仍可使用本机统计。",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun Tabs(selected: SafeTab, onSelected: (SafeTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SafeTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) SafeViolet.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.055f))
                    .border(
                        1.dp,
                        if (active) SafeViolet.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.07f),
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
private fun Summary(snapshot: AgentAnalyticsSnapshot) {
    Card(
        brush = Brush.linearGradient(
            listOf(
                Color(0xFF202D70).copy(alpha = 0.72f),
                Color(0xFF211B58).copy(alpha = 0.62f),
                Color(0xFF0E3851).copy(alpha = 0.54f),
            ),
        ),
    ) {
        Text(number(snapshot.totals.totalTokens), color = Color.White, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black)
        Text("累计 Token", color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        MetricLine(Metric("活跃连续", "${snapshot.totals.currentActiveStreakDays} 天", SafeMint))
        MetricLine(Metric("完成任务", snapshot.totals.completedTasks.toString(), SafeBlue))
        MetricLine(Metric("自主完成率", percent(snapshot.totals.autonomousCompletionRate), SafeViolet))
        MetricLine(Metric("执行动作", number(snapshot.totals.executedActions), SafeWarm))
    }
}

@Composable
private fun MetricsCard(title: String, subtitle: String, metrics: List<Metric>) {
    Card {
        CardTitle(title, subtitle)
        Spacer(Modifier.height(10.dp))
        metrics.forEach { MetricLine(it) }
    }
}

@Composable
private fun SkillCard(skills: AgentSkillInventory) {
    MetricsCard(
        "Skill 资产",
        "操作学习形成的本机可复用能力",
        listOf(
            Metric("全部 Skill", skills.totalSkills.toString(), SafeBlue),
            Metric("可用", skills.usableSkills.toString(), SafeMint),
            Metric("待审核", skills.reviewSkills.toString(), SafeWarm),
            Metric("覆盖应用", skills.scopedApps.toString(), SafeViolet),
            Metric("运行次数", skills.totalRuns.toString(), SafeBlue),
            Metric("成功运行", skills.successfulRuns.toString(), SafeMint),
        ),
    )
}

@Composable
private fun Heatmap(daily: List<AgentDailyActivity>) {
    val heat = remember(daily) { buildHeatmap(daily, 12) }
    val cell = 11.dp
    val gap = 3.dp
    val width = (cell + gap) * heat.weeks - gap
    val height = (cell + gap) * 7 - gap

    Card {
        CardTitle("Token 活动热力图", "${heat.weeks} 周 · ${heat.activeDays} 个活跃日 · 每格一天")
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(width).height(height)) {
                val cellPx = cell.toPx()
                val gapPx = gap.toPx()
                val max = heat.maxTokens.coerceAtLeast(1L).toFloat()
                heat.cells.forEach { entry ->
                    val ratio = (entry.tokens.toFloat() / max).coerceIn(0f, 1f)
                    val color = when {
                        entry.future -> Color.White.copy(alpha = 0.025f)
                        entry.tokens <= 0L -> Color.White.copy(alpha = 0.065f)
                        ratio < 0.2f -> SafeBlue.copy(alpha = 0.30f)
                        ratio < 0.45f -> SafeBlue.copy(alpha = 0.54f)
                        ratio < 0.72f -> SafeViolet.copy(alpha = 0.74f)
                        else -> SafeMint.copy(alpha = 0.94f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(entry.column * (cellPx + gapPx), entry.row * (cellPx + gapPx)),
                        size = Size(cellPx, cellPx),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: AgentTaskAnalytics) {
    Card {
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
        Text(statusLabel(task.status), color = statusTone(task.status), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        MetricLine(Metric("耗时", duration(task.durationMs), SafeBlue))
        MetricLine(Metric("动作", task.executedActions.toString(), SafeMint))
        MetricLine(Metric("Token", number(task.totalTokens), SafeViolet))
        MetricLine(Metric("用户介入", task.interventionCount.toString(), SafeWarm))
    }
}

@Composable
private fun ModelRow(model: AgentModelAnalytics) {
    val successRate = if (model.calls > 0L) {
        (model.calls - model.failures).coerceAtLeast(0L).toFloat() / model.calls.toFloat()
    } else {
        0f
    }
    Card {
        Text(
            model.displayName,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        MetricLine(Metric("调用次数", model.calls.toString(), SafeBlue))
        MetricLine(Metric("累计 Token", number(model.totalTokens), SafeViolet))
        MetricLine(Metric("调用成功率", percent(successRate), SafeMint))
    }
}

@Composable
private fun CapabilityRow(capability: AgentCapabilityAnalytics) {
    val total = safeAdd(capability.successes, capability.failures)
    val rate = if (total > 0L) capability.successes.toFloat() / total.toFloat() else 0f
    Card {
        Text(
            capability.displayName,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        MetricLine(Metric("类型", capabilityKind(capability.kind), capabilityTone(capability.kind)))
        MetricLine(Metric("使用次数", capability.uses.toString(), SafeBlue))
        MetricLine(Metric("成功率", if (total > 0L) percent(rate) else "累计", SafeMint))
    }
}

@Composable
private fun Card(
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(SafeCardColor))
            .border(1.dp, Color.White.copy(alpha = 0.075f), shape)
            .padding(horizontal = 17.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        content = content,
    )
}

@Composable
private fun CardTitle(title: String, subtitle: String) {
    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(2.dp))
    Text(subtitle, color = Color.White.copy(alpha = 0.45f), fontSize = 10.5.sp, lineHeight = 15.sp)
}

@Composable
private fun MetricLine(metric: Metric) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(metric.label, color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp)
        Text(
            metric.value,
            color = metric.tone,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
private fun EmptyCard(message: String) {
    Card { Text(message, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp) }
}

private fun buildHeatmap(daily: List<AgentDailyActivity>, requestedWeeks: Int): HeatData {
    val weeks = requestedWeeks.coerceIn(1, 52)
    val today = LocalDate.now(ZoneId.systemDefault())
    val endSunday = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val startMonday = endSunday.minusDays(weeks * 7L - 1L)
    val values = daily.associate { it.dateKey to it.totalTokens.coerceAtLeast(0L) }
    val cells = ArrayList<HeatCell>(weeks * 7)
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
        cells += HeatCell(index / 7, index % 7, value, future)
    }
    return HeatData(weeks, cells, maxTokens, activeDays)
}

private fun syncLabel(phase: AgentAnalyticsSyncPhase): String = when (phase) {
    AgentAnalyticsSyncPhase.Checking -> "检查中"
    AgentAnalyticsSyncPhase.Guest -> "仅本机"
    AgentAnalyticsSyncPhase.Syncing -> "同步中"
    AgentAnalyticsSyncPhase.Synced -> "已同步"
    AgentAnalyticsSyncPhase.Cached -> "已缓存"
    AgentAnalyticsSyncPhase.LocalOnly -> "仅本机"
    AgentAnalyticsSyncPhase.Failed -> "同步失败"
}

private fun syncTone(phase: AgentAnalyticsSyncPhase): Color = when (phase) {
    AgentAnalyticsSyncPhase.Synced -> SafeMint
    AgentAnalyticsSyncPhase.Cached -> SafeBlue
    AgentAnalyticsSyncPhase.Syncing,
    AgentAnalyticsSyncPhase.Checking -> SafeViolet
    AgentAnalyticsSyncPhase.Guest,
    AgentAnalyticsSyncPhase.LocalOnly -> SafeWarm
    AgentAnalyticsSyncPhase.Failed -> SafeDanger
}

private fun formatSyncTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "尚未成功同步"
    val dateTime = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
    return "%d月%d日 %02d:%02d".format(
        dateTime.monthValue,
        dateTime.dayOfMonth,
        dateTime.hour,
        dateTime.minute,
    )
}

private fun number(value: Long): String {
    val safe = value.coerceAtLeast(0L)
    return when {
        safe >= 100_000_000L -> decimal(safe / 100_000_000.0) + " 亿"
        safe >= 10_000L -> decimal(safe / 10_000.0) + " 万"
        else -> safe.toString()
    }
}

private fun decimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun percent(value: Float): String {
    val safe = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    return "${(safe * 100f).roundToInt()}%"
}

private fun duration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return when {
        seconds >= 3_600L -> "${seconds / 3_600L}h ${(seconds % 3_600L) / 60L}m"
        seconds >= 60L -> "${seconds / 60L}m ${seconds % 60L}s"
        else -> "${seconds}s"
    }
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
    "completed" -> SafeMint
    "running" -> SafeBlue
    "paused", "interrupted" -> SafeWarm
    else -> SafeDanger
}

private fun capabilityKind(kind: String): String = when (kind.lowercase()) {
    "feature" -> "功能"
    "tool" -> "工具"
    "action" -> "动作"
    "app" -> "应用"
    else -> "能力"
}

private fun capabilityTone(kind: String): Color = when (kind.lowercase()) {
    "feature" -> SafeMint
    "tool" -> SafeBlue
    "action" -> SafeViolet
    "app" -> SafeWarm
    else -> Color.White
}

private fun safeAdd(left: Long, right: Long): Long {
    val safeLeft = left.coerceAtLeast(0L)
    val safeRight = right.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - safeLeft < safeRight) Long.MAX_VALUE else safeLeft + safeRight
}
