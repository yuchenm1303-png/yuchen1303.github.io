package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal data class SettingsOpenGLDiagnosticsSession(
    val startupMetricsWasEnabled: Boolean,
    val baseline: PerformanceRuntimeSnapshot,
)

/**
 * 必须在设置页内容 Composition 之前调用，使随后创建的 TextureView/EGL 事件进入计数器。
 */
internal fun beginSettingsOpenGLDiagnostics(): SettingsOpenGLDiagnosticsSession {
    val wasEnabled = StartupMetrics.isEnabled
    StartupMetrics.configure(true)
    return SettingsOpenGLDiagnosticsSession(
        startupMetricsWasEnabled = wasEnabled,
        baseline = PerformanceRuntimeMetrics.snapshot(),
    )
}

internal fun endSettingsOpenGLDiagnostics(session: SettingsOpenGLDiagnosticsSession) {
    StartupMetrics.configure(session.startupMetricsWasEnabled)
}

@Composable
internal fun SettingsOpenGLDiagnosticsPanel(
    session: SettingsOpenGLDiagnosticsSession,
    modifier: Modifier = Modifier,
) {
    var snapshot by remember { mutableStateOf(PerformanceRuntimeMetrics.snapshot()) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(session) {
        while (true) {
            snapshot = PerformanceRuntimeMetrics.snapshot()
            delay(250L)
        }
    }

    val base = session.baseline
    val requestDelta = (snapshot.openGlRenderRequests - base.openGlRenderRequests).coerceAtLeast(0L)
    val frameDelta = (snapshot.openGlFrames - base.openGlFrames).coerceAtLeast(0L)
    val uploadDelta = (snapshot.openGlTextureUploads - base.openGlTextureUploads).coerceAtLeast(0L)
    val uploadBytesDelta = (snapshot.openGlTextureUploadBytes - base.openGlTextureUploadBytes).coerceAtLeast(0L)
    val contextCreatedDelta = (snapshot.openGlContextsCreated - base.openGlContextsCreated).coerceAtLeast(0L)
    val cacheFrameDelta = (snapshot.legacyGeometryCacheFrames - base.legacyGeometryCacheFrames).coerceAtLeast(0L)
    val cacheRebuildDelta = (snapshot.legacyGeometryCacheRebuilds - base.legacyGeometryCacheRebuilds).coerceAtLeast(0L)
    val cacheFallbackDelta = (snapshot.legacyGeometryCacheFallbacks - base.legacyGeometryCacheFallbacks).coerceAtLeast(0L)
    val fullClearDelta = (snapshot.openGlFullClearPixels - base.openGlFullClearPixels).coerceAtLeast(0L)

    val stage = when {
        snapshot.openGlSurfacePixels <= 1L ->
            "A：Host 尚未提交有效 Surface 尺寸"
        uploadDelta <= 0L ->
            "B：Host 已布局，但设置页纹理尚未送入"
        contextCreatedDelta <= 0L ->
            "C：纹理已送入，但本次未创建 EGL Context/WindowSurface"
        frameDelta <= 0L ->
            "D：EGL 已创建，但没有成功 swap 的帧"
        else ->
            "E：已有成功 swap；若仍不可见，锁定 Android 合成/层级"
    }

    val diagnosticText = buildString {
        appendLine("OPENGL DIAG · 设置页")
        appendLine(stage)
        appendLine("Surface 当前/峰值：${formatKpx(snapshot.openGlSurfacePixels)} / ${formatKpx(snapshot.openGlPeakSurfacePixels)}")
        appendLine("Context 存活/峰值/本次创建：${snapshot.openGlContextsAlive} / ${snapshot.openGlPeakContextsAlive} / +$contextCreatedDelta")
        appendLine("纹理上传：+$uploadDelta · ${formatMiB(uploadBytesDelta)} MiB")
        appendLine("渲染请求/成功帧：+$requestDelta / +$frameDelta")
        appendLine("legacy 缓存 帧/重建/回退：+$cacheFrameDelta / +$cacheRebuildDelta / +$cacheFallbackDelta")
        appendLine("Surface 缓存：${formatKpx(snapshot.legacyGeometryCachePixels)} · 全清：${formatMpx(fullClearDelta)} Mpx")
        append("进入设置页后等待 2 秒再截图；点击本面板复制全部信息")
    }

    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .background(Color(0xF21B0710), shape)
            .border(1.dp, Color(0xFFFF4D79), shape)
            .clickable {
                clipboard.setText(AnnotatedString(diagnosticText))
                copied = true
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = if (copied) "OPENGL 诊断已复制" else "OPENGL 诊断 · 点击复制",
                color = Color(0xFFFF7998),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = diagnosticText,
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

private fun formatKpx(pixels: Long): String =
    ((pixels.coerceAtLeast(0L) / 100L).toFloat() / 10f).toString() + " Kpx"

private fun formatMpx(pixels: Long): String =
    ((pixels.coerceAtLeast(0L) / 100_000L).toFloat() / 10f).toString()

private fun formatMiB(bytes: Long): String =
    ((bytes.coerceAtLeast(0L) / 104_857.6f).toInt() / 10f).toString()
