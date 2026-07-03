package com.yuchen.ailedger.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.StorageFolderIndexState
import com.yuchen.ailedger.service.StorageIndexedLargeFile
import com.yuchen.ailedger.service.StorageManagementRepository
import com.yuchen.ailedger.service.StorageResumableFolderCompleteRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun StorageFolderIndexCompleteScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { StorageResumableFolderCompleteRepository(context.applicationContext) }
    val storageRepository = remember(context) { StorageManagementRepository(context.applicationContext) }
    val stopSignal = remember { AtomicBoolean(false) }
    var result by remember { mutableStateOf<StorageFolderIndexState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var scanning by remember { mutableStateOf(false) }
    var filesExpanded by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val persisted = storageRepository.persistTreeUri(uri)
            message = if (persisted) "目录授权已更新，完整索引已重新建立断点。" else "系统没有授予可持久化目录权限。"
            if (persisted) scope.launch { result = withContext(Dispatchers.IO) { repository.reset() } }
        }
    }

    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume) firstResume = false else generation += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            stopSignal.set(true)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(generation) {
        val loaded = withContext(Dispatchers.IO) { runCatching { repository.loadState() } }
        loaded.onSuccess { result = it }
            .onFailure { message = it.message?.takeIf(String::isNotBlank) ?: "无法读取目录索引断点" }
        loading = false
    }

    fun scan(reset: Boolean) {
        if (scanning) return
        stopSignal.set(false)
        scanning = true
        message = null
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    if (reset) repository.reset()
                    repository.scanNextPage(stopSignal = stopSignal)
                }
            }
            loaded.onSuccess { stateResult ->
                result = stateResult
                val progress = stateResult.progress
                message = when {
                    stateResult.blockedReason != null -> stateResult.blockedReason
                    progress == null -> "没有可用的目录索引。"
                    progress.complete -> "完整索引已完成，共扫描 ${progress.scannedFiles} 个文件。"
                    progress.interrupted -> "扫描已暂停，目录队列和文件位置已保存。"
                    else -> "本批完成：累计 ${progress.scannedFiles} 个文件，仍有 ${progress.queuedDirectories} 个目录节点。"
                }
            }.onFailure { message = it.message?.takeIf(String::isNotBlank) ?: "目录扫描失败，当前断点已保留" }
            scanning = false
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
                    modifier = Modifier.width(144.dp).height(40.dp),
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
                    Text("FOLDER INDEX", color = FolderAccent.copy(alpha = 0.74f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("授权目录索引", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text("分批执行但完整覆盖你主动授权目录中的全部层级和文件。", color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            item {
                FolderInfoPanel(
                    "完整索引模式",
                    "每批处理 300 个文件并持续保存断点。分批只用于保持界面流畅，不限制最终扫描数量、目录层级或结果数量。",
                    FolderAccent,
                )
            }
            message?.let { item { FolderInfoPanel("当前状态", it, FolderSuccess) } }
            if (loading || result == null) {
                item { FolderLoadingPanel("正在恢复目录队列和全部文件索引…") }
            } else {
                val current = result!!
                if (!current.permissionValid || current.progress == null) {
                    item { FolderInfoPanel("需要目录授权", current.blockedReason ?: "请重新选择目录。", FolderWarning) }
                    item { FolderPrimaryAction("选择或重新授权目录", true) { treeLauncher.launch(storageRepository.savedTreeUri()) } }
                } else {
                    item { FolderGuardPanel(current) }
                    item {
                        FolderProgressPanel(
                            state = current,
                            scanning = scanning,
                            onContinue = { scan(false) },
                            onRestart = { scan(true) },
                            onStop = {
                                stopSignal.set(true)
                                message = "正在暂停；当前文件处理结束后会保存断点。"
                            },
                            onChangeFolder = { treeLauncher.launch(storageRepository.savedTreeUri()) },
                        )
                    }
                    current.progress.errorMessage?.let { item { FolderInfoPanel("部分目录已跳过", it, FolderWarning) } }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            FolderSectionHeader("已索引文件", "全部 ${current.largestFiles.size} 个")
                            StorageLongListControls(
                                totalCount = current.largestFiles.size,
                                expanded = filesExpanded,
                                previewCount = STORAGE_FILE_PREVIEW_COUNT,
                                onToggleExpanded = { filesExpanded = !filesExpanded },
                                tone = FolderAccent,
                            )
                        }
                    }
                    if (current.largestFiles.isEmpty()) {
                        item { FolderInfoPanel("暂无结果", "继续扫描后将按文件大小展示全部索引结果。", Color.White) }
                    } else {
                        items(
                            storagePreviewItems(current.largestFiles, filesExpanded, STORAGE_FILE_PREVIEW_COUNT),
                            key = StorageIndexedLargeFile::uri,
                        ) { file ->
                            IndexedFileCard(file) {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(file.uri), file.mimeType.ifBlank { "*/*" })
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(intent) }
                                    .onFailure { message = "没有应用能够打开该文件。" }
                            }
                        }
                    }
                }
            }
        }
    }
}
