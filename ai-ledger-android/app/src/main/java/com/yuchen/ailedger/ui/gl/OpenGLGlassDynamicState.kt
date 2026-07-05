package com.yuchen.ailedger.ui.gl

import android.view.Choreographer
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.yuchen.ailedger.ui.OpenGLFrameFinalizer
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
 * 热路径只更新 primitive pending 字段，不再为同一显示帧内的每次指针/动画写入创建
 * data class 副本。每帧最多生成一份最终快照，并与位置、背景原点一起在 PreDraw 提交。
 */
@Stable
class OpenGLGlassDynamicState {
    private val committedState = mutableStateOf(OpenGLGlassDynamicSnapshot())

    private var pendingPressValue = committedState.value.pressValue
    private var pendingOpenGlPress = committedState.value.openGlPress
    private var pendingPressCenterX = committedState.value.pressCenter.x
    private var pendingPressCenterY = committedState.value.pressCenter.y
    private var pendingRimFlowSeed = committedState.value.rimFlowSeed
    private var pendingRimFlowDirection = committedState.value.rimFlowDirection
    private var pendingRimFlowBand = committedState.value.rimFlowBand
    private var pendingRimFlowStrength = committedState.value.rimFlowStrength
    private var pendingSnapshot = committedState.value
    private var pendingSnapshotDirty = false

    private var framePosted = false
    private val frameListeners = linkedSetOf<() -> Unit>()
    private val finalDispatchAction: () -> Unit = {
        if (frameListeners.isNotEmpty()) {
            for (listener in frameListeners) listener()
        }
    }

    internal val snapshotState: State<OpenGLGlassDynamicSnapshot>
        get() = committedState

    internal fun latestSnapshot(): OpenGLGlassDynamicSnapshot {
        if (!pendingSnapshotDirty) return pendingSnapshot
        pendingSnapshot = OpenGLGlassDynamicSnapshot(
            pressValue = pendingPressValue,
            openGlPress = pendingOpenGlPress,
            pressCenter = Offset(pendingPressCenterX, pendingPressCenterY),
            rimFlowSeed = pendingRimFlowSeed,
            rimFlowDirection = pendingRimFlowDirection,
            rimFlowBand = pendingRimFlowBand,
            rimFlowStrength = pendingRimFlowStrength,
        )
        pendingSnapshotDirty = false
        return pendingSnapshot
    }

    internal fun updateAnimation(pressValue: Float, openGlPress: Float) {
        val nextPressValue = pressValue.coerceIn(-0.14f, 1.08f)
        val nextOpenGlPress = openGlPress.coerceIn(0f, 1f)
        if (nextPressValue == pendingPressValue && nextOpenGlPress == pendingOpenGlPress) return
        pendingPressValue = nextPressValue
        pendingOpenGlPress = nextOpenGlPress
        markPendingChanged()
    }

    internal fun updatePressCenter(center: Offset) {
        val nextX = center.x.coerceIn(0f, 1f)
        val nextY = center.y.coerceIn(0f, 1f)
        if (nextX == pendingPressCenterX && nextY == pendingPressCenterY) return
        pendingPressCenterX = nextX
        pendingPressCenterY = nextY
        markPendingChanged()
    }

    internal fun updateRimFlow(seed: Float, direction: Float, band: Int, strength: Float) {
        val nextSeed = seed.coerceIn(0f, 1f)
        val nextDirection = if (direction >= 0f) 1f else -1f
        val nextBand = band.coerceIn(0, 3)
        val nextStrength = strength.coerceIn(0.70f, 1.45f)
        if (
            nextSeed == pendingRimFlowSeed &&
            nextDirection == pendingRimFlowDirection &&
            nextBand == pendingRimFlowBand &&
            nextStrength == pendingRimFlowStrength
        ) return
        pendingRimFlowSeed = nextSeed
        pendingRimFlowDirection = nextDirection
        pendingRimFlowBand = nextBand
        pendingRimFlowStrength = nextStrength
        markPendingChanged()
    }

    internal fun reset() {
        if (
            pendingPressValue == 0f &&
            pendingOpenGlPress == 0f &&
            pendingPressCenterX == 0.50f &&
            pendingPressCenterY == 0.42f &&
            pendingRimFlowSeed == 0.50f &&
            pendingRimFlowDirection == 1f &&
            pendingRimFlowBand == 0 &&
            pendingRimFlowStrength == 1f
        ) return
        pendingPressValue = 0f
        pendingOpenGlPress = 0f
        pendingPressCenterX = 0.50f
        pendingPressCenterY = 0.42f
        pendingRimFlowSeed = 0.50f
        pendingRimFlowDirection = 1f
        pendingRimFlowBand = 0
        pendingRimFlowStrength = 1f
        markPendingChanged()
    }

    internal fun addFrameListener(listener: () -> Unit): () -> Unit {
        frameListeners += listener
        return { frameListeners -= listener }
    }

    private fun markPendingChanged() {
        pendingSnapshotDirty = true
        if (framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private val frameCallback = Choreographer.FrameCallback {
        framePosted = false
        val next = latestSnapshot()
        if (committedState.value == next) return@FrameCallback
        committedState.value = next

        // 动态状态只通知自身宿主，不再借用全局背景 ticker 把整批 Shell 标记为根采样脏。
        // 固定回调仍由根视图 PreDraw 提交，保证 OpenGL 与 Compose 使用同一帧最终状态。
        if (frameListeners.isNotEmpty()) {
            OpenGLFrameFinalizer.dispatch(finalDispatchAction)
        }
    }
}

private fun glassDynamicSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
