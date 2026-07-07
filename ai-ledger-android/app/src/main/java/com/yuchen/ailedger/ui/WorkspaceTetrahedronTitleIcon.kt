package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Workspace 入口的正四面体动态图标。
 *
 * 直接迁移自 workspace-tetrahedron-lab-default-boost.html 的最终 Canvas 参数：
 * 3D 顶点、透视投影、棱线深度排序、三段高光流动、顶点光核与低成本粒子场。
 * 这是普通 Compose Canvas 子节点，不接入 OpenGL registry，也不触发 geometry sync。
 */
@Composable
internal fun WorkspaceTetrahedronTitleIcon(modifier: Modifier = Modifier) {
    var frameNanos by remember { mutableStateOf(0L) }
    val phaseOffset = remember { 0.137f }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                frameNanos = nanos
            }
        }
    }

    Canvas(
        modifier = modifier.size(width = 27.dp, height = 22.dp)
    ) {
        drawWorkspaceTetrahedronIcon(
            timeSeconds = frameNanos / 1_000_000_000f,
            phaseOffset = phaseOffset
        )
    }
}

private data class WorkspacePoint3(val x: Float, val y: Float, val z: Float)
private data class WorkspacePoint2(val x: Float, val y: Float, val z: Float)
private data class WorkspaceEdge(
    val index: Int,
    val a: WorkspacePoint2,
    val b: WorkspacePoint2,
    val midZ: Float,
    val len: Float,
    val depth: Float
)

private val workspaceVertices = listOf(
    WorkspacePoint3(1f, 1f, 1f),
    WorkspacePoint3(-1f, -1f, 1f),
    WorkspacePoint3(-1f, 1f, -1f),
    WorkspacePoint3(1f, -1f, -1f)
)

private val workspaceEdges = listOf(
    0 to 1,
    0 to 2,
    0 to 3,
    1 to 2,
    1 to 3,
    2 to 3
)

private val workspaceEdgePath = intArrayOf(0, 1, 3, 5, 4, 2)

private const val workspaceSize = 132f
private const val workspacePerspective = 25f
private const val workspaceDepthSquash = 95f
private const val workspaceWander = 86f
private const val workspaceBreath = 23f
private const val workspaceTiltX = -18f
private const val workspaceTiltY = 28f
private const val workspaceTiltZ = -12f
private const val workspaceEdgeWidth = 190f
private const val workspaceGlow = 220f
private const val workspaceDispersion = 0f
private const val workspaceBackFade = 58f
private const val workspaceVertexGlow = 57f
private const val workspaceSpeed = 73f
private const val workspaceTrail = 92f
private const val workspaceAfterglow = 160f
private const val workspaceParticles = 140f
private const val workspaceThemeAlpha = 0.82f
private const val workspaceThemeSpeed = 0.38f
private const val workspaceLightBoost = 1.34f

private val workspaceThemeA = floatArrayOf(102f, 255f, 240f)
private val workspaceThemeB = floatArrayOf(93f, 132f, 255f)
private val workspaceThemeC = floatArrayOf(164f, 105f, 255f)

private fun DrawScope.drawWorkspaceTetrahedronIcon(timeSeconds: Float, phaseOffset: Float) {
    val projected = projectWorkspacePoints(timeSeconds, phaseOffset)
    val sortedEdges = workspaceEdges.indices
        .map { workspaceEdgeInfo(projected, it) }
        .sortedBy { it.midZ }
    val pathEdges = workspaceEdgePath.map { workspaceEdgeInfo(projected, it) }

    val base = min(size.width, size.height) * 0.055f * (workspaceEdgeWidth / 100f)
    val glow = workspaceGlow / 100f
    val dispersion = workspaceDispersion / 100f
    val energy = workspaceThemeAlpha * workspaceLightBoost

    drawWorkspaceCenterAura(projected, base, energy)

    sortedEdges.forEach { edge ->
        val frontAlpha = 0.30f + 0.70f * edge.depth
        val backAlpha = 1f - (workspaceBackFade / 100f) * (1f - edge.depth)
        val alpha = energy * frontAlpha * backAlpha
        val width = base * (0.70f + 0.44f * edge.depth)
        drawWorkspaceEdge(
            edge = edge,
            strokeWidth = width * 2.65f,
            brushAlpha = 0.28f,
            alpha = alpha * 0.28f,
            blurWidth = base * 0.82f * glow
        )
        drawWorkspaceEdge(
            edge = edge,
            strokeWidth = width * 1.08f,
            brushAlpha = 0.88f,
            alpha = alpha * 0.74f,
            blurWidth = base * 0.08f * glow
        )
        drawLine(
            color = Color(236, 255, 255, ((0.62f + 0.18f * dispersion) * alpha * 0.38f).coerceIn(0f, 1f)),
            start = Offset(edge.a.x, edge.a.y),
            end = Offset(edge.b.x, edge.b.y),
            strokeWidth = width * 0.30f,
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus
        )
    }

    drawWorkspaceMovingHighlights(
        pathEdges = pathEdges,
        base = base,
        energy = energy,
        timeSeconds = timeSeconds,
        phaseOffset = phaseOffset
    )

    projected
        .sortedBy { it.z }
        .forEach { point ->
            drawWorkspaceVertex(
                point = point,
                base = base,
                alpha = energy,
                timeSeconds = timeSeconds,
                front = 0.48f + 0.52f * clamp01((point.z + 1.5f) / 3f)
            )
        }

    drawWorkspaceParticles(
        pathEdges = pathEdges,
        base = base,
        energy = energy,
        timeSeconds = timeSeconds
    )
}

private fun DrawScope.projectWorkspacePoints(timeSeconds: Float, phaseOffset: Float): List<WorkspacePoint2> {
    val angles = workspaceAngles(timeSeconds)
    val iconSize = min(size.width, size.height) * 0.235f * (workspaceSize / 100f) *
        (1f + (workspaceBreath / 100f) * 0.045f * sin(timeSeconds * 1.72f + phaseOffset * 9f))
    val perspective = min(size.width, size.height) * (1.25f + workspacePerspective / 100f * 1.2f)
    val squash = workspaceDepthSquash / 100f
    return workspaceVertices.map { vertex ->
        var p = WorkspacePoint3(vertex.x, vertex.y, vertex.z * squash)
        p = rotateWorkspaceX(p, angles.x)
        p = rotateWorkspaceY(p, angles.y)
        p = rotateWorkspaceZ(p, angles.z)
        val k = perspective / (perspective - p.z * iconSize * 0.72f)
        WorkspacePoint2(
            x = size.width * 0.5f + p.x * iconSize * k,
            y = size.height * 0.53f + p.y * iconSize * k,
            z = p.z
        )
    }
}

private fun workspaceAngles(timeSeconds: Float): WorkspacePoint3 {
    val wander = workspaceWander / 100f
    return WorkspacePoint3(
        x = workspaceTiltX.degToRad() + wander * (0.22f * sin(timeSeconds * 0.63f + 1.7f) + 0.09f * sin(timeSeconds * 0.21f + 4.1f)),
        y = workspaceTiltY.degToRad() + wander * (0.28f * sin(timeSeconds * 0.48f + 2.2f) + 0.11f * sin(timeSeconds * 0.17f + 1.3f)),
        z = workspaceTiltZ.degToRad() + wander * (0.18f * sin(timeSeconds * 0.55f + 5.4f) + 0.08f * sin(timeSeconds * 0.27f + 2.0f))
    )
}

private fun workspaceEdgeInfo(points: List<WorkspacePoint2>, index: Int): WorkspaceEdge {
    val (ia, ib) = workspaceEdges[index]
    val a = points[ia]
    val b = points[ib]
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val midZ = (a.z + b.z) * 0.5f
    return WorkspaceEdge(
        index = index,
        a = a,
        b = b,
        midZ = midZ,
        len = len,
        depth = clamp01((midZ + 1.55f) / 3.1f)
    )
}

private fun DrawScope.drawWorkspaceCenterAura(points: List<WorkspacePoint2>, base: Float, energy: Float) {
    val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
    val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
    val radius = base * 12.5f * 0.52f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                workspaceThemeColor(0.12f, 0.11f * energy),
                workspaceThemeColor(0.65f, 0.050f * energy),
                Color(90, 100, 255, 0)
            ),
            center = Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx, cy),
        blendMode = BlendMode.Plus
    )
}

private fun DrawScope.drawWorkspaceEdge(
    edge: WorkspaceEdge,
    strokeWidth: Float,
    brushAlpha: Float,
    alpha: Float,
    blurWidth: Float
) {
    if (alpha <= 0.002f) return
    val start = Offset(edge.a.x, edge.a.y)
    val end = Offset(edge.b.x, edge.b.y)
    val brush = Brush.linearGradient(
        colors = listOf(
            workspaceThemeColor(edge.index * 0.13f, brushAlpha),
            Color(239, 255, 255, brushAlpha.coerceIn(0f, 1f)),
            workspaceThemeColor(0.48f + edge.index * 0.11f, brushAlpha)
        ),
        start = start,
        end = end
    )
    if (blurWidth > 0.2f) {
        drawLine(
            brush = brush,
            start = start,
            end = end,
            strokeWidth = strokeWidth + blurWidth * 0.20f,
            cap = StrokeCap.Round,
            alpha = (alpha * 0.55f).coerceIn(0f, 1f),
            blendMode = BlendMode.Plus
        )
    }
    drawLine(
        brush = brush,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.Plus
    )
}

private fun DrawScope.drawWorkspaceMovingHighlights(
    pathEdges: List<WorkspaceEdge>,
    base: Float,
    energy: Float,
    timeSeconds: Float,
    phaseOffset: Float
) {
    val edgeCount = pathEdges.size
    val trail = (workspaceTrail / 100f).coerceAtLeast(0.025f)
    val phase = (phaseOffset + timeSeconds * 0.115f * workspaceThemeSpeed * (workspaceSpeed / 100f)).positiveModulo(1f)
    val cycle = phase * edgeCount
    val heads = floatArrayOf(
        cycle,
        (cycle + edgeCount / 3f).positiveModulo(edgeCount.toFloat()),
        (cycle + edgeCount * 2f / 3f).positiveModulo(edgeCount.toFloat())
    )
    val segmentLength = 0.18f + trail * 0.34f
    val baseIntensity = 0.58f

    heads.forEachIndexed { headIndex, head ->
        for (edgeSlot in 0 until edgeCount) {
            val overlap = edgeSegmentOverlap(edgeSlot, head, segmentLength, edgeCount) ?: continue
            val edge = pathEdges[edgeSlot]
            val headOnThisEdge = head >= edgeSlot && head < edgeSlot + 1f
            val localHead = if (headOnThisEdge) head - edgeSlot else overlap.end
            val headEase = clamp01((localHead - overlap.start) / (overlap.end - overlap.start).coerceAtLeast(0.0001f))
            val visibleFraction = clamp01((overlap.end - overlap.start) / min(1f, segmentLength).coerceAtLeast(0.001f))
            val segmentAlpha = smooth(clamp01(visibleFraction / 0.22f))
            val intensity = baseIntensity * (0.72f + 0.28f * headEase) * (0.85f + 0.35f * edge.depth) * segmentAlpha * 0.82f
            val edgePos = head - edgeSlot
            val headAlpha = if (headOnThisEdge) {
                smooth(clamp01(edgePos / 0.12f)) * smooth(clamp01((1f - edgePos) / 0.12f)) * segmentAlpha
            } else {
                0f
            }
            drawWorkspaceBeamAlongEdge(
                edge = edge,
                start = overlap.start,
                end = overlap.end,
                width = base * (1.05f + 0.25f * edge.depth),
                intensity = intensity * energy,
                headProgress = phase + edgeSlot * 0.13f + headIndex * 0.21f,
                drawHead = headOnThisEdge,
                headAlpha = headAlpha
            )
        }
    }

    for (edgeSlot in 0 until edgeCount) {
        val edge = pathEdges[edgeSlot]
        var tail = 0f
        heads.forEach { head ->
            val delta = ((head - edgeSlot) + edgeCount).positiveModulo(edgeCount.toFloat())
            if (delta >= 1f && delta < 1.9f) {
                tail = maxOf(tail, 0.22f * (workspaceAfterglow / 100f) * kotlin.math.exp(-(delta - 1f) * 1.2f))
            }
        }
        if (tail > 0.01f) {
            drawWorkspaceBeamAlongEdge(
                edge = edge,
                start = 0f,
                end = 1f,
                width = base * (1.05f + 0.25f * edge.depth),
                intensity = tail * energy,
                headProgress = phase + edgeSlot * 0.13f,
                drawHead = false,
                headAlpha = 0f
            )
        }
    }
}

private data class WorkspaceSegmentOverlap(val start: Float, val end: Float)

private fun edgeSegmentOverlap(index: Int, head: Float, length: Float, count: Int): WorkspaceSegmentOverlap? {
    var best: WorkspaceSegmentOverlap? = null
    val segStart = head - length
    val segEnd = head
    listOf(-count.toFloat(), 0f, count.toFloat()).forEach { shift ->
        val start = maxOf(index.toFloat(), segStart + shift)
        val end = minOf(index + 1f, segEnd + shift)
        if (end > start && (best == null || end - start > best!!.end - best!!.start)) {
            best = WorkspaceSegmentOverlap(start - index, end - index)
        }
    }
    return best
}

private fun DrawScope.drawWorkspaceBeamAlongEdge(
    edge: WorkspaceEdge,
    start: Float,
    end: Float,
    width: Float,
    intensity: Float,
    headProgress: Float,
    drawHead: Boolean,
    headAlpha: Float
) {
    if (end <= 0f || start >= 1f || intensity <= 0f) return
    val s = clamp01(start)
    val e = clamp01(end)
    if (e <= s) return
    val p0 = pointOnEdge(edge, s)
    val p1 = pointOnEdge(edge, e)
    val startOffset = Offset(p0.x, p0.y)
    val endOffset = Offset(p1.x, p1.y)
    val beamBrush = Brush.linearGradient(
        colors = listOf(
            workspaceThemeColor(headProgress, 0f),
            workspaceThemeColor(headProgress, 0.30f),
            Color(230, 255, 255, 0.78f),
            Color.White.copy(alpha = 0.96f)
        ),
        start = startOffset,
        end = endOffset
    )
    drawLine(
        brush = beamBrush,
        start = startOffset,
        end = endOffset,
        strokeWidth = width * 2.15f,
        cap = StrokeCap.Round,
        alpha = (0.30f * intensity).coerceIn(0f, 1f),
        blendMode = BlendMode.Plus
    )
    drawLine(
        brush = beamBrush,
        start = startOffset,
        end = endOffset,
        strokeWidth = width * 1.10f,
        cap = StrokeCap.Round,
        alpha = (0.78f * intensity).coerceIn(0f, 1f),
        blendMode = BlendMode.Plus
    )
    drawLine(
        color = Color.White.copy(alpha = (0.70f * intensity).coerceIn(0f, 1f)),
        start = startOffset,
        end = endOffset,
        strokeWidth = width * 0.24f,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Plus
    )
    if (!drawHead || headAlpha <= 0.002f) return
    val radius = width * 0.42f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                workspaceThemeColor(headProgress, 0.95f),
                Color(90, 150, 255, 0)
            ),
            center = endOffset,
            radius = radius * 4.2f
        ),
        radius = radius * 4.2f,
        center = endOffset,
        alpha = headAlpha.coerceIn(0f, 1f),
        blendMode = BlendMode.Plus
    )
}

private fun DrawScope.drawWorkspaceVertex(
    point: WorkspacePoint2,
    base: Float,
    alpha: Float,
    timeSeconds: Float,
    front: Float
) {
    val strength = workspaceVertexGlow / 100f * alpha * front * workspaceLightBoost
    if (strength <= 0.01f) return
    val radius = base * (0.45f + 0.65f * strength)
    val twinkle = 1f + 0.10f * sin(timeSeconds * 7.1f + point.x * 0.01f) + 0.06f * sin(timeSeconds * 13.4f + point.y * 0.01f)
    val center = Offset(point.x, point.y)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.85f * strength * twinkle).coerceIn(0f, 1f)),
                workspaceThemeColor(0.10f, 0.52f * strength),
                workspaceThemeColor(0.72f, 0.16f * strength),
                Color(100, 130, 255, 0)
            ),
            center = center,
            radius = radius * 3.2f
        ),
        radius = radius * 3.2f,
        center = center,
        blendMode = BlendMode.Plus
    )
}

private fun DrawScope.drawWorkspaceParticles(
    pathEdges: List<WorkspaceEdge>,
    base: Float,
    energy: Float,
    timeSeconds: Float
) {
    val amount = workspaceParticles / 100f * energy
    if (amount <= 0.02f) return
    repeat(18) { index ->
        val seed = index * 19.17f + 4.3f
        val edge = pathEdges[workspaceEdgePath[index % 6]]
        val u = (seed * 0.017f + timeSeconds * (0.045f + 0.008f * (seed % 5f))).positiveModulo(1f)
        val point = pointOnEdge(edge, u)
        val front = 0.35f + 0.65f * edge.depth
        val alpha = (amount * front * (0.30f + 0.30f * sin(timeSeconds * 2.3f + seed))).coerceIn(0f, 0.55f)
        if (alpha <= 0.02f) return@repeat
        val radius = base * (0.045f + (index % 7) * 0.006f) * 3.6f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = alpha),
                    workspaceThemeColor(seed * 0.03f, alpha * 0.55f),
                    Color(120, 150, 255, 0)
                ),
                center = Offset(point.x, point.y),
                radius = radius
            ),
            radius = radius,
            center = Offset(point.x, point.y),
            blendMode = BlendMode.Plus
        )
    }
}

private fun pointOnEdge(edge: WorkspaceEdge, t: Float): WorkspacePoint2 {
    return WorkspacePoint2(
        x = lerp(edge.a.x, edge.b.x, t),
        y = lerp(edge.a.y, edge.b.y, t),
        z = lerp(edge.a.z, edge.b.z, t)
    )
}

private fun rotateWorkspaceX(point: WorkspacePoint3, angle: Float): WorkspacePoint3 {
    val c = cos(angle)
    val s = sin(angle)
    return WorkspacePoint3(point.x, point.y * c - point.z * s, point.y * s + point.z * c)
}

private fun rotateWorkspaceY(point: WorkspacePoint3, angle: Float): WorkspacePoint3 {
    val c = cos(angle)
    val s = sin(angle)
    return WorkspacePoint3(point.x * c + point.z * s, point.y, -point.x * s + point.z * c)
}

private fun rotateWorkspaceZ(point: WorkspacePoint3, angle: Float): WorkspacePoint3 {
    val c = cos(angle)
    val s = sin(angle)
    return WorkspacePoint3(point.x * c - point.y * s, point.x * s + point.y * c, point.z)
}

private fun workspaceThemeColor(progress: Float, alpha: Float): Color {
    val t = progress.positiveModulo(1f)
    return if (t < 0.46f) {
        rgbMix(workspaceThemeA, workspaceThemeB, t / 0.46f, alpha)
    } else {
        rgbMix(workspaceThemeB, workspaceThemeC, (t - 0.46f) / 0.54f, alpha)
    }
}

private fun rgbMix(a: FloatArray, b: FloatArray, t: Float, alpha: Float): Color {
    return Color(
        red = (lerp(a[0], b[0], t) / 255f).coerceIn(0f, 1f),
        green = (lerp(a[1], b[1], t) / 255f).coerceIn(0f, 1f),
        blue = (lerp(a[2], b[2], t) / 255f).coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f)
    )
}

private fun Float.degToRad(): Float = (this * PI / 180.0).toFloat()
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)
private fun smooth(value: Float): Float {
    val v = clamp01(value)
    return v * v * (3f - 2f * v)
}

private fun Float.positiveModulo(divisor: Float): Float {
    val raw = this % divisor
    return if (raw < 0f) raw + divisor else raw
}
