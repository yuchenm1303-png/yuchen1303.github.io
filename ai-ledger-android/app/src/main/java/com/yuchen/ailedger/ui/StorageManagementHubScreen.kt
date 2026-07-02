package com.yuchen.ailedger.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.StorageCandidateSource
import com.yuchen.ailedger.service.StorageCleanupHistoryEntry
import com.yuchen.ailedger.service.StorageCleanupHistoryStore
import com.yuchen.ailedger.service.StorageDuplicateGroup
import com.yuchen.ailedger.service.StorageIntelligenceFile
import com.yuchen.ailedger.service.StorageIntelligenceRepository
import com.yuchen.ailedger.service.StorageIntelligenceResult
import com.yuchen.ailedger.service.StorageManagementRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageManagementHubScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    var showIntelligence by remember { mutableStateOf(false) }
    if (showIntelligence) {
        StorageIntelligenceScreen(
            state = state,
            onBack = { showIntelligence = false },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StorageManagementScreen(
            state = state,
            onBack = onBack,
        )
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = 999,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp)
                .width(112.dp)
                .height(40.dp),
            role = GlassRole.Chip,
            onClick = { showIntelligence = true },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "智能分析 ›",
                    color = IntelligenceAccent.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun StorageIntelligenceScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageRepository = remember(context) { StorageManagementRepository(context.applicationContext) }
    val intelligenceRepository = remember(context) { StorageIntelligenceRepository(context.applicationContext) }
    val historyStore = remember(context) { StorageCleanupHistoryStore(context.applicationContext) }

    var analysis by remember { mutableStateOf<StorageIntelligenceResult?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<List<StorageIntelligenceFile>?>(null) }
    var operationRunning by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var pendingMediaFiles by remember { mutableStateOf<List<StorageIntelligenceFile>>(emptyList()) }
    var pendingFolderFiles by remember { mutableStateOf<List<StorageIntelligenceFile>>(emptyList()) }
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
                    intelligenceRepository.analyze(
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
            }.onFailure { error ->
                analysisError = error.message?.takeIf(String::isNotBlank) ?: "智能分析失败"
            }
            analyzing = false
        }
    }

    fun saveHistory(
        requested: List<StorageIntelligenceFile>,
        deleted: List<StorageIntelligenceFile>,
    ) {
        val now = System.currentTimeMillis()
        historyStore.add(
            StorageCleanupHistoryEntry(
                id = "$now-${requested.size}-${deleted.size}",
                createdAt = now,
                requestedCount = requested.size,
                deletedCount = deleted.size,
                failedCount = (requested.size - deleted.size).coerceAtLeast(0),
                releasedBytes = deleted.sumOf { it.sizeBytes },
                label = "智能存储清理",
            ),
        )
        history = historyStore.load()
    }

    suspend fun verifyAndFinishDeletion(files: List<StorageIntelligenceFile>) {
        val existingUris = withContext(Dispatchers.IO) { intelligenceRepository.existingUris(files) }
        val deleted = files.filterNot { it.uri in existingUris }
        withContext(Dispatchers.IO) { saveHistory(files, deleted) }
        operationRunning = false
        operationMessage = "清理完成：成功 ${deleted.size} 个，释放约 ${formatIntelligenceBytes(deleted.sumOf { it.sizeBytes })}；失败 ${files.size - deleted.size} 个。"
        selectedIds = emptySet()
        startAnalysis()
    }

    val mediaDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val allPending = (pendingMediaFiles + pendingFolderFiles).distinctBy { it.stableId }
            scope.launch {
                operationRunning = true
                withContext(Dispatchers.IO) {
                    storageRepository.deleteAuthorizedDocuments(pendingFolderFiles.map { Uri.parse(it.uri) })
                }
                pendingMediaFiles = emptyList()
                pendingFolderFiles = emptyList()
                verifyAndFinishDeletion(allPending)
            }
        } else {
            pendingMediaFiles = emptyList()
            pendingFolderFiles = emptyList()
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
            verifyAndFinishDeletion(unique)
        }
    }

    val duplicateFileIds by remember(analysis) {
        derivedStateOf {
            analysis?.duplicateGroups.orEmpty()
                .flatMapTo(hashSetOf()) { group -> group.files.map { it.stableId } }
        }
    }
    val oldFiles by remember(analysis, duplicateFileIds) {
        derivedStateOf {
            analysis?.oldFiles.orEmpty().filterNot { it.stableId in duplicateFileIds }
        }
    }
    val allFiles by remember(analysis, oldFiles) {
        derivedStateOf {
            buildList {
                analysis?.duplicateGroups.orEmpty().forEach { addAll(it.files) }
                addAll(oldFiles)
            }.distinctBy { it.stableId }
        }
    }
    val selectedFiles by remember(allFiles, selectedIds) {
        derivedStateOf { allFiles.filter { it.stableId in selectedIds && it.canDelete } }
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
                    Text("智能存储分析", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(
                        "用内容指纹确认完全重复文件，并整理长期未修改的大文件。分析只在你点击后运行。",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            item {
                IntelligenceInfoPanel(
                    title = "准确性与负载边界",
                    text = "重复文件先按来源和大小分组，再进行首尾指纹筛选，最后用完整 SHA-256 确认；不会仅凭名称判断。最多快速分析 600 个潜在重复文件，完整校验最多 240 个或 2 GB 数据。长期文件表示 180 天未修改，不代表一定无用。",
                    tone = IntelligenceAccent,
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
            analysisError?.let { error ->
                item { IntelligenceInfoPanel("分析未完成", error, IntelligenceWarning) }
            }
            operationMessage?.let { message ->
                item { IntelligenceInfoPanel("清理结果", message, IntelligenceSuccess) }
            }
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
            val duplicateGroups = analysis?.duplicateGroups.orEmpty()
            if (analysis != null) {
                item {
                    IntelligenceSectionHeader(
                        title = "完全重复文件",
                        detail = "${duplicateGroups.size} 组 · 可释放约 ${formatIntelligenceBytes(analysis?.recoverableBytes ?: 0L)}",
                    )
                }
                if (duplicateGroups.isEmpty()) {
                    item { IntelligenceEmptyPanel("当前分析范围内没有发现经过完整哈希确认的重复文件。") }
                } else {
                    items(duplicateGroups, key = { it.id }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            selectedIds = selectedIds,
                            onToggle = { file ->
                                selectedIds = toggleIntelligenceSelection(selectedIds, file)
                            },
                            onSelectSuggested = {
                                selectedIds = selectedIds + group.suggestedDeleteIds
                            },
                        )
                    }
                }
                item {
                    IntelligenceSectionHeader(
                        title = "长期未修改的大文件",
                        detail = "${oldFiles.size} 个 · 仅供检查",
                    )
                }
                if (oldFiles.isEmpty()) {
                    item { IntelligenceEmptyPanel("没有发现超过 20 MB 且 180 天未修改、同时不属于重复组的文件。") }
                } else {
                    items(oldFiles, key = { it.stableId }) { file ->
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
                IntelligenceSectionHeader(
                    title = "清理记录",
                    detail = if (history.isEmpty()) "暂无记录" else "最近 ${history.size.coerceAtMost(8)} 次",
                )
            }
            if (history.isEmpty()) {
                item { IntelligenceEmptyPanel("完成一次智能清理后，这里会记录实际删除数量和核验后的释放空间。") }
            } else {
                items(history.take(8), key = { it.id }) { entry ->
                    CleanupHistoryCard(entry)
                }
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

@Composable
private fun IntelligenceActionPanel(
    result: StorageIntelligenceResult?,
    analyzing: Boolean,
    includeMedia: Boolean,
    hasFolder: Boolean,
    onAnalyze: () -> Unit,
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
                    Text("分析范围", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            includeMedia && hasFolder -> "共享媒体 + 授权目录"
                            includeMedia -> "共享媒体"
                            hasFolder -> "授权目录"
                            else -> "尚未授权可分析范围"
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (analyzing) CircularProgressIndicator(modifier = Modifier.size(23.dp), strokeWidth = 2.dp, color = IntelligenceAccent)
            }
            result?.let {
                IntelligenceMetric("已索引文件", it.scannedFileCount.toString())
                IntelligenceMetric("完整哈希校验", "${it.fullHashedFileCount} 个")
                IntelligenceMetric("重复文件", "${it.duplicateGroups.size} 组 · ${formatIntelligenceBytes(it.recoverableBytes)}")
                IntelligenceMetric("分析耗时", formatElapsed(it.elapsedMs))
                if (it.limited) {
                    Text(
                        "已达到本次性能保护上限，结果是已完成范围内的准确结果，并非设备全部文件结论。",
                        color = IntelligenceWarning.copy(alpha = 0.86f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
            IntelligencePrimaryAction(
                text = when {
                    analyzing -> "正在计算内容指纹…"
                    result == null -> "开始智能分析"
                    else -> "重新进行智能分析"
                },
                enabled = !analyzing && (includeMedia || hasFolder),
                onClick = onAnalyze,
            )
            if (!includeMedia && !hasFolder) {
                Text(
                    "请先返回基础存储管理，授权共享媒体或选择一个目录。",
                    color = IntelligenceWarning.copy(alpha = 0.82f),
                    fontSize = 10.5.sp,
                )
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: StorageDuplicateGroup,
    selectedIds: Set<String>,
    onToggle: (StorageIntelligenceFile) -> Unit,
    onSelectSuggested: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        color = IntelligenceAccent.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, IntelligenceAccent.copy(alpha = 0.16f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("完全相同 · ${group.files.size} 份", color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text(
                        "每份 ${formatIntelligenceBytes(group.sizeBytes)} · 建议释放 ${formatIntelligenceBytes(group.recoverableBytes)}",
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 10.sp,
                    )
                }
                IntelligenceMiniAction("选择副本", enabled = group.suggestedDeleteIds.isNotEmpty(), onClick = onSelectSuggested)
            }
            group.files.forEach { file ->
                val keeper = file.stableId == group.keepFileId
                IntelligenceFileCard(
                    file = file,
                    selected = file.stableId in selectedIds,
                    label = if (keeper) "建议保留" else "完全相同副本",
                    enabled = file.canDelete && !keeper,
                    onClick = { onToggle(file) },
                )
            }
        }
    }
}

@Composable
private fun IntelligenceFileCard(
    file: StorageIntelligenceFile,
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) IntelligenceAccent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, if (selected) IntelligenceAccent.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier.size(21.dp).clip(RoundedCornerShape(7.dp))
                    .background(
                        when {
                            selected -> IntelligenceAccent.copy(alpha = 0.88f)
                            enabled -> Color.White.copy(alpha = 0.08f)
                            else -> IntelligenceSuccess.copy(alpha = 0.16f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        selected -> "✓"
                        !enabled -> "留"
                        else -> ""
                    },
                    color = if (selected) Color(0xFF101638) else IntelligenceSuccess,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.displayName,
                        color = Color.White.copy(alpha = if (enabled) 0.90f else 0.66f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatIntelligenceBytes(file.sizeBytes), color = IntelligenceAccent.copy(alpha = 0.80f), fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    "$label · ${if (file.source == StorageCandidateSource.MediaStore) "共享媒体" else "授权目录"}",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 9.5.sp,
                )
                Text(
                    file.location,
                    color = Color.White.copy(alpha = 0.30f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!file.canDelete) {
                    Text("文档提供方未开放删除能力", color = IntelligenceWarning.copy(alpha = 0.78f), fontSize = 8.8.sp)
                }
            }
        }
    }
}

@Composable
private fun IntelligenceSelectionPanel(
    files: List<StorageIntelligenceFile>,
    operationRunning: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = IntelligenceCritical.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, IntelligenceCritical.copy(alpha = 0.22f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            IntelligenceMetric("已选择", "${files.size} 个 · ${formatIntelligenceBytes(files.sumOf { it.sizeBytes })}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntelligenceSecondaryAction("清除选择", Modifier.weight(1f), onClear)
                IntelligenceDangerAction(
                    text = if (operationRunning) "正在处理…" else "清理已选",
                    enabled = !operationRunning,
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun IntelligenceDeleteDialog(
    files: List<StorageIntelligenceFile>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        title = { Text("确认清理智能分析结果", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "共 ${files.size} 个文件，约 ${formatIntelligenceBytes(files.sumOf { it.sizeBytes })}。",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "重复文件已通过完整 SHA-256 确认；长期未修改文件只是检查建议。媒体仍会交给 Android 系统二次确认，授权目录文件按当前勾选结果删除。",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text("删除后不保证能够恢复。", color = IntelligenceWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("继续清理", color = IntelligenceCritical, fontWeight = FontWeight.Black)
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
private fun CleanupHistoryCard(entry: StorageCleanupHistoryEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.label, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(formatIntelligenceDate(entry.createdAt), color = Color.White.copy(alpha = 0.36f), fontSize = 9.5.sp)
                Text(
                    "请求 ${entry.requestedCount} · 成功 ${entry.deletedCount} · 失败 ${entry.failedCount}",
                    color = Color.White.copy(alpha = 0.44f),
                    fontSize = 9.5.sp,
                )
            }
            Text(
                formatIntelligenceBytes(entry.releasedBytes),
                color = IntelligenceSuccess,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun IntelligenceSectionHeader(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(detail, color = Color.White.copy(alpha = 0.43f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IntelligenceInfoPanel(title: String, text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = tone.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(text, color = Color.White.copy(alpha = 0.60f), fontSize = 10.8.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun IntelligenceEmptyPanel(text: String) {
    IntelligenceInfoPanel("暂无结果", text, Color.White)
}

@Composable
private fun IntelligenceMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun IntelligencePrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = IntelligenceAccent.copy(alpha = if (enabled) 0.13f else 0.04f),
        border = BorderStroke(1.dp, IntelligenceAccent.copy(alpha = if (enabled) 0.28f else 0.08f)),
    ) {
        Text(
            text,
            color = IntelligenceAccent.copy(alpha = if (enabled) 0.92f else 0.34f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun IntelligenceMiniAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = IntelligenceAccent.copy(alpha = if (enabled) 0.10f else 0.035f),
        border = BorderStroke(1.dp, IntelligenceAccent.copy(alpha = if (enabled) 0.20f else 0.06f)),
    ) {
        Text(
            text,
            color = IntelligenceAccent.copy(alpha = if (enabled) 0.84f else 0.30f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun IntelligenceSecondaryAction(text: String, modifier: Modifier, onClick: () -> Unit) {
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
private fun IntelligenceDangerAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = IntelligenceCritical.copy(alpha = if (enabled) 0.12f else 0.04f),
        border = BorderStroke(1.dp, IntelligenceCritical.copy(alpha = if (enabled) 0.26f else 0.07f)),
    ) {
        Text(text, color = IntelligenceCritical.copy(alpha = if (enabled) 0.90f else 0.32f), fontSize = 10.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(11.dp))
    }
}

@Composable
private fun IntelligenceTextAction(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

private fun toggleIntelligenceSelection(
    selectedIds: Set<String>,
    file: StorageIntelligenceFile,
): Set<String> {
    if (!file.canDelete) return selectedIds
    return if (file.stableId in selectedIds) selectedIds - file.stableId else selectedIds + file.stableId
}

private fun Context.hasStorageMediaAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val images = hasPermissionForIntelligence(Manifest.permission.READ_MEDIA_IMAGES)
        val videos = hasPermissionForIntelligence(Manifest.permission.READ_MEDIA_VIDEO)
        val audio = hasPermissionForIntelligence(Manifest.permission.READ_MEDIA_AUDIO)
        val selected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasPermissionForIntelligence(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        images || videos || audio || selected
    } else {
        hasPermissionForIntelligence(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun Context.hasPermissionForIntelligence(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun formatIntelligenceBytes(bytes: Long): String {
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

private fun formatIntelligenceDate(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

private fun formatIntelligenceAge(timestamp: Long): String {
    if (timestamp <= 0L) return "时间未知"
    val days = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)).toInt()
    return when {
        days >= 365 -> "约 ${days / 365} 年"
        else -> "约 $days 天"
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    return if (elapsedMs < 1_000L) "${elapsedMs} ms" else String.format(Locale.CHINA, "%.1f 秒", elapsedMs / 1_000.0)
}

private val IntelligenceAccent = Color(0xFF8DF9EA)
private val IntelligenceSuccess = Color(0xFF83F3B8)
private val IntelligenceWarning = Color(0xFFFFCA72)
private val IntelligenceCritical = Color(0xFFFF7F8D)
