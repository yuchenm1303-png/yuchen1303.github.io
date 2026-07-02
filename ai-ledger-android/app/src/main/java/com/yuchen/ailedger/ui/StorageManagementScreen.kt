package com.yuchen.ailedger.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yuchen.ailedger.service.AppCacheUsage
import com.yuchen.ailedger.service.AuthorizedFolderScan
import com.yuchen.ailedger.service.DeviceStorageOverview
import com.yuchen.ailedger.service.StorageCandidateKind
import com.yuchen.ailedger.service.StorageCandidateSource
import com.yuchen.ailedger.service.StorageDeleteResult
import com.yuchen.ailedger.service.StorageFileCandidate
import com.yuchen.ailedger.service.StorageManagementRepository
import com.yuchen.ailedger.service.StorageScanSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class StorageCandidateFilter(val label: String) {
    All("全部候选"),
    Media("大型媒体"),
    Packages("安装与压缩包"),
    Folder("授权目录"),
}

private data class MediaAccessState(
    val visualFull: Boolean,
    val audioFull: Boolean,
    val visualPartial: Boolean,
) {
    val anyAccess: Boolean get() = visualFull || audioFull || visualPartial
    val summary: String
        get() = when {
            visualFull && audioFull -> "图片、视频和音频读取已授权"
            visualPartial && audioFull -> "图片/视频为用户选定范围，音频已授权"
            visualPartial -> "只能扫描用户选定的图片和视频"
            visualFull -> "图片和视频已授权，音频未授权"
            audioFull -> "音频已授权，图片和视频未授权"
            else -> "尚未授权共享媒体扫描"
        }
}

private data class PendingStorageDelete(
    val candidates: List<StorageFileCandidate>,
)

@Composable
fun StorageManagementScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { StorageManagementRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(true) }
    var snapshot by remember { mutableStateOf<StorageScanSnapshot?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var mediaAccess by remember { mutableStateOf(currentMediaAccess(context)) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filter by remember { mutableStateOf(StorageCandidateFilter.All) }
    var pendingDelete by remember { mutableStateOf<PendingStorageDelete?>(null) }
    var followUpFolderUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var operationRunning by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        mediaAccess = currentMediaAccess(context)
        refreshGeneration += 1
    }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val persisted = withContext(Dispatchers.IO) { repository.persistTreeUri(uri) }
                operationMessage = if (persisted) {
                    "目录授权已保存，后续只扫描这个目录及其子目录。"
                } else {
                    "目录授权未能持久保存，请重新选择。"
                }
                refreshGeneration += 1
            }
        }
    }
    val mediaDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                operationRunning = true
                val folderResult = withContext(Dispatchers.IO) {
                    repository.deleteAuthorizedDocuments(followUpFolderUris)
                }
                followUpFolderUris = emptyList()
                operationRunning = false
                operationMessage = if (folderResult.requestedCount == 0) {
                    "系统已完成所选媒体文件清理。"
                } else {
                    "系统已完成媒体清理；授权目录删除 ${folderResult.deletedCount}/${folderResult.requestedCount} 个文件。"
                }
                selectedIds = emptySet()
                refreshGeneration += 1
            }
        } else {
            followUpFolderUris = emptyList()
            operationMessage = "已取消媒体文件删除，未继续删除授权目录文件。"
        }
    }

    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume) {
                    firstResume = false
                } else {
                    mediaAccess = currentMediaAccess(context)
                    refreshGeneration += 1
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshGeneration) {
        scanning = true
        scanError = null
        mediaAccess = currentMediaAccess(context)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val overview = repository.loadOverview()
                val usageGranted = repository.hasUsageAccess()
                val appCaches = if (usageGranted) repository.loadAppCacheRanking() else emptyList()
                val media = if (mediaAccess.anyAccess) repository.scanAccessibleMedia() else emptyList()
                val folder = repository.scanSavedFolder()
                StorageScanSnapshot(
                    overview = overview,
                    usageAccessGranted = usageGranted,
                    appCaches = appCaches,
                    mediaCandidates = media,
                    folderScan = folder,
                )
            }
        }
        result.onSuccess { loaded ->
            snapshot = loaded
            val validIds = (loaded.mediaCandidates + loaded.folderScan?.candidates.orEmpty())
                .mapTo(hashSetOf()) { it.stableId }
            selectedIds = selectedIds.intersect(validIds)
        }.onFailure { error ->
            scanError = error.message?.takeIf(String::isNotBlank) ?: "存储扫描失败"
        }
        scanning = false
    }

    val allCandidates by remember(snapshot) {
        derivedStateOf {
            (snapshot?.mediaCandidates.orEmpty() + snapshot?.folderScan?.candidates.orEmpty())
                .distinctBy { it.stableId }
                .sortedByDescending { it.sizeBytes }
        }
    }
    val visibleCandidates by remember(allCandidates, filter) {
        derivedStateOf {
            allCandidates.filter { candidate ->
                when (filter) {
                    StorageCandidateFilter.All -> true
                    StorageCandidateFilter.Media -> candidate.source == StorageCandidateSource.MediaStore
                    StorageCandidateFilter.Packages -> candidate.kind in setOf(
                        StorageCandidateKind.Installer,
                        StorageCandidateKind.Archive,
                    )
                    StorageCandidateFilter.Folder -> candidate.source == StorageCandidateSource.AuthorizedFolder
                }
            }
        }
    }
    val selectedCandidates by remember(allCandidates, selectedIds) {
        derivedStateOf { allCandidates.filter { it.stableId in selectedIds && it.canDelete } }
    }

    fun openSystemStorage() {
        val intent = Intent(StorageManager.ACTION_MANAGE_STORAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .recoverCatching {
                context.startActivity(
                    Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
    }

    fun openAppStorage(packageName: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun executeConfirmedDelete(candidates: List<StorageFileCandidate>) {
        if (operationRunning || candidates.isEmpty()) return
        operationRunning = true
        operationMessage = null
        scope.launch {
            val mediaUris = candidates
                .filter { it.source == StorageCandidateSource.MediaStore }
                .map { Uri.parse(it.uri) }
            val folderUris = candidates
                .filter { it.source == StorageCandidateSource.AuthorizedFolder }
                .map { Uri.parse(it.uri) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaUris.isNotEmpty()) {
                val request = withContext(Dispatchers.IO) { repository.createMediaDeleteRequest(mediaUris) }
                if (request != null) {
                    followUpFolderUris = folderUris
                    operationRunning = false
                    mediaDeleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    return@launch
                }
            }
            val directResults = withContext(Dispatchers.IO) {
                val mediaResult = repository.deleteMediaDirect(mediaUris)
                val folderResult = repository.deleteAuthorizedDocuments(folderUris)
                mediaResult to folderResult
            }
            val combined = combineDeleteResults(directResults.first, directResults.second)
            operationRunning = false
            operationMessage = "清理完成：成功 ${combined.deletedCount} 个，失败 ${combined.failedCount} 个。"
            selectedIds = emptySet()
            refreshGeneration += 1
        }
    }

    GlassSceneScope(GlassSceneGroup.ToolsHomePage) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item { StorageBackButton(state, onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("STORAGE", color = StorageAccent.copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("存储管理", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(
                        "扫描应用缓存、大型媒体和你授权的目录；所有删除都由你勾选并确认。",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            item {
                StorageOverviewPanel(
                    overview = snapshot?.overview,
                    scanning = scanning,
                    onRefresh = { refreshGeneration += 1 },
                    onOpenSystemStorage = ::openSystemStorage,
                )
            }
            operationMessage?.let { message ->
                item { StorageNoticePanel("操作结果", message, StorageSuccess) }
            }
            scanError?.let { error ->
                item { StorageNoticePanel("扫描未完成", error, StorageWarning) }
            }
            item {
                StorageSection("扫描范围") {
                    StorageAccessRow(
                        title = "应用缓存统计",
                        detail = if (snapshot?.usageAccessGranted == true) {
                            "已授权，只读取各应用占用数字，不读取应用内部文件。"
                        } else {
                            "需要“使用情况访问权”才能读取其他应用缓存大小。"
                        },
                        granted = snapshot?.usageAccessGranted == true,
                        actionText = if (snapshot?.usageAccessGranted == true) "已授权" else "去授权",
                        onAction = {
                            runCatching { context.startActivity(repository.usageAccessIntent()) }
                                .recoverCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                        },
                    )
                    StorageAccessRow(
                        title = "共享媒体",
                        detail = mediaAccess.summary,
                        granted = mediaAccess.anyAccess,
                        actionText = if (mediaAccess.anyAccess) "调整范围" else "授权扫描",
                        onAction = { permissionLauncher.launch(requiredMediaPermissions()) },
                    )
                    StorageAccessRow(
                        title = "用户授权目录",
                        detail = folderAccessSummary(snapshot?.folderScan),
                        granted = snapshot?.folderScan != null && snapshot?.folderScan?.errorMessage == null,
                        actionText = if (snapshot?.folderScan == null) "选择目录" else "更换目录",
                        onAction = { folderLauncher.launch(repository.savedTreeUri()) },
                    )
                    if (snapshot?.folderScan != null) {
                        StorageInlineAction("撤销目录授权") {
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.clearSavedTreeUri() }
                                operationMessage = "已撤销授权目录，后续不会再扫描该目录。"
                                selectedIds = emptySet()
                                refreshGeneration += 1
                            }
                        }
                    }
                }
            }
            item {
                StorageNoticePanel(
                    title = "安全边界",
                    text = "这里不会扫描其他应用的私有目录，也不会把 pm clear 当作清缓存。应用缓存只提供排行和系统管理入口；文件清理只处理共享媒体或你明确授权的目录。",
                    tone = StorageAccent,
                )
            }
            item {
                StorageSection("可检查项目 · ${allCandidates.size}") {
                    val candidateBytes = allCandidates.sumOf { it.sizeBytes }
                    StorageMetricRow("候选文件合计", formatStorageBytes(candidateBytes))
                    StorageMetricRow("已选择", "${selectedCandidates.size} 个 · ${formatStorageBytes(selectedCandidates.sumOf { it.sizeBytes })}")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        StorageCandidateFilter.entries.forEach { item ->
                            StorageFilterChip(item.label, selected = filter == item) { filter = item }
                        }
                    }
                    if (selectedCandidates.isNotEmpty()) {
                        StoragePrimaryAction(
                            text = if (operationRunning) "正在处理…" else "清理已选 ${selectedCandidates.size} 项",
                            enabled = !operationRunning,
                        ) {
                            pendingDelete = PendingStorageDelete(selectedCandidates)
                        }
                    }
                }
            }
            when {
                scanning && snapshot == null -> item { StorageLoadingPanel("正在建立安全存储索引…") }
                visibleCandidates.isEmpty() -> item {
                    StorageEmptyPanel(
                        if (mediaAccess.anyAccess || snapshot?.folderScan != null) {
                            "当前扫描范围内没有达到大型文件或安装包规则的项目。"
                        } else {
                            "授权共享媒体或选择一个目录后，扫描结果会显示在这里。"
                        },
                    )
                }
                else -> items(visibleCandidates, key = { it.stableId }) { candidate ->
                    StorageCandidateCard(
                        candidate = candidate,
                        selected = candidate.stableId in selectedIds,
                        onToggle = {
                            if (candidate.canDelete) {
                                selectedIds = if (candidate.stableId in selectedIds) {
                                    selectedIds - candidate.stableId
                                } else {
                                    selectedIds + candidate.stableId
                                }
                            }
                        },
                    )
                }
            }
            item {
                StorageSection("应用缓存排行") {
                    val appCaches = snapshot?.appCaches.orEmpty()
                    StorageMetricRow("已统计应用", appCaches.size.toString())
                    StorageMetricRow("缓存合计", formatStorageBytes(appCaches.sumOf { it.cacheBytes }))
                    Text(
                        "点击应用进入 Android 系统应用信息页，再由系统安全管理缓存。这里不会清除登录状态和数据库。",
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
            val appCaches = snapshot?.appCaches.orEmpty()
            if (snapshot?.usageAccessGranted == true && appCaches.isEmpty() && !scanning) {
                item { StorageEmptyPanel("没有读取到可展示的应用缓存。") }
            } else {
                items(appCaches.take(20), key = { it.packageName }) { app ->
                    AppCacheCard(app = app, onOpen = { openAppStorage(app.packageName) })
                }
            }
        }
    }

    pendingDelete?.let { pending ->
        StorageDeleteConfirmationDialog(
            candidates = pending.candidates,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                executeConfirmedDelete(pending.candidates)
            },
        )
    }
}

@Composable
private fun StorageOverviewPanel(
    overview: DeviceStorageOverview?,
    scanning: Boolean,
    onRefresh: () -> Unit,
    onOpenSystemStorage: () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 19f,
        backdropAlpha = 1f,
        frostAlpha = 0.10f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))
                .background(Color(0xFF121743).copy(alpha = 0.31f)).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("设备空间", color = Color.White.copy(alpha = 0.50f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        overview?.let { "已用 ${formatStorageBytes(it.usedBytes)}" } ?: "正在读取…",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        overview?.let { "可用 ${formatStorageBytes(it.freeBytes)} / 总计 ${formatStorageBytes(it.totalBytes)}" }.orEmpty(),
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 11.sp,
                    )
                }
                if (scanning) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = StorageAccent)
            }
            Box(
                Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(overview?.usedFraction ?: 0f).height(9.dp)
                        .clip(RoundedCornerShape(999.dp)).background(StorageAccent.copy(alpha = 0.72f)),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StorageSmallAction("重新扫描", Modifier.weight(1f), onRefresh)
                StorageSmallAction("系统存储", Modifier.weight(1f), onOpenSystemStorage)
            }
        }
    }
}

@Composable
private fun StorageSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    FrostInfoGlassPanel(
        radius = 17f,
        backdropAlpha = 1f,
        frostAlpha = 0.085f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF121743).copy(alpha = 0.27f)).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 15.sp, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun StorageAccessRow(
    title: String,
    detail: String,
    granted: Boolean,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.045f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(9.dp).clip(RoundedCornerShape(999.dp))
                .background(if (granted) StorageSuccess else StorageWarning),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.90f), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(detail, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, lineHeight = 14.sp)
        }
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onAction),
            shape = RoundedCornerShape(999.dp),
            color = StorageAccent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, StorageAccent.copy(alpha = 0.20f)),
        ) {
            Text(
                actionText,
                color = StorageAccent.copy(alpha = 0.88f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun StorageCandidateCard(
    candidate: StorageFileCandidate,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val tone = when (candidate.kind) {
        StorageCandidateKind.Installer,
        StorageCandidateKind.Archive,
        StorageCandidateKind.Temporary -> StorageWarning
        else -> StorageAccent
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .clickable(enabled = candidate.canDelete, onClick = onToggle),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = if (selected) 0.14f else 0.055f),
        border = BorderStroke(1.dp, tone.copy(alpha = if (selected) 0.40f else 0.12f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier.size(23.dp).clip(RoundedCornerShape(7.dp))
                    .background(if (selected) tone.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (selected) "✓" else "", color = Color(0xFF101638), fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        candidate.displayName,
                        color = Color.White.copy(alpha = if (candidate.canDelete) 0.92f else 0.42f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatStorageBytes(candidate.sizeBytes), color = tone.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    "${candidate.kind.label} · ${if (candidate.source == StorageCandidateSource.MediaStore) "共享媒体" else "授权目录"}",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 10.sp,
                )
                Text(
                    candidate.location,
                    color = Color.White.copy(alpha = 0.32f),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(candidate.reviewReason, color = Color.White.copy(alpha = 0.43f), fontSize = 9.5.sp, lineHeight = 13.sp)
                if (!candidate.canDelete) {
                    Text("该文档提供方未开放删除能力", color = StorageWarning.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AppCacheCard(app: AppCacheUsage, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).clickable(onClick = onOpen),
        shape = RoundedCornerShape(21.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    app.packageName,
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "数据 ${formatStorageBytes(app.dataBytes)} · 应用 ${formatStorageBytes(app.appBytes)}",
                    color = Color.White.copy(alpha = 0.43f),
                    fontSize = 9.5.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatStorageBytes(app.cacheBytes), color = StorageWarning, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("缓存 · 去管理", color = Color.White.copy(alpha = 0.42f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun StorageDeleteConfirmationDialog(
    candidates: List<StorageFileCandidate>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        title = {
            Text("确认清理所选文件", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "共 ${candidates.size} 个文件，约 ${formatStorageBytes(candidates.sumOf { it.sizeBytes })}。",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "媒体文件会交给 Android 系统再次确认；授权目录文件会按你当前的勾选结果删除。清理不会触碰应用登录状态或数据库。",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    "删除后不保证可以恢复，请确认文件没有唯一副本。",
                    color = StorageWarning.copy(alpha = 0.86f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("继续清理", color = StorageCritical, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun StorageBackButton(state: AssistantUiState, onBack: () -> Unit) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier.width(116.dp).height(40.dp),
        role = GlassRole.Chip,
        onClick = onBack,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("‹ 返回功能", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun StorageMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.47f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White.copy(alpha = 0.86f), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun StorageFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) StorageAccent.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, if (selected) StorageAccent.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.12f)),
    ) {
        Text(
            label,
            color = if (selected) StorageAccent.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.65f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun StoragePrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = StorageCritical.copy(alpha = if (enabled) 0.12f else 0.04f),
        border = BorderStroke(1.dp, StorageCritical.copy(alpha = if (enabled) 0.28f else 0.08f)),
    ) {
        Text(
            text,
            color = StorageCritical.copy(alpha = if (enabled) 0.92f else 0.35f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun StorageSmallAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.76f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun StorageInlineAction(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun StorageNoticePanel(title: String, text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.20f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = tone.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(text, color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun StorageLoadingPanel(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.065f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = StorageAccent)
            Text(text, color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun StorageEmptyPanel(text: String) {
    StorageNoticePanel("暂无候选", text, Color.White)
}

private fun currentMediaAccess(context: Context): MediaAccessState {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val images = context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        val videos = context.hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
        val audio = context.hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
        val partial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        MediaAccessState(
            visualFull = images && videos,
            audioFull = audio,
            visualPartial = partial && !(images && videos),
        )
    } else {
        val granted = context.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        MediaAccessState(granted, granted, false)
    }
}

private fun requiredMediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun folderAccessSummary(scan: AuthorizedFolderScan?): String {
    if (scan == null) return "未选择目录；应用不会扫描普通文档和下载文件。"
    scan.errorMessage?.let { return it }
    val suffix = if (scan.truncated) "，已达到扫描上限" else ""
    return "${scan.displayName} · ${scan.scannedFileCount} 个文件 · ${formatStorageBytes(scan.scannedBytes)}$suffix"
}

private fun combineDeleteResults(first: StorageDeleteResult, second: StorageDeleteResult): StorageDeleteResult {
    return StorageDeleteResult(
        requestedCount = first.requestedCount + second.requestedCount,
        deletedCount = first.deletedCount + second.deletedCount,
        failedCount = first.failedCount + second.failedCount,
        errors = first.errors + second.errors,
    )
}

private fun formatStorageBytes(bytes: Long): String {
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

@Suppress("unused")
private fun formatStorageDate(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

private val StorageAccent = Color(0xFF8DF9EA)
private val StorageSuccess = Color(0xFF83F3B8)
private val StorageWarning = Color(0xFFFFCA72)
private val StorageCritical = Color(0xFFFF7F8D)
