package com.yuchen.ailedger.ui

import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
    var loading by remember { mutableStateOf(true) }
    var operationRunning by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    fun refresh(showLoading: Boolean = false) {
        if (showLoading) loading = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                repository.loadRanking(forceRefresh = true) to repository.shellStatus(forceRefresh = true)
            }
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

    fun requestShizuku() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { repository.requestShizukuPermission() }
            message = result.error.ifBlank { result.output }
            delay(650L)
            shellStatus = withContext(Dispatchers.IO) { repository.shellStatus(forceRefresh = true) }
        }
    }

    fun clearAllCaches() {
        if (operationRunning) return
        operationRunning = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { repository.clearAllAppCaches() }
            message = result.message
            ranking = withContext(Dispatchers.IO) { repository.loadRanking(forceRefresh = true) }
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
    val usageGranted = repository.hasUsageAccess()

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
                    Text("APP CACHE", color = StorageWarning.copy(alpha = 0.80f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("全机应用缓存", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(
                        "统计各应用缓存，并在 Shizuku/ADB Shell 授权后请求 Android 回收全机可清理缓存。",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            item {
                StorageNoticePanel(
                    title = "不会清除应用数据",
                    text = "增强清理固定使用 Android 的 pm trim-caches，只回收缓存，不执行 pm clear，不删除登录状态、数据库或用户文件。清理后部分应用可能需要重新下载图片和离线资源。",
                    tone = StorageWarning,
                )
            }
            message?.let { item { StorageNoticePanel("当前状态", it, if (operationRunning) StorageWarning else StorageSuccess) } }
            item {
                StorageSection("权限与执行方式") {
                    StorageAccessRow(
                        title = "缓存统计权限",
                        detail = if (usageGranted) "已获得使用情况访问权，可以统计并核验各应用缓存。" else "未授权时仍可请求系统回收缓存，但无法准确显示清理前后体积。",
                        granted = usageGranted,
                        actionText = if (usageGranted) "已授权" else "去授权",
                        onAction = {
                            runCatching { context.startActivity(repository.usageAccessIntent()) }
                                .recoverCatching {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                        },
                    )
                    StorageAccessRow(
                        title = "Shizuku / ADB Shell",
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
                StorageSection("缓存概览") {
                    StorageMetricRow("已统计应用", ranking.size.toString())
                    StorageMetricRow("当前缓存合计", if (usageGranted) formatStorageBytes(totalCache) else "需要统计权限")
                    StoragePrimaryAction(
                        text = when {
                            operationRunning -> "正在请求系统回收缓存…"
                            enhancedAvailable -> "清理全机可回收缓存"
                            else -> "需要 Shizuku / ADB Shell"
                        },
                        enabled = enhancedAvailable && !operationRunning,
                    ) { showConfirmation = true }
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
                loading -> item { StorageLoadingPanel("正在读取应用缓存统计和增强权限状态…") }
                !usageGranted -> item { StorageEmptyPanel("授权使用情况访问后，可查看各应用缓存排行并核验实际释放空间。") }
                ranking.isEmpty() -> item { StorageEmptyPanel("当前没有读取到可展示的应用缓存。") }
                else -> items(displayedRanking, key = AppCacheUsage::packageName) { app ->
                    AppCacheCard(app = app, onOpen = { openAppStorage(app.packageName) })
                }
            }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            containerColor = Color(0xFF10163A).copy(alpha = 0.99f),
            tonalElevation = 0.dp,
            title = { Text("确认清理全机应用缓存", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (usageGranted) "当前统计到约 ${formatStorageBytes(totalCache)} 应用缓存。" else "当前未获得缓存统计权限，无法预估释放空间。",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text("操作只请求 Android 回收可删除缓存，不会清除登录状态、数据库和用户文件。部分应用下次打开时会重新生成或下载缓存。", color = Color.White.copy(alpha = 0.60f), fontSize = 11.5.sp, lineHeight = 17.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                        clearAllCaches()
                    },
                ) { Text("确认清理缓存", color = StorageCritical, fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) { Text("取消", color = Color.White.copy(alpha = 0.64f), fontWeight = FontWeight.Bold) }
            },
        )
    }
}
