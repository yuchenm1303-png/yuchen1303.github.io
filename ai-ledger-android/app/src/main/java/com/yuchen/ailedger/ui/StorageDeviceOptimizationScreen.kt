package com.yuchen.ailedger.ui

import android.content.Intent
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.AgentExecutionResult
import com.yuchen.ailedger.service.AppManagementController
import com.yuchen.ailedger.service.DeviceShellStatus
import com.yuchen.ailedger.service.StorageCapacitySnapshot
import com.yuchen.ailedger.service.StorageCleanupHistoryEntry
import com.yuchen.ailedger.service.StorageManagementRepository
import com.yuchen.ailedger.service.StorageProductDashboard
import com.yuchen.ailedger.service.StorageProductizationRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class StorageOptimizationTab(val label: String) {
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
                    else -> "当前进度 ${stateResult.progress.processedCount}/${stateResult.progress.totalCount}。"
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
                        "完整分析应用占用、长期未用建议、清理趋势、断点恢复和权限状态。",
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
                item { OptimizeLoadingPanel("正在读取设备状态、全部清理历史和应用分析断点…") }
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
                            item { OptimizeEmptyPanel("当前完整分析结果中没有达到 90 天阈值的应用，或尚未授予使用情况访问权限。") }
                        } else {
                            items(unusedApps, key = { "unused-${it.packageName}" }) { app ->
                                OptimizationAppCard(app, highlightUnused = true) {
                                    openAppDetails(context, app.packageName)
                                }
                            }
                        }
                        item { OptimizeSectionHeader("应用占用排行", "已分析 ${current.appAnalysis.items.size} 个") }
                        if (current.appAnalysis.items.isEmpty()) {
                            item { OptimizeEmptyPanel("开始应用分析后显示应用、数据和缓存占用。") }
                        } else {
                            items(current.appAnalysis.largestApps, key = { "large-${it.packageName}" }) { app ->
                                OptimizationAppCard(app, highlightUnused = false) {
                                    openAppDetails(context, app.packageName)
                                }
                            }
                        }
                    }
                    StorageOptimizationTab.Trends -> {
                        item { CleanupTrendSummary(current.cleanupHistory) }
                        item { OptimizeSectionHeader("清理趋势", "${current.cleanupTrend.size} 天") }
                        if (current.cleanupTrend.isEmpty()) {
                            item { OptimizeEmptyPanel("完成智能清理或精细整理后，这里会按日期汇总实际释放空间。") }
                        } else {
                            items(current.cleanupTrend, key = { it.day.toString() }) { point ->
                                CleanupTrendCard(point, current.cleanupTrend.maxOfOrNull { it.releasedBytes } ?: 0L)
                            }
                        }
                        item { OptimizeSectionHeader("设备空间快照", "${current.capacitySnapshots.size} 个") }
                        if (current.capacitySnapshots.isEmpty()) {
                            item { OptimizeEmptyPanel("打开设备优化页后会记录空间快照。") }
                        } else {
                            items(current.capacitySnapshots.asReversed(), key = StorageCapacitySnapshot::createdAt) { snapshot ->
                                CapacitySnapshotCard(snapshot)
                            }
                        }
                        item { OptimizeSectionHeader("清理历史详情", "共 ${current.cleanupHistory.size} 次") }
                        if (current.cleanupHistory.isEmpty()) {
                            item { OptimizeEmptyPanel("暂无清理记录。") }
                        } else {
                            items(current.cleanupHistory, key = StorageCleanupHistoryEntry::id) { entry ->
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
                                openIntent(
                                    Intent(StorageManager.ACTION_MANAGE_STORAGE),
                                    Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
