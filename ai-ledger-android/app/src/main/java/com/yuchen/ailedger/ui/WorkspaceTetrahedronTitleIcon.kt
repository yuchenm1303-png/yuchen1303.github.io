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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun WorkspaceTetrahedronTitleIcon(modifier: Modifier = Modifier) {
    var frameNanos by remember { mutableStateOf(0L) }
    val phaseOffset = remember { 0.137f }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { frameNanos = it }
    }
    Canvas(modifier = modifier.size(width = 27.dp, height = 22.dp)) {
        drawWorkspaceTetrahedron(frameNanos / 1_000_000_000f, phaseOffset)
    }
}

private data class V3(val x: Float, val y: Float, val z: Float)
private data class P2(val x: Float, val y: Float, val z: Float)
private data class Edge(val index: Int, val a: P2, val b: P2, val depth: Float, val midZ: Float)
private data class Overlap(val start: Float, val end: Float)

private val tetraVertices = listOf(V3(1f, 1f, 1f), V3(-1f, -1f, 1f), V3(-1f, 1f, -1f), V3(1f, -1f, -1f))
private val tetraEdges = listOf(0 to 1, 0 to 2, 0 to 3, 1 to 2, 1 to 3, 2 to 3)
private val tetraPath = intArrayOf(0, 1, 3, 5, 4, 2)
private val themeA = floatArrayOf(102f, 255f, 240f)
private val themeB = floatArrayOf(93f, 132f, 255f)
private val themeC = floatArrayOf(164f, 105f, 255f)

private fun DrawScope.drawWorkspaceTetrahedron(time: Float, phaseOffset: Float) {
    val points = projectTetra(time, phaseOffset)
    val edges = tetraEdges.indices.map { edgeOf(points, it) }
    val sorted = edges.sortedBy { it.midZ }
    val path = tetraPath.map { edgeOf(points, it) }
    val base = min(size.width, size.height) * 0.055f * 1.05f
    val energy = 0.82f * 1.34f
    val center = Offset(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
    drawCircle(Brush.radialGradient(listOf(themeColor(0.12f, 0.11f * energy), themeColor(0.65f, 0.05f * energy), rgba(90, 100, 255, 0f)), center, base * 6.5f), base * 6.5f, center, blendMode = BlendMode.Plus)
    sorted.forEach { edge ->
        val alpha = energy * (0.30f + 0.70f * edge.depth) * (1f - 0.58f * (1f - edge.depth))
        val width = base * (0.70f + 0.44f * edge.depth)
        drawEdge(edge, width, alpha, base)
    }
    drawHighlights(path, base, energy, time, phaseOffset)
    points.sortedBy { it.z }.forEach { point -> drawVertex(point, base, energy, time, 0.48f + 0.52f * clamp01((point.z + 1.5f) / 3f)) }
}

private fun DrawScope.projectTetra(time: Float, phaseOffset: Float): List<P2> {
    val wander = 0.86f
    val angles = V3(
        (-18f).rad() + wander * (0.22f * sin(time * 0.63f + 1.7f) + 0.09f * sin(time * 0.21f + 4.1f)),
        28f.rad() + wander * (0.28f * sin(time * 0.48f + 2.2f) + 0.11f * sin(time * 0.17f + 1.3f)),
        (-12f).rad() + wander * (0.18f * sin(time * 0.55f + 5.4f) + 0.08f * sin(time * 0.27f + 2.0f))
    )
    val iconSize = min(size.width, size.height) * 0.235f * 1.32f * (1f + 0.23f * 0.045f * sin(time * 1.72f + phaseOffset * 9f))
    val perspective = min(size.width, size.height) * (1.25f + 0.25f * 1.2f)
    return tetraVertices.map { vertex ->
        var point = V3(vertex.x, vertex.y, vertex.z * 0.95f)
        point = rotateX(point, angles.x)
        point = rotateY(point, angles.y)
        point = rotateZ(point, angles.z)
        val k = perspective / (perspective - point.z * iconSize * 0.72f)
        P2(size.width * 0.5f + point.x * iconSize * k, size.height * 0.53f + point.y * iconSize * k, point.z)
    }
}

private fun edgeOf(points: List<P2>, index: Int): Edge {
    val (ia, ib) = tetraEdges[index]
    val a = points[ia]
    val b = points[ib]
    val midZ = (a.z + b.z) * 0.5f
    return Edge(index, a, b, clamp01((midZ + 1.55f) / 3.1f), midZ)
}

private fun DrawScope.drawEdge(edge: Edge, width: Float, alpha: Float, base: Float) {
    val start = Offset(edge.a.x, edge.a.y)
    val end = Offset(edge.b.x, edge.b.y)
    val brush = Brush.linearGradient(listOf(themeColor(edge.index * 0.13f, 0.42f), rgba(239, 255, 255, 0.88f), themeColor(0.48f + edge.index * 0.11f, 0.52f)), start, end)
    drawLine(brush, start, end, width * 2.65f + base * 0.35f, StrokeCap.Round, alpha = (alpha * 0.24f).coerceIn(0f, 1f), blendMode = BlendMode.Plus)
    drawLine(brush, start, end, width * 1.08f, StrokeCap.Round, alpha = (alpha * 0.74f).coerceIn(0f, 1f), blendMode = BlendMode.Plus)
    drawLine(rgba(236, 255, 255, alpha * 0.38f), start, end, width * 0.30f, StrokeCap.Round, blendMode = BlendMode.Plus)
}

private fun DrawScope.drawHighlights(path: List<Edge>, base: Float, energy: Float, time: Float, phaseOffset: Float) {
    val phase = (phaseOffset + time * 0.115f * 0.38f * 0.73f).positiveModulo(1f)
    val count = path.size
    val heads = floatArrayOf(phase * count, (phase * count + count / 3f).positiveModulo(count.toFloat()), (phase * count + count * 2f / 3f).positiveModulo(count.toFloat()))
    val length = 0.18f + 0.92f * 0.34f
    heads.forEachIndexed { headIndex, head ->
        for (slot in 0 until count) {
            val overlap = overlap(slot, head, length, count) ?: continue
            val edge = path[slot]
            val headOnEdge = head >= slot && head < slot + 1f
            val edgePos = head - slot
            val headAlpha = if (headOnEdge) smooth(clamp01(edgePos / 0.12f)) * smooth(clamp01((1f - edgePos) / 0.12f)) else 0f
            val visible = smooth(clamp01(((overlap.end - overlap.start) / length.coerceAtLeast(0.001f)) / 0.22f))
            val intensity = 0.58f * (0.85f + 0.35f * edge.depth) * visible * 0.82f * energy
            drawBeam(edge, overlap.start, overlap.end, base * (1.05f + 0.25f * edge.depth), intensity, phase + slot * 0.13f + headIndex * 0.21f, headOnEdge, headAlpha)
        }
    }
    for (slot in 0 until count) {
        val edge = path[slot]
        var tail = 0f
        heads.forEach { head ->
            val delta = ((head - slot) + count).positiveModulo(count.toFloat())
            if (delta >= 1f && delta < 1.9f) tail = maxOf(tail, 0.22f * 1.60f * exp(-(delta - 1f) * 1.2f))
        }
        if (tail > 0.01f) drawBeam(edge, 0f, 1f, base * (1.05f + 0.25f * edge.depth), tail * energy, phase + slot * 0.13f, false, 0f)
    }
}

private fun overlap(index: Int, head: Float, length: Float, count: Int): Overlap? {
    var best: Overlap? = null
    val segStart = head - length
    val segEnd = head
    listOf(-count.toFloat(), 0f, count.toFloat()).forEach { shift ->
        val start = maxOf(index.toFloat(), segStart + shift)
        val end = minOf(index + 1f, segEnd + shift)
        if (end > start && (best == null || end - start > best!!.end - best!!.start)) best = Overlap(start - index, end - index)
    }
    return best
}

private fun DrawScope.drawBeam(edge: Edge, start: Float, end: Float, width: Float, intensity: Float, progress: Float, drawHead: Boolean, headAlpha: Float) {
    val a = pointOnEdge(edge, clamp01(start))
    val b = pointOnEdge(edge, clamp01(end))
    val startOffset = Offset(a.x, a.y)
    val endOffset = Offset(b.x, b.y)
    val brush = Brush.linearGradient(listOf(themeColor(progress, 0f), themeColor(progress, 0.30f), rgba(230, 255, 255, 0.78f), Color.White.copy(alpha = 0.96f)), startOffset, endOffset)
    drawLine(brush, startOffset, endOffset, width * 2.15f, StrokeCap.Round, alpha = (0.30f * intensity).coerceIn(0f, 1f), blendMode = BlendMode.Plus)
    drawLine(brush, startOffset, endOffset, width * 1.10f, StrokeCap.Round, alpha = (0.78f * intensity).coerceIn(0f, 1f), blendMode = BlendMode.Plus)
    drawLine(Color.White.copy(alpha = (0.70f * intensity).coerceIn(0f, 1f)), startOffset, endOffset, width * 0.24f, StrokeCap.Round, blendMode = BlendMode.Plus)
    if (drawHead && headAlpha > 0.002f) drawCircle(Brush.radialGradient(listOf(Color.White, themeColor(progress, 0.95f), rgba(90, 150, 255, 0f)), endOffset, width * 1.76f), width * 1.76f, endOffset, alpha = headAlpha, blendMode = BlendMode.Plus)
}

private fun DrawScope.drawVertex(point: P2, base: Float, alpha: Float, time: Float, front: Float) {
    val strength = 0.30f * alpha * front * 1.34f
    val radius = base * (0.45f + 0.65f * strength)
    val twinkle = 1f + 0.10f * sin(time * 7.1f + point.x * 0.01f) + 0.06f * sin(time * 13.4f + point.y * 0.01f)
    val center = Offset(point.x, point.y)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = (0.85f * strength * twinkle).coerceIn(0f, 1f)), themeColor(0.10f, 0.52f * strength), themeColor(0.72f, 0.16f * strength), rgba(100, 130, 255, 0f)), center, radius * 3.2f), radius * 3.2f, center, blendMode = BlendMode.Plus)
}

private fun pointOnEdge(edge: Edge, t: Float): P2 = P2(lerp(edge.a.x, edge.b.x, t), lerp(edge.a.y, edge.b.y, t), lerp(edge.a.z, edge.b.z, t))
private fun rotateX(p: V3, a: Float): V3 { val c = cos(a); val s = sin(a); return V3(p.x, p.y * c - p.z * s, p.y * s + p.z * c) }
private fun rotateY(p: V3, a: Float): V3 { val c = cos(a); val s = sin(a); return V3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c) }
private fun rotateZ(p: V3, a: Float): V3 { val c = cos(a); val s = sin(a); return V3(p.x * c - p.y * s, p.x * s + p.y * c, p.z) }
private fun themeColor(progress: Float, alpha: Float): Color { val t = progress.positiveModulo(1f); return if (t < 0.46f) rgbMix(themeA, themeB, t / 0.46f, alpha) else rgbMix(themeB, themeC, (t - 0.46f) / 0.54f, alpha) }
private fun rgbMix(a: FloatArray, b: FloatArray, t: Float, alpha: Float): Color = Color(lerp(a[0], b[0], t) / 255f, lerp(a[1], b[1], t) / 255f, lerp(a[2], b[2], t) / 255f, alpha.coerceIn(0f, 1f))
private fun rgba(r: Int, g: Int, b: Int, alpha: Float): Color = Color(r / 255f, g / 255f, b / 255f, alpha.coerceIn(0f, 1f))
private fun Float.rad(): Float = (this * PI / 180.0).toFloat()
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)
private fun smooth(value: Float): Float { val v = clamp01(value); return v * v * (3f - 2f * v) }
private fun Float.positiveModulo(divisor: Float): Float { val raw = this % divisor; return if (raw < 0f) raw + divisor else raw }
