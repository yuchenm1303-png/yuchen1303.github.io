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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

@Composable
internal fun VisualIntelligenceDiagnosticsSettingsContent(state: AssistantUiState) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) {
        VisualIntelligenceDiagnosticsStore.get(context.applicationContext)
    }
    val diagnostics by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedTaskId by remember { mutableLongStateOf(0L) }
    var detailText by remember { mutableStateOf("") }
    var loadingDetail by remember { mutableStateOf(false) }

    LaunchedEffect(diagnostics.sessions) {
        if (selectedTaskId == 0L || diagnostics.sessions.none { it.taskId == selectedTaskId }) {
            selectedTaskId = diagnostics.sessions.firstOrNull()?.taskId ?: 0L
        }
    }
    LaunchedEffect(selectedTaskId, diagnostics.sessions) {
        if (selectedTaskId <= 0L) {
            detailText = ""
            return@LaunchedEffect
        }
        loadingDetail = true
        detailText = store.readSessionText(selectedTaskId)
        loadingDetail = false
    }

    Text(
        "智力诊断 · 阶段一",
        color = Color.White.copy(alpha = 0.88f),
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 2.dp),
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "采集真实决策链",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "旁路保存既有截图、任务状态、模型上下文与执行记录，不额外截图，也不改变模型决策。",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Switch(
            checked = diagnostics.enabled,
            onCheckedChange = store::setEnabled,
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        DiagnosticMetric("任务", diagnostics.sessions.size.toString(), Modifier.weight(1f))
        DiagnosticMetric(
            "观察",
            diagnostics.sessions.sumOf { it.observationCount }.toString(),
            Modifier.weight(1f),
        )
        DiagnosticMetric(
            "截图",
            diagnostics.sessions.sumOf { it.frameCount }.toString(),
            Modifier.weight(1f),
        )
    }

    if (diagnostics.sessions.isEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.045f))
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "还没有诊断记录",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "开启视觉智能并执行一次任务后，这里会按任务显示逐轮数据。",
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        Text(
            "最近任务",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Black,
        )
        diagnostics.sessions.take(6).forEach { session ->
            DiagnosticSessionRow(
                session = session,
                selected = session.taskId == selectedTaskId,
                onClick = { selectedTaskId = session.taskId },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
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
            detailText = ""
            Toast.makeText(context, "诊断记录已清空", Toast.LENGTH_SHORT).show()
        }

        Text(
            "诊断预览",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            when {
                loadingDetail -> "正在读取诊断记录…"
                detailText.isBlank() -> "暂无可显示内容"
                else -> detailText.takeLast(6_000)
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
                .padding(horizontal = 12.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun DiagnosticMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.050f))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White.copy(alpha = 0.88f), fontSize = 17.sp, fontWeight = FontWeight.Black)
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
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (selected) 0.095f else 0.048f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                session.goal.ifBlank { "手机智能体任务" },
                color = Color.White.copy(alpha = if (selected) 0.94f else 0.78f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(time, color = Color.White.copy(alpha = 0.38f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
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
        radius = 20,
        modifier = modifier.height(58.dp),
        role = GlassRole.Chip,
        onClick = { if (enabled) onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Column(verticalArrangement = Arrangement.SpaceBetween) {
                Text(
                    title,
                    color = Color.White.copy(alpha = if (enabled) 0.90f else 0.38f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = if (enabled) 0.46f else 0.24f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
