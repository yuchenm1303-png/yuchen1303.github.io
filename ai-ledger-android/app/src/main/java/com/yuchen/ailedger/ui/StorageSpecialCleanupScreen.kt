package com.yuchen.ailedger.ui

import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.StorageSpecialCleanupItem
import com.yuchen.ailedger.service.StorageSpecialCleanupKind
import com.yuchen.ailedger.service.StorageSpecialCleanupMode
import com.yuchen.ailedger.service.StorageSpecialCleanupRepository
import com.yuchen.ailedger.service.StorageSpecialCleanupRisk
import com.yuchen.ailedger.service.StorageSpecialCleanupScan
import com.yuchen.ailedger.service.StorageSpecialCleanupSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun StorageDownloadCleanupScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    StorageSpecialCleanupScreen(state, StorageSpecialCleanupMode.Downloads, onBack)
}

@Composable
internal fun StorageJunkCleanupScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    StorageSpecialCleanupScreen(state, StorageSpecialCleanupMode.Junk, onBack)
}

@Composable
private fun StorageSpecialCleanupScreen(
    state: AssistantUiState,
    mode: StorageSpecialCleanupMode,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { StorageSpecialCleanupRepository(context.applicationContext) }
    var snapshot by remember(mode) { mutableStateOf<StorageSpecialCleanupScan?>(null) }
    var scanning by remember(mode) { mutableStateOf(true) }
    var operationRunning by remember(mode) { mutableStateOf(false) }
    var message by remember(mode) { mutableStateOf<String?>(null) }
    var selectedKind by remember(mode) { mutableStateOf<StorageSpecialCleanupKind?>(null) }
    var selectedIds by remember(mode) { mutableStateOf<Set<String>>(emptySet()) }
    var expanded by remember(mode, selectedKind) { mutableStateOf(false) }
    var pendingDelete by remember(mode) { mutableStateOf<List<StorageSpecialCleanupItem>?>(null) }
    var pendingMedia by remember(mode) { mutableStateOf<List<StorageSpecialCleanupItem>>(emptyList()) }
    var pendingFolder by remember(mode) { mutableStateOf<List<StorageSpecialCleanupItem>>(emptyList()) }

    BackHandler(onBack = onBack)

    fun scan() {
        if (scanning || operationRunning) return
        scanning = true
        message = null
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { repository.scan(mode) } }
            loaded.onSuccess { result ->
                snapshot = result
                val validIds = result.items.mapTo(hashSetOf(), StorageSpecialCleanupItem::stableId)
                selectedIds = selectedIds.intersect(validIds)
                if (selectedKind != null && result.items.none { it.kind == selectedKind }) selectedKind = null
                result.errorMessage?.let { message = it }
            }.onFailure { error ->
                message = error.message?.takeIf(String::isNotBlank) ?: "专项清理扫描失败"
            }
            scanning = false
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val persisted = repository.persistTreeUri(mode, uri)
            message = if (persisted) "目录授权已保存，正在重新扫描。" else "目录授权未能持久保存，请重新选择。"
            if (persisted) scan()
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                operationRunning = true
                val folderResult = withContext(Dispatchers.IO) { repository.deleteAuthorized(pendingFolder) }
                val mediaCount = pendingMedia.size
                pendingMedia = emptyList()
                pendingFolder = emptyList()
                selectedIds = emptySet()
                operationRunning = false
                message = "系统已确认删除 $mediaCount 个下载项目；授权目录删除 ${folderResult.deletedCount}/${folderResult.requestedCount} 个。"
                scan()
            }
        } else {
            pendingMedia = emptyList()
            pendingFolder = emptyList()
            operationRunning = false
            message = "已取消系统删除确认，授权目录项目未继续删除。"
        }
    }

    fun executeDelete(items: List<StorageSpecialCleanupItem>) {
        val unique = items.distinctBy(StorageSpecialCleanupItem::stableId).filter(StorageSpecialCleanupItem::canDelete)
        if (unique.isEmpty() || operationRunning) return
        operationRunning = true
        message = null
        scope.launch {
            val media = unique.filter { it.source == StorageSpecialCleanupSource.MediaStoreDownloads }
            val folder = unique.filter { it.source == StorageSpecialCleanupSource.AuthorizedFolder }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && media.isNotEmpty()) {
                val request = withContext(Dispatchers.IO) { repository.createMediaDeleteRequest(media) }
                if (request != null) {
                    pendingMedia = media
                    pendingFolder = folder
                    operationRunning = false
                    deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    return@launch
                }
            }
            val results = withContext(Dispatchers.IO) {
                repository.deleteMediaDirect(media) to repository.deleteAuthorized(folder)
            }
            val deleted = results.first.deletedCount + results.second.deletedCount
            val requested = results.first.requestedCount + results.second.requestedCount
            selectedIds = emptySet()
            operationRunning = false
            message = "清理完成：成功 $deleted 个，失败 ${requested - deleted} 个。"
            scan()
        }
    }

    LaunchedEffect(mode) {
        scanning = false
        scan()
    }

    val allItems = snapshot?.items.orEmpty()
    val availableKinds by remember(allItems) {
        derivedStateOf { allItems.map(StorageSpecialCleanupItem::kind).distinct().sortedBy { it.ordinal } }
    }
    val visibleItems by remember(allItems, selectedKind) {
        derivedStateOf { selectedKind?.let { kind -> allItems.filter { it.kind == kind } } ?: allItems }
    }
    val displayedItems by remember(visibleItems, expanded) {
        derivedStateOf { storagePreviewItems(visibleItems, expanded, STORAGE_FILE_PREVIEW_COUNT) }
    }
    val selectableIds by remember(visibleItems) {
        derivedStateOf {
            visibleItems.asSequence().filter(StorageSpecialCleanupItem::canDelete)
                .mapTo(linkedSetOf(), StorageSpecialCleanupItem::stableId)
        }
    }
    val lowRiskIds by remember(visibleItems) {
        derivedStateOf {
            visibleItems.asSequence()
                .filter { it.canDelete && it.kind.risk == StorageSpecialCleanupRisk.Low }
                .mapTo(linkedSetOf(), StorageSpecialCleanupItem::stableId)
        }
    }
    val selectedItems by remember(allItems, selectedIds) {
        derivedStateOf { allItems.filter { it.canDelete && it.stableId in selectedIds } }
    }
    val selectedVisibleCount by remember(selectableIds, selectedIds) {
        derivedStateOf { selectedIds.count { it in selectableIds } }
    }

    val title = if (mode == StorageSpecialCleanupMode.Downloads) "下载与安装包" else "基础垃圾文件"
    val eyebrow = if (mode == StorageSpecialCleanupMode.Downloads) "DOWNLOAD CLEANUP" else "BASIC JUNK"
    val subtitle = if (mode == StorageSpecialCleanupMode.Downloads) {
        "整理安装包、压缩包、下载残留、长期未整理和大型下载文件。"
    } else {
        "检查零字节文件、空文件夹、下载残留、旧临时文件、日志和备份。"
    }
    val accent = if (mode == StorageSpecialCleanupMode.Downloads) Color(0xFF9CD8FF) else Color(0xFFFFCA72)
    val treeUri = repository.savedTreeUri(mode)

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
                    Text(eyebrow, color = accent.copy(alpha = 0.78f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(title, color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(subtitle, color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            item {
                StorageNoticePanel(
                    title = "扫描边界",
                    text = if (mode == StorageSpecialCleanupMode.Downloads) {
                        "先读取 Android 允许访问的系统下载集合；授权目录后会额外扫描该目录及子目录。普通应用不能绕过系统直接读取所有下载文件。"
                    } else {
                        "只检查你主动授权的目录。低风险仅表示规则较明确，删除前仍会展示项目并再次确认。"
                    },
                    tone = accent,
                )
            }
            item {
                StorageSection("扫描范围") {
                    StorageAccessRow(
                        title = if (mode == StorageSpecialCleanupMode.Downloads) "下载目录补充授权" else "垃圾文件检查目录",
                        detail = when {
                            treeUri == null && mode == StorageSpecialCleanupMode.Downloads -> "尚未选择目录；当前只显示系统允许访问的下载集合。"
                            treeUri == null -> "请选择一个需要检查的目录。"
                            else -> "已授权：${snapshot?.treeName ?: "目录"}"
                        },
                        granted = treeUri != null,
                        actionText = if (treeUri == null) "选择目录" else "更换目录",
                        onAction = { folderLauncher.launch(treeUri) },
                    )
                    if (treeUri != null) {
                        StorageInlineAction("撤销此专项的目录授权") {
                            repository.clearTreeUri(mode)
                            selectedIds = emptySet()
                            snapshot = null
                            message = "专项目录授权已撤销。"
                            scan()
                        }
                    }
                    StoragePrimaryAction(
                        text = if (scanning) "正在扫描…" else "重新扫描",
                        enabled = !scanning && !operationRunning,
                        onClick = ::scan,
                    )
                }
            }
            message?.let { item { StorageNoticePanel("当前状态", it, StorageWarning) } }
            snapshot?.let { current ->
                item {
                    StorageSection("扫描结果") {
                        StorageMetricRow("发现项目", "${current.items.size} 个")
                        StorageMetricRow("候选体积", formatStorageBytes(current.items.sumOf { it.sizeBytes }))
                        StorageMetricRow("授权目录扫描", "${current.scannedFileCount} 文件 · ${current.scannedDirectoryCount} 目录")
                        if (mode == StorageSpecialCleanupMode.Downloads) {
                            StorageMetricRow("系统下载集合", "${current.mediaStoreCount} 个")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            StorageFilterChip("全部 ${allItems.size}", selectedKind == null) {
                                selectedKind = null
                                expanded = false
                            }
                            availableKinds.forEach { kind ->
                                val count = allItems.count { it.kind == kind }
                                StorageFilterChip("${kind.label} $count", selectedKind == kind) {
                                    selectedKind = kind
                                    expanded = false
                                }
                            }
                        }
                        StorageLongListControls(
                            totalCount = visibleItems.size,
                            expanded = expanded,
                            previewCount = STORAGE_FILE_PREVIEW_COUNT,
                            selectedCount = selectedVisibleCount,
                            onToggleExpanded = { expanded = !expanded },
                            onSelectAll = if (selectableIds.isEmpty()) null else ({ selectedIds = selectedIds + selectableIds }),
                            onClearSelection = if (selectedVisibleCount == 0) null else ({ selectedIds = selectedIds - selectableIds }),
                            tone = accent,
                        )
                        if (lowRiskIds.isNotEmpty()) {
                            StorageInlineAction("只选择当前分类中的低风险项目 · ${lowRiskIds.size} 个") {
                                selectedIds = selectedIds + lowRiskIds
                            }
                        }
                        if (selectedItems.isNotEmpty()) {
                            StorageMetricRow("已选择", "${selectedItems.size} 个 · ${formatStorageBytes(selectedItems.sumOf { it.sizeBytes })}")
                            StoragePrimaryAction(
                                text = if (operationRunning) "正在处理…" else "清理已选 ${selectedItems.size} 项",
                                enabled = !operationRunning,
                            ) { pendingDelete = selectedItems }
                        }
                    }
                }
            }
            when {
                scanning && snapshot == null -> item { StorageLoadingPanel("正在建立专项文件索引…") }
                snapshot == null -> item { StorageEmptyPanel("开始扫描后显示结果。") }
                visibleItems.isEmpty() -> item {
                    StorageEmptyPanel(
                        if (mode == StorageSpecialCleanupMode.Junk && treeUri == null) {
                            "选择需要检查的目录后才能识别基础垃圾文件。"
                        } else {
                            "当前范围内没有符合规则的项目。"
                        },
                    )
                }
                else -> items(displayedItems, key = StorageSpecialCleanupItem::stableId) { item ->
                    StorageSpecialCleanupCard(
                        item = item,
                        selected = item.stableId in selectedIds,
                        accent = accent,
                        onToggle = {
                            if (item.canDelete) {
                                selectedIds = if (item.stableId in selectedIds) selectedIds - item.stableId else selectedIds + item.stableId
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { items ->
        StorageSpecialCleanupDeleteDialog(
            title = title,
            items = items,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                executeDelete(items)
            },
        )
    }
}

@Composable
private fun StorageSpecialCleanupCard(
    item: StorageSpecialCleanupItem,
    selected: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    val tone = when (item.kind.risk) {
        StorageSpecialCleanupRisk.Low -> StorageSuccess
        StorageSpecialCleanupRisk.Review -> accent
    }
    val shape = RoundedCornerShape(21.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().composeGlassMotionClickable(shape = shape, enabled = item.canDelete, onClick = onToggle),
        shape = shape,
        color = tone.copy(alpha = if (selected) 0.14f else 0.05f),
        border = BorderStroke(1.dp, tone.copy(alpha = if (selected) 0.36f else 0.12f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.width(23.dp).height(23.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (selected) "✓" else if (item.isDirectory) "夹" else "", color = tone, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.displayName,
                        color = Color.White.copy(alpha = if (item.canDelete) 0.92f else 0.45f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatStorageBytes(item.sizeBytes), color = tone, fontSize = 9.8.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    "${item.kind.label} · ${item.kind.risk.label} · ${if (item.source == StorageSpecialCleanupSource.MediaStoreDownloads) "系统下载集合" else "授权目录"}",
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 9.4.sp,
                )
                if (item.modifiedAt > 0L) {
                    Text("最后修改 ${formatSpecialCleanupDate(item.modifiedAt)}", color = Color.White.copy(alpha = 0.36f), fontSize = 9.sp)
                }
                Text(item.location, color = Color.White.copy(alpha = 0.30f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.kind.explanation, color = Color.White.copy(alpha = 0.48f), fontSize = 9.4.sp, lineHeight = 13.sp)
                if (!item.canDelete) Text("文档提供方未开放删除能力", color = StorageWarning, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun StorageSpecialCleanupDeleteDialog(
    title: String,
    items: List<StorageSpecialCleanupItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val reviewCount = items.count { it.kind.risk == StorageSpecialCleanupRisk.Review }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10163A).copy(alpha = 0.99f),
        tonalElevation = 0.dp,
        title = { Text("确认清理$title", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "共 ${items.size} 个项目，约 ${formatStorageBytes(items.sumOf { it.sizeBytes })}。",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (reviewCount > 0) {
                    Text("其中 $reviewCount 个项目需要人工检查，不会因为全选而跳过本次确认。", color = StorageWarning, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Text("系统下载集合会交给 Android 再次确认；授权目录项目按当前选择删除。删除后不保证能够恢复。", color = Color.White.copy(alpha = 0.58f), fontSize = 11.5.sp, lineHeight = 17.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("继续清理", color = StorageCritical, fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color.White.copy(alpha = 0.64f), fontWeight = FontWeight.Bold) }
        },
    )
}

private fun formatSpecialCleanupDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp))
}
