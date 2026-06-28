package com.yuchen.ailedger.ui.gl

import android.view.Choreographer
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import kotlin.math.max

@Immutable
internal data class OpenGLGlassDynamicSnapshot(
    val pressValue: Float = 0f,
    val openGlPress: Float = 0f,
    val pressCenter: Offset = Offset(0.50f, 0.42f),
    val rimFlowSeed: Float = 0.50f,
    val rimFlowDirection: Float = 1f,
    val rimFlowBand: Int = 0,
    val rimFlowStrength: Float = 1f,
) {
    val pressCompression: Float
        get() = glassDynamicSmoothStep((pressValue.coerceAtLeast(0f) / 0.72f).coerceIn(0f, 1f))

    val pressRebound: Float
        get() = glassDynamicSmoothStep((-pressValue / 0.10f).coerceIn(0f, 1f))

    val surfaceOpticsPress: Float
        get() = max(
            pressValue.coerceAtLeast(0f),
            max(openGlPress.coerceIn(0f, 1f) * 0.62f, pressRebound * 0.24f),
        )

    val glassIntensityScale: Float
        get() = 1f + pressCompression * 0.10f
}

/**
 * Shell 动态效果的非布局状态通道。
 *
 * 指针移动与两条按压动画都只写 pending 快照；同一显示帧内无论写入多少次，
 * 最终只在 Choreographer 回调中提交最后一份状态。Compose 图层/绘制节点读取
 * [snapshotState] 只触发 layer/draw invalidation，OpenGL Host 则通过监听器直接消费，
 * 不再依赖整块 Shell 重组。
 */
@Stable
internal class OpenGLGlassDynamicState {
    private val committedState = mutableStateOf(OpenGLGlassDynamicSnapshot())
    private var pendingSnapshot = committedState.value
    private var framePosted = false
    private val frameListeners = linkedSetOf<() -> Unit>()

    val snapshotState: State<OpenGLGlassDynamicSnapshot>
        get() = committedState

    fun latestSnapshot(): OpenGLGlassDynamicSnapshot = pendingSnapshot

    fun updateAnimation(pressValue: Float, openGlPress: Float) {
        val next = pendingSnapshot.copy(
            pressValue = pressValue.coerceIn(-0.14f, 1.08f),
            openGlPress = openGlPress.coerceIn(0f, 1f),
        )
        enqueue(next)
    }

    fun updatePressCenter(center: Offset) {
        enqueue(
            pendingSnapshot.copy(
                pressCenter = Offset(
                    x = center.x.coerceIn(0f, 1f),
                    y = center.y.coerceIn(0f, 1f),
                ),
            ),
        )
    }

    fun updateRimFlow(seed: Float, direction: Float, band: Int, strength: Float) {
        enqueue(
            pendingSnapshot.copy(
                rimFlowSeed = seed.coerceIn(0f, 1f),
                rimFlowDirection = if (direction >= 0f) 1f else -1f,
                rimFlowBand = band.coerceIn(0, 3),
                rimFlowStrength = strength.coerceIn(0.70f, 1.45f),
            ),
        )
    }

    fun reset() {
        enqueue(OpenGLGlassDynamicSnapshot())
    }

    fun addFrameListener(listener: () -> Unit): () -> Unit {
        frameListeners += listener
        return { frameListeners -= listener }
    }

    private fun enqueue(next: OpenGLGlassDynamicSnapshot) {
        if (next == pendingSnapshot) return
        pendingSnapshot = next
        if (framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private val frameCallback = Choreographer.FrameCallback {
        framePosted = false
        val next = pendingSnapshot
        if (committedState.value != next) committedState.value = next
        if (frameListeners.isNotEmpty()) {
            frameListeners.toList().forEach { listener -> listener() }
        }
    }
}

private fun glassDynamicSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
