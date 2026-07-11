package com.yuchen.ailedger.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuchen.ailedger.model.ActionGroupContentBlock
import com.yuchen.ailedger.model.CalloutContentBlock
import com.yuchen.ailedger.model.ChartContentBlock
import com.yuchen.ailedger.model.CodeContentBlock
import com.yuchen.ailedger.model.ImageContentBlock
import com.yuchen.ailedger.model.ImageGalleryContentBlock
import com.yuchen.ailedger.model.KeyValueContentBlock
import com.yuchen.ailedger.model.MessageActionType
import com.yuchen.ailedger.model.MessageCalloutTone
import com.yuchen.ailedger.model.MessageContentBlock
import com.yuchen.ailedger.model.MessageImageItem
import com.yuchen.ailedger.model.RichTextContentBlock
import com.yuchen.ailedger.model.TableContentBlock
import com.yuchen.ailedger.service.ProjectArtifactExportService
import com.yuchen.ailedger.service.ProjectRevisionSummary
import com.yuchen.ailedger.service.ProjectWorkspaceStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val RESULT_IMAGE_MAX_BYTES = 12 * 1024 * 1024
private const val RESULT_IMAGE_BUFFER_BYTES = 16 * 1024
private const val COMPACT_CODE_LINES = 12
private const val COMPACT_TABLE_ROWS = 5

private val resultImageHttpClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }

private sealed interface ExpandedResult {
    data class Code(val block: CodeContentBlock) : ExpandedResult
    data class Table(val block: TableContentBlock) : ExpandedResult
    data class Chart(val block: ChartContentBlock) : ExpandedResult
    data class Image(val image: MessageImageItem) : ExpandedResult
    data class ProjectFiles(val project: ProjectPreviewDescriptor) : ExpandedResult
    data class ProjectVersions(val project: ProjectPreviewDescriptor) : ExpandedResult
}

@Composable
fun MessageContentBlockList(
    blocks: List<MessageContentBlock>,
    onOpenUrl: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (blocks.isEmpty()) return
    val presented = remember(blocks) { adaptAgentResultContent(blocks) }
    var expandedResult by remember(blocks) { mutableStateOf<ExpandedResult?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presented.forEach { content ->
            when (content) {
                is PresentedMessageContent.Project -> ProjectResultCard(
                    project = content.descriptor,
                    onOpenFiles = { expandedResult = ExpandedResult.ProjectFiles(content.descriptor) },
                    onOpenVersions = { expandedResult = ExpandedResult.ProjectVersions(content.descriptor) },
                )
                is PresentedMessageContent.Standard -> when (val block = content.block) {
                    is RichTextContentBlock -> RichTextBlockView(block)
                    is CodeContentBlock -> CompactCodeResultCard(block) {
                        expandedResult = ExpandedResult.Code(block)
                    }
                    is TableContentBlock -> CompactTableResultCard(block) {
                        expandedResult = ExpandedResult.Table(block)
                    }
                    is ChartContentBlock -> CompactChartResultCard(block) {
                        expandedResult = ExpandedResult.Chart(block)
                    }
                    is ImageContentBlock -> CompactImageResultCard(block.image) {
                        expandedResult = ExpandedResult.Image(block.image)
                    }
                    is ImageGalleryContentBlock -> ImageGalleryResultCard(block) { image ->
                        expandedResult = ExpandedResult.Image(image)
                    }
                    is KeyValueContentBlock -> KeyValueBlockView(block)
                    is CalloutContentBlock -> CalloutBlockView(block)
                    is ActionGroupContentBlock -> ActionGroupBlockView(block, onOpenUrl)
                }
            }
        }
    }

    expandedResult?.let { result ->
        ExpandedResultDialog(
            result = result,
            onDismiss = { expandedResult = null },
        )
    }
}

@Composable
private fun RichTextBlockView(block: RichTextContentBlock) {
    ResultSurface {
        OptimizedRichMessageContent(
            text = block.text,
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun CompactCodeResultCard(block: CodeContentBlock, onExpand: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val lines = remember(block.code) { block.code.lines() }
    val preview = remember(block.code) { lines.take(COMPACT_CODE_LINES).joinToString("\n") }
    ResultSurface {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultHeader(
                title = block.fileName ?: block.language?.uppercase() ?: "代码",
                subtitle = block.caption ?: "${lines.size} 行",
                actions = {
                    ResultAction("复制") { clipboard.setText(AnnotatedString(block.code)) }
                    TextDownloadAction(
                        label = "下载",
                        fileName = block.fileName ?: defaultCodeFileName(block.language),
                        mimeType = "text/plain",
                        content = block.code,
                    )
                    ResultAction("⤢", onExpand)
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .horizontalScroll(rememberScrollState())
                    .padding(11.dp),
            ) {
                Text(
                    text = preview,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (lines.size > COMPACT_CODE_LINES) {
                Text(
                    text = "已折叠 ${lines.size - COMPACT_CODE_LINES} 行 · 点击右上角全屏查看",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 8.5.sp,
                )
            }
        }
    }
}

@Composable
private fun CompactTableResultCard(block: TableContentBlock, onExpand: () -> Unit) {
    val csv = remember(block) { tableToCsv(block) }
    ResultSurface {
        Column(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultHeader(
                title = block.title ?: "数据表格",
                subtitle = "${block.rows.size} 行 · ${block.columns.size} 列",
                actions = {
                    TextDownloadAction("下载", safeFileName(block.title ?: "table", "csv"), "text/csv", csv)
                    ResultAction("⤢", onExpand)
                },
                modifier = Modifier.padding(horizontal = 11.dp),
            )
            TablePreview(block = block, rows = block.rows.take(COMPACT_TABLE_ROWS))
            if (block.rows.size > COMPACT_TABLE_ROWS) {
                Text(
                    text = "还有 ${block.rows.size - COMPACT_TABLE_ROWS} 行，展开后查看完整数据",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 8.5.sp,
                    modifier = Modifier.padding(horizontal = 11.dp),
                )
            }
        }
    }
}

@Composable
private fun TablePreview(block: TableContentBlock, rows: List<List<String>>) {
    val columnCount = block.columns.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 0)
    if (columnCount <= 0) return
    val columns = List(columnCount) { block.columns.getOrNull(it).orEmpty() }
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        if (columns.any(String::isNotBlank)) ResultTableRow(columns, header = true)
        rows.forEachIndexed { index, row ->
            ResultTableRow(
                cells = List(columnCount) { row.getOrNull(it).orEmpty() },
                header = false,
                alternate = index % 2 == 1,
            )
        }
    }
}

@Composable
private fun ResultTableRow(cells: List<String>, header: Boolean, alternate: Boolean = false) {
    Row(
        modifier = Modifier.background(
            Color.White.copy(alpha = when {
                header -> 0.10f
                alternate -> 0.045f
                else -> 0.015f
            }),
        ),
        verticalAlignment = Alignment.Top,
    ) {
        cells.forEach { cell ->
            Text(
                text = cell,
                color = Color.White.copy(alpha = if (header) 0.88f else 0.72f),
                fontSize = if (header) 9.5.sp else 9.sp,
                lineHeight = 14.sp,
                fontWeight = if (header) FontWeight.Black else FontWeight.Medium,
                modifier = Modifier.width(126.dp).padding(horizontal = 9.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CompactChartResultCard(block: ChartContentBlock, onExpand: () -> Unit) {
    val csv = remember(block) { chartToCsv(block) }
    ResultSurface {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultHeader(
                title = block.title ?: "数据图表",
                subtitle = block.subtitle ?: chartTypeLabel(block),
                actions = {
                    TextDownloadAction("数据", safeFileName(block.title ?: "chart", "csv"), "text/csv", csv)
                    ResultAction("⤢", onExpand)
                },
            )
            MessageChartCanvas(
                block = block,
                modifier = Modifier.height(190.dp).clickable(onClick = onExpand),
            )
            MessageChartLegend(block.series)
        }
    }
}

@Composable
private fun CompactImageResultCard(image: MessageImageItem, onExpand: () -> Unit) {
    val state by rememberMessageImageState(image)
    ResultSurface {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ResultHeader(
                title = image.alt.ifBlank { "图片" },
                subtitle = image.caption,
                actions = {
                    ImageDownloadAction(image, state)
                    ResultAction("⤢", onExpand)
                },
            )
            MessageImageView(
                image = image,
                state = state,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand),
            )
        }
    }
}

@Composable
private fun ImageGalleryResultCard(block: ImageGalleryContentBlock, onExpand: (MessageImageItem) -> Unit) {
    ResultSurface {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultHeader(
                title = block.title ?: "图片成果",
                subtitle = "${block.images.size} 张",
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                block.images.forEach { image ->
                    val state by rememberMessageImageState(image)
                    MessageImageView(
                        image = image,
                        state = state,
                        modifier = Modifier.width(220.dp).clickable { onExpand(image) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectResultCard(
    project: ProjectPreviewDescriptor,
    onOpenFiles: () -> Unit,
    onOpenVersions: () -> Unit,
) {
    val context = LocalContext.current
    ResultSurface {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ResultHeader(
                title = project.title,
                subtitle = listOfNotNull(project.revisionId, project.statusLabel).joinToString(" · "),
                actions = {
                    ResultAction("⤢") { openProjectPreview(context, project) }
                },
            )
            InlineProjectPreview(project)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = project.frameworkLabel,
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                project.fileCount?.let {
                    Text("$it 个文件", color = Color.White.copy(alpha = 0.36f), fontSize = 8.5.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ResultAction("全屏") { openProjectPreview(context, project) }
                ResultAction("代码", onOpenFiles)
                ResultAction("版本", onOpenVersions)
                ProjectDownloadAction(project)
            }
        }
    }
}

@Composable
private fun KeyValueBlockView(block: KeyValueContentBlock) {
    ResultSurface {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            block.title?.takeIf(String::isNotBlank)?.let {
                Text(it, color = Color.White.copy(alpha = 0.90f), fontSize = 11.5.sp, fontWeight = FontWeight.Black)
            }
            block.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.045f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.label, color = Color.White.copy(alpha = 0.44f), fontSize = 8.5.sp)
                        item.detail?.takeIf(String::isNotBlank)?.let {
                            Text(it, color = Color.White.copy(alpha = 0.34f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        item.value,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalloutBlockView(block: CalloutContentBlock) {
    val accent = when (block.tone) {
        MessageCalloutTone.Info -> Color(0xFF9FD8FF)
        MessageCalloutTone.Success -> Color(0xFF8DF9C2)
        MessageCalloutTone.Warning -> Color(0xFFFFD26A)
        MessageCalloutTone.Error -> Color(0xFFFFA2A2)
    }
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(accent.copy(alpha = 0.09f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(accent))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.title?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = accent.copy(alpha = 0.94f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                OptimizedRichMessageContent(
                    text = block.text,
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ActionGroupBlockView(
    block: ActionGroupContentBlock,
    onOpenUrl: (title: String, url: String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    ResultSurface {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            block.title?.takeIf(String::isNotBlank)?.let {
                Text(it, color = Color.White.copy(alpha = 0.82f), fontSize = 10.5.sp, fontWeight = FontWeight.Black)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                block.actions.forEach { action ->
                    val enabled = when (action.type) {
                        MessageActionType.OpenUrl -> action.value.startsWith("https://") || action.value.startsWith("http://")
                        MessageActionType.CopyText -> action.value.isNotBlank()
                    }
                    ResultAction(action.label, enabled = enabled, emphasis = action.emphasis) {
                        when (action.type) {
                            MessageActionType.CopyText -> clipboard.setText(AnnotatedString(action.value))
                            MessageActionType.OpenUrl -> {
                                if (ProjectPreviewActivity.canOpen(action.value)) {
                                    runCatching { context.startActivity(ProjectPreviewActivity.createIntent(context, action.value)) }
                                } else {
                                    onOpenUrl(action.label, action.value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedResultDialog(result: ExpandedResult, onDismiss: () -> Unit) {
    when (result) {
        is ExpandedResult.ProjectFiles -> ProjectFilesDialog(result.project, onDismiss)
        is ExpandedResult.ProjectVersions -> ProjectVersionsDialog(result.project, onDismiss)
        else -> Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070A10))
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                FullscreenHeader(
                    title = when (result) {
                        is ExpandedResult.Code -> result.block.fileName ?: result.block.language?.uppercase() ?: "代码"
                        is ExpandedResult.Table -> result.block.title ?: "数据表格"
                        is ExpandedResult.Chart -> result.block.title ?: "数据图表"
                        is ExpandedResult.Image -> result.image.alt.ifBlank { "图片" }
                        else -> "成果预览"
                    },
                    onClose = onDismiss,
                )
                when (result) {
                    is ExpandedResult.Code -> FullscreenCode(result.block)
                    is ExpandedResult.Table -> FullscreenTable(result.block)
                    is ExpandedResult.Chart -> FullscreenChart(result.block)
                    is ExpandedResult.Image -> FullscreenImage(result.image)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun FullscreenHeader(title: String, onClose: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF0B1019)).padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResultAction("返回", onClick = onClose)
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
private fun FullscreenCode(block: CodeContentBlock) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ResultAction("复制") { clipboard.setText(AnnotatedString(block.code)) }
            TextDownloadAction("下载", block.fileName ?: defaultCodeFileName(block.language), "text/plain", block.code)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        ) {
            Text(
                block.code,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 11.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun FullscreenTable(block: TableContentBlock) {
    val csv = remember(block) { tableToCsv(block) }
    Column(Modifier.fillMaxSize().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TextDownloadAction("下载 CSV", safeFileName(block.title ?: "table", "csv"), "text/csv", csv)
        }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TablePreview(block, block.rows)
        }
    }
}

@Composable
private fun FullscreenChart(block: ChartContentBlock) {
    val csv = remember(block) { chartToCsv(block) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TextDownloadAction("下载数据", safeFileName(block.title ?: "chart", "csv"), "text/csv", csv)
        }
        block.subtitle?.takeIf(String::isNotBlank)?.let {
            Text(it, color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, lineHeight = 14.sp)
        }
        MessageChartCanvas(block, Modifier.height(380.dp))
        MessageChartLegend(block.series)
        ChartDataDetails(block)
    }
}

@Composable
private fun ChartDataDetails(block: ChartContentBlock) {
    block.series.forEachIndexed { seriesIndex, series ->
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                series.name.ifBlank { "系列 ${seriesIndex + 1}" },
                color = messageChartColor(seriesIndex).copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
            series.points.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { point ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.05f)).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(point.label.ifBlank { point.x?.toString() ?: "数据" }, color = Color.White.copy(alpha = 0.42f), fontSize = 8.sp, maxLines = 1)
                            Text(formatChartNumber(point.y), color = Color.White.copy(alpha = 0.86f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FullscreenImage(image: MessageImageItem) {
    val state by rememberMessageImageState(image)
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ImageDownloadAction(image, state, label = "下载图片")
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageImageView(image, state, Modifier.fillMaxSize(), fullscreen = true)
        }
    }
}

@Composable
private fun ProjectFilesDialog(project: ProjectPreviewDescriptor, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val files by produceState<List<String>>(emptyList(), project.stableKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { ProjectWorkspaceStore(context.applicationContext).listFiles(project.projectId) }.getOrDefault(emptyList())
        }
    }
    var selectedPath by remember(project.stableKey, files) { mutableStateOf(files.firstOrNull()) }
    val content by produceState("", project.stableKey, selectedPath) {
        value = withContext(Dispatchers.IO) {
            selectedPath?.let { path ->
                runCatching { ProjectWorkspaceStore(context.applicationContext).readFile(project.projectId, path).first }.getOrDefault("")
            }.orEmpty()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF070A10)).statusBarsPadding().navigationBarsPadding(),
        ) {
            FullscreenHeader("${project.title} · 代码", onDismiss) {
                selectedPath?.let { path ->
                    TextDownloadAction("下载", path.substringAfterLast('/'), "text/plain", content)
                }
            }
            if (files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有可显示的项目文件", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    files.forEach { path ->
                        ResultAction(path.substringAfterLast('/'), emphasis = path == selectedPath) { selectedPath = path }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.Black.copy(alpha = 0.24f))
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                        .padding(13.dp),
                ) {
                    Text(content, color = Color.White.copy(alpha = 0.90f), fontSize = 10.5.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun ProjectVersionsDialog(project: ProjectPreviewDescriptor, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val revisions by produceState<List<ProjectRevisionSummary>>(emptyList(), project.stableKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { ProjectWorkspaceStore(context.applicationContext).listRevisions(project.projectId) }.getOrDefault(emptyList())
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF070A10)).statusBarsPadding().navigationBarsPadding()) {
            FullscreenHeader("${project.title} · 版本", onDismiss)
            if (revisions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无版本记录", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(revisions, key = { it.revisionId }) { revision ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.055f))
                                .padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(revision.revisionId, color = Color.White.copy(alpha = 0.90f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                                Text(revision.summary, color = Color.White.copy(alpha = 0.48f), fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(formatRevisionTime(revision.createdAt), color = Color.White.copy(alpha = 0.30f), fontSize = 8.sp)
                            }
                            Text("${revision.fileCount} 文件", color = Color.White.copy(alpha = 0.38f), fontSize = 8.5.sp)
                            ResultAction("预览") { openProjectPreview(context, project, revision.revisionId) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 11.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf(String::isNotBlank)?.let {
                Text(it, color = Color.White.copy(alpha = 0.42f), fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

@Composable
private fun ResultAction(
    label: String,
    enabled: Boolean = true,
    emphasis: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = Color.White.copy(alpha = if (enabled) 0.82f else 0.30f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (emphasis) 0.14f else 0.07f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

@Composable
private fun ResultSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.075f)),
    ) {
        content()
    }
}

@Composable
private fun TextDownloadAction(
    label: String,
    fileName: String,
    mimeType: String,
    content: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                        ?: error("output_unavailable")
                }.isSuccess
            }
            Toast.makeText(context, if (success) "文件已保存" else "文件保存失败", Toast.LENGTH_SHORT).show()
        }
    }
    ResultAction(label) { launcher.launch(fileName) }
}

@Composable
private fun ProjectDownloadAction(project: ProjectPreviewDescriptor) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingFile by remember(project.stableKey) { mutableStateOf<java.io.File?>(null) }
    var busy by remember(project.stableKey) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val source = pendingFile
        if (uri == null || source == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { ProjectArtifactExportService.copyToUri(context, source, uri) }.isSuccess
            }
            busy = false
            Toast.makeText(context, if (success) "项目 ZIP 已保存" else "项目导出失败", Toast.LENGTH_SHORT).show()
        }
    }
    ResultAction(if (busy) "导出中…" else "下载 ZIP", enabled = !busy) {
        busy = true
        scope.launch {
            val generated = withContext(Dispatchers.IO) {
                runCatching {
                    ProjectArtifactExportService.createZip(context, project.projectId, project.revisionId)
                }.getOrNull()
            }
            if (generated == null) {
                busy = false
                Toast.makeText(context, "项目导出失败", Toast.LENGTH_SHORT).show()
            } else {
                pendingFile = generated
                launcher.launch(generated.name)
            }
        }
    }
}

@Composable
private fun ImageDownloadAction(
    image: MessageImageItem,
    state: MessageImageLoadState,
    label: String = "下载",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ready = state as? MessageImageLoadState.Ready
    val mime = ready?.mimeType ?: image.mimeType ?: "image/png"
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val bytes = ready?.bytes
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("output_unavailable")
                }.isSuccess
            }
            Toast.makeText(context, if (success) "图片已保存" else "图片保存失败", Toast.LENGTH_SHORT).show()
        }
    }
    ResultAction(label, enabled = ready != null) {
        launcher.launch(imageDownloadName(image, mime))
    }
}

@Composable
private fun rememberMessageImageState(image: MessageImageItem): androidx.compose.runtime.State<MessageImageLoadState> {
    val context = LocalContext.current
    return produceState<MessageImageLoadState>(MessageImageLoadState.Loading, image.source, image.mimeType) {
        value = withContext(Dispatchers.IO) { loadMessageImage(context, image) }
    }
}

@Composable
private fun MessageImageView(
    image: MessageImageItem,
    state: MessageImageLoadState,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.height(resolveImageHeight(image)))
                .clip(RoundedCornerShape(15.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                MessageImageLoadState.Loading -> Text("正在加载图片…", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp)
                MessageImageLoadState.Failed -> Text("图片加载失败", color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp)
                is MessageImageLoadState.Ready -> Image(
                    bitmap = state.bitmap,
                    contentDescription = image.alt,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        if (!fullscreen) {
            image.caption?.takeIf(String::isNotBlank)?.let {
                Text(it, color = Color.White.copy(alpha = 0.48f), fontSize = 8.5.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private sealed interface MessageImageLoadState {
    data object Loading : MessageImageLoadState
    data object Failed : MessageImageLoadState
    data class Ready(val bitmap: ImageBitmap, val bytes: ByteArray, val mimeType: String) : MessageImageLoadState
}

private fun loadMessageImage(context: Context, image: MessageImageItem): MessageImageLoadState {
    val source = image.source
    val bytes = runCatching {
        when {
            source.startsWith("data:image/", ignoreCase = true) -> {
                val encoded = source.substringAfter(',', "")
                if (encoded.length > RESULT_IMAGE_MAX_BYTES * 2) return MessageImageLoadState.Failed
                Base64.decode(encoded, Base64.DEFAULT)
            }
            source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true) -> {
                val request = Request.Builder().url(source).get().build()
                resultImageHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return MessageImageLoadState.Failed
                    val body = response.body ?: return MessageImageLoadState.Failed
                    if (body.contentLength() > RESULT_IMAGE_MAX_BYTES) return MessageImageLoadState.Failed
                    body.byteStream().use { it.readBoundedImageBytes() ?: return MessageImageLoadState.Failed }
                }
            }
            source.startsWith("content://", ignoreCase = true) || source.startsWith("file://", ignoreCase = true) -> {
                context.contentResolver.openInputStream(Uri.parse(source))?.use { it.readBoundedImageBytes() }
                    ?: return MessageImageLoadState.Failed
            }
            else -> {
                if (source.length > RESULT_IMAGE_MAX_BYTES * 2) return MessageImageLoadState.Failed
                Base64.decode(source, Base64.DEFAULT)
            }
        }
    }.getOrNull() ?: return MessageImageLoadState.Failed
    if (bytes.isEmpty() || bytes.size > RESULT_IMAGE_MAX_BYTES) return MessageImageLoadState.Failed
    val bitmap = ByteArrayInputStream(bytes).use(android.graphics.BitmapFactory::decodeStream)
        ?: return MessageImageLoadState.Failed
    return MessageImageLoadState.Ready(
        bitmap = bitmap.asImageBitmap(),
        bytes = bytes,
        mimeType = image.mimeType ?: inferImageMime(source),
    )
}

private fun InputStream.readBoundedImageBytes(): ByteArray? {
    val output = ByteArrayOutputStream(minOf(RESULT_IMAGE_MAX_BYTES, RESULT_IMAGE_BUFFER_BYTES * 4))
    val buffer = ByteArray(RESULT_IMAGE_BUFFER_BYTES)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total += count
        if (total > RESULT_IMAGE_MAX_BYTES) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun resolveImageHeight(image: MessageImageItem): androidx.compose.ui.unit.Dp {
    val ratio = if ((image.width ?: 0) > 0 && (image.height ?: 0) > 0) {
        image.width!!.toFloat() / image.height!!.toFloat()
    } else {
        16f / 10f
    }.coerceIn(0.62f, 2.2f)
    return (260f / ratio).coerceIn(150f, 300f).dp
}

private fun openProjectPreview(context: Context, project: ProjectPreviewDescriptor, revisionId: String? = project.revisionId) {
    val uri = Uri.parse(project.previewUrl).buildUpon().clearQuery()
        .appendQueryParameter("projectId", project.projectId)
        .apply { revisionId?.let { appendQueryParameter("revision", it) } }
        .build()
        .toString()
    runCatching { context.startActivity(ProjectPreviewActivity.createIntent(context, uri)) }
        .onFailure { Toast.makeText(context, "无法打开项目预览", Toast.LENGTH_SHORT).show() }
}

private fun tableToCsv(block: TableContentBlock): String = buildString {
    if (block.columns.isNotEmpty()) appendLine(block.columns.joinToString(",", transform = ::csvCell))
    block.rows.forEach { appendLine(it.joinToString(",", transform = ::csvCell)) }
}

private fun chartToCsv(block: ChartContentBlock): String = buildString {
    appendLine("series,label,x,y")
    block.series.forEach { series ->
        series.points.forEachIndexed { index, point ->
            append(csvCell(series.name.ifBlank { "series" })).append(',')
            append(csvCell(point.label)).append(',')
            append(point.x ?: index.toDouble()).append(',')
            append(point.y).appendLine()
        }
    }
}

private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

private fun chartTypeLabel(block: ChartContentBlock): String = when (block.type) {
    com.yuchen.ailedger.model.MessageChartType.Line -> "折线图"
    com.yuchen.ailedger.model.MessageChartType.Bar -> "柱状图"
    com.yuchen.ailedger.model.MessageChartType.Pie -> "饼图"
    com.yuchen.ailedger.model.MessageChartType.Scatter -> "散点图"
}

private fun defaultCodeFileName(language: String?): String = when (language?.lowercase()) {
    "kotlin", "kt" -> "code.kt"
    "java" -> "code.java"
    "javascript", "js" -> "code.js"
    "typescript", "ts" -> "code.ts"
    "python", "py" -> "code.py"
    "html" -> "index.html"
    "css" -> "styles.css"
    "json" -> "data.json"
    else -> "code.txt"
}

private fun safeFileName(title: String, extension: String): String {
    val base = title.trim().replace(Regex("[^A-Za-z0-9\\u4e00-\\u9fa5_-]+"), "-").trim('-').take(48).ifBlank { "result" }
    return "$base.$extension"
}

private fun imageDownloadName(image: MessageImageItem, mimeType: String): String {
    val extension = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/svg+xml" -> "svg"
        else -> "png"
    }
    return safeFileName(image.alt.ifBlank { "image" }, extension)
}

private fun inferImageMime(source: String): String = when {
    source.startsWith("data:image/jpeg", ignoreCase = true) -> "image/jpeg"
    source.startsWith("data:image/webp", ignoreCase = true) -> "image/webp"
    source.startsWith("data:image/gif", ignoreCase = true) -> "image/gif"
    source.startsWith("data:image/svg", ignoreCase = true) -> "image/svg+xml"
    source.substringBefore('?').endsWith(".jpg", true) || source.substringBefore('?').endsWith(".jpeg", true) -> "image/jpeg"
    source.substringBefore('?').endsWith(".webp", true) -> "image/webp"
    source.substringBefore('?').endsWith(".gif", true) -> "image/gif"
    else -> "image/png"
}

private fun formatRevisionTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
