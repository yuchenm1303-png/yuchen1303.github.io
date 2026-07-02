package com.yuchen.ailedger.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.yuchen.ailedger.service.AppManagementRepository
import com.yuchen.ailedger.service.DeviceShellStatus
import com.yuchen.ailedger.service.ManagedAppAction
import com.yuchen.ailedger.service.ManagedAppDetails
import com.yuchen.ailedger.service.ManagedAppSummary
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ManagedAppFilter(val label: String) {
    All("全部"), User("用户应用"), System("系统应用"), Disabled("已禁用"), Launchable("可打开")
}

private enum class ManagedAppSort(val label: String) {
    Name("按名称"), Size("按安装包大小")
}

internal data class PendingManagedAppAction(
    val action: ManagedAppAction,
    val app: ManagedAppSummary,
)

@Composable
fun AppManagementScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { AppManagementRepository(context.applicationContext) }
    val controller = remember(context) { AppManagementController(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<ManagedAppSummary>>(emptyList()) }
    var shellStatus by remember { mutableStateOf<DeviceShellStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<PendingManagedAppAction?>(null) }
    var runningAction by remember { mutableStateOf<ManagedAppAction?>(null) }
    var actionResult by remember { mutableStateOf<AgentExecutionResult?>(null) }

    BackHandler {
        if (selectedPackage != null) selectedPackage = null else onBack()
    }

    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume) firstResume = false else refreshGeneration += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshGeneration) {
        loading = apps.isEmpty()
        loadError = null
        val loaded = withContext(Dispatchers.IO) { runCatching { repository.loadApps() } }
        loaded.onSuccess { apps = it }
            .onFailure { loadError = it.message?.takeIf(String::isNotBlank) ?: "无法读取应用列表" }
        shellStatus = withContext(Dispatchers.IO) { controller.shellStatus(forceRefresh = refreshGeneration > 0) }
        loading = false
    }

    fun executeAction(action: ManagedAppAction, app: ManagedAppSummary, confirmed: Boolean) {
        if (runningAction != null) return
        runningAction = action
        actionResult = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { controller.execute(action, app, confirmed) }
            runningAction = null
            actionResult = result
            if (result.ok && action in stateChangingActions) refreshGeneration += 1
        }
    }

    fun requestAction(action: ManagedAppAction, app: ManagedAppSummary) {
        if (action.requiresConfirmation) pendingAction = PendingManagedAppAction(action, app)
        else executeAction(action, app, confirmed = false)
    }

    fun requestShizuku() {
        if (runningAction != null) return
        scope.launch {
            actionResult = withContext(Dispatchers.IO) { controller.requestShizukuPermission() }
            delay(700L)
            shellStatus = withContext(Dispatchers.IO) { controller.shellStatus(forceRefresh = true) }
        }
    }

    GlassSceneScope(GlassSceneGroup.ToolsHomePage) {
        val packageName = selectedPackage
        if (packageName == null) {
            ManagedAppListPage(
                state = state,
                apps = apps,
                shellStatus = shellStatus,
                loading = loading,
                error = loadError,
                repository = repository,
                onBack = onBack,
                onRefresh = { refreshGeneration += 1 },
                onOpenApp = { selectedPackage = it.packageName },
                onRequestShizuku = ::requestShizuku,
                actionResult = actionResult,
            )
        } else {
            ManagedAppDetailsPage(
                state = state,
                packageName = packageName,
                refreshGeneration = refreshGeneration,
                repository = repository,
                shellStatus = shellStatus,
                runningAction = runningAction,
                actionResult = actionResult,
                onBack = { selectedPackage = null },
                onAction = ::requestAction,
                onGrantStorageAccess = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                        .recoverCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                },
                onRequestShizuku = ::requestShizuku,
            )
        }
    }

    pendingAction?.let { pending ->
        ManagedAppConfirmationDialog(
            pending = pending,
            onDismiss = { pendingAction = null },
            onConfirm = {
                pendingAction = null
                executeAction(pending.action, pending.app, confirmed = true)
            },
        )
    }
}

@Composable
private fun ManagedAppListPage(
    state: AssistantUiState,
    apps: List<ManagedAppSummary>,
    shellStatus: DeviceShellStatus?,
    loading: Boolean,
    error: String?,
    repository: AppManagementRepository,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenApp: (ManagedAppSummary) -> Unit,
    onRequestShizuku: () -> Unit,
    actionResult: AgentExecutionResult?,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ManagedAppFilter.All) }
    var sort by remember { mutableStateOf(ManagedAppSort.Name) }
    val filteredApps by remember(apps, query, filter, sort) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase(Locale.ROOT)
            apps.asSequence()
                .filter { app ->
                    cleanQuery.isBlank() || app.label.lowercase(Locale.ROOT).contains(cleanQuery) ||
                        app.packageName.lowercase(Locale.ROOT).contains(cleanQuery)
                }
                .filter { app ->
                    when (filter) {
                        ManagedAppFilter.All -> true
                        ManagedAppFilter.User -> !app.isSystemApp
                        ManagedAppFilter.System -> app.isSystemApp
                        ManagedAppFilter.Disabled -> !app.isEnabled
                        ManagedAppFilter.Launchable -> app.isLaunchable
                    }
                }
                .let { sequence ->
                    when (sort) {
                        ManagedAppSort.Name -> sequence.sortedWith(
                            compareBy<ManagedAppSummary> { it.label.lowercase(Locale.ROOT) }
                                .thenBy { it.packageName },
                        )
                        ManagedAppSort.Size -> sequence.sortedByDescending { it.apkBytes }
                    }
                }
                .toList()
        }
    }
    val userCount = remember(apps) { apps.count { !it.isSystemApp } }
    val systemCount = remember(apps) { apps.count { it.isSystemApp } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item { AppManagementBackButton(state, "‹ 返回功能", onBack) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("APP CONTROL", color = AppAccent.copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("应用控制", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                Text(
                    "查看所有应用的详细信息，并通过统一安全执行链管理存储、权限和运行状态。",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
        item {
            FrostInfoGlassPanel(
                radius = 18f,
                backdropAlpha = 1f,
                frostAlpha = 0.09f,
                dimAlpha = 0f,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                        .background(Color(0xFF111742).copy(alpha = 0.28f)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSummaryMetric("全部", apps.size.toString(), Modifier.weight(1f))
                        AppSummaryMetric("用户", userCount.toString(), Modifier.weight(1f))
                        AppSummaryMetric("系统", systemCount.toString(), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("增强控制", color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            Text(shellStatusLabel(shellStatus), color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        if (shellStatus?.shizukuAvailable == true && !shellStatus.shizukuGranted) {
                            AppCompactAction("请求授权", onRequestShizuku)
                        }
                    }
                }
            }
        }
        actionResult?.let { result -> item { AppActionResultPanel(result) } }
        item { AppSearchField(query = query, onQueryChange = { query = it }) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    ManagedAppFilter.entries.forEach { item ->
                        AppSelectionChip(item.label, selected = filter == item) { filter = item }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    ManagedAppSort.entries.forEach { item ->
                        AppSelectionChip(item.label, selected = sort == item) { sort = item }
                    }
                    AppSelectionChip("刷新", selected = false, onClick = onRefresh)
                }
            }
        }
        when {
            loading -> item { AppLoadingPanel("正在读取已安装应用…") }
            error != null -> item { AppErrorPanel(error, onRefresh) }
            filteredApps.isEmpty() -> item { AppEmptyPanel("没有找到符合条件的应用") }
            else -> items(filteredApps, key = { it.packageName }) { app ->
                ManagedAppCard(app, state, repository) { onOpenApp(app) }
            }
        }
    }
}

@Composable
private fun ManagedAppDetailsPage(
    state: AssistantUiState,
    packageName: String,
    refreshGeneration: Int,
    repository: AppManagementRepository,
    shellStatus: DeviceShellStatus?,
    runningAction: ManagedAppAction?,
    actionResult: AgentExecutionResult?,
    onBack: () -> Unit,
    onAction: (ManagedAppAction, ManagedAppSummary) -> Unit,
    onGrantStorageAccess: () -> Unit,
    onRequestShizuku: () -> Unit,
) {
    var details by remember(packageName) { mutableStateOf<ManagedAppDetails?>(null) }
    var loading by remember(packageName) { mutableStateOf(true) }
    var error by remember(packageName) { mutableStateOf<String?>(null) }

    LaunchedEffect(packageName, refreshGeneration) {
        loading = true
        error = null
        val loaded = withContext(Dispatchers.IO) { runCatching { repository.loadDetails(packageName) } }
        loaded.onSuccess { value ->
            details = value
            if (value == null) error = "应用可能已经被卸载，无法继续读取详情。"
        }.onFailure { error = it.message?.takeIf(String::isNotBlank) ?: "读取应用详情失败" }
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item { AppManagementBackButton(state, "‹ 返回列表", onBack) }
        when {
            loading -> item { AppLoadingPanel("正在读取应用详情…") }
            error != null -> item { AppErrorPanel(error.orEmpty(), onBack) }
            details != null -> {
                val appDetails = requireNotNull(details)
                val app = appDetails.summary
                item { ManagedAppHero(app, appDetails, repository) }
                if (app.isProtected) {
                    item { AppNoticePanel("系统保护已开启", app.protectionReason, AppWarning) }
                }
                actionResult?.let { result -> item { AppActionResultPanel(result) } }
                item {
                    AppDetailSection("基本信息") {
                        AppDetailRow("应用类型", if (app.isSystemApp) "系统应用" else "用户应用")
                        AppDetailRow("当前状态", if (app.isEnabled) "已启用" else "已禁用")
                        AppDetailRow("桌面入口", if (app.isLaunchable) "可直接打开" else "没有 Launcher 入口")
                        AppDetailRow("版本", "${appDetails.versionName}（${appDetails.versionCode}）")
                        AppDetailRow("SDK", "min ${appDetails.minSdk} · target ${appDetails.targetSdk}")
                        AppDetailRow("UID", app.uid.toString())
                        AppDetailRow("安装时间", formatAppDate(appDetails.firstInstallTime))
                        AppDetailRow("更新时间", formatAppDate(appDetails.lastUpdateTime))
                        AppDetailRow("安装来源", appDetails.installerPackage)
                    }
                }
                item {
                    AppDetailSection("存储占用") {
                        AppDetailRow("安装包", formatBytes(appDetails.storage.apkBytes))
                        AppDetailRow("应用文件", formatOptionalBytes(appDetails.storage.appBytes))
                        AppDetailRow("用户数据", formatOptionalBytes(appDetails.storage.dataBytes))
                        AppDetailRow("缓存", formatOptionalBytes(appDetails.storage.cacheBytes))
                        AppDetailRow("应用与数据合计", formatOptionalBytes(appDetails.storage.totalPrivateBytes))
                        if (!appDetails.storageAccessGranted) {
                            Spacer(Modifier.height(4.dp))
                            AppInlineButton("授权读取精确存储占用", onGrantStorageAccess)
                        }
                    }
                }
                item {
                    AppNoticePanel(
                        title = "清缓存与清数据必须分开",
                        text = "“管理存储与缓存”会进入 Android 系统页面安全处理缓存；“清除全部数据”会删除账号登录、设置和本地数据库，不能当作普通垃圾清理。",
                        tone = AppAccent,
                    )
                }
                item {
                    AppDetailSection("安装路径") {
                        AppDetailRow("主 APK", appDetails.sourceDir.ifBlank { "未知" })
                        AppDetailRow("分包数量", appDetails.splitApkCount.toString())
                        AppDetailRow("数据目录", appDetails.dataDir.ifBlank { "未知" })
                        AppDetailRow("原生库目录", appDetails.nativeLibraryDir.ifBlank { "未知" })
                    }
                }
                item {
                    AppDetailSection("日常操作") {
                        actionRows(dailyActions, app, runningAction, onAction)
                    }
                }
                item {
                    AppDetailSection("增强控制") {
                        Text(shellStatusLabel(shellStatus), color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, lineHeight = 16.sp)
                        if (shellStatus?.shizukuAvailable == true && !shellStatus.shizukuGranted) {
                            Spacer(Modifier.height(7.dp))
                            AppInlineButton("请求 Shizuku 授权", onRequestShizuku)
                            Spacer(Modifier.height(7.dp))
                        }
                        actionRows(
                            listOf(ManagedAppAction.ForceStop, if (app.isEnabled) ManagedAppAction.Disable else ManagedAppAction.Enable),
                            app,
                            runningAction,
                            onAction,
                        )
                    }
                }
                item {
                    AppDetailSection("危险操作") {
                        actionRows(listOf(ManagedAppAction.ClearData, ManagedAppAction.Uninstall), app, runningAction, onAction)
                    }
                }
                item {
                    AppDetailSection("权限清单 · ${appDetails.permissions.size}") {
                        if (appDetails.permissions.isEmpty()) {
                            Text("该应用没有声明可读取的权限。", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp)
                        }
                    }
                }
                items(appDetails.permissions, key = { it.name }) { permission -> ManagedPermissionCard(permission) }
            }
        }
    }
}
