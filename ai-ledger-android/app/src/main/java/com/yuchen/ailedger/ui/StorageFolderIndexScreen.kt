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
import com.yuchen.ailedger.service.StorageResumableFolderRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageFolderIndexScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { StorageResumableFolderRepository(context.applicationContext) }
    val storageRepository = remember(context) { StorageManagementRepository(context.applicationContext) }
    val stopSignal = remember { AtomicBoolean(false) }

    var indexState by remember { mutableStateOf<StorageFolderIndexState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var scanning by remember { mutableStateOf(false) }
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val persisted = storageRepository.persistTreeUri(uri)
            message = if (persisted) "目录授权已更新，已创建新的分页索引断点。" else "系统没有授予可持久化目录权限。"
            if (persisted) {
                scope.launch {
                    indexState = withContext(Dispatchers.IO) { repository.reset() }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume) firstResume = false else refreshGeneration += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            stopSignal.set(true)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshGeneration) {
        val loaded = withContext(Dispatchers.IO) { runCatching { repository.loadState() } }
        loaded.onSuccess { indexState = it }
            .onFailure { message = it.message?.takeIf(String::isNotBlank) ?: "无法读取目录索引断点" }
        loading = false
    }

    fun scanPage(reset: Boolean) {
        if (scanning) return
        stopSignal.set(false)
        scanning = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (reset) repository.reset()
                    repository.scanNextPage(stopSignal = stopSignal)
                }
            }
            result.onSuccess { loaded ->
                indexState = loaded
                val progress = loaded.progress
                message = when {
                    loaded.blockedReason != null -> loaded.blockedReason
                    progress == null -> "没有可用的目录索引。"
                    progress.complete -> "目录索引已完成，共扫描 ${progress.scannedFiles} 个文件。"
                    progress.interrupted -> "扫描已暂停，目录队列和文件位置已保存。"
                    else -> "本批扫描完成：累计 ${progress.scannedFiles} 个文件，仍有 ${progress.queuedDirectories} 个目录节点待处理。"
                }
            }.onFailure { error ->
                message = error.message?.takeIf(String::isNotBlank) ?: "目录分页扫描失败，当前断点已保留"
            }
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
                    Text("大目录索引", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(
                        "按批扫描用户授权目录，保存目录队列和当前位置，用于统计与寻找大型文件。",
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            item {
                FolderInfoPanel(
                    "安全说明",
                    "每批最多处理 300 个文件，每 10 个文件保存一次检查点。索引不提供删除按钮，也不会读取其他应用私有目录；文档提供方列表顺序变化时，最多可能重复统计少量文件，但不会据此执行清理。",
                    FolderAccent,
                )
            }
            message?.let { text -> item { FolderInfoPanel("当前状态", text, FolderSuccess) } }
            if (loading || indexState == null) {
                item { FolderLoadingPanel("正在恢复目录队列和大型文件排行…") }
            } else {
                val current = indexState!!
                if (!current.permissionValid || current.progress == null) {
                    item {
                        FolderInfoPanel(
                            "需要目录授权",
                            current.blockedReason ?: "请重新选择要建立索引的目录。",
                            FolderWarning,
                        )
                    }
                    item {
                        FolderPrimaryAction("选择或重新授权目录", true) {
                            treeLauncher.launch(storageRepository.savedTreeUri())
                        }
                    }
                } else {
                    item { FolderGuardPanel(current) }
                    item {
                        FolderProgressPanel(
                            state = current,
                            scanning = scanning,
                            onContinue = { scanPage(reset = false) },
                            onRestart = { scanPage(reset = true) },
                            onStop = {
                                stopSignal.set(true)
                                message = "正在暂停；当前文件处理结束后会保存断点。"
                            },
                            onChangeFolder = { treeLauncher.launch(storageRepository.savedTreeUri()) },
                        )
                    }
                    current.progress.errorMessage?.let { error ->
                        item { FolderInfoPanel("部分目录已跳过", error, FolderWarning) }
                    }
                    item {
                        FolderSectionHeader(
                            "已索引的大型文件",
                            "显示前 ${current.largestFiles.size.coerceAtMost(120)} 个",
                        )
                    }
                    if (current.largestFiles.isEmpty()) {
                        item { FolderInfoPanel("暂无结果", "继续扫描后将按文件大小保留前 120 个索引结果。", Color.White) }
                    } else {
                        items(current.largestFiles, key = StorageIndexedLargeFile::uri) { file ->
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
