package com.yuchen.ailedger.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.AgentExecutionResult
import com.yuchen.ailedger.service.AppManagementController
import com.yuchen.ailedger.service.DeviceShellStatus
import com.yuchen.ailedger.service.StorageAppAnalysisState
import com.yuchen.ailedger.service.StorageAppOptimizationItem
import com.yuchen.ailedger.service.StorageCapacitySnapshot
import com.yuchen.ailedger.service.StorageCleanupHistoryEntry
import com.yuchen.ailedger.service.StorageCleanupTrendPoint
import com.yuchen.ailedger.service.StorageCompatibilityReport
import com.yuchen.ailedger.service.StorageDeviceGuard
import com.yuchen.ailedger.service.StorageManagementRepository
import com.yuchen.ailedger.service.StoragePermissionHealth
import com.yuchen.ailedger.service.StorageProductDashboard
import com.yuchen.ailedger.service.StorageProductizationRepository
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class StorageOptimizationTab(val label: String) {
    Overview("总览"),
    Apps("应用分析"),
    Trends("趋势记录"),
    Health("权限与兼容"),
}

@Composable
fun StorageDeviceOptimizationScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { StorageProductizationRepository(context.applicationContext) }
    val storageRepository = remember(context) { StorageManagementRepository(context.applicationContext) }
    val appController = remember(context) { AppManagementController(context.applicationContext) }
    val stopSignal = remember { AtomicBoolean(false) }

    var tab by remember { mutableStateOf(StorageOptimizationTab.Overview) }
    var dashboard by remember { mutableStateOf<StorageProductDashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var analyzingApps by remember { mutableStateOf(false) }
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var shellStatus by remember { mutableStateOf<DeviceShellStatus?>(null) }
    var shellResult by remember { mutableStateOf<AgentExecutionResult?>(null) }

    BackHandler(onBack = onBack)

    fun refreshDashboard(showLoading: Boolean = false) {
        if (showLoading) loading = true
        refreshGeneration += 1
    }

    fun openIntent(intent: Intent, fallback: Intent? = null) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching {
                fallback?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshDashboard()
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val persisted = storageRepository.persistTreeUri(uri)
            message = if (persisted) "目录授权已恢复。" else "系统没有授予可持久化目录权限。"
            refreshDashboard()
        }
    }

    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume) firstResume = false else refreshDashboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            stopSignal.set(true)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshGeneration) {
        val loaded = withContext(Dispatchers.IO) { runCatching { repository.loadDashboard() } }
        loaded.onSuccess { dashboard = it }
            .onFailure { message = it.message?.takeIf(String::isNotBlank) ?: "无法读取设备优化状态" }
        shellStatus = withContext(Dispatchers.IO) { appController.shellStatus(forceRefresh = refreshGeneration > 0) }
        loading = false
    }

    fun runAppPage(reset: Boolean) {
        if (analyzingApps) return
        stopSignal.set(false)
        analyzingApps = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (reset) repository.resetAppAnalysis()
                    repository.analyzeNextAppPage(stopSignal = stopSignal)
                }
            }
            result.onSuccess { stateResult ->
                dashboard = dashboard?.copy(
                    deviceGuard = stateResult.deviceGuard,
                    appAnalysis = stateResult,
                ) ?: repository.loadDashboard()
                message = when {
                    stateResult.blockedReason != null -> stateResult.blockedReason
                    stateResult.progress.complete -> "应用分析已完成，共处理 ${stateResult.progress.processedCount} 个用户应用。"
                    stateResult.progress.interrupted -> "分析已暂停，断点保存在 ${stateResult.progress.processedCount}/${stateResult.progress.totalCount}。"
                    else -> "已完成一批，当前进度 ${stateResult.progress.processedCount}/${stateResult.progress.totalCount}。"
                }
            }.onFailure { error ->
                message = error.message?.takeIf(String::isNotBlank) ?: "应用分析失败，已保留当前断点"
            }
            analyzingApps = false
        }
    }

    fun requestShizuku() {
        scope.launch {
            shellResult = withContext(Dispatchers.IO) { appController.requestShizukuPermission() }
            delay(500L)
            shellStatus = withContext(Dispatchers.IO) { appController.shellStatus(forceRefresh = true) }
        }
    }

    val current = dashboard
    GlassSceneScope(GlassSceneGroup.ToolsHomePage) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                PressableGlass(
                    quality = state.quality,
                    glassIntensity = state.glassIntensity,
                    motionIntensity = state.motionIntensity,
                    radius = 999,
                    modifier = Modifier.width(144.dp).height(40.dp),
                    role = GlassRole.Chip,
                    onClick = onBack,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("‹ 返回存储管理", color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DEVICE OPTIMIZE", color = OptimizeAccent.copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("设备优化", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(
                        "补全应用占用、长期未用建议、清理趋势、断点恢复、设备保护和权限诊断。",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            item {
                OptimizeInfoPanel(
                    "第四阶段边界",
                    "没有后台常驻扫描，也不会自动卸载、清数据或批量结束应用。应用建议只负责分析，最终处理仍进入 Android 系统页面；Shizuku 仅展示现有安全执行链状态。",
                    OptimizeAccent,
                )
            }
            message?.let { text -> item { OptimizeInfoPanel("当前状态", text, OptimizeSuccess) } }
            shellResult?.let { result ->
                item { OptimizeInfoPanel("增强控制结果", result.message, if (result.ok) OptimizeSuccess else OptimizeWarning) }
            }
            if (loading || current == null) {
                item { OptimizeLoadingPanel("正在读取设备状态、清理历史和应用分析断点…") }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        StorageOptimizationTab.entries.forEach { item ->
                            OptimizeFilterChip(item.label, tab == item) { tab = item }
                        }
                        OptimizeFilterChip("刷新", false) { refreshDashboard(showLoading = false) }
                    }
                }
                when (tab) {
                    StorageOptimizationTab.Overview -> {
                        item { DeviceGuardPanel(current.deviceGuard) }
                        item { OptimizationOverviewPanel(current) { tab = it } }
                        item { AppProgressSummary(current.appAnalysis) { tab = StorageOptimizationTab.Apps } }
                    }
                    StorageOptimizationTab.Apps -> {
                        item {
                            AppAnalysisControlPanel(
                                analysis = current.appAnalysis,
                                analyzing = analyzingApps,
                                onContinue = { runAppPage(reset = false) },
                                onRestart = { runAppPage(reset = true) },
                                onStop = {
                                    stopSignal.set(true)
                                    message = "正在停止；当前应用处理完成后会保存断点。"
                                },
                                onGrantUsageAccess = {
                                    openIntent(
                                        storageRepository.usageAccessIntent(),
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                    )
                                },
                            )
                        }
                        val unusedApps = current.appAnalysis.longUnusedApps
                        item { OptimizeSectionHeader("长期未用建议", "${unusedApps.size} 个 · 90 天阈值") }
                        if (unusedApps.isEmpty()) {
                            item { OptimizeEmptyPanel("当前已分析范围内没有达到 90 天阈值的应用，或尚未授予使用情况访问权限。") }
                        } else {
                            items(unusedApps.take(40), key = { "unused-${it.packageName}" }) { app ->
                                OptimizationAppCard(app, highlightUnused = true) {
                                    openAppDetails(context, app.packageName)
                                }
                            }
                        }
                        item { OptimizeSectionHeader("应用占用排行", "已分析 ${current.appAnalysis.items.size} 个") }
                        if (current.appAnalysis.items.isEmpty()) {
                            item { OptimizeEmptyPanel("开始应用分析后显示应用、数据和缓存占用。") }
                        } else {
                            items(current.appAnalysis.largestApps.take(50), key = { "large-${it.packageName}" }) { app ->
                                OptimizationAppCard(app, highlightUnused = false) {
                                    openAppDetails(context, app.packageName)
                                }
                            }
                        }
                    }
                    StorageOptimizationTab.Trends -> {
                        item { CleanupTrendSummary(current.cleanupHistory) }
                        item { OptimizeSectionHeader("最近清理趋势", "${current.cleanupTrend.size} 天") }
                        if (current.cleanupTrend.isEmpty()) {
                            item { OptimizeEmptyPanel("完成智能清理或精细整理后，这里会按日期汇总实际释放空间。") }
                        } else {
                            items(current.cleanupTrend, key = { it.day.toString() }) { point ->
                                CleanupTrendCard(point, current.cleanupTrend.maxOfOrNull { it.releasedBytes } ?: 0L)
                            }
                        }
                        item { OptimizeSectionHeader("设备空间快照", "${current.capacitySnapshots.size} 个") }
                        if (current.capacitySnapshots.isEmpty()) {
                            item { OptimizeEmptyPanel("打开设备优化页后会按时间或空间变化记录轻量快照。") }
                        } else {
                            items(current.capacitySnapshots.takeLast(20).reversed(), key = StorageCapacitySnapshot::createdAt) { snapshot ->
                                CapacitySnapshotCard(snapshot)
                            }
                        }
                        item { OptimizeSectionHeader("清理历史详情", "最近 ${current.cleanupHistory.size.coerceAtMost(20)} 次") }
                        if (current.cleanupHistory.isEmpty()) {
                            item { OptimizeEmptyPanel("暂无清理记录。") }
                        } else {
                            items(current.cleanupHistory.take(20), key = StorageCleanupHistoryEntry::id) { entry ->
                                OptimizeHistoryCard(entry)
                            }
                        }
                    }
                    StorageOptimizationTab.Health -> {
                        item {
                            PermissionHealthPanel(
                                health = current.permissions,
                                onUsageAccess = {
                                    openIntent(storageRepository.usageAccessIntent(), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                },
                                onMediaAccess = {
                                    val permissions = storageMediaPermissions()
                                    if (permissions.isNotEmpty()) mediaPermissionLauncher.launch(permissions)
                                    else openAppDetails(context, context.packageName)
                                },
                                onFolderAccess = { treeLauncher.launch(storageRepository.savedTreeUri()) },
                                onAppSettings = { openAppDetails(context, context.packageName) },
                            )
                        }
                        item {
                            ShizukuHealthPanel(
                                shellStatus = shellStatus,
                                onRequest = ::requestShizuku,
                            )
                        }
                        item { CompatibilityPanel(current.compatibility) }
                        item {
                            OptimizePrimaryAction(
                                "打开 Android 存储管理器",
                                enabled = true,
                            ) {
                                openIntent(Intent(Settings.ACTION_MANAGE_STORAGE), Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceGuardPanel(guard: StorageDeviceGuard) {
    val tone = if (guard.heavyWorkAllowed) OptimizeSuccess else OptimizeCritical
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        color = tone.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.19f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("设备负载保护", color = tone, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(guard.reason, color = Color.White.copy(alpha = 0.64f), fontSize = 10.5.sp, lineHeight = 15.sp)
                }
                Text(if (guard.heavyWorkAllowed) "可继续" else "已暂停", color = tone, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptimizeMetric("电量", guard.batteryPercent?.let { "$it%" } ?: "未知", Modifier.weight(1f))
                OptimizeMetric("供电", if (guard.charging) "充电中" else "电池", Modifier.weight(1f))
                OptimizeMetric("温度", guard.thermalLabel, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OptimizationOverviewPanel(
    dashboard: StorageProductDashboard,
    onOpen: (StorageOptimizationTab) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptimizeMetric("已用", formatOptimizeBytes(dashboard.overview.usedBytes), Modifier.weight(1f))
            OptimizeMetric("可用", formatOptimizeBytes(dashboard.overview.freeBytes), Modifier.weight(1f))
        }
        OptimizeOverviewCard(
            title = "应用占用与长期未用",
            value = "${dashboard.appAnalysis.progress.processedCount}/${dashboard.appAnalysis.progress.totalCount}",
            detail = if (dashboard.appAnalysis.progress.complete) "分析完成" else "支持分批和断点恢复",
            tone = OptimizeAccent,
        ) { onOpen(StorageOptimizationTab.Apps) }
        OptimizeOverviewCard(
            title = "清理趋势",
            value = formatOptimizeBytes(dashboard.cleanupHistory.sumOf { it.releasedBytes }),
            detail = "${dashboard.cleanupHistory.sumOf { it.deletedCount }} 个文件已核验删除",
            tone = OptimizeSuccess,
        ) { onOpen(StorageOptimizationTab.Trends) }
        OptimizeOverviewCard(
            title = "权限健康",
            value = if (dashboard.permissions.healthy) "状态正常" else "需要检查",
            detail = permissionSummary(dashboard.permissions),
            tone = if (dashboard.permissions.healthy) OptimizeSuccess else OptimizeWarning,
        ) { onOpen(StorageOptimizationTab.Health) }
    }
}

@Composable
private fun AppProgressSummary(analysis: StorageAppAnalysisState, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("应用分析进度", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("${analysis.progress.processedCount}/${analysis.progress.totalCount}", color = OptimizeAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.07f))) {
                Box(
                    Modifier.fillMaxWidth(analysis.progress.fraction.coerceIn(0f, 1f)).height(7.dp)
                        .clip(RoundedCornerShape(999.dp)).background(OptimizeAccent.copy(alpha = 0.72f)),
                )
            }
            Text(
                when {
                    analysis.progress.complete -> "完整分析已完成"
                    analysis.progress.interrupted -> "上次分析中断，可从断点继续"
                    analysis.progress.processedCount > 0 -> "断点已保存，可继续下一批"
                    else -> "尚未开始应用占用分析"
                },
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun AppAnalysisControlPanel(
    analysis: StorageAppAnalysisState,
    analyzing: Boolean,
    onContinue: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onGrantUsageAccess: () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.09f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                .background(Color(0xFF121743).copy(alpha = 0.30f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("分批应用分析", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        "每批最多 20 个应用，每处理一个应用就保存断点。",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 10.5.sp,
                    )
                }
                if (analyzing) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = OptimizeAccent)
            }
            AppProgressSummaryInline(analysis)
            if (!analysis.usageAccessGranted) {
                OptimizeInfoPanel("使用情况权限未开启", "可以分析安装包大小，但无法可靠判断最近使用时间和私有数据占用。", OptimizeWarning)
                OptimizePrimaryAction("开启使用情况访问", true, onGrantUsageAccess)
            }
            if (!analysis.deviceGuard.heavyWorkAllowed) {
                OptimizeInfoPanel("设备保护已触发", analysis.deviceGuard.reason, OptimizeCritical)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (analyzing) {
                    OptimizeSecondaryAction("暂停并保存", Modifier.weight(1f), onStop)
                } else {
                    OptimizeSecondaryAction("重新开始", Modifier.weight(1f), onRestart)
                    OptimizePrimaryAction(
                        text = when {
                            analysis.progress.complete -> "已完成"
                            analysis.progress.processedCount > 0 -> "继续下一批"
                            else -> "开始第一批"
                        },
                        enabled = !analysis.progress.complete && analysis.deviceGuard.heavyWorkAllowed,
                        modifier = Modifier.weight(1f),
                        onClick = onContinue,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppProgressSummaryInline(analysis: StorageAppAnalysisState) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("进度", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
            Text("${analysis.progress.processedCount}/${analysis.progress.totalCount}", color = OptimizeAccent, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.07f))) {
            Box(
                Modifier.fillMaxWidth(analysis.progress.fraction.coerceIn(0f, 1f)).height(7.dp)
                    .clip(RoundedCornerShape(999.dp)).background(OptimizeAccent.copy(alpha = 0.72f)),
            )
        }
    }
}

@Composable
private fun OptimizationAppCard(
    app: StorageAppOptimizationItem,
    highlightUnused: Boolean,
    onOpenSettings: () -> Unit,
) {
    val tone = if (highlightUnused) OptimizeWarning else OptimizeAccent
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpenSettings),
        shape = RoundedCornerShape(20.dp),
        color = tone.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.14f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(app.label, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, color = Color.White.copy(alpha = 0.33f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatOptimizeBytes(app.totalBytes ?: app.apkBytes), color = tone, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Text(app.suggestionReason, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, lineHeight = 14.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OptimizeTinyMetric("应用", formatOptimizeBytes(app.appBytes ?: app.apkBytes), Modifier.weight(1f))
                OptimizeTinyMetric("数据", formatOptionalBytes(app.dataBytes), Modifier.weight(1f))
                OptimizeTinyMetric("缓存", formatOptionalBytes(app.cacheBytes), Modifier.weight(1f))
            }
            Text("点击进入系统应用信息页", color = Color.White.copy(alpha = 0.34f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun CleanupTrendSummary(history: List<StorageCleanupHistoryEntry>) {
    val released = history.sumOf { it.releasedBytes }
    val deleted = history.sumOf { it.deletedCount }
    val failed = history.sumOf { it.failedCount }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OptimizeMetric("累计释放", formatOptimizeBytes(released), Modifier.weight(1f))
        OptimizeMetric("成功", deleted.toString(), Modifier.weight(1f))
        OptimizeMetric("失败", failed.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun CleanupTrendCard(point: StorageCleanupTrendPoint, maximum: Long) {
    val fraction = if (maximum > 0L) (point.releasedBytes.toFloat() / maximum.toFloat()).coerceIn(0.03f, 1f) else 0.03f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(point.day.format(DateTimeFormatter.ofPattern("MM-dd")), color = Color.White.copy(alpha = 0.72f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formatOptimizeBytes(point.releasedBytes), color = OptimizeSuccess, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.06f))) {
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(OptimizeSuccess.copy(alpha = 0.66f)))
            }
            Text("删除 ${point.deletedCount} · 失败 ${point.failedCount}", color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun CapacitySnapshotCard(snapshot: StorageCapacitySnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatOptimizeDateTime(snapshot.createdAt), color = Color.White.copy(alpha = 0.72f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Text("已用 ${formatOptimizeBytes(snapshot.usedBytes)}", color = Color.White.copy(alpha = 0.42f), fontSize = 9.5.sp)
            }
            Text("可用 ${formatOptimizeBytes(snapshot.freeBytes)}", color = OptimizeAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun OptimizeHistoryCard(entry: StorageCleanupHistoryEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(formatOptimizeDateTime(entry.createdAt), color = Color.White.copy(alpha = 0.34f), fontSize = 9.sp)
                Text("成功 ${entry.deletedCount} · 失败 ${entry.failedCount}", color = Color.White.copy(alpha = 0.42f), fontSize = 9.2.sp)
            }
            Text(formatOptimizeBytes(entry.releasedBytes), color = OptimizeSuccess, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PermissionHealthPanel(
    health: StoragePermissionHealth,
    onUsageAccess: () -> Unit,
    onMediaAccess: () -> Unit,
    onFolderAccess: () -> Unit,
    onAppSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        OptimizeSectionHeader("权限健康", if (health.healthy) "状态正常" else "需要处理")
        PermissionHealthRow(
            "使用情况访问",
            if (health.usageAccessGranted) "已授权" else "未授权",
            health.usageAccessGranted,
            onUsageAccess,
        )
        PermissionHealthRow(
            "共享媒体",
            when {
                health.selectedPhotoAccessOnly -> "仅部分照片"
                health.mediaAccessGranted -> "已授权"
                else -> "未授权"
            },
            health.mediaAccessGranted,
            onMediaAccess,
        )
        PermissionHealthRow(
            "授权目录",
            when {
                !health.authorizedFolderPresent -> "尚未选择"
                health.authorizedFolderPermissionValid -> "授权有效"
                else -> "授权已失效"
            },
            !health.authorizedFolderPresent || health.authorizedFolderPermissionValid,
            onFolderAccess,
        )
        OptimizeSecondaryAction("打开本应用系统设置", Modifier.fillMaxWidth(), onAppSettings)
    }
}

@Composable
private fun PermissionHealthRow(
    title: String,
    status: String,
    ok: Boolean,
    onRepair: () -> Unit,
) {
    val tone = if (ok) OptimizeSuccess else OptimizeWarning
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).clickable(onClick = onRepair),
        shape = RoundedCornerShape(19.dp),
        color = tone.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.15f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(status, color = tone, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (ok) "检查 ›" else "修复 ›", color = tone, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ShizukuHealthPanel(shellStatus: DeviceShellStatus?, onRequest: () -> Unit) {
    val available = shellStatus?.shizukuAvailable == true
    val granted = shellStatus?.shizukuGranted == true
    val tone = if (granted) OptimizeSuccess else OptimizeAccent
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.16f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Shizuku 可选增强", color = tone, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                when {
                    granted -> "已授权。仍只通过现有动作白名单和高风险确认链执行，不开放任意 shell 清理。"
                    available -> "服务可用但尚未授权。授权不会开启后台自动清理。"
                    else -> "当前未检测到可用服务，存储管理的基础、智能和精细整理功能不受影响。"
                },
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
            if (available && !granted) OptimizePrimaryAction("请求 Shizuku 授权", true, onRequest)
        }
    }
}

@Composable
private fun CompatibilityPanel(report: StorageCompatibilityReport) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("厂商兼容诊断", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(report.title, color = OptimizeAccent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            report.guidance.forEach { item ->
                Text("• $item", color = Color.White.copy(alpha = 0.52f), fontSize = 10.2.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun OptimizeOverviewCard(
    title: String,
    value: String,
    detail: String,
    tone: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.16f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                Text(value, color = tone, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 9.7.sp)
            }
            Text("进入 ›", color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OptimizeMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(17.dp), color = Color.White.copy(alpha = 0.055f)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.40f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OptimizeTinyMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.32f), fontSize = 8.5.sp)
        Text(value, color = Color.White.copy(alpha = 0.68f), fontSize = 9.3.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OptimizeSectionHeader(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 9.8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OptimizeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) OptimizeAccent.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, if (selected) OptimizeAccent.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.10f)),
    ) {
        Text(
            label,
            color = if (selected) OptimizeAccent else Color.White.copy(alpha = 0.64f),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun OptimizeInfoPanel(title: String, text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        color = tone.copy(alpha = 0.072f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.17f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = tone, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
            Text(text, color = Color.White.copy(alpha = 0.58f), fontSize = 10.5.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun OptimizeEmptyPanel(text: String) {
    OptimizeInfoPanel("暂无结果", text, Color.White)
}

@Composable
private fun OptimizeLoadingPanel(text: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.05f)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(21.dp), strokeWidth = 2.dp, color = OptimizeAccent)
            Text(text, color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun OptimizePrimaryAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = OptimizeAccent.copy(alpha = if (enabled) 0.13f else 0.04f),
        border = BorderStroke(1.dp, OptimizeAccent.copy(alpha = if (enabled) 0.28f else 0.08f)),
    ) {
        Text(
            text,
            color = OptimizeAccent.copy(alpha = if (enabled) 0.92f else 0.34f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(11.dp),
        )
    }
}

@Composable
private fun OptimizeSecondaryAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.70f), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(11.dp))
    }
}

private fun permissionSummary(health: StoragePermissionHealth): String {
    return buildList {
        if (!health.usageAccessGranted) add("使用情况")
        if (!health.mediaAccessGranted) add("媒体")
        if (health.authorizedFolderPresent && !health.authorizedFolderPermissionValid) add("目录授权")
    }.joinToString("、").ifBlank { "使用情况、媒体和目录授权均可用" }
}

private fun storageMediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        buildList {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        }.toTypedArray()
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun openAppDetails(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun formatOptimizeBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    val digits = if (value >= 100 || index == 0) 0 else 1
    return String.format(Locale.CHINA, "%.${digits}f %s", value, units[index])
}

private fun formatOptionalBytes(bytes: Long?): String = bytes?.let(::formatOptimizeBytes) ?: "需授权"

private fun formatOptimizeDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

private val OptimizeAccent = Color(0xFF8DF9EA)
private val OptimizeSuccess = Color(0xFF83F3B8)
private val OptimizeWarning = Color(0xFFFFCA72)
private val OptimizeCritical = Color(0xFFFF7F8D)
