package com.yuchen.ailedger.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.StorageCandidateKind
import com.yuchen.ailedger.service.StorageCandidateSource
import com.yuchen.ailedger.service.StorageFileCandidate
import com.yuchen.ailedger.service.StorageManagementRepository
import com.yuchen.ailedger.service.StorageScanCancellationController
import com.yuchen.ailedger.service.StorageScanSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class StorageCandidateFilter(val label: String) {
    All("全部候选"),
    Media("大型媒体"),
    Packages("安装与压缩包"),
    Folder("授权目录"),
}

internal data class MediaAccessState(
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
    inlineFeatureContent: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { StorageManagementRepository(context.applicationContext) }
    val scanCancellation = remember { StorageScanCancellationController() }
    val scope = rememberCoroutineScope()
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(true) }
    var snapshot by remember { mutableStateOf<StorageScanSnapshot?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var mediaAccess by remember { mutableStateOf(currentMediaAccess(context)) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filter by remember { mutableStateOf(StorageCandidateFilter.All) }
    var candidatesExpanded by remember { mutableStateOf(false) }
    var appCachesExpanded by remember { mutableStateOf(false) }
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

    DisposableEffect(scanCancellation) {
        onDispose { scanCancellation.cancel() }
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

    LaunchedEffect(filter) {
        candidatesExpanded = false
    }

    LaunchedEffect(refreshGeneration) {
        val scanToken = scanCancellation.begin()
        scanning = true
        scanError = null
        mediaAccess = currentMediaAccess(context)
        try {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    scanToken.throwIfCancelled()
                    val overview = repository.loadOverview()
                    val usageGranted = repository.hasUsageAccess()
                    val appCaches = if (usageGranted) {
                        repository.loadAppCacheRanking(cancellation = scanToken)
                    } else {
                        emptyList()
                    }
                    val media = if (mediaAccess.anyAccess) {
                        repository.scanAccessibleMedia(cancellation = scanToken)
                    } else {
                        emptyList()
                    }
                    val folder = repository.scanSavedFolder(cancellation = scanToken)
                    scanToken.throwIfCancelled()
                    StorageScanSnapshot(
                        overview = overview,
                        usageAccessGranted = usageGranted,
                        appCaches = appCaches,
                        mediaCandidates = media,
                        folderScan = folder,
                    )
                }
            }
            scanToken.throwIfCancelled()
            result.onSuccess { loaded ->
                snapshot = loaded
                val validIds = (loaded.mediaCandidates + loaded.folderScan?.candidates.orEmpty())
                    .mapTo(hashSetOf()) { it.stableId }
                selectedIds = selectedIds.intersect(validIds)
            }.onFailure { error ->
                scanError = error.message?.takeIf(String::isNotBlank) ?: "存储扫描失败"
            }
            scanning = false
        } finally {
            scanCancellation.complete(scanToken)
        }
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
    val displayedCandidates by remember(visibleCandidates, candidatesExpanded) {
        derivedStateOf {
            storagePreviewItems(visibleCandidates, candidatesExpanded, STORAGE_FILE_PREVIEW_COUNT)
        }
    }
    val visibleSelectableIds by remember(visibleCandidates) {
        derivedStateOf {
            visibleCandidates.asSequence().filter(StorageFileCandidate::canDelete)
                .mapTo(linkedSetOf(), StorageFileCandidate::stableId)
        }
    }
    val selectedVisibleCount by remember(visibleSelectableIds, selectedIds) {
        derivedStateOf { selectedIds.count { it in visibleSelectableIds } }
    }
    val selectedCandidates by remember(allCandidates, selectedIds) {
        derivedStateOf { allCandidates.filter { it.stableId in selectedIds && it.canDelete } }
    }
    val appCaches = snapshot?.appCaches.orEmpty()
    val displayedAppCaches by remember(appCaches, appCachesExpanded) {
        derivedStateOf {
            storagePreviewItems(appCaches, appCachesExpanded, STORAGE_FILE_PREVIEW_COUNT)
        }
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
            inlineFeatureContent?.let { content ->
                item { content() }
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
                    StorageLongListControls(
                        totalCount = visibleCandidates.size,
                        expanded = candidatesExpanded,
                        previewCount = STORAGE_FILE_PREVIEW_COUNT,
                        selectedCount = selectedVisibleCount,
                        onToggleExpanded = { candidatesExpanded = !candidatesExpanded },
                        onSelectAll = if (visibleSelectableIds.isEmpty()) null else ({
                            selectedIds = selectedIds + visibleSelectableIds
                        }),
                        onClearSelection = if (selectedVisibleCount == 0) null else ({
                            selectedIds = selectedIds - visibleSelectableIds
                        }),
                        tone = StorageAccent,
                    )
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
                else -> items(displayedCandidates, key = { it.stableId }) { candidate ->
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
                    StorageMetricRow("已统计应用", appCaches.size.toString())
                    StorageMetricRow("缓存合计", formatStorageBytes(appCaches.sumOf { it.cacheBytes }))
                    Text(
                        "点击应用进入 Android 系统应用信息页，再由系统安全管理缓存。这里不会清除登录状态和数据库。",
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    )
                    StorageLongListControls(
                        totalCount = appCaches.size,
                        expanded = appCachesExpanded,
                        previewCount = STORAGE_FILE_PREVIEW_COUNT,
                        onToggleExpanded = { appCachesExpanded = !appCachesExpanded },
                        tone = StorageWarning,
                    )
                }
            }
            if (snapshot?.usageAccessGranted == true && appCaches.isEmpty() && !scanning) {
                item { StorageEmptyPanel("没有读取到可展示的应用缓存。") }
            } else {
                items(displayedAppCaches, key = { it.packageName }) { app ->
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
