package com.yuchen.ailedger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.core.content.FileProvider
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.VisualDiagnosticSessionSummary
import com.yuchen.ailedger.service.VisualIntelligenceDiagnosticsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val DiagnosticPreviewMaxChars = 6_000
private val DiagnosticAccent = Color(0xFF8DF9EA)

@Composable
internal fun VisualIntelligenceDiagnosticsSettingsContent(state: AssistantUiState) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) {
        VisualIntelligenceDiagnosticsStore.get(context.applicationContext)
    }
    val diagnostics by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedTaskId by remember { mutableLongStateOf(0L) }
    var loadedTaskId by remember { mutableLongStateOf(0L) }
    var detailText by remember { mutableStateOf("") }
    var loadingDetail by remember { mutableStateOf(false) }

    LaunchedEffect(diagnostics.sessions) {
        if (selectedTaskId == 0L || diagnostics.sessions.none { it.taskId == selectedTaskId }) {
            val nextTaskId = diagnostics.sessions.firstOrNull()?.taskId ?: 0L
            if (nextTaskId != selectedTaskId) {
                selectedTaskId = nextTaskId
                loadedTaskId = 0L
                detailText = ""
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DiagnosticSectionHeader()
            DiagnosticOverviewCard(
                enabled = diagnostics.enabled,
                taskCount = diagnostics.sessions.size,
                observationCount = diagnostics.sessions.sumOf { it.observationCount },
                frameCount = diagnostics.sessions.sumOf { it.frameCount },
                onEnabledChange = store::setEnabled,
            )
        }

        if (diagnostics.sessions.isEmpty()) {
            DiagnosticEmptyState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DiagnosticSectionTitle("最近任务")
                diagnostics.sessions.take(6).forEach { session ->
                    DiagnosticSessionRow(
                        session = session,
                        selected = session.taskId == selectedTaskId,
                        onClick = {
                            if (selectedTaskId != session.taskId) {
                                selectedTaskId = session.taskId
                                loadedTaskId = 0L
                                detailText = ""
                            }
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DiagnosticSectionTitle("诊断操作")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DiagnosticActionButton(
                        title = "复制诊断文本",
                        subtitle = "已脱敏",
                        state = state,
                        modifier = Modifier.weight(1f),
                        enabled = selectedTaskId > 0L,
                    ) {
                        scope.launch {
                            val text = store.readSessionText(selectedTaskId)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("视觉智能诊断", text))
                            Toast.makeText(context, "诊断文本已复制", Toast.LENGTH_SHORT).show()
                        }
                    }
                    DiagnosticActionButton(
                        title = "导出诊断包",
                        subtitle = "ZIP · 可保存到下载",
                        state = state,
                        modifier = Modifier.weight(1f),
                        enabled = selectedTaskId > 0L,
                    ) {
                        scope.launch {
                            val file = store.exportSession(selectedTaskId)
                            if (file == null) {
                                Toast.makeText(context, "诊断包生成失败", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "导出视觉智能诊断包"))
                        }
                    }
                }

                DiagnosticActionButton(
                    title = "清空诊断记录",
                    subtitle = "删除本机诊断文本与截图",
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = diagnostics.sessions.isNotEmpty(),
                ) {
                    store.clearAll()
                    selectedTaskId = 0L
                    loadedTaskId = 0L
                    detailText = ""
                    Toast.makeText(context, "诊断记录已清空", Toast.LENGTH_SHORT).show()
                }

                DiagnosticActionButton(
                    title = when {
                        loadingDetail -> "正在加载诊断预览"
                        loadedTaskId == selectedTaskId && detailText.isNotBlank() -> "刷新诊断预览"
                        else -> "加载诊断预览"
                    },
                    subtitle = "仅在需要时读取，页面打开不再自动构建报告",
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedTaskId > 0L && !loadingDetail,
                ) {
                    scope.launch {
                        loadingDetail = true
                        val taskId = selectedTaskId
                        runCatching { store.readSessionText(taskId) }
                            .onSuccess { text ->
                                if (selectedTaskId == taskId) {
                                    detailText = text.takeLast(DiagnosticPreviewMaxChars)
                                    loadedTaskId = taskId
                                }
                            }
                            .onFailure {
                                if (selectedTaskId == taskId) {
                                    detailText = "诊断预览读取失败"
                                    loadedTaskId = taskId
                                }
                            }
                        loadingDetail = false
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DiagnosticSectionTitle("诊断预览")
                Text(
                    when {
                        loadingDetail -> "正在读取诊断记录…"
                        loadedTaskId != selectedTaskId -> "默认不读取完整诊断报告，点击“加载诊断预览”后显示。"
                        detailText.isBlank() -> "暂无可显示内容"
                        else -> detailText
                    },
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 30,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.16f))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "智力诊断",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(DiagnosticAccent.copy(alpha = 0.10f))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "阶段一",
                color = DiagnosticAccent.copy(alpha = 0.80f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun DiagnosticSectionTitle(title: String) {
    Text(
        title,
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@Composable
private fun DiagnosticOverviewCard(
    enabled: Boolean,
    taskCount: Int,
    observationCount: Int,
    frameCount: Int,
    onEnabledChange: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.058f))
            .padding(horizontal = 15.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "采集真实决策链",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (enabled) "诊断采集已开启" else "诊断采集已暂停",
                    color = if (enabled) {
                        DiagnosticAccent.copy(alpha = 0.74f)
                    } else {
                        Color.White.copy(alpha = 0.38f)
                    },
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }

        Text(
            "旁路保存既有截图、任务状态、模型上下文和执行记录；不额外截图，也不干预模型决策。",
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.075f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiagnosticMetric("任务", taskCount.toString(), Modifier.weight(1f))
            DiagnosticMetricDivider()
            DiagnosticMetric("观察", observationCount.toString(), Modifier.weight(1f))
            DiagnosticMetricDivider()
            DiagnosticMetric("截图", frameCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun DiagnosticMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            value,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 18.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun DiagnosticMetricDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(36.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
private fun DiagnosticEmptyState() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.042f))
            .padding(horizontal = 15.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DiagnosticAccent.copy(alpha = 0.075f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "诊",
                color = DiagnosticAccent.copy(alpha = 0.72f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "暂无诊断记录",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                "执行一次视觉智能任务后，这里会按任务展示逐轮数据。",
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DiagnosticSessionRow(
    session: VisualDiagnosticSessionSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val time = remember(session.updatedAt) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(session.updatedAt))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (selected) 0.095f else 0.048f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                session.goal.ifBlank { "手机智能体任务" },
                color = Color.White.copy(alpha = if (selected) 0.94f else 0.78f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                time,
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Text(
            "${session.status} · ${session.eventCount} 条事件 · ${session.frameCount} 张图 · ID ${session.taskId}",
            color = Color.White.copy(alpha = 0.44f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val detail = session.latestResult.ifBlank { session.latestAction }
        if (detail.isNotBlank()) {
            Text(
                detail,
                color = Color.White.copy(alpha = 0.54f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DiagnosticActionButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (enabled) 0.90f else 0.62f,
        motionIntensity = state.motionIntensity,
        radius = 22,
        modifier = modifier.height(64.dp),
        role = GlassRole.Chip,
        onClick = { if (enabled) onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    title,
                    color = Color.White.copy(alpha = if (enabled) 0.90f else 0.38f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = if (enabled) 0.46f else 0.24f),
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
