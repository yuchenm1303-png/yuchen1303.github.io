package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.service.AgentWorkspaceModeController
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun WorkspaceModeTitleSwitch(modifier: Modifier = Modifier) {
    val enabled by AgentWorkspaceModeController.enabled.collectAsState()
    var frameNanos by remember { mutableStateOf(0L) }
    val activeLevel by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "workspace-mode-title-switch-active-level"
    )
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { frameNanos = it }
    }
    Canvas(
        modifier = modifier
            .size(width = 32.dp, height = 26.dp)
            .clickable { AgentWorkspaceModeController.toggle() }
    ) {
        drawWorkspaceModeSwitchGlyph(
            time = frameNanos / 1_000_000_000f,
            activeLevel = activeLevel
        )
    }
}

private fun DrawScope.drawWorkspaceModeSwitchGlyph(time: Float, activeLevel: Float) {
    val activation = activeLevel.coerceIn(0f, 1f)
    val center = Offset(size.width * 0.5f, size.height * 0.52f)
    val breath = 0.86f + 0.14f * sin(time * 2.0f).coerceIn(-1f, 1f)
    val cubeSize = size.minDimension * (0.35f + 0.04f * activation) * breath
    val shellRadius = size.minDimension * (0.42f + 0.12f * activation)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF8DF9EA).copy(alpha = 0.08f + activation * 0.14f),
                Color(0xFF6C7CFF).copy(alpha = 0.03f + activation * 0.07f),
                Color.Transparent
            ),
            center = center,
            radius = shellRadius
        ),
        radius = shellRadius,
        center = center,
        blendMode = BlendMode.Plus
    )

    val halfW = cubeSize * 0.52f
    val halfH = cubeSize * 0.36f
    val top = center.y - cubeSize * 0.58f
    val mid = center.y - cubeSize * 0.13f
    val bottom = center.y + cubeSize * 0.55f
    val left = center.x - halfW
    val right = center.x + halfW
    val cx = center.x

    val topFace = Path().apply {
        moveTo(cx, top)
        lineTo(right, top + halfH)
        lineTo(cx, mid + halfH)
        lineTo(left, top + halfH)
        close()
    }
    val leftFace = Path().apply {
        moveTo(left, top + halfH)
        lineTo(cx, mid + halfH)
        lineTo(cx, bottom)
        lineTo(left, bottom - halfH)
        close()
    }
    val rightFace = Path().apply {
        moveTo(right, top + halfH)
        lineTo(cx, mid + halfH)
        lineTo(cx, bottom)
        lineTo(right, bottom - halfH)
        close()
    }

    val inactiveAlpha = 0.40f + activation * 0.54f
    drawPath(topFace, Color(0xFFFFD36B).copy(alpha = inactiveAlpha))
    drawPath(leftFace, Color(0xFFFF92C7).copy(alpha = inactiveAlpha * 0.90f))
    drawPath(rightFace, Color(0xFF8EE4FF).copy(alpha = inactiveAlpha * 0.92f))

    if (activation > 0.01f) {
        val sweep = ((time * 0.42f) % 1f)
        val start = Offset(center.x - cubeSize * 1.4f + sweep * cubeSize * 2.1f, top)
        val end = Offset(start.x + cubeSize * 0.65f, bottom)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.36f * activation),
                    Color.Transparent
                ),
                start = start,
                end = end
            ),
            start = start,
            end = end,
            strokeWidth = cubeSize * 0.12f,
            alpha = activation,
            blendMode = BlendMode.Plus
        )
    }
}
