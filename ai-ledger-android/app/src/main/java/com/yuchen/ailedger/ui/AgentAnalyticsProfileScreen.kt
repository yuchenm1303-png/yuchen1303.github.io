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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.AgentAnalyticsDiagnosticsUiState
import com.yuchen.ailedger.AgentAnalyticsSyncPhase
import com.yuchen.ailedger.AgentAnalyticsSyncUiState
import com.yuchen.ailedger.AgentAnalyticsViewModel
import com.yuchen.ailedger.data.AgentAnalyticsOwner
import com.yuchen.ailedger.data.SupabaseAccountState
import com.yuchen.ailedger.data.UserProfileState
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

private val ProfileStatsBlue = Color(0xFF8FB2FF)
private val ProfileStatsViolet = Color(0xFFB49BFF)
private val ProfileStatsMint = Color(0xFF7BE8D2)
private val ProfileStatsWarm = Color(0xFFFFC58A)
private val ProfileStatsDanger = Color(0xFFFF9EAF)
private val ProfileStatsCardColor = Color(0xFF11152F).copy(alpha = 0.78f)

private enum class ProfileStatsTab(val label: String) {
    Overview("总览"), Tokens("Token"), Tasks("任务"), Capabilities("能力")
}

private data class ProfileMetric(val label: String, val value: String, val detail: String)
private data class ProfileHeatCell(val column: Int, val row: Int, val tokens: Long, val future: Boolean)
private data class ProfileHeatmap(
    val weeks: Int,
    val cells: List<ProfileHeatCell>,
    val maxTokens: Long,
    val activeDays: Int,
)

/**
 * 账号化智能体统计页面。
 *
 * 仅使用标准 Compose 与单个小型 Canvas，不注册玻璃/OpenGL，不启动动画时钟。
 */
@Composable
internal fun AgentAnalyticsProfileScreen(
    viewModel: AgentAnalyticsViewModel,
    onBack: () -> Unit,
) {
    val snapshot by viewModel.state.collectAsState()
    val skills by viewModel.skillInventory.collectAsState()
    val owner by viewModel.owner.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val profileState by viewModel.profileState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    var tabName by rememberSaveable { mutableStateOf(ProfileStatsTab.Overview.name) }
    var diagnosticsOpen by rememberSaveable { mutableStateOf(false) }
    val selectedTab = remember(tabName) {
        ProfileStatsTab.entries.firstOrNull { it.name == tabName } ?: ProfileStatsTab.Overview
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == ProfileStatsTab.Capabilities) viewModel.ensureSkillInventoryLoaded()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "profile-stats-back") {
            ProfileStatsBack(
                onBack = onBack,
                onDiagnostics = { diagnosticsOpen = !diagnosticsOpen },
                diagnosticsHasWarning = diagnostics.hasLocalLoadFailure,
            )
        }
        if (diagnosticsOpen) {
            item(key = "profile-stats-diagnostics") {
                ProfileStatsDiagnosticsCard(
                    snapshot = snapshot,
                    skills = skills,
                    owner = owner,
                    accountState = accountState,
                    syncState = syncState,
                    diagnostics = diagnostics,
                    selectedTab = selectedTab,
                )
            }
        }
        item(key = "profile-stats-identity") {
            ProfileStatsIdentity(
                snapshot = snapshot,
                owner = owner,
                accountState = accountState,
                profileState = profileState,
                syncState = syncState,
            )
        }
        item(key = "profile-stats-sync") {
            ProfileStatsSyncCard(
                owner = owner,
                accountState = accountState,
                syncState = syncState,
                onSync = viewModel::retryCloudSync,
            )
        }
        item(key = "profile-stats-tabs") {
            ProfileStatsTabs(selectedTab) { tabName = it.name }
        }

        if (!snapshot.loaded) {
            item(key = "profile-stats-loading") {
                ProfileStatsCard {
                    Text(
                        if (diagnostics.hasLocalLoadFailure) "统计读取失败" else "正在读取统计",
                        color = if (diagnostics.hasLocalLoadFailure) ProfileStatsDanger else Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (diagnostics.hasLocalLoadFailure) {
                            "已停止把读取失败误显示成 0。请点右上角“诊断”复制详情，我会继续判断根因。"
                        } else {
                            "本页低频读取本地快照，不持续观察数据库。"
                        },
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    if (diagnostics.hasLocalLoadFailure) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            diagnostics.lastLocalLoadMessage,
                            color = Color.White.copy(alpha = 0.36f),
                            fontSize = 9.5.sp,
                            lineHeight = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            when (selectedTab) {
                ProfileStatsTab.Overview -> {
                    item(key = "profile-stats-heatmap") { ProfileStatsHeatmap(snapshot.dailyActivity) }
                    item(key = "profile-stats-runtime") { ProfileStatsRuntime(snapshot) }
                    item(key = "profile-stats-recent-title") {
                        ProfileStatsSectionTitle("最近任务", "本机任务摘要")
                    }
                    if (snapshot.recentTasks.isEmpty()) {
                        item(key = "profile-stats-recent-empty") { ProfileStatsEmpty("还没有智能体任务记录") }
                    } else {
                        items(snapshot.recentTasks.take(5), key = { "profile-overview-${it.taskId}" }) {
                            ProfileStatsTaskRow(it)
                        }
                    }
                }

                ProfileStatsTab.Tokens -> {
                    item(key = "profile-token-summary") {
                        ProfileStatsRows(
                            title = "Token 构成",
                            subtitle = "真实 usage 与本地估算分开显示",
                            rows = listOf(
                                Triple("Provider 真实", profileNumber(snapshot.totals.providerTokens), ProfileStatsMint),
                                Triple("Estimated 估算", profileNumber(snapshot.totals.estimatedTokens), ProfileStatsViolet),
                                Triple("峰值日", profileNumber(snapshot.totals.peakDailyTokens), ProfileStatsWarm),
                                Triple("累计总量", profileNumber(snapshot.totals.totalTokens), ProfileStatsBlue),
                            ),
                        )
                    }
                    item(key = "profile-token-heatmap") { ProfileStatsHeatmap(snapshot.dailyActivity) }
                    item(key = "profile-model-title") { ProfileStatsSectionTitle("模型使用", "按累计 Token 排序") }
                    if (snapshot.modelUsage.isEmpty()) {
                        item(key = "profile-model-empty") { ProfileStatsEmpty("暂无模型使用数据") }
                    } else {
                        items(snapshot.modelUsage.take(12), key = { "profile-model-${it.modelId}" }) {
                            ProfileStatsModelRow(it)
                        }
                    }
                }

                ProfileStatsTab.Tasks -> {
                    item(key = "profile-task-summary") {
                        ProfileStatsRows(
                            title = "任务效率",
                            subtitle = "成功率、自主性与耗时",
                            rows = listOf(
                                Triple("任务成功率", profilePercent(snapshot.totals.taskSuccessRate), ProfileStatsMint),
                                Triple("自主完成率", profilePercent(snapshot.totals.autonomousCompletionRate), ProfileStatsBlue),
                                Triple("完成任务", snapshot.totals.completedTasks.toString(), ProfileStatsViolet),
                                Triple("介入后完成", snapshot.totals.assistedCompletedTasks.toString(), ProfileStatsWarm),
                                Triple("累计耗时", profileDuration(snapshot.totals.totalTaskDurationMs), ProfileStatsBlue),
                            ),
                        )
                    }
                    item(key = "profile-task-title") { ProfileStatsSectionTitle("任务记录", "最多显示最近 100 条") }
                    if (snapshot.recentTasks.isEmpty()) {
                        item(key = "profile-task-empty") { ProfileStatsEmpty("暂无任务记录") }
                    } else {
                        items(snapshot.recentTasks, key = { "profile-task-${it.taskId}" }) {
                            ProfileStatsTaskRow(it)
                        }
                    }
                }

                ProfileStatsTab.Capabilities -> {
                    item(key = "profile-skill-summary") { ProfileStatsSkillCard(skills) }
                    item(key = "profile-capability-title") {
                        ProfileStatsSectionTitle("能力使用", "工具、功能、动作与应用")
                    }
                    if (snapshot.capabilityUsage.isEmpty()) {
                        item(key = "profile-capability-empty") { ProfileStatsEmpty("暂无能力使用数据") }
                    } else {
                        items(
                            snapshot.capabilityUsage.take(40),
                            key = { "profile-capability-${it.kind}-${it.key}" },
                        ) { ProfileStatsCapabilityRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatsBack(
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    diagnosticsHasWarning: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(88.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(19.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹ 返回", color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "智能体统计",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (diagnosticsHasWarning) ProfileStatsDanger.copy(alpha = 0.16f)
                        else Color.White.copy(alpha = 0.07f)
                    )
                    .border(
                        1.dp,
                        if (diagnosticsHasWarning) ProfileStatsDanger.copy(alpha = 0.28f)
                        else Color.White.copy(alpha = 0.09f),
                        RoundedCornerShape(15.dp),
                    )
                    .clickable(onClick = onDiagnostics),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "诊断",
                    color = if (diagnosticsHasWarning) ProfileStatsDanger else Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun ProfileStatsDiagnosticsCard(
    snapshot: AgentAnalyticsSnapshot,
    skills: AgentSkillInventory,
    owner: AgentAnalyticsOwner,
    accountState: SupabaseAccountState,
    syncState: AgentAnalyticsSyncUiState,
    diagnostics: AgentAnalyticsDiagnosticsUiState,
    selectedTab: ProfileStatsTab,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val report = remember(snapshot, skills, owner, accountState, syncState, diagnostics, selectedTab) {
        profileBuildDiagnosticsReport(
            snapshot = snapshot,
            skills = skills,
            owner = owner,
            accountState = accountState,
            syncState = syncState,
            diagnostics = diagnostics,
            selectedTab = selectedTab,
        )
    }
    ProfileStatsCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("统计诊断", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(
                    "复制后发给我，用来继续定位为什么统计始终为 0。",
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
            Box(
                modifier = Modifier
                    .width(82.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ProfileStatsBlue.copy(alpha = 0.13f))
                    .border(1.dp, ProfileStatsBlue.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(report))
                        copied = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (copied) "已复制" else "复制导出", color = ProfileStatsBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            report,
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 9.sp,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun ProfileStatsIdentity(
    snapshot: AgentAnalyticsSnapshot,
    owner: AgentAnalyticsOwner,
    accountState: SupabaseAccountState,
    profileState: UserProfileState,
    syncState: AgentAnalyticsSyncUiState,
) {
    val loggedIn = accountState.isLoggedIn && !owner.isGuest
    val email = accountState.email.orEmpty()
    val displayName = if (loggedIn) {
        profileState.profile?.displayName?.takeIf(String::isNotBlank)
            ?: email.substringBefore('@')
                .replace(Regex("[._-]+"), " ")
                .trim()
                .take(24)
                .ifBlank { "AI Ledger 用户" }
    } else {
        "本地用户"
    }
    val fallback = displayName.firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifBlank { "AI" }
    val handle = if (loggedIn) {
        "@" + email.substringBefore('@').trim().take(22).ifBlank { "account" }
    } else {
        "@local"
    }
    val metrics = listOf(
        ProfileMetric("峰值 Token", profileNumber(snapshot.totals.peakDailyTokens), "单日最高"),
        ProfileMetric("最长任务", profileDuration(snapshot.totals.longestTaskDurationMs), "本机单次"),
        ProfileMetric("当前连续", "${snapshot.totals.currentActiveStreakDays} 天", "活跃天数"),
        ProfileMetric("最长连续", "${snapshot.totals.longestActiveStreakDays} 天", "历史纪录"),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        UserProfileAvatar(
            localAvatarPath = profileState.localAvatarPath,
            avatarVersion = profileState.profile?.avatarVersion ?: 0L,
            fallbackText = fallback,
            size = 92.dp,
            loggedIn = loggedIn,
        )
        Text(
            displayName,
            color = Color.White,
            fontSize = 29.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(handle, color = Color.White.copy(alpha = 0.46f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(profileSyncTone(syncState.phase).copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    if (loggedIn) "账号空间" else "仅本机",
                    color = profileSyncTone(syncState.phase),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.035f))
                .border(1.dp, Color.White.copy(alpha = 0.065f), RoundedCornerShape(24.dp))
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            metrics.forEachIndexed { index, metric ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(44.dp)
                            .background(Color.White.copy(alpha = 0.07f))
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        metric.value,
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        metric.label,
                        color = Color.White.copy(alpha = 0.44f),
                        fontSize = 8.5.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        metric.detail,
                        color = Color.White.copy(alpha = 0.23f),
                        fontSize = 7.sp,
                        lineHeight = 8.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStatsSyncCard(
    owner: AgentAnalyticsOwner,
    accountState: SupabaseAccountState,
    syncState: AgentAnalyticsSyncUiState,
    onSync: () -> Unit,
) {
    val loggedIn = accountState.isLoggedIn && !owner.isGuest
    val phase = if (accountState.loading) AgentAnalyticsSyncPhase.Checking else syncState.phase
    val syncing = phase == AgentAnalyticsSyncPhase.Syncing
    ProfileStatsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    profileSyncLabel(phase),
                    color = profileSyncTone(phase),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    when {
                        accountState.loading -> "正在确认账号状态，不会阻塞本地统计。"
                        loggedIn -> syncState.message
                        else -> "访客数据只保存在当前设备，登录后会切换到独立账号空间。"
                    },
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
                if (loggedIn && syncState.lastSyncedAtMillis > 0L) {
                    Text(
                        "上次同步 ${profileSyncTime(syncState.lastSyncedAtMillis)} · 云端仅保存每日数值聚合",
                        color = Color.White.copy(alpha = 0.29f),
                        fontSize = 8.5.sp,
                        lineHeight = 12.sp,
                    )
                }
            }
            if (loggedIn) {
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (syncing) Color.White.copy(alpha = 0.045f)
                            else ProfileStatsBlue.copy(alpha = 0.13f)
                        )
                        .border(
                            1.dp,
                            ProfileStatsBlue.copy(alpha = if (syncing) 0.08f else 0.20f),
                            RoundedCornerShape(14.dp),
                        )
                        .clickable(enabled = !syncing, onClick = onSync),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (syncing) "同步中" else "立即同步",
                        color = if (syncing) Color.White.copy(alpha = 0.35f) else ProfileStatsBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStatsTabs(
    selected: ProfileStatsTab,
    onSelected: (ProfileStatsTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProfileStatsTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (active) ProfileStatsViolet.copy(alpha = 0.20f)
                        else Color.White.copy(alpha = 0.045f)
                    )
                    .border(
                        1.dp,
                        if (active) ProfileStatsViolet.copy(alpha = 0.28f)
                        else Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = !active) { onSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    color = Color.White.copy(alpha = if (active) 0.94f else 0.48f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun ProfileStatsRuntime(snapshot: AgentAnalyticsSnapshot) {
    ProfileStatsRows(
        title = "运行概览",
        subtitle = "本机明细与账号每日聚合",
        rows = listOf(
            Triple("累计 Token", profileNumber(snapshot.totals.totalTokens), ProfileStatsBlue),
            Triple("模型调用", profileNumber(snapshot.totals.modelCalls), ProfileStatsViolet),
            Triple("规划轮次", profileNumber(snapshot.totals.agentModelTurns), ProfileStatsMint),
            Triple("完成任务", snapshot.totals.completedTasks.toString(), ProfileStatsWarm),
            Triple("执行动作", profileNumber(snapshot.totals.executedActions), ProfileStatsBlue),
            Triple("累计任务时长", profileDuration(snapshot.totals.totalTaskDurationMs), ProfileStatsViolet),
        ),
    )
}

@Composable
private fun ProfileStatsRows(
    title: String,
    subtitle: String,
    rows: List<Triple<String, String, Color>>,
) {
    ProfileStatsCard {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp)
        Spacer(Modifier.height(9.dp))
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.first, color = Color.White.copy(alpha = 0.46f), fontSize = 10.5.sp)
                Text(row.second, color = row.third, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ProfileStatsHeatmap(daily: List<AgentDailyActivity>) {
    val heatmap = remember(daily) { profileBuildHeatmap(daily, 14) }
    val cell = 10.dp
    val gap = 3.dp
    val chartWidth = (cell + gap) * heatmap.weeks - gap
    val chartHeight = (cell + gap) * 7 - gap
    ProfileStatsCard {
        Text("Token 活动", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "${heatmap.weeks} 周 · ${heatmap.activeDays} 个活跃日 · 每格一天",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(13.dp))
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(chartWidth).height(chartHeight)) {
                val cellPx = cell.toPx()
                val gapPx = gap.toPx()
                val max = heatmap.maxTokens.coerceAtLeast(1L).toFloat()
                heatmap.cells.forEach { entry ->
                    val ratio = (entry.tokens.toFloat() / max).coerceIn(0f, 1f)
                    val color = when {
                        entry.future -> Color.White.copy(alpha = 0.02f)
                        entry.tokens <= 0L -> Color.White.copy(alpha = 0.055f)
                        ratio < 0.20f -> ProfileStatsBlue.copy(alpha = 0.28f)
                        ratio < 0.45f -> ProfileStatsBlue.copy(alpha = 0.52f)
                        ratio < 0.72f -> ProfileStatsViolet.copy(alpha = 0.72f)
                        else -> ProfileStatsMint.copy(alpha = 0.92f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(
                            entry.column * (cellPx + gapPx),
                            entry.row * (cellPx + gapPx),
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
private fun ProfileStatsTaskRow(task: AgentTaskAnalytics) {
    ProfileStatsCard {
        Text(
            task.goal.ifBlank { "未命名智能体任务" },
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        ProfileStatsInline("状态", profileTaskStatus(task.status), profileTaskTone(task.status))
        ProfileStatsInline("耗时", profileDuration(task.durationMs), ProfileStatsBlue)
        ProfileStatsInline("动作", task.executedActions.toString(), ProfileStatsMint)
        ProfileStatsInline("Token", profileNumber(task.totalTokens), ProfileStatsViolet)
    }
}

@Composable
private fun ProfileStatsModelRow(model: AgentModelAnalytics) {
    val successRate = if (model.calls > 0L) {
        (model.calls - model.failures).coerceAtLeast(0L).toFloat() / model.calls.toFloat()
    } else {
        0f
    }
    ProfileStatsCard {
        Text(
            model.displayName,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        ProfileStatsInline("调用次数", model.calls.toString(), ProfileStatsBlue)
        ProfileStatsInline("累计 Token", profileNumber(model.totalTokens), ProfileStatsViolet)
        ProfileStatsInline("调用成功率", profilePercent(successRate), ProfileStatsMint)
    }
}

@Composable
private fun ProfileStatsCapabilityRow(capability: AgentCapabilityAnalytics) {
    val resultCount = profileSafeAdd(capability.successes, capability.failures)
    val rate = if (resultCount > 0L) capability.successes.toFloat() / resultCount.toFloat() else 0f
    ProfileStatsCard {
        Text(
            capability.displayName,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        ProfileStatsInline("类型", profileCapabilityKind(capability.kind), ProfileStatsViolet)
        ProfileStatsInline("使用次数", capability.uses.toString(), ProfileStatsBlue)
        ProfileStatsInline("成功率", if (resultCount > 0L) profilePercent(rate) else "累计", ProfileStatsMint)
    }
}

@Composable
private fun ProfileStatsSkillCard(skills: AgentSkillInventory) {
    ProfileStatsRows(
        title = "Skill 资产",
        subtitle = "操作学习形成的本机可复用能力",
        rows = listOf(
            Triple("全部 Skill", skills.totalSkills.toString(), ProfileStatsBlue),
            Triple("可用", skills.usableSkills.toString(), ProfileStatsMint),
            Triple("待审核", skills.reviewSkills.toString(), ProfileStatsWarm),
            Triple("覆盖应用", skills.scopedApps.toString(), ProfileStatsViolet),
            Triple("运行次数", skills.totalRuns.toString(), ProfileStatsBlue),
            Triple("成功运行", skills.successfulRuns.toString(), ProfileStatsMint),
        ),
    )
}

@Composable
private fun ProfileStatsInline(label: String, value: String, tone: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.43f), fontSize = 10.sp)
        Text(value, color = tone, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ProfileStatsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(23.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ProfileStatsCardColor)
            .border(1.dp, Color.White.copy(alpha = 0.065f), shape)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        content = content,
    )
}

@Composable
private fun ProfileStatsSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.40f), fontSize = 10.5.sp)
    }
}

@Composable
private fun ProfileStatsEmpty(message: String) {
    ProfileStatsCard {
        Text(message, color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp)
    }
}

private fun profileBuildHeatmap(
    daily: List<AgentDailyActivity>,
    requestedWeeks: Int,
): ProfileHeatmap {
    val weeks = requestedWeeks.coerceIn(1, 52)
    val today = LocalDate.now(ZoneId.systemDefault())
    val endSunday = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val startMonday = endSunday.minusDays(weeks * 7L - 1L)
    val values = daily.associate { it.dateKey to it.totalTokens.coerceAtLeast(0L) }
    val cells = ArrayList<ProfileHeatCell>(weeks * 7)
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
        cells += ProfileHeatCell(index / 7, index % 7, value, future)
    }
    return ProfileHeatmap(weeks, cells, maxTokens, activeDays)
}

private fun profileBuildDiagnosticsReport(
    snapshot: AgentAnalyticsSnapshot,
    skills: AgentSkillInventory,
    owner: AgentAnalyticsOwner,
    accountState: SupabaseAccountState,
    syncState: AgentAnalyticsSyncUiState,
    diagnostics: AgentAnalyticsDiagnosticsUiState,
    selectedTab: ProfileStatsTab,
): String {
    return buildString {
        appendLine("AI Ledger 智能体统计诊断")
        appendLine("导出时间: ${profileDebugTime(System.currentTimeMillis())}")
        appendLine("选中页签: ${selectedTab.name}/${selectedTab.label}")
        appendLine()
        appendLine("[账号与 owner]")
        appendLine("loggedIn=${accountState.isLoggedIn}, loading=${accountState.loading}, ownerGuest=${owner.isGuest}")
        appendLine("emailMask=${profileMaskEmail(accountState.email.orEmpty())}")
        appendLine("storageKey=${profileMaskStorageKey(owner.storageKey)}")
        appendLine("databaseName=${owner.databaseName}")
        appendLine()
        appendLine("[本机读取]")
        appendLine("success=${diagnostics.lastLocalLoadSuccess}, at=${profileDebugTime(diagnostics.lastLocalLoadAtMillis)}, durationMs=${diagnostics.lastLocalLoadDurationMs}")
        appendLine("errorType=${diagnostics.lastLocalLoadErrorType.ifBlank { "none" }}")
        appendLine("message=${diagnostics.lastLocalLoadMessage}")
        appendLine("localDaily=${diagnostics.localDailyCount}, localTasks=${diagnostics.localTaskCount}, localModels=${diagnostics.localModelCount}, localCapabilities=${diagnostics.localCapabilityCount}")
        appendLine("localTokens=${diagnostics.localTotalTokens}, localModelCalls=${diagnostics.localModelCalls}, localCompletedTasks=${diagnostics.localCompletedTasks}")
        appendLine()
        appendLine("[页面合并快照]")
        appendLine("loaded=${snapshot.loaded}, daily=${snapshot.dailyActivity.size}, tasks=${snapshot.recentTasks.size}, models=${snapshot.modelUsage.size}, capabilities=${snapshot.capabilityUsage.size}")
        appendLine("mergedDailyDiag=${diagnostics.mergedDailyCount}, mergedTokensDiag=${diagnostics.mergedTotalTokens}")
        appendLine("totalTokens=${snapshot.totals.totalTokens}, provider=${snapshot.totals.providerTokens}, estimated=${snapshot.totals.estimatedTokens}, peak=${snapshot.totals.peakDailyTokens}")
        appendLine("chatCalls=${snapshot.totals.chatCalls}, modelCalls=${snapshot.totals.modelCalls}, agentTasks=${snapshot.totals.agentTasks}, completed=${snapshot.totals.completedTasks}")
        appendLine("currentStreak=${snapshot.totals.currentActiveStreakDays}, longestStreak=${snapshot.totals.longestActiveStreakDays}, longestTaskMs=${snapshot.totals.longestTaskDurationMs}")
        appendLine("dailyKeys=${snapshot.dailyActivity.takeLast(8).joinToString { it.dateKey + ":" + it.totalTokens }}")
        appendLine("modelKeys=${snapshot.modelUsage.take(6).joinToString { it.modelId + ":" + it.totalTokens }}")
        appendLine()
        appendLine("[云同步]")
        appendLine("phase=${syncState.phase}, lastSynced=${profileDebugTime(syncState.lastSyncedAtMillis)}, uploadedDays=${syncState.uploadedDayCount}, remoteDays=${syncState.remoteDayCount}")
        appendLine("message=${syncState.message}")
        appendLine()
        appendLine("[Skill]")
        appendLine("total=${skills.totalSkills}, usable=${skills.usableSkills}, runs=${skills.totalRuns}, successRuns=${skills.successfulRuns}, apps=${skills.scopedApps}")
    }
}

private fun profileSyncLabel(phase: AgentAnalyticsSyncPhase): String = when (phase) {
    AgentAnalyticsSyncPhase.Checking -> "正在检查账号"
    AgentAnalyticsSyncPhase.Guest -> "本机访客统计"
    AgentAnalyticsSyncPhase.Syncing -> "正在同步账号统计"
    AgentAnalyticsSyncPhase.Synced -> "账号统计已同步"
    AgentAnalyticsSyncPhase.Cached -> "已复用同步缓存"
    AgentAnalyticsSyncPhase.LocalOnly -> "当前仅显示本机数据"
    AgentAnalyticsSyncPhase.Failed -> "云端同步暂时失败"
}

private fun profileSyncTone(phase: AgentAnalyticsSyncPhase): Color = when (phase) {
    AgentAnalyticsSyncPhase.Synced -> ProfileStatsMint
    AgentAnalyticsSyncPhase.Cached -> ProfileStatsBlue
    AgentAnalyticsSyncPhase.Syncing,
    AgentAnalyticsSyncPhase.Checking -> ProfileStatsViolet
    AgentAnalyticsSyncPhase.Guest,
    AgentAnalyticsSyncPhase.LocalOnly -> ProfileStatsWarm
    AgentAnalyticsSyncPhase.Failed -> ProfileStatsDanger
}

private fun profileSyncTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "尚未成功同步"
    val time = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
    return "%d月%d日 %02d:%02d".format(
        time.monthValue,
        time.dayOfMonth,
        time.hour,
        time.minute,
    )
}

private fun profileDebugTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "0"
    val time = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
    return "%04d-%02d-%02d %02d:%02d:%02d".format(
        time.year,
        time.monthValue,
        time.dayOfMonth,
        time.hour,
        time.minute,
        time.second,
    )
}

private fun profileTaskStatus(status: String): String = when (status.lowercase()) {
    "completed" -> "已完成"
    "failed" -> "失败"
    "paused" -> "已暂停"
    "cancelled", "canceled" -> "已停止"
    "budget_exceeded" -> "达到上限"
    "interrupted" -> "意外中断"
    "running" -> "执行中"
    else -> status.ifBlank { "未知" }
}

private fun profileTaskTone(status: String): Color = when (status.lowercase()) {
    "completed" -> ProfileStatsMint
    "running" -> ProfileStatsBlue
    "paused", "interrupted" -> ProfileStatsWarm
    else -> ProfileStatsDanger
}

private fun profileCapabilityKind(kind: String): String = when (kind.lowercase()) {
    "feature" -> "功能"
    "tool" -> "工具"
    "action" -> "动作"
    "app" -> "应用"
    else -> "能力"
}

private fun profileNumber(value: Long): String {
    val safe = value.coerceAtLeast(0L)
    return when {
        safe >= 100_000_000L -> profileDecimal(safe / 100_000_000.0) + " 亿"
        safe >= 10_000L -> profileDecimal(safe / 10_000.0) + " 万"
        else -> safe.toString()
    }
}

private fun profileDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun profilePercent(value: Float): String {
    val safe = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    return "${(safe * 100f).roundToInt()}%"
}

private fun profileDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return when {
        seconds >= 3_600L -> "${seconds / 3_600L}h ${(seconds % 3_600L) / 60L}m"
        seconds >= 60L -> "${seconds / 60L}m ${seconds % 60L}s"
        else -> "${seconds}s"
    }
}

private fun profileSafeAdd(left: Long, right: Long): Long {
    val safeLeft = left.coerceAtLeast(0L)
    val safeRight = right.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - safeLeft < safeRight) Long.MAX_VALUE else safeLeft + safeRight
}

private fun profileMaskEmail(email: String): String {
    val clean = email.trim()
    if ('@' !in clean) return clean.take(2) + "***"
    val name = clean.substringBefore('@')
    val domain = clean.substringAfter('@')
    return name.take(2).ifBlank { "**" } + "***@" + domain
}

private fun profileMaskStorageKey(key: String): String {
    val clean = key.trim()
    if (clean.length <= 18) return clean
    return clean.take(10) + "…" + clean.takeLast(8)
}
