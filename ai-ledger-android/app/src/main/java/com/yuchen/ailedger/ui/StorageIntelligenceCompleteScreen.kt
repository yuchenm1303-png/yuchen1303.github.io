package com.yuchen.ailedger.ui

import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import com.yuchen.ailedger.service.StorageCandidateSource
import com.yuchen.ailedger.service.StorageCleanupHistoryEntry
import com.yuchen.ailedger.service.StorageCleanupHistoryStore
import com.yuchen.ailedger.service.StorageIntelligenceCompleteRepository
import com.yuchen.ailedger.service.StorageIntelligenceFile
import com.yuchen.ailedger.service.StorageIntelligenceRepository
import com.yuchen.ailedger.service.StorageIntelligenceResult
import com.yuchen.ailedger.service.StorageManagementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun StorageIntelligenceCompleteScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageRepository = remember(context) { StorageManagementRepository(context.applicationContext) }
    val analysisRepository = remember(context) { StorageIntelligenceCompleteRepository(context.applicationContext) }
    val verificationRepository = remember(context) { StorageIntelligenceRepository(context.applicationContext) }
    val historyStore = remember(context) { StorageCleanupHistoryStore(context.applicationContext) }
    var analysis by remember { mutableStateOf<StorageIntelligenceResult?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var duplicatesExpanded by remember { mutableStateOf(false) }
    var oldFilesExpanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<StorageIntelligenceFile>?>(null) }
    var operationRunning by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var pendingMedia by remember { mutableStateOf<List<StorageIntelligenceFile>>(emptyList()) }
    var pendingFolder by remember { mutableStateOf<List<StorageIntelligenceFile>>(emptyList()) }
    var history by remember { mutableStateOf(historyStore.load()) }

    BackHandler(onBack = onBack)

    fun startAnalysis() {
        if (analyzing || operationRunning) return
        analyzing = true
        analysisError = null
        operationMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    analysisRepository.analyze(
                        includeMedia = context.hasStorageMediaAccess(),
                        authorizedTreeUri = storageRepository.savedTreeUri(),
                    )
                }
            }
            result.onSuccess { loaded ->
                analysis = loaded
                val validIds = buildSet {
                    loaded.duplicateGroups.forEach { group -> group.files.forEach { add(it.stableId) } }
                    loaded.oldFiles.forEach { add(it.stableId) }
                }
                selectedIds = selectedIds.intersect(validIds)
            }.onFailure { analysisError = it.message?.takeIf(String::isNotBlank) ?: "智能分析失败" }
            analyzing = false
        }
    }

    fun recordHistory(requested: List<StorageIntelligenceFile>, deleted: List<StorageIntelligenceFile>) {
        val now = System.currentTimeMillis()
        historyStore.add(
            StorageCleanupHistoryEntry(
                id = "$now-${requested.size}-${deleted.size}",
                createdAt = now,
                requestedCount = requested.size,
                deletedCount = deleted.size,
                failedCount = (requested.size - deleted.size).coerceAtLeast(0),
                releasedBytes = deleted.sumOf { it.sizeBytes },
                label = "智能文件清理",
            ),
        )
        history = historyStore.load()
    }

    suspend fun verifyDeletion(files: List<StorageIntelligenceFile>) {
        val existing = withContext(Dispatchers.IO) { verificationRepository.existingUris(files) }
        val deleted = files.filterNot { it.uri in existing }
        withContext(Dispatchers.IO) { recordHistory(files, deleted) }
        operationRunning = false
        selectedIds = emptySet()
        startAnalysis()
        operationMessage = "清理完成：成功 ${deleted.size} 个，释放约 ${formatIntelligenceBytes(deleted.sumOf { it.sizeBytes })}；失败 ${files.size - deleted.size} 个。"
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val requested = (pendingMedia + pendingFolder).distinctBy { it.stableId }
            scope.launch {
                operationRunning = true
                withContext(Dispatchers.IO) {
                    storageRepository.deleteAuthorizedDocuments(pendingFolder.map { Uri.parse(it.uri) })
                }
                pendingMedia = emptyList()
                pendingFolder = emptyList()
                verifyDeletion(requested)
            }
        } else {
            pendingMedia = emptyList()
            pendingFolder = emptyList()
            operationRunning = false
            operationMessage = "已取消系统删除确认，未继续删除授权目录中的文件。"
        }
    }

    fun executeDelete(files: List<StorageIntelligenceFile>) {
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
                    pendingMedia = media
                    pendingFolder = folder
                    operationRunning = false
                    deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    return@launch
                }
            }
            withContext(Dispatchers.IO) {
                storageRepository.deleteMediaDirect(media.map { Uri.parse(it.uri) })
                storageRepository.deleteAuthorizedDocuments(folder.map { Uri.parse(it.uri) })
            }
            verifyDeletion(unique)
        }
    }

    val duplicateIds by remember(analysis) {
        derivedStateOf { analysis?.duplicateGroups.orEmpty().flatMapTo(hashSetOf()) { it.files.map(StorageIntelligenceFile::stableId) } }
    }
    val suggestedDuplicateIds by remember(analysis) {
        derivedStateOf {
            analysis?.duplicateGroups.orEmpty().flatMapTo(linkedSetOf()) { it.suggestedDeleteIds }
        }
    }
    val oldFiles by remember(analysis, duplicateIds) {
        derivedStateOf { analysis?.oldFiles.orEmpty().filterNot { it.stableId in duplicateIds } }
    }
    val oldFileIds by remember(oldFiles) {
        derivedStateOf {
            oldFiles.asSequence().filter(StorageIntelligenceFile::canDelete)
                .mapTo(linkedSetOf(), StorageIntelligenceFile::stableId)
        }
    }
    val allFiles by remember(analysis, oldFiles) {
        derivedStateOf {
            buildList {
                analysis?.duplicateGroups.orEmpty().forEach { addAll(it.files) }
                addAll(oldFiles)
            }.distinctBy(StorageIntelligenceFile::stableId)
        }
    }
    val selectedFiles by remember(allFiles, selectedIds) {
        derivedStateOf { allFiles.filter { it.stableId in selectedIds && it.canDelete } }
    }
    val duplicateGroups = analysis?.duplicateGroups.orEmpty()
    val displayedDuplicateGroups by remember(duplicateGroups, duplicatesExpanded) {
        derivedStateOf {
            storagePreviewItems(duplicateGroups, duplicatesExpanded, STORAGE_GROUP_PREVIEW_COUNT)
        }
    }
    val displayedOldFiles by remember(oldFiles, oldFilesExpanded) {
        derivedStateOf {
            storagePreviewItems(oldFiles, oldFilesExpanded, STORAGE_FILE_PREVIEW_COUNT)
        }
    }
    val displayedHistory by remember(history, historyExpanded) {
        derivedStateOf {
            storagePreviewItems(history, historyExpanded, STORAGE_HISTORY_PREVIEW_COUNT)
        }
    }
    val selectedDuplicateCount by remember(duplicateIds, selectedIds) {
        derivedStateOf { selectedIds.count { it in duplicateIds } }
    }
    val selectedOldFileCount by remember(oldFileIds, selectedIds) {
        derivedStateOf { selectedIds.count { it in oldFileIds } }
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
                    modifier = Modifier.width(132.dp).height(40.dp),
                    role = GlassRole.Chip,
                    onClick = onBack,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("‹ 基础存储管理", color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("INTELLIGENCE", color = IntelligenceAccent.copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("智能文件分析", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text("完整索引可访问文件，并对重复候选完成内容校验。", color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            item {
                IntelligenceInfoPanel(
                    "完整分析范围",
                    "共享媒体与授权目录均不再按文件数量、目录层级、哈希数量或累计读取量截断。",
                    IntelligenceAccent,
                )
            }
            item {
                IntelligenceActionPanel(
                    result = analysis,
                    analyzing = analyzing,
                    includeMedia = context.hasStorageMediaAccess(),
                    hasFolder = storageRepository.savedTreeUri() != null,
                    onAnalyze = ::startAnalysis,
                )
            }
            analysisError?.let { item { IntelligenceInfoPanel("分析未完成", it, IntelligenceWarning) } }
            operationMessage?.let { item { IntelligenceInfoPanel("清理结果", it, IntelligenceSuccess) } }
            if (selectedFiles.isNotEmpty()) {
                item {
                    IntelligenceSelectionPanel(
                        files = selectedFiles,
                        operationRunning = operationRunning,
                        onClear = { selectedIds = emptySet() },
                        onDelete = { pendingDelete = selectedFiles },
                    )
                }
            }
            if (analysis != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        IntelligenceSectionHeader("完全重复文件", "${duplicateGroups.size} 组 · 可释放约 ${formatIntelligenceBytes(analysis?.recoverableBytes ?: 0L)}")
                        StorageLongListControls(
                            totalCount = duplicateGroups.size,
                            expanded = duplicatesExpanded,
                            previewCount = STORAGE_GROUP_PREVIEW_COUNT,
                            selectedCount = selectedDuplicateCount,
                            selectAllLabel = "全选建议副本",
                            onToggleExpanded = { duplicatesExpanded = !duplicatesExpanded },
                            onSelectAll = if (suggestedDuplicateIds.isEmpty()) null else ({
                                selectedIds = selectedIds + suggestedDuplicateIds
                            }),
                            onClearSelection = if (selectedDuplicateCount == 0) null else ({
                                selectedIds = selectedIds - duplicateIds
                            }),
                            tone = IntelligenceAccent,
                        )
                    }
                }
                if (duplicateGroups.isEmpty()) {
                    item { IntelligenceEmptyPanel("没有发现经过完整哈希确认的重复文件。") }
                } else {
                    items(displayedDuplicateGroups, key = { it.id }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            selectedIds = selectedIds,
                            onToggle = { selectedIds = toggleIntelligenceSelection(selectedIds, it) },
                            onSelectSuggested = { selectedIds = selectedIds + group.suggestedDeleteIds },
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        IntelligenceSectionHeader("长期未修改的大文件", "${oldFiles.size} 个 · 仅供检查")
                        StorageLongListControls(
                            totalCount = oldFiles.size,
                            expanded = oldFilesExpanded,
                            previewCount = STORAGE_FILE_PREVIEW_COUNT,
                            selectedCount = selectedOldFileCount,
                            onToggleExpanded = { oldFilesExpanded = !oldFilesExpanded },
                            onSelectAll = if (oldFileIds.isEmpty()) null else ({
                                selectedIds = selectedIds + oldFileIds
                            }),
                            onClearSelection = if (selectedOldFileCount == 0) null else ({
                                selectedIds = selectedIds - oldFileIds
                            }),
                            tone = IntelligenceWarning,
                        )
                    }
                }
                if (oldFiles.isEmpty()) {
                    item { IntelligenceEmptyPanel("没有发现超过 20 MB 且 180 天未修改、同时不属于重复组的文件。") }
                } else {
                    items(displayedOldFiles, key = { it.stableId }) { file ->
                        IntelligenceFileCard(
                            file = file,
                            selected = file.stableId in selectedIds,
                            label = "${formatIntelligenceAge(file.modifiedAt)}未修改",
                            enabled = file.canDelete,
                            onClick = { selectedIds = toggleIntelligenceSelection(selectedIds, file) },
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    IntelligenceSectionHeader("清理记录", if (history.isEmpty()) "暂无记录" else "共 ${history.size} 次")
                    StorageLongListControls(
                        totalCount = history.size,
                        expanded = historyExpanded,
                        previewCount = STORAGE_HISTORY_PREVIEW_COUNT,
                        onToggleExpanded = { historyExpanded = !historyExpanded },
                        tone = IntelligenceSuccess,
                    )
                }
            }
            if (history.isEmpty()) {
                item { IntelligenceEmptyPanel("完成一次智能清理后，这里会记录实际删除数量和核验后的释放空间。") }
            } else {
                items(displayedHistory, key = { it.id }) { CleanupHistoryCard(it) }
                item {
                    IntelligenceTextAction("清空清理记录") {
                        historyStore.clear()
                        history = emptyList()
                    }
                }
            }
        }
    }

    pendingDelete?.let { files ->
        IntelligenceDeleteDialog(
            files = files,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                executeDelete(files)
            },
        )
    }
}
