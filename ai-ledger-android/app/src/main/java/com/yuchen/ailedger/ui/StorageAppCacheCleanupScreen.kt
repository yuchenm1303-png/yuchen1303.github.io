package com.yuchen.ailedger.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.AppCacheUsage
import com.yuchen.ailedger.service.DeviceShellStatus
import com.yuchen.ailedger.service.StorageAppCacheCleanupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppCacheCleanupMethod {
    System,
    Enhanced,
}

@Composable
internal fun StorageAppCacheCleanupScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { StorageAppCacheCleanupRepository(context.applicationContext) }
    var ranking by remember { mutableStateOf<List<AppCacheUsage>>(emptyList()) }
    var shellStatus by remember { mutableStateOf<DeviceShellStatus?>(null) }
    var usageGranted by remember { mutableStateOf(repository.hasUsageAccess()) }
    var allFilesGranted by remember { mutableStateOf(repository.hasAllFilesAccess()) }
    var loading by remember { mutableStateOf(true) }
    var operationRunning by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmationMethod by remember { mutableStateOf<AppCacheCleanupMethod?>(null) }
    var systemCleanupBeforeFreeBytes by remember { mutableStateOf<Long?>(null) }

    BackHandler(onBack = onBack)

    fun refresh(showLoading: Boolean = false) {
        if (showLoading) loading = true
        scope.launch {
            val currentUsageGranted = repository.hasUsageAccess()
            val currentAllFilesGranted = repository.hasAllFilesAccess()
            val loaded = withContext(Dispatchers.IO) {
                val apps = if (currentUsageGranted) repository.loadRanking(forceRefresh = true) else emptyList()
                apps to repository.shellStatus(forceRefresh = true)
            }
            usageGranted = currentUsageGranted
            allFilesGranted = currentAllFilesGranted
            ranking = loaded.first
            shellStatus = loaded.second
            loading = false
        }
    }

    fun openSystemStorage() {
        val primary = Intent(StorageManager.ACTION_MANAGE_STORAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(primary) }
            .recoverCatching {
                context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
    }

    fun openAppStorage(packageName: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    val usageAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh(showLoading = true)
    }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allFilesGranted = repository.hasAllFilesAccess()
        message = if (allFilesGranted) {
            "所有文件访问已开启，系统一键缓存清理现在可用。"
        } else {
            "未开启所有文件访问，系统一键缓存清理仍不可用。"
        }
        refresh()
    }
    val systemCacheCleanupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            val currentUsageGranted = repository.hasUsageAccess()
            val updatedRanking = withContext(Dispatchers.IO) {
                if (currentUsageGranted) repository.loadRanking(forceRefresh = true) else emptyList()
            }
            val afterFreeBytes = withContext(Dispatchers.IO) { repository.deviceFreeBytes() }
            val releasedBytes = systemCleanupBeforeFreeBytes?.let { before -> (afterFreeBytes - before).coerceAtLeast(0L) }
            usageGranted = currentUsageGranted
            ranking = updatedRanking
            operationRunning = false
            message = when (result.resultCode) {
                Activity.RESULT_OK -> when {
                    releasedBytes == null -> "Android 系统已完成全应用缓存清理；无法读取清理前空间快照，因此未计算实际释放量。"
                    releasedBytes > 0L -> "Android 系统已完成全应用缓存清理，设备可用空间增加 ${formatStorageBytes(releasedBytes)}。"
                    else -> "Android 系统返回缓存清理完成，但设备可用空间暂未增加；部分缓存可能已被系统保留或空间统计尚未刷新。"
                }
                Activity.RESULT_CANCELED -> "已取消系统缓存清理，没有继续删除应用缓存。"
                else -> "系统缓存清理返回错误代码 ${result.resultCode}，请稍后重试或使用 Shizuku 增强回收。"
            }
            systemCleanupBeforeFreeBytes = null
        }
    }

    fun requestAllFilesAccess() {
        runCatching { allFilesAccessLauncher.launch(repository.allFilesAccessIntent()) }
            .onFailure { message = it.message?.takeIf(String::isNotBlank) ?: "无法打开所有文件访问设置。" }
    }

    fun requestShizuku() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { repository.requestShizukuPermission() }
            message = result.error.ifBlank { result.output }
            delay(650L)
            shellStatus = withContext(Dispatchers.IO) { repository.shellStatus(forceRefresh = true) }
        }
    }

    fun startSystemCacheCleanup() {
        if (operationRunning) return
        if (!repository.canUseSystemCacheCleanup()) {
            requestAllFilesAccess()
            return
        }
        operationRunning = true
        message = "正在打开 Android 全应用缓存清理确认…"
        scope.launch {
            systemCleanupBeforeFreeBytes = withContext(Dispatchers.IO) { repository.deviceFreeBytes() }
            runCatching { systemCacheCleanupLauncher.launch(repository.systemCacheCleanupIntent()) }
                .onFailure { error ->
                    operationRunning = false
                    systemCleanupBeforeFreeBytes = null
                    message = error.message?.takeIf(String::isNotBlank) ?: "当前系统没有提供全应用缓存清理界面。"
                }
        }
    }

    fun clearAllCachesEnhanced() {
        if (operationRunning) return
        operationRunning = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { repository.clearAllAppCachesEnhanced() }
            message = result.message
            val currentUsageGranted = repository.hasUsageAccess()
            ranking = withContext(Dispatchers.IO) {
                if (currentUsageGranted) repository.loadRanking(forceRefresh = true) else emptyList()
            }
            usageGranted = currentUsageGranted
            shellStatus = withContext(Dispatchers.IO) { repository.shellStatus(forceRefresh = true) }
            operationRunning = false
        }
    }

    LaunchedEffect(Unit) { refresh(showLoading = true) }

    val displayedRanking by remember(ranking, expanded) {
        derivedStateOf { storagePreviewItems(ranking, expanded, STORAGE_FILE_PREVIEW_COUNT) }
    }
    val totalCache by remember(ranking) { derivedStateOf { ranking.sumOf(AppCacheUsage::cacheBytes) } }
    val enhancedAvailable = shellStatus?.isAdbShellLike == true
    val systemCleanupAvailable = repository.canUseSystemCacheCleanup()

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
                    modifier = Modifier.width(136.dp).height(40.dp),
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
                    Text("APP CACHE CLEANUP", color = StorageWarning.copy(alpha = 0.80f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("全机应用缓存", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(
                        "一键调用 Android 的全应用缓存清理，并提供 Shizuku/ADB Shell 增强回收和清理后可用空间核验。",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            item {
                StorageNoticePanel(
                    title = "缓存统计和系统页面口径可能不同",
                    text = "应用列表里的数值来自 Android StorageStatsManager，部分系统会把可清缓存、外部缓存或应用内部临时数据合并成不同口径；系统设置页的“清空缓存”数值才是该厂商界面的清理按钮口径。实际释放空间现在以清理后设备可用空间变化为准。",
                    tone = StorageWarning,
                )
            }
            message?.let { item { StorageNoticePanel("当前状态", it, if (operationRunning) StorageWarning else StorageSuccess) } }
            item {
                StorageSection("权限与执行方式") {
                    StorageAccessRow(
                        title = "全应用缓存清理权限",
                        detail = if (allFilesGranted) {
                            "已开启所有文件访问，可以调用 Android 官方的一键清理全部应用缓存。"
                        } else {
                            "Android 要求所有文件访问权限，才能启动官方的全应用缓存清理流程。"
                        },
                        granted = allFilesGranted,
                        actionText = if (allFilesGranted) "管理权限" else "开启权限",
                        onAction = ::requestAllFilesAccess,
                    )
                    StorageAccessRow(
                        title = "缓存统计权限",
                        detail = if (usageGranted) {
                            "已获得使用情况访问权，可以显示系统统计口径的缓存排行。"
                        } else {
                            "清理仍可执行，但无法显示每个应用的统计口径缓存。"
                        },
                        granted = usageGranted,
                        actionText = if (usageGranted) "已授权" else "去授权",
                        onAction = {
                            runCatching { usageAccessLauncher.launch(repository.usageAccessIntent()) }
                                .recoverCatching {
                                    usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                        },
                    )
                    StorageAccessRow(
                        title = "Shizuku / ADB Shell 增强回收",
                        detail = shellStatus?.message ?: "正在检测增强权限…",
                        granted = enhancedAvailable,
                        actionText = when {
                            enhancedAvailable -> "可用"
                            shellStatus?.shizukuAvailable == true -> "请求授权"
                            else -> "检测状态"
                        },
                        onAction = {
                            if (shellStatus?.shizukuAvailable == true && !enhancedAvailable) requestShizuku() else refresh()
                        },
                    )
                    StorageSmallAction("打开系统存储管理器", Modifier, ::openSystemStorage)
                }
            }
            item {
                StorageSection("一键清理") {
                    StorageMetricRow("已统计应用", if (usageGranted) ranking.size.toString() else "需要统计权限")
                    StorageMetricRow("统计口径缓存", if (usageGranted) formatStorageBytes(totalCache) else "未授权")
                    StoragePrimaryAction(
                        text = when {
                            operationRunning -> "正在清理全机应用缓存…"
                            systemCleanupAvailable -> "一键清理所有应用缓存"
                            else -> "开启权限并启用一键清理"
                        },
                        enabled = !operationRunning,
                    ) {
                        if (systemCleanupAvailable) confirmationMethod = AppCacheCleanupMethod.System else requestAllFilesAccess()
                    }
                    if (enhancedAvailable) {
                        StorageSmallAction(
                            "Shizuku 增强直接回收",
                            Modifier,
                        ) { confirmationMethod = AppCacheCleanupMethod.Enhanced }
                    }
                    StorageSmallAction("重新统计应用缓存", Modifier) { refresh(showLoading = true) }
                    StorageLongListControls(
                        totalCount = ranking.size,
                        expanded = expanded,
                        previewCount = STORAGE_FILE_PREVIEW_COUNT,
                        onToggleExpanded = { expanded = !expanded },
                        tone = StorageWarning,
                    )
                }
            }
            when {
                loading -> item { StorageLoadingPanel("正在读取应用缓存统计和清理权限状态…") }
                !usageGranted -> item { StorageEmptyPanel("授权使用情况访问后，可查看每个应用的缓存统计明细；上方系统一键清理不依赖这项统计权限。") }
                ranking.isEmpty() -> item { StorageEmptyPanel("当前没有读取到可展示的应用缓存统计。") }
                else -> {
                    item { OptimizeSectionHeader("应用缓存统计明细", "点击单个应用可进入系统详情，以厂商系统页面数值为准") }
                    items(displayedRanking, key = AppCacheUsage::packageName) { app ->
                        AppCacheCard(app = app, onOpen = { openAppStorage(app.packageName) })
                    }
                }
            }
        }
    }

    confirmationMethod?.let { method ->
        AlertDialog(
            onDismissRequest = { confirmationMethod = null },
            containerColor = Color(0xFF10163A).copy(alpha = 0.99f),
            tonalElevation = 0.dp,
            title = {
                Text(
                    if (method == AppCacheCleanupMethod.System) "确认一键清理所有应用缓存" else "确认 Shizuku 增强缓存回收",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (usageGranted) "当前系统统计口径约 ${formatStorageBytes(totalCache)}，实际释放空间以清理后可用空间变化为准。" else "当前未获得缓存统计权限，但仍可执行系统缓存清理。",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (method == AppCacheCleanupMethod.System) {
                            "接下来会进入 Android 官方确认界面，并请求清理设备上所有应用的可清缓存。"
                        } else {
                            "接下来会通过已授权的 Shizuku/ADB Shell 执行固定的 pm trim-caches 命令。"
                        },
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                    )
                    Text("不会清除应用数据、账号、数据库或用户文件；部分应用下次打开时会重新生成或下载缓存。", color = StorageWarning, fontSize = 11.sp, lineHeight = 16.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmationMethod = null
                        if (method == AppCacheCleanupMethod.System) startSystemCacheCleanup() else clearAllCachesEnhanced()
                    },
                ) { Text("确认清理缓存", color = StorageCritical, fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { confirmationMethod = null }) {
                    Text("取消", color = Color.White.copy(alpha = 0.64f), fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}
