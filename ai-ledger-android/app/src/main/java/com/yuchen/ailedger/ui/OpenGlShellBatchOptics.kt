package com.yuchen.ailedger.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.ui.gl.OpenGLGlassDynamicState

internal fun Modifier.openGlBatchStandaloneShellFrame(
    radius: Int,
    glassIntensity: Float,
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val safeIntensity = glassIntensity.coerceIn(0.25f, 1.45f)
    return shadow(
        elevation = 5.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(
            alpha = (0.028f * safeIntensity).coerceIn(0.004f, 0.080f),
        ),
        spotColor = Color.White.copy(
            alpha = (0.0035f * safeIntensity).coerceIn(0.001f, 0.014f),
        ),
    ).clip(shape)
}

private fun openGlBatchSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

internal fun Modifier.openGlBatchShellPressSurfaceOptics(
    dynamicState: OpenGLGlassDynamicState,
    radius: Int,
    prismEdgeHighlight: Float,
): Modifier = drawWithContent {
    drawContent()
    val dynamic = dynamicState.snapshotState.value
    val safePress = dynamic.surfaceOpticsPress.coerceIn(0f, 1.08f)
    if (safePress < 0.001f) return@drawWithContent

    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val raw = (safePress / 0.72f).coerceIn(0f, 1f)
    val p = openGlBatchSmoothStep(raw)
    val breath = openGlBatchSmoothStep((safePress / 0.50f).coerceIn(0f, 1f)) *
        (1f - 0.11f * openGlBatchSmoothStep(((safePress - 0.58f) / 0.28f).coerceIn(0f, 1f)))
    val compression = p * p
    val centerNorm = Offset(
        dynamic.pressCenter.x.coerceIn(0f, 1f),
        dynamic.pressCenter.y.coerceIn(0f, 1f),
    )
    val center = Offset(centerNorm.x * w, centerNorm.y * h)
    val rimInset = 0.56.dp.toPx()
    val rimRadius = (radius.dp.toPx() - rimInset).coerceAtLeast(0f)
    val cornerRadius = CornerRadius(rimRadius, rimRadius)
    val rimSize = Size(
        (w - rimInset * 2f).coerceAtLeast(1f),
        (h - rimInset * 2f).coerceAtLeast(1f),
    )
    val maxSide = maxOf(w, h)
    val pressGlow = p

    fun nearEdge(distance: Float): Float =
        (1f - distance / 0.42f).coerceIn(0f, 1f) * pressGlow

    val topNear = nearEdge(centerNorm.y)
    val bottomNear = nearEdge(1f - centerNorm.y)
    val leftNear = nearEdge(centerNorm.x)
    val rightNear = nearEdge(1f - centerNorm.x)
    val edgeStroke = (0.74.dp + (0.26f * p).dp).toPx()
    val localEdgeStroke = (1.18.dp + (0.48f * p).dp).toPx()
    val flow = openGlBatchSmoothStep((safePress / 0.62f).coerceIn(0f, 1f))
    val seedShift = (dynamic.rimFlowSeed - 0.5f) * 0.36f
    val sweepX = if (dynamic.rimFlowDirection >= 0f) {
        -0.24f + seedShift + flow * 1.42f
    } else {
        1.24f + seedShift - flow * 1.42f
    }
    val bandStartY = when (dynamic.rimFlowBand % 4) {
        0 -> 0.02f
        1 -> 0.74f
        2 -> 0.10f
        else -> 0.18f
    }
    val bandEndY = when (dynamic.rimFlowBand % 4) {
        0 -> 0.26f
        1 -> 0.98f
        2 -> 0.92f
        else -> 0.58f
    }
    val bandAlpha = breath * dynamic.rimFlowStrength.coerceIn(0.70f, 1.45f)
    val prism = prismEdgeHighlight.coerceIn(0f, 2f)
    val prismSoft = prism * 0.55f

    val pressureField = Brush.radialGradient(
        listOf(
            Color(0xFFEFFFFF).copy(alpha = 0.066f * breath),
            Color(0xFFB8F7FF).copy(alpha = 0.032f * breath),
            Color(0xFF82E8FF).copy(alpha = 0.010f * breath),
            Color.Transparent,
        ),
        center,
        maxSide * (0.86f + 0.06f * p),
    )
    val broadHalo = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = 0.021f * breath),
            Color(0xFFD8FFFF).copy(alpha = 0.014f * breath),
            Color.Transparent,
        ),
        Offset(w * 0.50f, h * 0.40f),
        maxSide * 1.18f,
    )
    val elasticSurfaceField = Brush.radialGradient(
        listOf(
            Color.Transparent,
            Color(0xFF102C66).copy(alpha = 0.006f * p),
            Color(0xFF030B1A).copy(alpha = 0.034f * compression),
        ),
        center,
        maxSide * (1.00f + 0.035f * p),
    )
    val lowerWeight = Brush.verticalGradient(
        listOf(
            Color.Transparent,
            Color.Transparent,
            Color(0xFF020815).copy(alpha = 0.044f * compression),
        ),
        h * 0.44f,
        h,
    )
    val ambientRim = Brush.radialGradient(
        listOf(
            Color(0xFFEFFFFF).copy(alpha = 0.052f * breath),
            Color(0xFF92FFF1).copy(alpha = (0.018f + 0.020f * prismSoft) * breath),
            Color(0xFFFF8BE8).copy(alpha = 0.014f * prismSoft * breath),
            Color.Transparent,
        ),
        center,
        maxSide * 0.74f,
    )
    val flowingRim = Brush.linearGradient(
        listOf(
            Color.Transparent,
            Color(0xFFFF6ADB).copy(alpha = 0.20f * prism * bandAlpha),
            Color.White.copy(alpha = 0.34f * bandAlpha),
            Color(0xFFFFE08A).copy(alpha = 0.18f * prism * bandAlpha),
            Color(0xFF62FFF0).copy(alpha = (0.14f + 0.16f * prism) * bandAlpha),
            Color(0xFF92A6FF).copy(alpha = 0.12f * prism * bandAlpha),
            Color.Transparent,
        ),
        Offset(w * (sweepX - 0.26f), h * bandStartY),
        Offset(w * (sweepX + 0.22f), h * bandEndY),
    )

    fun prismHalo(power: Float, white: Float, cyan: Float) = listOf(
        Color.White.copy(alpha = white * power),
        Color(0xFFFF7DE2).copy(alpha = 0.050f * prism * power),
        Color(0xFFFFE28A).copy(alpha = 0.036f * prism * power),
        Color(0xFF80FFF2).copy(alpha = cyan * power * (0.65f + prism * 0.35f)),
        Color.Transparent,
    )

    val topEdgeHalo = Brush.radialGradient(
        prismHalo(topNear, 0.23f, 0.072f),
        Offset(center.x, rimInset),
        maxSide * 0.38f,
    )
    val bottomEdgeHalo = Brush.radialGradient(
        prismHalo(bottomNear, 0.16f, 0.054f),
        Offset(center.x, h - rimInset),
        maxSide * 0.36f,
    )
    val leftEdgeHalo = Brush.radialGradient(
        prismHalo(leftNear, 0.18f, 0.060f),
        Offset(rimInset, center.y),
        maxSide * 0.34f,
    )
    val rightEdgeHalo = Brush.radialGradient(
        prismHalo(rightNear, 0.18f, 0.060f),
        Offset(w - rimInset, center.y),
        maxSide * 0.34f,
    )

    drawRect(broadHalo, blendMode = BlendMode.Screen)
    drawRect(pressureField, blendMode = BlendMode.Screen)
    drawRect(elasticSurfaceField, blendMode = BlendMode.Multiply)
    drawRect(lowerWeight, blendMode = BlendMode.Multiply)
    drawRoundRect(
        brush = ambientRim,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(edgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = flowingRim,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(0.82.dp.toPx() + 0.20.dp.toPx() * prism),
        blendMode = BlendMode.Plus,
    )
    drawRoundRect(
        brush = topEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = bottomEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = leftEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = rightEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
}
