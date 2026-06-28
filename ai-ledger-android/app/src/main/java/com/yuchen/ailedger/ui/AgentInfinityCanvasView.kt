package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.Canvas as NativeCanvas
import android.view.View
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

internal class AgentInfinityCanvasView(context: Context) : View(context) {
    private var enabled = false
    private var targetState = AgentInfinityWebState.Off
    private var renderedState = AgentInfinityWebState.Off
    private var lastActiveState = AgentInfinityWebState.Standby
    private var direction: AgentInfinityToggleDirection? = null
    private var transitionStartNanos = 0L
    private var lastFrameNanos = 0L
    private var phase = 0f
    private var renderer = AgentInfinityWebRenderer()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = false
        isFocusable = false
    }

    fun setState(nextEnabled: Boolean, nextState: AgentInfinityWebState) {
        targetState = nextState
        if (nextEnabled && nextState != AgentInfinityWebState.Off) {
            lastActiveState = nextState
        }
        if (nextEnabled != enabled) {
            enabled = nextEnabled
            direction = if (nextEnabled) {
                renderedState = nextState.takeUnless { it == AgentInfinityWebState.Off }
                    ?: AgentInfinityWebState.Standby
                AgentInfinityToggleDirection.On
            } else {
                renderedState = lastActiveState
                AgentInfinityToggleDirection.Off
            }
            transitionStartNanos = System.nanoTime()
            lastFrameNanos = 0L
        } else if (direction == null) {
            renderedState = nextState
        }
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        renderer = AgentInfinityWebRenderer()
    }

    override fun onDraw(canvas: NativeCanvas) {
        super.onDraw(canvas)
        if (width <= 1 || height <= 1) return
        val now = System.nanoTime()
        val motion = currentMotion(now)
        val theme = renderedState.theme()
        if (lastFrameNanos != 0L) {
            val deltaSeconds = ((now - lastFrameNanos).coerceAtMost(40_000_000L)) / 1_000_000_000f
            phase = (phase + deltaSeconds * 0.285f * theme.speed *
                AgentInfinityWebSpec.speed * motion.motion) % 1f
        }
        lastFrameNanos = now
        val bitmap = renderer.render(
            width = width,
            height = height,
            state = renderedState,
            theme = theme,
            phase = phase,
            timeSeconds = now / 1_000_000_000f,
            motion = motion
        )
        canvas.drawBitmap(bitmap, 0f, 0f, renderer.outputPaint(motion, enabled))
        if (enabled || direction != null) postInvalidateOnAnimation()
    }

    private fun currentMotion(now: Long): AgentInfinityMotionFrame {
        val currentDirection = direction ?: return if (enabled) {
            AgentInfinityMotionFrame(1f, 1f, 1f, 0f)
        } else {
            AgentInfinityMotionFrame(0f, 0f, 0f, 0f)
        }
        val duration = if (currentDirection == AgentInfinityToggleDirection.On) 560_000_000f else 420_000_000f
        val t = ((now - transitionStartNanos) / duration).coerceIn(0f, 1f)
        if (t >= 1f) {
            direction = null
            renderedState = if (enabled) targetState else AgentInfinityWebState.Off
            return if (enabled) {
                AgentInfinityMotionFrame(1f, 1f, 1f, 0f)
            } else {
                AgentInfinityMotionFrame(0f, 0f, 0f, 0f)
            }
        }
        return if (currentDirection == AgentInfinityToggleDirection.On) {
            AgentInfinityMotionFrame(
                energy = easeOut(t),
                trail = smoothStep((t - 0.18f) / 0.67f),
                motion = 0.24f + 0.76f * smoothStep((t - 0.08f) / 0.72f),
                flash = 0.78f * exp(-((t - 0.34f) / 0.115f).pow(2))
            )
        } else {
            AgentInfinityMotionFrame(
                energy = 1f - smoothStep((t - 0.10f) / 0.82f),
                trail = 1f - smoothStep((t - 0.04f) / 0.70f),
                motion = if (t < 0.27f) {
                    1f + 0.18f * sin(PI.toFloat() * t / 0.27f)
                } else {
                    1f - 0.84f * smoothStep((t - 0.27f) / 0.73f)
                },
                flash = 0.36f * exp(-((t - 0.38f) / 0.12f).pow(2))
            )
        }
    }
}

private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun easeOut(value: Float): Float =
    1f - (1f - value.coerceIn(0f, 1f)).pow(3)
