package com.yuchen.ailedger.ui

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Choreographer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val LocalRainbowPrismStyle = compositionLocalOf { RainbowPrismStyle() }

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val RAINBOW_PHASE_A_SECONDS = 9.2
private const val RAINBOW_PHASE_B_SECONDS = 13.7
private const val RAINBOW_PHASE_C_SECONDS = 11.1
private const val RAINBOW_TAU = PI * 2.0
private const val RAINBOW_CORNER_RADIUS_PX = 30f
private const val RAINBOW_ALPHA_NORMALIZER = 4.48f
private const val UNSET_FRAME_NANOS = Long.MIN_VALUE

/*
 * 各层颜色只在类加载时构建一次。动态 overall / sweep / halo 通过 Paint alpha 统一缩放，
 * 与原来逐颜色 copy(alpha = coefficient * factor) 的结果等价，同时消除逐帧颜色列表分配。
 */
private val RainbowLayerAColors = intArrayOf(
    normalizedRainbowColor(0xFFFF62D8, 0.070f),
    normalizedRainbowColor(0xFFFFD86E, 0.050f),
    normalizedRainbowColor(0xFF55FFF0, 0.066f),
    Color.Transparent.toArgb(),
)

private val RainbowLayerBColors = intArrayOf(
    normalizedRainbowColor(0xFF62FFF0, 0.052f),
    normalizedRainbowColor(0xFF7F95FF, 0.050f),
    normalizedRainbowColor(0xFFFF78E4, 0.035f),
    Color.Transparent.toArgb(),
)

private val RainbowLayerCColors = intArrayOf(
    Color.Transparent.toArgb(),
    normalizedRainbowColor(0xFFFFE58A, 0.030f),
    normalizedRainbowColor(0xFF76FFF2, 0.038f),
    normalizedRainbowColor(0xFFFF7BE5, 0.026f),
    Color.Transparent.toArgb(),
)

private val RainbowHaloColors = intArrayOf(
    normalizedRainbowColor(0xFFFFFFFF, 0.045f),
    normalizedRainbowColor(0xFF72FFF2, 0.074f),
    normalizedRainbowColor(0xFFFF76DE, 0.054f),
    normalizedRainbowColor(0xFF7B95FF, 0.044f),
    Color.Transparent.toArgb(),
)

/**
 * 聊天 Shell 的彩虹薄膜和标题控件保持独立绘制边界。
 *
 * 彩虹薄膜由独立 DrawModifierNode 直接跟随 Choreographer VSync，只失效自身的绘制层；
 * 不再写 Compose State、不触发逐帧重组，也不参与 OpenGL geometry / registry / sync 链。
 */
@Composable
fun RainbowChatGlassOverlay(
    quality: RenderQuality,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    style: RainbowPrismStyle = LocalRainbowPrismStyle.current,
) {
    Box(modifier = modifier) {
        RainbowChatGlassFilm(
            quality = quality,
            motionIntensity = motionIntensity,
            style = style,
            modifier = Modifier.fillMaxSize(),
        )

        AgentChatMemoryTitleControls(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 54.dp, top = 7.dp),
        )
    }
}

@Composable
private fun RainbowChatGlassFilm(
    quality: RenderQuality,
    motionIntensity: Float,
    style: RainbowPrismStyle,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.rainbowChatGlassFilm(
            motionOn = quality.enableMotion && motionIntensity > 0.02f,
            motionIntensity = motionIntensity,
            style = style,
        ),
    )
}

private fun Modifier.rainbowChatGlassFilm(
    motionOn: Boolean,
    motionIntensity: Float,
    style: RainbowPrismStyle,
): Modifier = this.then(
    RainbowChatGlassFilmElement(
        motionOn = motionOn,
        motionIntensity = motionIntensity,
        style = style,
    ),
)

private data class RainbowChatGlassFilmElement(
    val motionOn: Boolean,
    val motionIntensity: Float,
    val style: RainbowPrismStyle,
) : ModifierNodeElement<RainbowChatGlassFilmNode>() {
    override fun create(): RainbowChatGlassFilmNode = RainbowChatGlassFilmNode(
        motionOn = motionOn,
        motionIntensity = motionIntensity,
        style = style,
    )

    override fun update(node: RainbowChatGlassFilmNode) {
        node.update(
            motionOn = motionOn,
            motionIntensity = motionIntensity,
            style = style,
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "rainbowChatGlassFilm"
        properties["motionOn"] = motionOn
        properties["motionIntensity"] = motionIntensity
        properties["style"] = style
    }
}

private class RainbowChatGlassFilmNode(
    motionOn: Boolean,
    motionIntensity: Float,
    style: RainbowPrismStyle,
) : Modifier.Node(), DrawModifierNode, Choreographer.FrameCallback {
    private var motionOn = motionOn
    private var motionIntensity = motionIntensity
    private var style = style

    private var choreographer: Choreographer? = null
    private var framePosted = false
    private var startFrameNanos = UNSET_FRAME_NANOS
    private var elapsedNanos = 0L

    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val radialMatrixA = Matrix()
    private val radialMatrixB = Matrix()
    private val linearMatrix = Matrix()
    private val haloMatrix = Matrix()
    private val linearMatrixValues = FloatArray(9)

    private val radialShaderA = RadialGradient(
        0f,
        0f,
        1f,
        RainbowLayerAColors,
        null,
        Shader.TileMode.CLAMP,
    )
    private val radialShaderB = RadialGradient(
        0f,
        0f,
        1f,
        RainbowLayerBColors,
        null,
        Shader.TileMode.CLAMP,
    )
    private val linearShader = LinearGradient(
        0f,
        0f,
        1f,
        0f,
        RainbowLayerCColors,
        null,
        Shader.TileMode.CLAMP,
    )
    private val haloShader = RadialGradient(
        0f,
        0f,
        1f,
        RainbowHaloColors,
        null,
        Shader.TileMode.CLAMP,
    )

    override fun onAttach() {
        choreographer = Choreographer.getInstance()
        if (motionOn) postNextFrame()
    }

    override fun onDetach() {
        cancelPendingFrame()
        choreographer = null
        startFrameNanos = UNSET_FRAME_NANOS
        elapsedNanos = 0L
    }

    fun update(
        motionOn: Boolean,
        motionIntensity: Float,
        style: RainbowPrismStyle,
    ) {
        val motionChanged = this.motionOn != motionOn
        val visualChanged =
            motionChanged ||
                this.motionIntensity != motionIntensity ||
                this.style != style

        this.motionOn = motionOn
        this.motionIntensity = motionIntensity
        this.style = style

        if (motionChanged) {
            startFrameNanos = UNSET_FRAME_NANOS
            elapsedNanos = 0L
            if (motionOn) {
                postNextFrame()
            } else {
                cancelPendingFrame()
            }
        }
        if (visualChanged) invalidateDraw()
    }

    override fun doFrame(frameTimeNanos: Long) {
        framePosted = false
        if (!isAttached || !motionOn) return

        if (startFrameNanos == UNSET_FRAME_NANOS) {
            // 与原 withFrameNanos 起点保持一致：首个 VSync 只建立时间原点。
            startFrameNanos = frameTimeNanos
        } else {
            elapsedNanos = (frameTimeNanos - startFrameNanos).coerceAtLeast(0L)
            invalidateDraw()
        }
        postNextFrame()
    }

    override fun ContentDrawScope.draw() {
        drawContent()

        val width = size.width.coerceAtLeast(1f)
        val height = size.height.coerceAtLeast(1f)
        val elapsedSeconds = elapsedNanos / NANOS_PER_SECOND
        val radians1 = if (motionOn) {
            elapsedSeconds / RAINBOW_PHASE_A_SECONDS * RAINBOW_TAU
        } else {
            0.42 * RAINBOW_TAU
        }
        val radians2 = if (motionOn) {
            (elapsedSeconds / RAINBOW_PHASE_B_SECONDS + 0.37) * RAINBOW_TAU
        } else {
            0.58 * RAINBOW_TAU
        }
        val radians3 = if (motionOn) {
            (elapsedSeconds / RAINBOW_PHASE_C_SECONDS + 0.71) * RAINBOW_TAU
        } else {
            0.30 * RAINBOW_TAU
        }

        val base = (0.88f + motionIntensity.coerceIn(0f, 1.4f) * 0.16f)
            .coerceIn(0.76f, 1.12f) * style.overall.coerceIn(0f, 2f)
        val minSweep = minOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
        val maxSweep = maxOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
        val breath01 = unitWave(radians3)
        val sweep = minSweep + (maxSweep - minSweep) * breath01
        val halo = style.rainbowHalo.coerceIn(0f, 2f)
        val sweepAlphaFactor = base * sweep
        val haloAlphaFactor = base * halo

        val slowWave = unitWave(radians1 + radians2 * 0.37)
        val ax = 0.50f + 0.34f * cosFloat(radians1)
        val ay = 0.28f + 0.18f * sinFloat(radians1 * 0.73 + 0.80)
        val bx = 0.54f + 0.32f * cosFloat(radians2 + 1.70)
        val by = 0.56f + 0.30f * sinFloat(radians2 * 0.82 + 2.20)
        val cx = 0.50f + 0.38f * cosFloat(radians1 * 0.58 + radians2 * 0.32)
        val cy = 0.50f + 0.34f * sinFloat(radians2 * 0.64 + 1.10)
        val maxSide = maxOf(width, height)
        val canvas = drawContext.canvas.nativeCanvas

        if (sweep > 0.001f && sweepAlphaFactor > 0.001f) {
            drawRadialFilm(
                canvas = canvas,
                shader = radialShaderA,
                matrix = radialMatrixA,
                centerX = width * ax.coerceIn(0.08f, 0.92f),
                centerY = height * ay.coerceIn(0.06f, 0.70f),
                radius = maxSide * (0.78f + 0.22f * slowWave),
                width = width,
                height = height,
                alphaFactor = sweepAlphaFactor,
            )
            drawRadialFilm(
                canvas = canvas,
                shader = radialShaderB,
                matrix = radialMatrixB,
                centerX = width * bx.coerceIn(0.10f, 0.90f),
                centerY = height * by.coerceIn(0.18f, 0.92f),
                radius = maxSide * 0.88f,
                width = width,
                height = height,
                alphaFactor = sweepAlphaFactor,
            )
            drawLinearFilm(
                canvas = canvas,
                startX = width * (cx - 0.44f).coerceIn(-0.20f, 0.80f),
                startY = height * (cy - 0.42f).coerceIn(-0.16f, 0.72f),
                endX = width * (cx + 0.46f).coerceIn(0.20f, 1.20f),
                endY = height * (cy + 0.46f).coerceIn(0.26f, 1.18f),
                width = width,
                height = height,
                alphaFactor = sweepAlphaFactor,
            )
        }

        if (halo > 0.001f && haloAlphaFactor > 0.001f) {
            drawRadialFilm(
                canvas = canvas,
                shader = haloShader,
                matrix = haloMatrix,
                centerX = width * (0.74f + 0.05f * cosFloat(radians3)),
                centerY = height * (0.62f + 0.08f * slowWave),
                radius = maxSide * (0.62f + 0.08f * unitWave(radians3)),
                width = width,
                height = height,
                alphaFactor = haloAlphaFactor,
            )
        }
    }

    private fun drawRadialFilm(
        canvas: android.graphics.Canvas,
        shader: Shader,
        matrix: Matrix,
        centerX: Float,
        centerY: Float,
        radius: Float,
        width: Float,
        height: Float,
        alphaFactor: Float,
    ) {
        matrix.reset()
        matrix.setScale(radius.coerceAtLeast(1f), radius.coerceAtLeast(1f))
        matrix.postTranslate(centerX, centerY)
        shader.setLocalMatrix(matrix)
        drawShaderFilm(canvas, shader, width, height, alphaFactor)
    }

    private fun drawLinearFilm(
        canvas: android.graphics.Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        width: Float,
        height: Float,
        alphaFactor: Float,
    ) {
        val dx = endX - startX
        val dy = endY - startY
        linearMatrixValues[0] = dx
        linearMatrixValues[1] = -dy
        linearMatrixValues[2] = startX
        linearMatrixValues[3] = dy
        linearMatrixValues[4] = dx
        linearMatrixValues[5] = startY
        linearMatrixValues[6] = 0f
        linearMatrixValues[7] = 0f
        linearMatrixValues[8] = 1f
        linearMatrix.setValues(linearMatrixValues)
        linearShader.setLocalMatrix(linearMatrix)
        drawShaderFilm(canvas, linearShader, width, height, alphaFactor)
    }

    private fun drawShaderFilm(
        canvas: android.graphics.Canvas,
        shader: Shader,
        width: Float,
        height: Float,
        alphaFactor: Float,
    ) {
        screenPaint.shader = shader
        screenPaint.alpha = ((alphaFactor / RAINBOW_ALPHA_NORMALIZER).coerceIn(0f, 1f) * 255f)
            .roundToInt()
        if (screenPaint.alpha <= 0) return
        canvas.drawRoundRect(
            0f,
            0f,
            width,
            height,
            RAINBOW_CORNER_RADIUS_PX,
            RAINBOW_CORNER_RADIUS_PX,
            screenPaint,
        )
    }

    private fun postNextFrame() {
        if (!isAttached || !motionOn || framePosted) return
        val activeChoreographer = choreographer ?: Choreographer.getInstance().also {
            choreographer = it
        }
        framePosted = true
        activeChoreographer.postFrameCallback(this)
    }

    private fun cancelPendingFrame() {
        if (framePosted) choreographer?.removeFrameCallback(this)
        framePosted = false
    }
}

private fun normalizedRainbowColor(argb: Long, coefficient: Float): Int =
    Color(argb).copy(alpha = (coefficient * RAINBOW_ALPHA_NORMALIZER).coerceIn(0f, 1f)).toArgb()

private fun sinFloat(value: Double): Float = sin(value).toFloat()

private fun cosFloat(value: Double): Float = cos(value).toFloat()

private fun unitWave(value: Double): Float = ((sin(value) + 1.0) * 0.5).toFloat().coerceIn(0f, 1f)
