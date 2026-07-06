package com.yuchen.ailedger

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.AgentExecutionResult
import com.yuchen.ailedger.service.AppControlInsights
import com.yuchen.ailedger.service.AppControlInsightsRepository
import com.yuchen.ailedger.service.AppManagementActionPolicy
import com.yuchen.ailedger.service.AppManagementController
import com.yuchen.ailedger.service.AppManagementRepository
import com.yuchen.ailedger.service.ManagedAppAction
import com.yuchen.ailedger.service.ManagedAppActionTone
import com.yuchen.ailedger.service.ManagedAppDetails
import com.yuchen.ailedger.service.ManagedAppSummary
import com.yuchen.ailedger.service.appControlDurationLabel
import com.yuchen.ailedger.service.appControlHumanBytes
import com.yuchen.ailedger.service.appControlUsageLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class NativeAppControlFilter(val title: String) {
    Optimize("优化"),
    Running("后台"),
    Storage("存储"),
    Risk("风险"),
    All("全部"),
}

@Composable
fun NativeAppControlPanel(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { AppManagementRepository(context) }
    val insightsRepository = remember(context) { AppControlInsightsRepository(context) }
    val controller = remember(context) { AppManagementController(context) }

    var loading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<ManagedAppSummary>>(emptyList()) }
    var insights by remember { mutableStateOf<AppControlInsights?>(null) }
    var filter by remember { mutableStateOf(NativeAppControlFilter.Optimize) }
    var query by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var selectedDetails by remember { mutableStateOf<ManagedAppDetails?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<Pair<ManagedAppAction, ManagedAppSummary>?>(null) }
    var lastResult by remember { mutableStateOf<String?>(null) }

    fun refreshData() {
        scope.launch {
            loading = true
            val loaded = withContext(Dispatchers.IO) { repository.loadApps() }
            val loadedInsights = withContext(Dispatchers.IO) { insightsRepository.loadInsights(loaded) }
            apps = loaded
            insights = loadedInsights
            loading = false
        }
    }

    fun executeAction(action: ManagedAppAction, app: ManagedAppSummary, confirmed: Boolean) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                controller.execute(action = action, app = app, confirmed = confirmed)
            }
            lastResult = result.uiMessage(action, app)
            pendingAction = null
            if (action == ManagedAppAction.ForceStop || action == ManagedAppAction.Disable || action == ManagedAppAction.Enable || action == ManagedAppAction.Uninstall) {
                refreshData()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    LaunchedEffect(selectedPackage) {
        val packageName = selectedPackage
        selectedDetails = null
        if (packageName != null) {
            selectedDetails = withContext(Dispatchers.IO) { repository.loadDetails(packageName) }
        }
    }

    val currentInsights = insights
    val signals = currentInsights?.signals.orEmpty()
    val visibleSignals = remember(signals, filter, query) {
        val needle = query.trim().lowercase(Locale.ROOT)
        signals.filter { signal ->
            val app = signal.app
            val matchesQuery = needle.isBlank() ||
                app.label.lowercase(Locale.ROOT).contains(needle) ||
                app.packageName.lowercase(Locale.ROOT).contains(needle)
            val matchesFilter = when (filter) {
                NativeAppControlFilter.Optimize -> signal.cleanCandidate || signal.lowUseButActive || signal.storageHeavy
                NativeAppControlFilter.Running -> signal.runtime != null
                NativeAppControlFilter.Storage -> signal.storageHeavy
                NativeAppControlFilter.Risk -> signal.lowUseButActive || app.isProtected || !app.isEnabled
                NativeAppControlFilter.All -> true
            }
            matchesQuery && matchesFilter
        }.take(120)
    }

    NativeAppControlSurface(
        modifier = modifier,
        headerAction = { NativeAppTinyButton("返回", onBack) },
    ) {
        item {
            NativeAppControlHeader(
                loading = loading,
                dashboard = currentInsights?.dashboard,
                onRefresh = ::refreshData,
                onUsageAccess = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                onShizuku = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { controller.requestShizukuPermission() }
                        lastResult = result.message.ifBlank { result.diagnostics }
                    }
                },
            )
        }

        lastResult?.let { message ->
            item { NativeAppResultCard(message = message) }
        }

        pendingAction?.let { (action, app) ->
            item {
                NativeAppConfirmCard(
                    action = action,
                    app = app,
                    onCancel = { pendingAction = null },
                    onConfirm = { executeAction(action, app, confirmed = true) },
                )
            }
        }

        item {
            NativeAppSearchAndFilters(
                query = query,
                onQuery = { query = it },
                filter = filter,
                onFilter = { filter = it },
                count = visibleSignals.size,
            )
        }

        if (loading && visibleSignals.isEmpty()) {
            item { NativeAppLoadingCard() }
        } else if (visibleSignals.isEmpty()) {
            item { NativeAppEmptyCard(filter = filter) }
        } else {
            items(visibleSignals, key = { it.app.packageName }) { signal ->
                val selected = selectedPackage == signal.app.packageName
                NativeManagedAppCard(
                    signal = signal,
                    selected = selected,
                    details = if (selected) selectedDetails else null,
                    showAdvanced = showAdvanced,
                    onToggleAdvanced = { showAdvanced = !showAdvanced },
                    onSelect = {
                        selectedPackage = if (selected) null else signal.app.packageName
                        showAdvanced = false
                    },
                    onAction = { action ->
                        val availability = AppManagementActionPolicy.availability(action, signal.app)
                        when {
                            !availability.enabled -> lastResult = "已阻止“${action.title}”：${availability.reason}"
                            action.requiresConfirmation -> pendingAction = action to signal.app
                            else -> executeAction(action, signal.app, confirmed = false)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NativeAppControlSurface(
    modifier: Modifier,
    headerAction: @Composable () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .padding(top = 82.dp, bottom = 92.dp)
            .shadow(12.dp, RoundedCornerShape(30.dp), clip = false),
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.035f),
                            Color(0x12000000),
                        ),
                    ),
                )
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("应用体检与控制", color = Color(0xFF8BF7FF).copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("应用控制", color = Color.White.copy(alpha = 0.96f), fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text(
                        "从设置跳转升级为后台体检、智能清理、存储风险和增强控制中心。",
                        color = Color.White.copy(alpha = 0.52f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                headerAction()
            }
            Spacer(modifier = Modifier.height(14.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun NativeAppControlHeader(
    loading: Boolean,
    dashboard: com.yuchen.ailedger.service.AppControlDashboard?,
    onRefresh: () -> Unit,
    onUsageAccess: () -> Unit,
    onShizuku: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.095f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        ) {
            Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("实时体检", color = Color.White.copy(alpha = 0.94f), fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text(
                            dashboard?.shellMessage ?: "正在读取应用清单和运行状态…",
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    if (loading) CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp, color = Color.White.copy(alpha = 0.8f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NativeAppMetric("应用", dashboard?.totalApps?.toString() ?: "--", Modifier.weight(1f))
                    NativeAppMetric("后台", dashboard?.runningApps?.toString() ?: "--", Modifier.weight(1f))
                    NativeAppMetric("可清", dashboard?.cleanCandidates?.toString() ?: "--", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NativeAppMetric("低频活跃", dashboard?.lowUseButActiveApps?.toString() ?: "--", Modifier.weight(1f))
                    NativeAppMetric("空间大户", dashboard?.storageHeavyApps?.toString() ?: "--", Modifier.weight(1f))
                    NativeAppMetric("估算内存", dashboard?.estimatedRuntimeBytes?.appControlHumanBytes() ?: "受限", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NativeAppTinyButton("刷新", onRefresh, Modifier.weight(1f))
                    NativeAppTinyButton(if (dashboard?.usageAccessGranted == true) "使用记录已开" else "开启使用记录", onUsageAccess, Modifier.weight(1f))
                    NativeAppTinyButton(if (dashboard?.enhancedControlAvailable == true) "增强已就绪" else "增强授权", onShizuku, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NativeAppMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun NativeAppSearchAndFilters(
    query: String,
    onQuery: (String) -> Unit,
    filter: NativeAppControlFilter,
    onFilter: (NativeAppControlFilter) -> Unit,
    count: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                if (query.isBlank()) {
                    Text("搜索应用名或包名", color = Color.White.copy(alpha = 0.38f), fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { onQuery(it.take(48)) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Color.White.copy(alpha = 0.84f)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                NativeAppControlFilter.Optimize,
                NativeAppControlFilter.Running,
                NativeAppControlFilter.Storage,
                NativeAppControlFilter.Risk,
                NativeAppControlFilter.All,
            ).forEach { item ->
                NativeAppFilterChip(
                    text = item.title,
                    selected = filter == item,
                    onClick = { onFilter(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text("当前筛选 $count 个应用", color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NativeManagedAppCard(
    signal: com.yuchen.ailedger.service.AppOptimizationSignal,
    selected: Boolean,
    details: ManagedAppDetails?,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    onSelect: () -> Unit,
    onAction: (ManagedAppAction) -> Unit,
) {
    val app = signal.app
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = if (selected) 0.125f else 0.085f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.22f else 0.12f)),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (selected) 0.12f else 0.07f),
                            Color(0x0D6AD7FF),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.width(42.dp).height(42.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(app.label.firstOrNull()?.toString() ?: "A", color = Color.White.copy(alpha = 0.90f), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(modifier = Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, color = Color.White.copy(alpha = 0.94f), fontSize = 15.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(app.packageName, color = Color.White.copy(alpha = 0.42f), fontSize = 10.8.sp, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${signal.score}", color = Color(0xFF8BF7FF).copy(alpha = 0.90f), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("体检分", color = Color.White.copy(alpha = 0.40f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            NativeAppTags(tags = signal.tags)
            Text(signal.recommendation, color = Color.White.copy(alpha = 0.58f), fontSize = 12.2.sp, lineHeight = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NativeAppMetric("状态", signal.runtime?.stateLabel ?: if (app.isEnabled) "未运行" else "已禁用", Modifier.weight(1f))
                NativeAppMetric("内存", signal.runtime?.estimatedMemoryBytes?.appControlHumanBytes() ?: "受限", Modifier.weight(1f))
                NativeAppMetric("安装包", app.apkBytes.appControlHumanBytes(), Modifier.weight(1f))
            }
            if (selected) {
                NativeAppExpandedPanel(
                    signal = signal,
                    details = details,
                    showAdvanced = showAdvanced,
                    onToggleAdvanced = onToggleAdvanced,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun NativeAppExpandedPanel(
    signal: com.yuchen.ailedger.service.AppOptimizationSignal,
    details: ManagedAppDetails?,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    onAction: (ManagedAppAction) -> Unit,
) {
    val app = signal.app
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            signal.quickActions.take(3).forEach { action ->
                NativeAppActionButton(action = action, app = app, onClick = { onAction(action) }, modifier = Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NativeAppActionButton(ManagedAppAction.BatterySettings, app, { onAction(ManagedAppAction.BatterySettings) }, Modifier.weight(1f))
            NativeAppTinyButton(if (showAdvanced) "收起高级" else "高级控制", onToggleAdvanced, Modifier.weight(1f))
        }
        NativeAppDetailsBlock(details = details, signal = signal)
        if (showAdvanced) {
            NativeAppAdvancedActions(app = app, onAction = onAction)
        }
    }
}

@Composable
private fun NativeAppDetailsBlock(details: ManagedAppDetails?, signal: com.yuchen.ailedger.service.AppOptimizationSignal) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (details == null) {
                Text("正在读取应用详情…", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp)
                return@Column
            }
            val storage = details.storage
            Text("详情体检", color = Color.White.copy(alpha = 0.90f), fontSize = 14.sp, fontWeight = FontWeight.Black)
            NativeAppInfoLine("版本", "${details.versionName} · target ${details.targetSdk}")
            NativeAppInfoLine("近 7 天使用", "${signal.totalForegroundMs.appControlDurationLabel()} · ${signal.lastUsedTime.appControlUsageLabel()}")
            NativeAppInfoLine("存储", "应用 ${storage.appBytes?.appControlHumanBytes() ?: "未知"} · 数据 ${storage.dataBytes?.appControlHumanBytes() ?: "未知"} · 缓存 ${storage.cacheBytes?.appControlHumanBytes() ?: "未知"}")
            if (!details.storageAccessGranted) {
                Text("开启使用情况访问后，可显示更准确的数据/缓存占用。", color = Color(0xFFFFD06B).copy(alpha = 0.88f), fontSize = 11.5.sp, lineHeight = 16.sp)
            }
            val dangerous = details.permissions.filter { it.dangerous }
            NativeAppInfoLine("危险权限", if (dangerous.isEmpty()) "未发现危险权限" else "${dangerous.count { it.granted }}/${dangerous.size} 已授权")
            dangerous.take(4).forEach { permission ->
                Text(
                    "• ${permission.label}：${if (permission.granted) "已授权" else "未授权"}",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 11.4.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun NativeAppAdvancedActions(app: ManagedAppSummary, onAction: (ManagedAppAction) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x22FF7A7A),
        border = BorderStroke(1.dp, Color(0x55FFB1B1)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("高级控制", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text("这些动作会影响应用运行或数据，继续沿用原有确认和保护策略。", color = Color.White.copy(alpha = 0.54f), fontSize = 11.5.sp, lineHeight = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NativeAppActionButton(ManagedAppAction.ForceStop, app, { onAction(ManagedAppAction.ForceStop) }, Modifier.weight(1f))
                NativeAppActionButton(if (app.isEnabled) ManagedAppAction.Disable else ManagedAppAction.Enable, app, { onAction(if (app.isEnabled) ManagedAppAction.Disable else ManagedAppAction.Enable) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NativeAppActionButton(ManagedAppAction.ClearData, app, { onAction(ManagedAppAction.ClearData) }, Modifier.weight(1f))
                NativeAppActionButton(ManagedAppAction.Uninstall, app, { onAction(ManagedAppAction.Uninstall) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NativeAppTags(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.take(4).forEach { tag ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            ) {
                Text(tag, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White.copy(alpha = 0.58f), fontSize = 10.2.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NativeAppActionButton(action: ManagedAppAction, app: ManagedAppSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val availability = AppManagementActionPolicy.availability(action, app)
    val toneColor = when (action.tone) {
        ManagedAppActionTone.Critical -> Color(0xFFFF9A9A)
        ManagedAppActionTone.Warning -> Color(0xFFFFD06B)
        ManagedAppActionTone.Normal -> Color.White.copy(alpha = 0.88f)
    }
    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(enabled = availability.enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color.White.copy(alpha = if (availability.enabled) 0.10f else 0.045f),
        border = BorderStroke(1.dp, toneColor.copy(alpha = if (availability.enabled) 0.34f else 0.10f)),
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(action.title, color = toneColor.copy(alpha = if (availability.enabled) 0.92f else 0.36f), fontSize = 11.4.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun NativeAppFilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = if (selected) 0.18f else 0.07f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.26f else 0.10f)),
    ) {
        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = if (selected) 0.90f else 0.52f), fontSize = 11.2.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun NativeAppTinyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.80f), fontSize = 11.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun NativeAppInfoLine(title: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(title, color = Color.White.copy(alpha = 0.40f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(84.dp))
        Text(value, color = Color.White.copy(alpha = 0.64f), fontSize = 11.7.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NativeAppConfirmCard(
    action: ManagedAppAction,
    app: ManagedAppSummary,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0x26FFB45E),
        border = BorderStroke(1.dp, Color(0x66FFD06B)),
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("确认执行：${action.title}", color = Color.White.copy(alpha = 0.94f), fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text("目标应用：${app.label}\n${action.description}", color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, lineHeight = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NativeAppTinyButton("取消", onCancel, Modifier.weight(1f))
                NativeAppTinyButton("确认执行", onConfirm, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NativeAppResultCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)),
    ) {
        Text(message, modifier = Modifier.padding(12.dp), color = Color.White.copy(alpha = 0.70f), fontSize = 12.2.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun NativeAppLoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp, color = Color.White.copy(alpha = 0.78f))
            Spacer(modifier = Modifier.width(10.dp))
            Text("正在扫描应用、后台状态和优化建议…", color = Color.White.copy(alpha = 0.58f), fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun NativeAppEmptyCard(filter: NativeAppControlFilter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Text("${filter.title} 分类下暂时没有需要处理的应用。", modifier = Modifier.padding(14.dp), color = Color.White.copy(alpha = 0.58f), fontSize = 12.5.sp)
    }
}

private fun AgentExecutionResult.uiMessage(action: ManagedAppAction, app: ManagedAppSummary): String {
    val prefix = if (ok) "已执行" else "执行失败"
    val detail = message.ifBlank { diagnostics.ifBlank { action.description } }
    return "$prefix：${app.label} · ${action.title}\n$detail"
}
