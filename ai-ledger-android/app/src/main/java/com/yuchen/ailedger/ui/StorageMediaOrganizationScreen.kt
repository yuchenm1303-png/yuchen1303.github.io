package com.yuchen.ailedger.ui

import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import com.yuchen.ailedger.service.BurstPhotoGroup
import com.yuchen.ailedger.service.SimilarPhotoGroup
import com.yuchen.ailedger.service.StorageCandidateSource
import com.yuchen.ailedger.service.StorageCleanupHistoryEntry
import com.yuchen.ailedger.service.StorageCleanupHistoryStore
import com.yuchen.ailedger.service.StorageMediaOrganizationRepository
import com.yuchen.ailedger.service.StorageManagementRepository
import com.yuchen.ailedger.service.StorageOrganizationFile
import com.yuchen.ailedger.service.StorageOrganizationIgnoreRules
import com.yuchen.ailedger.service.StorageOrganizationIgnoreStore
import com.yuchen.ailedger.service.StorageOrganizationKind
import com.yuchen.ailedger.service.StorageOrganizationSnapshot
import com.yuchen.ailedger.service.StorageReviewRisk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class StorageOrganizationTab(val label: String) {
    Overview("概览"),
    Similar("相似照片"),
    Screenshots("截图"),
    Bursts("连拍"),
    Quality("画质候选"),
    Downloads("授权目录"),
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
    var expandedTabs by remember { mutableStateOf<Set<StorageOrganizationTab>>(emptySet()) }
    var expandedDownloadKinds by remember { mutableStateOf<Set<StorageOrganizationKind>>(emptySet()) }
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
                analysisError = error.message?.takeIf(String::isNotBlank) ?: "照片与目录整理分析失败"
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
                    label = "照片与授权目录整理",
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

    fun toggleTabExpansion(tab: StorageOrganizationTab) {
        expandedTabs = if (tab in expandedTabs) expandedTabs - tab else expandedTabs + tab
    }

    fun toggleDownloadExpansion(kind: StorageOrganizationKind) {
        expandedDownloadKinds = if (kind in expandedDownloadKinds) {
            expandedDownloadKinds - kind
        } else {
            expandedDownloadKinds + kind
        }
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
                    Text("PHOTO & FILES", color = OrganizationAccent.copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("照片与授权目录", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(
                        "整理共享图片中的相似照片、截图、连拍和画质候选，并分类检查你主动授权目录中的文件。",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            item {
                OrganizationInfoPanel(
                    title = "照片与目录整理边界",
                    text = "相似照片使用缩略图感知哈希，只表示视觉接近；画质候选使用缩略图锐度与分辨率启发式，只能作为检查线索。截图和连拍依据目录、名称与时间整理，页面默认不会自动勾选任何照片、文档或媒体文件。",
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
                analyzing && snapshot == null -> item { OrganizationLoadingPanel("正在分析照片缩略图并整理授权目录…") }
                snapshot == null -> item { OrganizationEmptyPanel("点击“开始照片与目录分析”后显示分类结果。") }
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
                    val files = groups.flatMap { it.files }.distinctBy(StorageOrganizationFile::stableId)
                    val selectableIds = files.asSequence().filter(StorageOrganizationFile::canDelete)
                        .mapTo(linkedSetOf(), StorageOrganizationFile::stableId)
                    val expanded = StorageOrganizationTab.Similar in expandedTabs
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            OrganizationSectionHeader("相似照片", "${groups.size} 组 · 不代表完全重复")
                            StorageLongListControls(
                                totalCount = groups.size,
                                expanded = expanded,
                                previewCount = STORAGE_GROUP_PREVIEW_COUNT,
                                selectedCount = selectedIds.count { it in selectableIds },
                                selectAllLabel = "全选当前照片",
                                onToggleExpanded = { toggleTabExpansion(StorageOrganizationTab.Similar) },
                                onSelectAll = if (selectableIds.isEmpty()) null else ({ selectedIds = selectedIds + selectableIds }),
                                onClearSelection = ({ selectedIds = selectedIds - selectableIds }),
                                tone = OrganizationCaution,
                            )
                        }
                    }
                    if (groups.isEmpty()) {
                        item { OrganizationEmptyPanel("当前分析范围内没有达到相似阈值的照片组。") }
                    } else {
                        items(
                            storagePreviewItems(groups, expanded, STORAGE_GROUP_PREVIEW_COUNT),
                            key = SimilarPhotoGroup::id,
                        ) { group ->
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
                    val selectableIds = files.asSequence().filter(StorageOrganizationFile::canDelete)
                        .mapTo(linkedSetOf(), StorageOrganizationFile::stableId)
                    val expanded = StorageOrganizationTab.Screenshots in expandedTabs
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            OrganizationSectionHeader("截图", "${files.size} 个 · 建议逐项检查")
                            StorageLongListControls(
                                totalCount = files.size,
                                expanded = expanded,
                                previewCount = STORAGE_FILE_PREVIEW_COUNT,
                                selectedCount = selectedIds.count { it in selectableIds },
                                onToggleExpanded = { toggleTabExpansion(StorageOrganizationTab.Screenshots) },
                                onSelectAll = if (selectableIds.isEmpty()) null else ({ selectedIds = selectedIds + selectableIds }),
                                onClearSelection = ({ selectedIds = selectedIds - selectableIds }),
                                tone = OrganizationAccent,
                            )
                        }
                    }
                    if (files.isEmpty()) {
                        item { OrganizationEmptyPanel("没有在已授权图片范围内识别到截图。") }
                    } else {
                        items(
                            storagePreviewItems(files, expanded, STORAGE_FILE_PREVIEW_COUNT),
                            key = StorageOrganizationFile::stableId,
                        ) { file ->
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
                    val files = groups.flatMap { it.files }.distinctBy(StorageOrganizationFile::stableId)
                    val selectableIds = files.asSequence().filter(StorageOrganizationFile::canDelete)
                        .mapTo(linkedSetOf(), StorageOrganizationFile::stableId)
                    val expanded = StorageOrganizationTab.Bursts in expandedTabs
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            OrganizationSectionHeader("连拍候选", "${groups.size} 组 · 时间相邻不等于重复")
                            StorageLongListControls(
                                totalCount = groups.size,
                                expanded = expanded,
                                previewCount = STORAGE_GROUP_PREVIEW_COUNT,
                                selectedCount = selectedIds.count { it in selectableIds },
                                selectAllLabel = "全选当前照片",
                                onToggleExpanded = { toggleTabExpansion(StorageOrganizationTab.Bursts) },
                                onSelectAll = if (selectableIds.isEmpty()) null else ({ selectedIds = selectedIds + selectableIds }),
                                onClearSelection = ({ selectedIds = selectedIds - selectableIds }),
                                tone = OrganizationCaution,
                            )
                        }
                    }
                    if (groups.isEmpty()) {
                        item { OrganizationEmptyPanel("没有识别到明确的 BURST 文件或同目录短时间连续照片组。") }
                    } else {
                        items(
                            storagePreviewItems(groups, expanded, STORAGE_GROUP_PREVIEW_COUNT),
                            key = BurstPhotoGroup::id,
                        ) { group ->
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
                    val selectableIds = files.asSequence().filter(StorageOrganizationFile::canDelete)
                        .mapTo(linkedSetOf(), StorageOrganizationFile::stableId)
                    val expanded = StorageOrganizationTab.Quality in expandedTabs
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            OrganizationSectionHeader("画质候选", "${files.size} 个 · 仅供预览判断")
                            StorageLongListControls(
                                totalCount = files.size,
                                expanded = expanded,
                                previewCount = STORAGE_FILE_PREVIEW_COUNT,
                                selectedCount = selectedIds.count { it in selectableIds },
                                onToggleExpanded = { toggleTabExpansion(StorageOrganizationTab.Quality) },
                                onSelectAll = if (selectableIds.isEmpty()) null else ({ selectedIds = selectedIds + selectableIds }),
                                onClearSelection = ({ selectedIds = selectedIds - selectableIds }),
                                tone = OrganizationWarning,
                            )
                        }
                    }
                    if (files.isEmpty()) {
                        item { OrganizationEmptyPanel("没有识别到达到当前阈值的模糊或低分辨率候选。") }
                    } else {
                        items(
                            storagePreviewItems(files, expanded, STORAGE_FILE_PREVIEW_COUNT),
                            key = StorageOrganizationFile::stableId,
                        ) { file ->
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
                    val activeFiles = activeDownloadCategories.flatMap { it.files }
                        .distinctBy(StorageOrganizationFile::stableId)
                    val activeIds = activeFiles.asSequence().filter(StorageOrganizationFile::canDelete)
                        .mapTo(linkedSetOf(), StorageOrganizationFile::stableId)
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
                            StorageLongListControls(
                                totalCount = activeFiles.size,
                                expanded = true,
                                previewCount = Int.MAX_VALUE,
                                selectedCount = selectedIds.count { it in activeIds },
                                onSelectAll = if (activeIds.isEmpty()) null else ({ selectedIds = selectedIds + activeIds }),
                                onClearSelection = ({ selectedIds = selectedIds - activeIds }),
                                tone = OrganizationSuccess,
                            )
                            val lowRiskFiles = activeFiles.filter { it.risk == StorageReviewRisk.Low && it.canDelete }
                            if (lowRiskFiles.isNotEmpty()) {
                                OrganizationTextAction("只选择当前分类中的低风险建议 · ${lowRiskFiles.size} 个") {
                                    selectedIds = selectedIds + lowRiskFiles.map { it.stableId }
                                }
                            }
                        }
                    }
                    if (activeDownloadCategories.isEmpty()) {
                        item { OrganizationEmptyPanel("授权目录中没有达到分类规则的文件。") }
                    } else {
                        activeDownloadCategories.forEach { category ->
                            val expanded = category.kind in expandedDownloadKinds
                            val categoryIds = category.files.asSequence().filter(StorageOrganizationFile::canDelete)
                                .mapTo(linkedSetOf(), StorageOrganizationFile::stableId)
                            item(key = "header-${category.kind.name}") {
                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    OrganizationSectionHeader(
                                        category.kind.label,
                                        "${category.files.size} 个 · ${formatOrganizationBytes(category.totalBytes)}",
                                    )
                                    StorageLongListControls(
                                        totalCount = category.files.size,
                                        expanded = expanded,
                                        previewCount = STORAGE_FILE_PREVIEW_COUNT,
                                        selectedCount = selectedIds.count { it in categoryIds },
                                        onToggleExpanded = { toggleDownloadExpansion(category.kind) },
                                        tone = riskTone(category.files.firstOrNull()?.risk ?: StorageReviewRisk.Review),
                                    )
                                }
                            }
                            items(
                                storagePreviewItems(category.files, expanded, STORAGE_FILE_PREVIEW_COUNT),
                                key = StorageOrganizationFile::stableId,
                            ) { file ->
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
