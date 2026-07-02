package com.yuchen.ailedger.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.BurstPhotoGroup
import com.yuchen.ailedger.service.SimilarPhotoGroup
import com.yuchen.ailedger.service.StorageCandidateSource
import com.yuchen.ailedger.service.StorageCleanupHistoryEntry
import com.yuchen.ailedger.service.StorageCleanupHistoryStore
import com.yuchen.ailedger.service.StorageDownloadCategory
import com.yuchen.ailedger.service.StorageManagementRepository
import com.yuchen.ailedger.service.StorageMediaOrganizationRepository
import com.yuchen.ailedger.service.StorageOrganizationFile
import com.yuchen.ailedger.service.StorageOrganizationIgnoreRules
import com.yuchen.ailedger.service.StorageOrganizationIgnoreStore
import com.yuchen.ailedger.service.StorageOrganizationKind
import com.yuchen.ailedger.service.StorageOrganizationSnapshot
import com.yuchen.ailedger.service.StorageReviewRisk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class StorageOrganizationTab(val label: String) {
    Overview("概览"),
    Similar("相似照片"),
    Screenshots("截图"),
    Bursts("连拍"),
    Quality("画质候选"),
    Downloads("下载分类"),
}

@Composable
fun StorageMediaOrganizationScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { StorageMediaOrganizationRepository(context.applicationContext) }
    val storageRepository = remember(context) { StorageManagementRepository(context.applicationContext) }
    val ignoreStore = remember(context) { StorageOrganizationIgnoreStore(context.applicationContext) }
    val historyStore = remember(context) { StorageCleanupHistoryStore(context.applicationContext) }

    var selectedTab by remember { mutableStateOf(StorageOrganizationTab.Overview) }
    var snapshot by remember { mutableStateOf<StorageOrganizationSnapshot?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var operationRunning by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var previewFile by remember { mutableStateOf<StorageOrganizationFile?>(null) }
    var pendingDelete by remember { mutableStateOf<List<StorageOrganizationFile>?>(null) }
    var pendingMediaFiles by remember { mutableStateOf<List<StorageOrganizationFile>>(emptyList()) }
    var pendingFolderFiles by remember { mutableStateOf<List<StorageOrganizationFile>>(emptyList()) }
    var ignoreRules by remember { mutableStateOf(ignoreStore.load()) }
    var selectedDownloadKind by remember { mutableStateOf<StorageOrganizationKind?>(null) }

    BackHandler(onBack = onBack)

    fun startAnalysis() {
        if (analyzing || operationRunning) return
        analyzing = true
        analysisError = null
        operationMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repository.analyze(
                        includeMedia = context.hasOrganizationMediaAccess(),
                        authorizedTreeUri = storageRepository.savedTreeUri(),
                        ignoreRules = ignoreRules,
                    )
                }
            }
            result.onSuccess { loaded ->
                snapshot = loaded
                val validIds = loaded.allOrganizationFiles().mapTo(hashSetOf()) { it.stableId }
                selectedIds = selectedIds.intersect(validIds)
                if (selectedDownloadKind != null && loaded.downloadCategories.none { it.kind == selectedDownloadKind }) {
                    selectedDownloadKind = null
                }
            }.onFailure { error ->
                analysisError = error.message?.takeIf(String::isNotBlank) ?: "精细整理分析失败"
            }
            analyzing = false
        }
    }

    suspend fun verifyAndFinish(files: List<StorageOrganizationFile>) {
        delay(250L)
        val existing = withContext(Dispatchers.IO) { repository.existingUris(files) }
        val deleted = files.filterNot { it.uri in existing }
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            historyStore.add(
                StorageCleanupHistoryEntry(
                    id = "$now-media-${files.size}-${deleted.size}",
                    createdAt = now,
                    requestedCount = files.size,
                    deletedCount = deleted.size,
                    failedCount = (files.size - deleted.size).coerceAtLeast(0),
                    releasedBytes = deleted.sumOf { it.sizeBytes },
                    label = "精细媒体整理",
                ),
            )
        }
        operationRunning = false
        selectedIds = emptySet()
        operationMessage = "清理完成：成功 ${deleted.size} 个，释放约 ${formatOrganizationBytes(deleted.sumOf { it.sizeBytes })}；失败 ${files.size - deleted.size} 个。"
        startAnalysis()
    }

    val mediaDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val requested = (pendingMediaFiles + pendingFolderFiles).distinctBy { it.stableId }
            scope.launch {
                operationRunning = true
                withContext(Dispatchers.IO) {
                    storageRepository.deleteAuthorizedDocuments(pendingFolderFiles.map { Uri.parse(it.uri) })
                }
                pendingMediaFiles = emptyList()
                pendingFolderFiles = emptyList()
                verifyAndFinish(requested)
            }
        } else {
            pendingMediaFiles = emptyList()
            pendingFolderFiles = emptyList()
            operationRunning = false
            operationMessage = "已取消系统媒体删除确认，未继续删除授权目录文件。"
        }
    }

    fun executeDelete(files: List<StorageOrganizationFile>) {
        val unique = files.distinctBy { it.stableId }.filter { it.canDelete }
        if (unique.isEmpty() || operationRunning) return
        operationRunning = true
        operationMessage = null
        scope.launch {
            val media = unique.filter { it.source == StorageCandidateSource.MediaStore }
            val folder = unique.filter { it.source == StorageCandidateSource.AuthorizedFolder }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && media.isNotEmpty()) {
                val request = withContext(Dispatchers.IO) {
                    storageRepository.createMediaDeleteRequest(media.map { Uri.parse(it.uri) })
                }
                if (request != null) {
                    pendingMediaFiles = media
                    pendingFolderFiles = folder
                    operationRunning = false
                    mediaDeleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    return@launch
                }
            }
            withContext(Dispatchers.IO) {
                storageRepository.deleteMediaDirect(media.map { Uri.parse(it.uri) })
                storageRepository.deleteAuthorizedDocuments(folder.map { Uri.parse(it.uri) })
            }
            verifyAndFinish(unique)
        }
    }

    fun applyIgnore(action: () -> Unit) {
        action()
        ignoreRules = ignoreStore.load()
        previewFile = null
        selectedIds = emptySet()
        startAnalysis()
    }

    val allFiles by remember(snapshot) {
        derivedStateOf { snapshot?.allOrganizationFiles().orEmpty() }
    }
    val selectedFiles by remember(allFiles, selectedIds) {
        derivedStateOf { allFiles.filter { it.stableId in selectedIds && it.canDelete } }
    }
    val activeDownloadCategories by remember(snapshot, selectedDownloadKind) {
        derivedStateOf {
            val categories = snapshot?.downloadCategories.orEmpty()
            selectedDownloadKind?.let { kind -> categories.filter { it.kind == kind } } ?: categories
        }
    }

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
                    Text("ORGANIZE", color = OrganizationAccent.copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("精细整理", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(
                        "整理相似照片、截图、连拍、画质候选和授权目录文件；所有结果都需要人工检查。",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            item {
                OrganizationInfoPanel(
                    title = "第三阶段安全边界",
                    text = "相似照片使用缩略图感知哈希，只表示视觉接近；画质候选使用缩略图锐度与分辨率启发式，只能作为检查线索。截图和连拍依据目录、名称与时间整理，页面不会自动勾选照片、文档或媒体文件。",
                    tone = OrganizationAccent,
                )
            }
            item {
                OrganizationAnalysisPanel(
                    snapshot = snapshot,
                    analyzing = analyzing,
                    includeMedia = context.hasOrganizationMediaAccess(),
                    hasFolder = storageRepository.savedTreeUri() != null,
                    ignoredCount = ignoreRules.ignoredUris.size + ignoreRules.ignoredDirectories.size,
                    onAnalyze = ::startAnalysis,
                    onClearIgnoreRules = {
                        ignoreStore.clear()
                        ignoreRules = StorageOrganizationIgnoreRules(emptySet(), emptySet())
                        selectedIds = emptySet()
                        startAnalysis()
                    },
                )
            }
            analysisError?.let { error ->
                item { OrganizationInfoPanel("分析未完成", error, OrganizationWarning) }
            }
            operationMessage?.let { message ->
                item { OrganizationInfoPanel("操作结果", message, OrganizationSuccess) }
            }
            if (snapshot != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        StorageOrganizationTab.entries.forEach { tab ->
                            OrganizationFilterChip(tab.label, selected = selectedTab == tab) {
                                selectedTab = tab
                            }
                        }
                    }
                }
            }
            if (selectedFiles.isNotEmpty()) {
                item {
                    OrganizationSelectionPanel(
                        files = selectedFiles,
                        operationRunning = operationRunning,
                        onClear = { selectedIds = emptySet() },
                        onDelete = { pendingDelete = selectedFiles },
                    )
                }
            }
            when {
                analyzing && snapshot == null -> item { OrganizationLoadingPanel("正在生成缩略图感知签名并整理目录…") }
                snapshot == null -> item { OrganizationEmptyPanel("点击“开始精细整理分析”后显示分类结果。") }
                selectedTab == StorageOrganizationTab.Overview -> {
                    item {
                        OrganizationOverview(
                            snapshot = snapshot!!,
                            onOpen = { selectedTab = it },
                        )
                    }
                }
                selectedTab == StorageOrganizationTab.Similar -> {
                    val groups = snapshot?.similarGroups.orEmpty()
                    item { OrganizationSectionHeader("相似照片", "${groups.size} 组 · 不自动选择") }
                    if (groups.isEmpty()) {
                        item { OrganizationEmptyPanel("当前分析范围内没有达到相似阈值的照片组。") }
                    } else {
                        items(groups, key = SimilarPhotoGroup::id) { group ->
                            SimilarPhotoGroupCard(
                                group = group,
                                selectedIds = selectedIds,
                                onToggle = { selectedIds = toggleOrganizationSelection(selectedIds, it) },
                                onPreview = { previewFile = it },
                            )
                        }
                    }
                }
                selectedTab == StorageOrganizationTab.Screenshots -> {
                    val files = snapshot?.screenshots.orEmpty()
                    item { OrganizationSectionHeader("截图", "${files.size} 个 · 建议逐项检查") }
                    if (files.isEmpty()) {
                        item { OrganizationEmptyPanel("没有在已授权图片范围内识别到截图。") }
                    } else {
                        items(files, key = StorageOrganizationFile::stableId) { file ->
                            OrganizationFileCard(
                                file = file,
                                selected = file.stableId in selectedIds,
                                onToggle = { selectedIds = toggleOrganizationSelection(selectedIds, file) },
                                onPreview = { previewFile = file },
                            )
                        }
                    }
                }
                selectedTab == StorageOrganizationTab.Bursts -> {
                    val groups = snapshot?.burstGroups.orEmpty()
                    item { OrganizationSectionHeader("连拍候选", "${groups.size} 组 · 时间相邻不等于重复") }
                    if (groups.isEmpty()) {
                        item { OrganizationEmptyPanel("没有识别到明确的 BURST 文件或同目录短时间连续照片组。") }
                    } else {
                        items(groups, key = BurstPhotoGroup::id) { group ->
                            BurstPhotoGroupCard(
                                group = group,
                                selectedIds = selectedIds,
                                onToggle = { selectedIds = toggleOrganizationSelection(selectedIds, it) },
                                onPreview = { previewFile = it },
                            )
                        }
                    }
                }
                selectedTab == StorageOrganizationTab.Quality -> {
                    val files = snapshot?.qualityCandidates.orEmpty()
                    item { OrganizationSectionHeader("画质候选", "${files.size} 个 · 仅供预览判断") }
                    if (files.isEmpty()) {
                        item { OrganizationEmptyPanel("没有识别到达到当前阈值的模糊或低分辨率候选。") }
                    } else {
                        items(files, key = StorageOrganizationFile::stableId) { file ->
                            OrganizationFileCard(
                                file = file,
                                selected = file.stableId in selectedIds,
                                onToggle = { selectedIds = toggleOrganizationSelection(selectedIds, file) },
                                onPreview = { previewFile = file },
                            )
                        }
                    }
                }
                selectedTab == StorageOrganizationTab.Downloads -> {
                    val categories = snapshot?.downloadCategories.orEmpty()
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OrganizationSectionHeader("授权目录分类", "${snapshot?.downloadFileCount ?: 0} 个")
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                OrganizationFilterChip("全部", selectedDownloadKind == null) { selectedDownloadKind = null }
                                categories.forEach { category ->
                                    OrganizationFilterChip(
                                        "${category.kind.label} ${category.files.size}",
                                        selectedDownloadKind == category.kind,
                                    ) { selectedDownloadKind = category.kind }
                                }
                            }
                            val lowRiskFiles = activeDownloadCategories.flatMap { it.files }
                                .filter { it.risk == StorageReviewRisk.Low && it.canDelete }
                            if (lowRiskFiles.isNotEmpty()) {
                                OrganizationTextAction("选择当前分类中的低风险建议 · ${lowRiskFiles.size} 个") {
                                    selectedIds = selectedIds + lowRiskFiles.map { it.stableId }
                                }
                            }
                        }
                    }
                    if (activeDownloadCategories.isEmpty()) {
                        item { OrganizationEmptyPanel("授权目录中没有达到分类规则的文件。") }
                    } else {
                        activeDownloadCategories.forEach { category ->
                            item(key = "header-${category.kind.name}") {
                                OrganizationSectionHeader(
                                    category.kind.label,
                                    "${category.files.size} 个 · ${formatOrganizationBytes(category.totalBytes)}",
                                )
                            }
                            items(category.files, key = StorageOrganizationFile::stableId) { file ->
                                OrganizationFileCard(
                                    file = file,
                                    selected = file.stableId in selectedIds,
                                    onToggle = { selectedIds = toggleOrganizationSelection(selectedIds, file) },
                                    onPreview = { previewFile = file },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewFile?.let { file ->
        OrganizationPreviewDialog(
            file = file,
            repository = repository,
            selected = file.stableId in selectedIds,
            onDismiss = { previewFile = null },
            onToggleSelection = {
                selectedIds = toggleOrganizationSelection(selectedIds, file)
                previewFile = null
            },
            onIgnoreFile = { applyIgnore { ignoreStore.ignoreFile(file) } },
            onIgnoreDirectory = { applyIgnore { ignoreStore.ignoreDirectory(file) } },
        )
    }
    pendingDelete?.let { files ->
        OrganizationDeleteDialog(
            files = files,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                executeDelete(files)
            },
        )
    }
}

@Composable
private fun OrganizationOverview(
    snapshot: StorageOrganizationSnapshot,
    onOpen: (StorageOrganizationTab) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OrganizationOverviewCard(
            title = "相似照片",
            value = "${snapshot.similarGroups.size} 组 · ${snapshot.similarPhotoCount} 张",
            detail = "缩略图视觉接近，不等于完全重复",
            tone = OrganizationCaution,
        ) { onOpen(StorageOrganizationTab.Similar) }
        OrganizationOverviewCard(
            title = "截图",
            value = "${snapshot.screenshots.size} 张",
            detail = "依据截图目录和文件名识别",
            tone = OrganizationAccent,
        ) { onOpen(StorageOrganizationTab.Screenshots) }
        OrganizationOverviewCard(
            title = "连拍候选",
            value = "${snapshot.burstGroups.size} 组 · ${snapshot.burstPhotoCount} 张",
            detail = "依据 BURST 名称或同目录 4 秒内连续照片识别",
            tone = OrganizationCaution,
        ) { onOpen(StorageOrganizationTab.Bursts) }
        OrganizationOverviewCard(
            title = "画质候选",
            value = "${snapshot.qualityCandidates.size} 张",
            detail = "缩略图锐度或分辨率提示，需要人工预览",
            tone = OrganizationWarning,
        ) { onOpen(StorageOrganizationTab.Quality) }
        OrganizationOverviewCard(
            title = "授权目录分类",
            value = "${snapshot.downloadFileCount} 个文件",
            detail = "安装包、压缩包、文档、媒体和其他大文件",
            tone = OrganizationSuccess,
        ) { onOpen(StorageOrganizationTab.Downloads) }
    }
}

@Composable
private fun OrganizationAnalysisPanel(
    snapshot: StorageOrganizationSnapshot?,
    analyzing: Boolean,
    includeMedia: Boolean,
    hasFolder: Boolean,
    ignoredCount: Int,
    onAnalyze: () -> Unit,
    onClearIgnoreRules: () -> Unit,
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("当前范围", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            includeMedia && hasFolder -> "共享图片 + 授权目录"
                            includeMedia -> "共享图片"
                            hasFolder -> "授权目录"
                            else -> "尚未授权可分析范围"
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (analyzing) CircularProgressIndicator(modifier = Modifier.size(23.dp), strokeWidth = 2.dp, color = OrganizationAccent)
            }
            snapshot?.let {
                OrganizationMetric("图片索引", "${it.indexedImageCount} 张")
                OrganizationMetric("感知与画质分析", "${it.perceptualHashedCount} 张")
                OrganizationMetric("画质候选", "${it.qualityCandidates.size} 张")
                OrganizationMetric("授权目录分类", "${it.indexedFolderCount} 个")
                OrganizationMetric("分析耗时", formatOrganizationElapsed(it.elapsedMs))
                if (it.limited) {
                    Text(
                        "已触发性能保护上限，结果只代表本轮已完成范围。",
                        color = OrganizationWarning,
                        fontSize = 10.sp,
                    )
                }
            }
            OrganizationPrimaryAction(
                text = when {
                    analyzing -> "正在分析缩略图与目录…"
                    snapshot == null -> "开始精细整理分析"
                    else -> "重新分析"
                },
                enabled = !analyzing && (includeMedia || hasFolder),
                onClick = onAnalyze,
            )
            if (ignoredCount > 0) {
                OrganizationTextAction("清空忽略规则 · $ignoredCount 条", onClearIgnoreRules)
            }
            if (!includeMedia && !hasFolder) {
                Text("请先在基础存储管理中授权共享媒体或选择目录。", color = OrganizationWarning, fontSize = 10.5.sp)
            }
        }
    }
}

@Composable
private fun SimilarPhotoGroupCard(
    group: SimilarPhotoGroup,
    selectedIds: Set<String>,
    onToggle: (StorageOrganizationFile) -> Unit,
    onPreview: (StorageOrganizationFile) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        color = OrganizationCaution.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, OrganizationCaution.copy(alpha = 0.17f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("视觉相似 · ${group.files.size} 张", color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(
                "最大感知距离 ${group.maxHashDistance} · 请预览后手动选择，不提供一键选副本。",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.8.sp,
            )
            group.files.forEach { file ->
                OrganizationFileCard(
                    file = file,
                    selected = file.stableId in selectedIds,
                    onToggle = { onToggle(file) },
                    onPreview = { onPreview(file) },
                )
            }
        }
    }
}

@Composable
private fun BurstPhotoGroupCard(
    group: BurstPhotoGroup,
    selectedIds: Set<String>,
    onToggle: (StorageOrganizationFile) -> Unit,
    onPreview: (StorageOrganizationFile) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        color = OrganizationCaution.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, OrganizationCaution.copy(alpha = 0.15f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (group.explicitBurstName) "明确连拍文件 · ${group.files.size} 张" else "时间相邻照片 · ${group.files.size} 张",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (group.explicitBurstName) "文件名含 BURST 或连拍标记。" else "同一目录内照片时间连续且尺寸接近，不保证来自同一次拍摄。",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.8.sp,
            )
            group.files.forEach { file ->
                OrganizationFileCard(
                    file = file,
                    selected = file.stableId in selectedIds,
                    onToggle = { onToggle(file) },
                    onPreview = { onPreview(file) },
                )
            }
        }
    }
}

@Composable
private fun OrganizationFileCard(
    file: StorageOrganizationFile,
    selected: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        color = riskTone(file.risk).copy(alpha = if (selected) 0.13f else 0.045f),
        border = BorderStroke(1.dp, riskTone(file.risk).copy(alpha = if (selected) 0.34f else 0.11f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier.size(23.dp).clip(RoundedCornerShape(7.dp))
                    .background(if (selected) riskTone(file.risk).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.07f))
                    .clickable(enabled = file.canDelete, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (selected) "✓" else "", color = Color(0xFF101638), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onPreview),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.displayName,
                        color = Color.White.copy(alpha = 0.91f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatOrganizationBytes(file.sizeBytes), color = riskTone(file.risk), fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    "${file.kind.label} · ${file.risk.label} · ${formatOrganizationDate(file.modifiedAt)}",
                    color = Color.White.copy(alpha = 0.44f),
                    fontSize = 9.3.sp,
                )
                file.reviewNote.takeIf(String::isNotBlank)?.let { note ->
                    Text(note, color = riskTone(file.risk).copy(alpha = 0.75f), fontSize = 8.9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(file.location, color = Color.White.copy(alpha = 0.29f), fontSize = 8.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!file.canDelete) {
                    Text("文档提供方未开放删除能力", color = OrganizationWarning, fontSize = 8.8.sp)
                }
            }
        }
    }
}

@Composable
private fun OrganizationPreviewDialog(
    file: StorageOrganizationFile,
    repository: StorageMediaOrganizationRepository,
    selected: Boolean,
    onDismiss: () -> Unit,
    onToggleSelection: () -> Unit,
    onIgnoreFile: () -> Unit,
    onIgnoreDirectory: () -> Unit,
) {
    val preview by produceState<Bitmap?>(null, file.uri) {
        value = withContext(Dispatchers.IO) { repository.loadPreviewBitmap(file) }
    }
    DisposableEffect(preview) {
        onDispose {
            preview?.takeIf { !it.isRecycled }?.recycle()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.99f),
        tonalElevation = 0.dp,
        title = { Text("文件预览", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (file.mimeType.startsWith("image/")) {
                    if (preview != null) {
                        Image(
                            bitmap = preview!!.asImageBitmap(),
                            contentDescription = file.displayName,
                            modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = OrganizationAccent)
                        }
                    }
                }
                Text(file.displayName, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("${formatOrganizationBytes(file.sizeBytes)} · ${file.mimeType.ifBlank { "未知类型" }}", color = Color.White.copy(alpha = 0.60f), fontSize = 11.sp)
                if (file.width > 0 && file.height > 0) {
                    Text("${file.width} × ${file.height}", color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp)
                }
                Text(file.location, color = Color.White.copy(alpha = 0.43f), fontSize = 10.sp, lineHeight = 14.sp)
                file.reviewNote.takeIf(String::isNotBlank)?.let { note ->
                    Text(note, color = riskTone(file.risk), fontSize = 10.5.sp, lineHeight = 15.sp)
                }
                Text(file.risk.explanation, color = Color.White.copy(alpha = 0.54f), fontSize = 10.5.sp, lineHeight = 15.sp)
                OrganizationTextAction("永不提示此文件", onIgnoreFile)
                OrganizationTextAction("忽略此目录", onIgnoreDirectory)
            }
        },
        confirmButton = {
            TextButton(enabled = file.canDelete, onClick = onToggleSelection) {
                Text(if (selected) "取消选择" else "加入清理选择", color = OrganizationCritical, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Color.White.copy(alpha = 0.64f), fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun OrganizationDeleteDialog(
    files: List<StorageOrganizationFile>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cautionCount = files.count { it.risk == StorageReviewRisk.Caution }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.99f),
        tonalElevation = 0.dp,
        title = { Text("确认清理所选文件", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "共 ${files.size} 个文件，约 ${formatOrganizationBytes(files.sumOf { it.sizeBytes })}。",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (cautionCount > 0) {
                    Text("其中 $cautionCount 个属于谨慎处理项目，可能包含唯一照片、媒体或文档。", color = OrganizationWarning, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Text(
                    "相似照片、连拍和画质候选都不等于重复文件。媒体删除仍由 Android 系统再次确认。",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("继续清理", color = OrganizationCritical, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.64f), fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun OrganizationSelectionPanel(
    files: List<StorageOrganizationFile>,
    operationRunning: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = OrganizationCritical.copy(alpha = 0.085f),
        border = BorderStroke(1.dp, OrganizationCritical.copy(alpha = 0.22f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OrganizationMetric("已选择", "${files.size} 个 · ${formatOrganizationBytes(files.sumOf { it.sizeBytes })}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrganizationSecondaryAction("清除选择", Modifier.weight(1f), onClear)
                OrganizationDangerAction(
                    if (operationRunning) "正在处理…" else "清理已选",
                    enabled = !operationRunning,
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun OrganizationOverviewCard(
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
                Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(value, color = tone.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 9.8.sp)
            }
            Text("进入 ›", color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OrganizationSectionHeader(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 9.8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OrganizationFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) OrganizationAccent.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, if (selected) OrganizationAccent.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.10f)),
    ) {
        Text(
            label,
            color = if (selected) OrganizationAccent else Color.White.copy(alpha = 0.64f),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun OrganizationInfoPanel(title: String, text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = tone.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(text, color = Color.White.copy(alpha = 0.60f), fontSize = 10.8.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun OrganizationEmptyPanel(text: String) {
    OrganizationInfoPanel("暂无结果", text, Color.White)
}

@Composable
private fun OrganizationLoadingPanel(text: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.055f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(21.dp), strokeWidth = 2.dp, color = OrganizationAccent)
            Text(text, color = Color.White.copy(alpha = 0.60f), fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun OrganizationMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun OrganizationPrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = OrganizationAccent.copy(alpha = if (enabled) 0.13f else 0.04f),
        border = BorderStroke(1.dp, OrganizationAccent.copy(alpha = if (enabled) 0.28f else 0.08f)),
    ) {
        Text(text, color = OrganizationAccent.copy(alpha = if (enabled) 0.92f else 0.34f), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun OrganizationTextAction(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

@Composable
private fun OrganizationSecondaryAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.70f), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(11.dp))
    }
}

@Composable
private fun OrganizationDangerAction(text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = OrganizationCritical.copy(alpha = if (enabled) 0.12f else 0.04f),
        border = BorderStroke(1.dp, OrganizationCritical.copy(alpha = if (enabled) 0.26f else 0.07f)),
    ) {
        Text(text, color = OrganizationCritical.copy(alpha = if (enabled) 0.90f else 0.32f), fontSize = 10.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(11.dp))
    }
}

private fun StorageOrganizationSnapshot.allOrganizationFiles(): List<StorageOrganizationFile> {
    return buildList {
        similarGroups.forEach { addAll(it.files) }
        addAll(screenshots)
        burstGroups.forEach { addAll(it.files) }
        addAll(qualityCandidates)
        downloadCategories.forEach { addAll(it.files) }
    }.distinctBy { it.stableId }
}

private fun toggleOrganizationSelection(
    selectedIds: Set<String>,
    file: StorageOrganizationFile,
): Set<String> {
    if (!file.canDelete) return selectedIds
    return if (file.stableId in selectedIds) selectedIds - file.stableId else selectedIds + file.stableId
}

private fun Context.hasOrganizationMediaAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val images = hasOrganizationPermission(Manifest.permission.READ_MEDIA_IMAGES)
        val selected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasOrganizationPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        images || selected
    } else {
        hasOrganizationPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun Context.hasOrganizationPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun riskTone(risk: StorageReviewRisk): Color {
    return when (risk) {
        StorageReviewRisk.Low -> OrganizationSuccess
        StorageReviewRisk.Review -> OrganizationAccent
        StorageReviewRisk.Caution -> OrganizationCaution
    }
}

private fun formatOrganizationBytes(bytes: Long): String {
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

private fun formatOrganizationDate(timestamp: Long): String {
    if (timestamp <= 0L) return "时间未知"
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp))
}

private fun formatOrganizationElapsed(elapsedMs: Long): String {
    return if (elapsedMs < 1_000L) "$elapsedMs ms" else String.format(Locale.CHINA, "%.1f 秒", elapsedMs / 1_000.0)
}

private val OrganizationAccent = Color(0xFF8DF9EA)
private val OrganizationSuccess = Color(0xFF83F3B8)
private val OrganizationWarning = Color(0xFFFFCA72)
private val OrganizationCaution = Color(0xFFFFB47A)
private val OrganizationCritical = Color(0xFFFF7F8D)
