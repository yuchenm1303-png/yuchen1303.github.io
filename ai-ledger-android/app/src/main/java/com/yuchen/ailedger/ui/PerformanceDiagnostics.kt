package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalPerformanceDiagnostics = compositionLocalOf { PerformanceDiagnosticsState() }

data class PerformanceDiagnosticsState(
    val disablePagePrewarm: Boolean = false,
    val disableContinuousAnimations: Boolean = false,
    val disableOpenGlGlass: Boolean = false,
    val extremeLiteMode: Boolean = false
) {
    val pagePrewarmOff: Boolean get() = disablePagePrewarm || extremeLiteMode
    val continuousAnimationsOff: Boolean get() = disableContinuousAnimations || extremeLiteMode
    val openGlGlassOff: Boolean get() = disableOpenGlGlass || extremeLiteMode
}

@Composable
fun PerformanceDiagnosticsPanel(
    state: PerformanceDiagnosticsState,
    onStateChange: (PerformanceDiagnosticsState) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val frames = StartupMetrics.frameStats
    val warmup = StartupMetrics.warmupState
    val panelModifier = modifier
        .clip(RoundedCornerShape(18.dp))
        .background(Color(0xCC101A35))
        .clickable { expanded = !expanded }
        .padding(horizontal = 10.dp, vertical = 8.dp)

    if (!expanded) {
        Row(
            modifier = panelModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("诊断", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(frames.shortFpsLabel(), color = Color(0xFF8DF9EA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    Column(
        modifier = panelModifier.fillMaxWidth(0.72f),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("首进程性能诊断", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text(frames.shortFpsLabel(), color = Color(0xFF8DF9EA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text("页面：$warmup", color = Color.White.copy(alpha = 0.58f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        DiagnosticSwitchRow(
            title = "禁用页面预热",
            checked = state.disablePagePrewarm,
            enabled = !state.extremeLiteMode,
            onToggle = { onStateChange(state.copy(disablePagePrewarm = !state.disablePagePrewarm)) }
        )
        DiagnosticSwitchRow(
            title = "禁用持续动画",
            checked = state.disableContinuousAnimations,
            enabled = !state.extremeLiteMode,
            onToggle = { onStateChange(state.copy(disableContinuousAnimations = !state.disableContinuousAnimations)) }
        )
        DiagnosticSwitchRow(
            title = "禁用 OpenGL 玻璃",
            checked = state.disableOpenGlGlass,
            enabled = !state.extremeLiteMode,
            onToggle = { onStateChange(state.copy(disableOpenGlGlass = !state.disableOpenGlGlass)) }
        )
        DiagnosticSwitchRow(
            title = "极简模式",
            checked = state.extremeLiteMode,
            enabled = true,
            onToggle = { onStateChange(state.copy(extremeLiteMode = !state.extremeLiteMode)) }
        )
        Text("测试方法：首装第一次打开后，逐个开关打开，看 FPS 是否立刻接近第二次打开的 90。", color = Color.White.copy(alpha = 0.45f), fontSize = 8.sp, lineHeight = 11.sp)
        Text("点面板空白处收起", color = Color.White.copy(alpha = 0.35f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DiagnosticSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val active = checked || !enabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (checked) 0.13f else 0.055f))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White.copy(alpha = if (enabled) 0.86f else 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (active) Color(0xFF8DF9EA).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(if (checked) "开" else "关", color = Color(0xFF07132D), fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

private fun StartupFrameStats.shortFpsLabel(): String {
    val fps = (currentFps * 10).toInt() / 10f
    val max = (maxFrameMs * 10).toInt() / 10f
    return "FPS $fps · max $max"
}
