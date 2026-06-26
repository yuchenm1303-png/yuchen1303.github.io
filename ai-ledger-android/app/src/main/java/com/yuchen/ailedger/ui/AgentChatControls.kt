package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.service.AgentOverlayProgress
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

@Composable
internal fun AgentChatHeaderOverlay(modifier: Modifier = Modifier) = Unit

@Composable
internal fun AgentChatGlassTitleControls(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val agentEnabled by AgentRuntimeController.enabled.collectAsState()
    val progress by AgentRuntimeController.progress.collectAsState()
    var overlayVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(overlayVisible, progress.updatedAt) {
        if (overlayVisible) {
            if (AgentOverlayService.canDrawOverlays(context)) {
                AgentOverlayService.ensureStarted(context.applicationContext)
            } else {
                overlayVisible = false
            }
        }
    }

    Row(
        modifier = modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AgentInfinityWebCapsule(
            enabled = agentEnabled,
            progress = progress,
            onClick = { AgentRuntimeController.setEnabled(!agentEnabled) }
        )
        AgentHeaderSwitchPill(
            label = "浮窗",
            enabled = overlayVisible,
            activeColors = listOf(Color(0xEE8DFFF4), Color(0xCC9B73FF), Color(0xAA4FB6FF)),
            onClick = {
                val next = !overlayVisible
                if (next) {
                    val allowed = AgentOverlayService.requestPermissionIfNeeded(context.applicationContext)
                    if (allowed) {
                        overlayVisible = true
                        AgentOverlayService.ensureStarted(context.applicationContext)
                    } else {
                        Toast.makeText(context, "请开启悬浮窗权限，用来显示智能体执行进展", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    overlayVisible = false
                    AgentOverlayService.stop(context.applicationContext)
                }
            }
        )
    }
}

@Composable
private fun AgentInfinityWebCapsule(
    enabled: Boolean,
    progress: AgentOverlayProgress,
    onClick: () -> Unit
) {
    val state = when {
        !enabled -> AgentInfinityWebState.Off
        progress.userTakeoverPaused -> AgentInfinityWebState.Paused
        progress.running -> AgentInfinityWebState.Running
        progress.status.contains("失败") ||
            progress.status.contains("错误") ||
            progress.status.contains("异常") -> AgentInfinityWebState.Error
        else -> AgentInfinityWebState.Standby
    }
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val sweep = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        coroutineScope {
            launch {
                scaleX.snapTo(1f)
                scaleX.animateTo(
                    1f,
                    if (enabled) {
                        keyframes {
                            durationMillis = 560
                            0.974f at 78
                            1.036f at 235
                            0.992f at 358
                            1.010f at 459
                            1f at 560
                        }
                    } else {
                        keyframes {
                            durationMillis = 420
                            1.015f at 76
                            0.981f at 202
                            1.006f at 302
                            1f at 420
                        }
                    }
                )
            }
            launch {
                scaleY.snapTo(1f)
                scaleY.animateTo(
                    1f,
                    if (enabled) {
                        keyframes {
                            durationMillis = 560
                            1.026f at 78
                            0.984f at 235
                            1.010f at 358
                            0.996f at 459
                            1f at 560
                        }
                    } else {
                        keyframes {
                            durationMillis = 420
                            0.988f at 76
                            1.017f at 202
                            0.996f at 302
                            1f at 420
                        }
                    }
                )
            }
            launch {
                sweep.snapTo(0f)
                sweep.animateTo(
                    1f,
                    tween(
                        durationMillis = if (enabled) 520 else 360,
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    val active by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (enabled) 560 else 420,
            easing = FastOutSlowInEasing
        ),
        label = "agent-web-active"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val sweepWave = sin(PI.toFloat() * sweep.value).coerceAtLeast(0f)
    val sweepDirection = if (enabled) sweep.value else 1f - sweep.value

    Box(
        modifier = Modifier
            .width(88.dp)
            .height(22.dp)
            .graphicsLayer {
                this.scaleX = scaleX.value
                this.scaleY = scaleY.value
            }
            .toggleable(
                value = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onValueChange = { onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.height / 2f
            drawRoundRect(
                color = Color(0xFF0A1132).copy(alpha = if (enabled) 0.80f else 0.76f),
                cornerRadius = CornerRadius(radius)
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.09f),
                        0.44f to Color(0xFF52FFEA).copy(alpha = 0.03f * active),
                        0.82f to Color(0xFF8558FF).copy(alpha = 0.09f * active),
                        1f to Color.Transparent
                    ),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f)
                ),
                cornerRadius = CornerRadius(radius)
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.16f),
                        0.28f to Color.Transparent
                    ),
                    center = Offset(size.width * 0.75f, size.height * 0.06f),
                    radius = size.width * 0.28f
                ),
                cornerRadius = CornerRadius(radius)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = if (enabled) 0.20f else 0.10f),
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 7.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "Agent",
                color = Color.White.copy(alpha = 0.62f + active * 0.34f),
                fontSize = 9.2.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            AndroidView(
                factory = { AgentInfinityCanvasView(it) },
                update = { it.setState(enabled, state) },
                modifier = Modifier
                    .width(42.dp)
                    .height(18.dp)
            )
        }

        if (sweepWave > 0.001f) {
            Canvas(Modifier.matchParentSize()) {
                val centerX = size.width * (-0.35f + sweepDirection * 1.70f)
                val sweepWidth = size.width * 0.42f
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.54f * sweepWave),
                            Color(0xFF74FFF0).copy(alpha = 0.28f * sweepWave),
                            Color.Transparent
                        ),
                        start = Offset(centerX - sweepWidth, 0f),
                        end = Offset(centerX + sweepWidth, size.height)
                    ),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            }
        }
    }
}

private class AgentInfinityCanvasView(context: Context) : View(context) {
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

private data class AgentInfinityGeometry(
    val raw: List<PointF>,
    val uniform: List<PointF>,
    val crossingB: Float
)

private class AgentInfinityWebRenderer {
    private var width = 0
    private var height = 0
    private var geometry: AgentInfinityGeometry? = null
    private var baseBitmap: Bitmap? = null
    private var frameBitmap: Bitmap? = null
    private var baseCanvas: NativeCanvas? = null
    private var frameCanvas: NativeCanvas? = null
    private var baseKey = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val output = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val addMode = PorterDuffXfermode(PorterDuff.Mode.ADD)

    fun outputPaint(motion: AgentInfinityMotionFrame, enabled: Boolean): Paint {
        val opacity = if (enabled) 1f else if (motion.energy > 0f) 0.76f + 0.24f * motion.energy else 0.76f
        val brightness = if (enabled) 1f else 0.72f
        val saturation = if (enabled) 1f else 0.48f
        output.alpha = (opacity * 255f).roundToInt()
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    brightness, 0f, 0f, 0f, 0f,
                    0f, brightness, 0f, 0f, 0f,
                    0f, 0f, brightness, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        output.colorFilter = ColorMatrixColorFilter(matrix)
        return output
    }

    fun render(
        width: Int,
        height: Int,
        state: AgentInfinityWebState,
        theme: AgentInfinityWebTheme,
        phase: Float,
        timeSeconds: Float,
        motion: AgentInfinityMotionFrame
    ): Bitmap {
        ensure(width, height)
        val currentGeometry = geometry ?: error("Missing infinity geometry")
        val base = min(width, height) * 0.090f * AgentInfinityWebSpec.band
        val key = "$width|$height|${state.name}|${theme.a}|${theme.b}|${theme.c}|${theme.alpha}"
        if (key != baseKey) {
            val canvas = baseCanvas ?: error("Missing base canvas")
            canvas.drawColor(NativeColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawRibbon(canvas, currentGeometry.raw, base, theme)
            drawCrossing(canvas, base)
            baseKey = key
        }

        val canvas = frameCanvas ?: error("Missing frame canvas")
        canvas.drawColor(NativeColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(baseBitmap ?: error("Missing base bitmap"), 0f, 0f, null)

        val reveal = max(0.025f, motion.trail)
        val energy = motion.energy
        when (state) {
            AgentInfinityWebState.Running -> drawSegment(
                canvas, currentGeometry, phase,
                min(0.54f, AgentInfinityWebSpec.trail / 0.80f) * reveal,
                base * 1.04f, theme, energy
            )
            AgentInfinityWebState.Standby -> drawSegment(
                canvas, currentGeometry, phase,
                min(0.24f, AgentInfinityWebSpec.trail / 1.80f) * reveal,
                base * 0.92f, theme, 0.72f * energy
            )
            AgentInfinityWebState.Paused -> if (energy > 0.02f) {
                drawSegment(canvas, currentGeometry, 0.985f, 0.08f * reveal, base * 0.92f, theme, 0.72f * energy)
            }
            AgentInfinityWebState.Error -> {
                val beat = max(0f, sin(timeSeconds * 3.2f)).pow(9)
                drawSegment(canvas, currentGeometry, phase, 0.09f * reveal, base * 0.96f, theme, (0.66f + 0.30f * beat) * energy)
            }
            AgentInfinityWebState.Off -> Unit
        }

        val pulse = when (state) {
            AgentInfinityWebState.Paused -> 0.16f + 0.17f * (0.5f + 0.5f * sin(timeSeconds * 2.1f))
            AgentInfinityWebState.Error -> 0.06f + 0.62f * max(0f, sin(timeSeconds * 3.2f)).pow(10)
            AgentInfinityWebState.Off -> 0f
            else -> {
                val distance = min(
                    circularDistance(phase, 0f),
                    circularDistance(phase, currentGeometry.crossingB)
                )
                0.035f + 0.65f * AgentInfinityWebSpec.sparkPass *
                    exp(-(distance / 0.030f).pow(2))
            }
        }
        drawStar(
            canvas = canvas,
            theme = theme,
            strength = max(
                if (state == AgentInfinityWebState.Off) 0f else 0.18f * energy,
                max(pulse * energy, motion.flash)
            ),
            timeSeconds = timeSeconds,
            phase = phase,
            base = base
        )
        return frameBitmap ?: error("Missing frame bitmap")
    }

    private fun ensure(newWidth: Int, newHeight: Int) {
        if (width == newWidth && height == newHeight && geometry != null) return
        width = newWidth
        height = newHeight
        baseBitmap?.recycle()
        frameBitmap?.recycle()
        baseBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        baseCanvas = NativeCanvas(baseBitmap!!)
        frameCanvas = NativeCanvas(frameBitmap!!)
        geometry = buildGeometry()
        baseKey = ""
    }

    private fun buildGeometry(): AgentInfinityGeometry {
        val count = 260
        val raw = List(count + 1) { index ->
            point(index / count.toFloat() * PI.toFloat() * 2f)
        }
        val cumulative = FloatArray(raw.size)
        var total = 0f
        for (index in 0 until count) {
            total += hypot(
                raw[index + 1].x - raw[index].x,
                raw[index + 1].y - raw[index].y
            )
            cumulative[index + 1] = total
        }
        val uniformCount = 340
        val uniform = List(uniformCount) { sample ->
            pointAtDistance(raw, cumulative, count, total, total * sample / uniformCount.toFloat())
        }
        return AgentInfinityGeometry(raw, uniform, cumulative[count / 2] / total.coerceAtLeast(0.0001f))
    }

    private fun point(t: Float): PointF {
        val s = sin(t)
        val c = cos(t)
        val absS = abs(s)
        val side = tanh(s * 4.6f)
        val left = (1f - side) / 2f
        val right = (1f + side) / 2f
        val denominator = 1f + AgentInfinityWebSpec.family *
            (0.30f + 0.85f * AgentInfinityWebSpec.waist) * c * c
        var xn = s / denominator
        var yn = s * c / denominator
        yn *= 0.52f + 0.48f * absS.pow(0.42f + AgentInfinityWebSpec.pinch * 1.12f)
        yn *= 0.74f + 0.26f * absS.pow(0.30f + AgentInfinityWebSpec.shoulder * 1.25f)
        yn *= 1f + (AgentInfinityWebSpec.crossAngle - 1f) * (1f - absS).pow(2.3f)
        yn *= 1f + (AgentInfinityWebSpec.tipRound - 1f) * absS.pow(4f)
        val xAmplitude = width * 0.335f * AgentInfinityWebSpec.aspect * AgentInfinityWebSpec.overall *
            (1f + left * AgentInfinityWebSpec.asym * 0.48f - right * AgentInfinityWebSpec.asym * 0.22f) *
            (1f + side * AgentInfinityWebSpec.lobe * 0.22f)
        val yAmplitude = height * 0.205f * AgentInfinityWebSpec.overall *
            (1f + right * AgentInfinityWebSpec.asym * 0.28f - left * AgentInfinityWebSpec.asym * 0.11f) *
            (1f + side * AgentInfinityWebSpec.lobe * 0.10f)
        var x = xn * xAmplitude
        var y = yn * yAmplitude
        y *= 1f + (if (y >= 0f) AgentInfinityWebSpec.vertical else -AgentInfinityWebSpec.vertical) * 0.25f
        y += side * (-height * 0.034f * AgentInfinityWebSpec.asym) * AgentInfinityWebSpec.overall
        x += sin(2f * t) * width * 0.024f *
            (0.35f + 0.65f * AgentInfinityWebSpec.shoulder) * AgentInfinityWebSpec.overall
        x += width * 0.08f * AgentInfinityWebSpec.centerBias
        val angle = AgentInfinityWebSpec.tilt * PI.toFloat() / 180f
        val ca = cos(angle)
        val sa = sin(angle)
        return PointF(
            width * 0.5f + x * ca - y * sa,
            height * 0.52f + x * sa + y * ca
        )
    }

    private fun pointAtDistance(
        points: List<PointF>,
        cumulative: FloatArray,
        count: Int,
        total: Float,
        distance: Float
    ): PointF {
        val target = ((distance % total) + total) % total
        var low = 0
        var high = count - 1
        var index = 0
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                cumulative[middle + 1] <= target -> low = middle + 1
                cumulative[middle] > target -> high = middle - 1
                else -> {
                    index = middle
                    break
                }
            }
        }
        if (low > high) index = min(low, count - 1)
        val span = cumulative[index + 1] - cumulative[index]
        val fraction = if (span > 0f) (target - cumulative[index]) / span else 0f
        val first = points[index]
        val second = points[index + 1]
        return PointF(
            first.x + (second.x - first.x) * fraction,
            first.y + (second.y - first.y) * fraction
        )
    }

    private fun drawRibbon(
        canvas: NativeCanvas,
        points: List<PointF>,
        base: Float,
        theme: AgentInfinityWebTheme
    ) {
        drawStroke(canvas, points, base * 4.2f, gradient(theme, 0.18f), theme.alpha * 0.28f, base * 1.28f * AgentInfinityWebSpec.glow, true)
        drawStroke(canvas, points, base * 2.5f, gradient(theme, 0.30f), theme.alpha * 0.45f, base * 0.52f * AgentInfinityWebSpec.glow, true)
        drawColorStroke(canvas, points, base * 1.46f, NativeColor.rgb(14, 23, 67), theme.alpha * 0.78f, 0f, false)
        drawStroke(canvas, points, base * 1.28f, gradient(theme, 0.95f), theme.alpha * 0.89f, base * 0.09f * AgentInfinityWebSpec.glow, true)
        drawStroke(canvas, points, base * 0.88f, gradient(theme, 0.94f), theme.alpha * 0.93f, base * 0.045f * AgentInfinityWebSpec.glow, true)
        drawColorStroke(canvas, points, base * 0.40f, NativeColor.rgb(229, 253, 255), theme.alpha * 0.61f, base * 0.065f, true)
        drawColorStroke(
            canvas, points, max(1f, base * 0.095f), NativeColor.rgb(73, 255, 247),
            0.40f * AgentInfinityWebSpec.dispersion * theme.alpha,
            base * 0.075f, true, -base * 0.36f * AgentInfinityWebSpec.dispersion
        )
        drawColorStroke(
            canvas, points, max(1f, base * 0.095f), NativeColor.rgb(242, 104, 255),
            0.32f * AgentInfinityWebSpec.dispersion * theme.alpha,
            base * 0.08f, true, base * 0.38f * AgentInfinityWebSpec.dispersion
        )
    }

    private fun drawCrossing(canvas: NativeCanvas, base: Float) {
        val centerX = width * 0.5f
        val centerY = height * 0.52f
        val radius = base * (0.88f + 0.42f * AgentInfinityWebSpec.crossingDepth)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(
                NativeColor.argb(25, 3, 7, 28),
                NativeColor.argb(13, 6, 12, 38),
                NativeColor.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.rotate(AgentInfinityWebSpec.tilt, centerX, centerY)
        canvas.scale(1f, 0.58f, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radius, paint)
        canvas.restore()
    }

    private fun drawSegment(
        canvas: NativeCanvas,
        geometry: AgentInfinityGeometry,
        headProgress: Float,
        lengthFraction: Float,
        strokeWidth: Float,
        theme: AgentInfinityWebTheme,
        intensity: Float
    ) {
        val count = geometry.uniform.size
        val headIndex = (((headProgress % 1f) + 1f) % 1f) * count
        val lengthSamples = max(8f, min(count * 0.58f, lengthFraction * count))
        val sampleCount = max(42, ceil(lengthFraction * 220f).toInt())
        val points = List(sampleCount + 1) { index ->
            val q = index / sampleCount.toFloat()
            uniformPoint(geometry.uniform, headIndex - lengthSamples + lengthSamples * q)
        }
        drawFaded(canvas, points, strokeWidth * 1.58f, gradient(theme, 1f), null, 0.56f * intensity, 4, 0.34f, max(2f, strokeWidth * 0.60f))
        drawFaded(canvas, points, strokeWidth * 0.88f, gradient(theme, 1f), null, 0.98f * intensity, 9, 0.34f, max(0.4f, strokeWidth * 0.12f))
        drawFaded(canvas, points, strokeWidth * 0.22f, null, NativeColor.WHITE, 0.82f * intensity, 9, 0.34f, max(0.2f, strokeWidth * 0.05f))
        val head = points.last()
        val radius = strokeWidth * 0.37f
        paint.reset()
        paint.isAntiAlias = true
        paint.xfermode = addMode
        paint.shader = RadialGradient(
            head.x,
            head.y,
            radius * 3f,
            intArrayOf(
                NativeColor.WHITE,
                themeColor(theme, headProgress, 0.95f),
                NativeColor.TRANSPARENT
            ),
            floatArrayOf(0f, 0.18f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(head.x, head.y, radius * 3f, paint)
        paint.xfermode = null
    }

    private fun drawFaded(
        canvas: NativeCanvas,
        points: List<PointF>,
        strokeWidth: Float,
        shader: Shader?,
        color: Int?,
        targetAlpha: Float,
        layers: Int,
        fadeFraction: Float,
        blur: Float
    ) {
        val fadeSamples = max(2, floor((points.size - 1) * fadeFraction).toInt())
        repeat(layers) { index ->
            val u0 = index / layers.toFloat()
            val u1 = (index + 1) / layers.toFloat()
            val s0 = u0 * u0 * (3f - 2f * u0)
            val s1 = u1 * u1 * (3f - 2f * u1)
            val start = min(points.size - 2, (fadeSamples * u0).roundToInt())
            val path = Path().apply {
                moveTo(points[start].x, points[start].y)
                for (pointIndex in start + 1 until points.size) {
                    lineTo(points[pointIndex].x, points[pointIndex].y)
                }
            }
            configurePaint(
                width = strokeWidth,
                shader = shader,
                color = color,
                alpha = targetAlpha * (s1 - s0),
                blur = blur,
                additive = true,
                cap = Paint.Cap.BUTT
            )
            canvas.drawPath(path, paint)
        }
    }

    private fun drawStar(
        canvas: NativeCanvas,
        theme: AgentInfinityWebTheme,
        strength: Float,
        timeSeconds: Float,
        phase: Float,
        base: Float
    ) {
        if (strength <= 0.012f) return
        val centerX = width * 0.5f
        val centerY = height * 0.52f
        val twinkle = max(
            0.05f,
            1f + AgentInfinityWebSpec.sparkTwinkle * (
                0.18f * sin(timeSeconds * 7.8f) +
                    0.07f * sin(timeSeconds * 15.6f + phase * PI.toFloat() * 2f)
                )
        )
        val spark = min(4f, strength * twinkle * AgentInfinityWebSpec.spark)
        val visible = min(1.6f, spark)
        val horizontalLength = base * 5.3f * AgentInfinityWebSpec.sparkH * (1f + 0.12f * min(spark, 2f))
        val verticalLength = base * 4f * AgentInfinityWebSpec.sparkV * (1f + 0.10f * min(spark, 2f))
        val haloRadius = base * (0.58f + 0.78f * min(spark, 2f)) * AgentInfinityWebSpec.sparkCore

        paint.reset()
        paint.isAntiAlias = true
        paint.xfermode = addMode
        paint.shader = RadialGradient(
            centerX,
            centerY,
            haloRadius * 3.2f,
            intArrayOf(
                NativeColor.argb((min(1f, 0.96f * visible) * 255f).roundToInt(), 255, 255, 255),
                NativeColor.argb((min(1f, 0.68f * visible) * 255f).roundToInt(), 225, 255, 255),
                themeColor(theme, 0.08f, min(1f, 0.34f * visible)),
                themeColor(theme, 0.72f, min(1f, 0.18f * visible)),
                NativeColor.TRANSPARENT
            ),
            floatArrayOf(0f, 0.12f, 0.34f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, haloRadius * 3.2f, paint)
        drawBeam(canvas, centerX, centerY, horizontalLength, base * 0.40f, false, theme, 0.52f * spark, base * 0.34f * AgentInfinityWebSpec.sparkSoft)
        drawBeam(canvas, centerX, centerY, verticalLength, base * 0.36f, true, theme, 0.50f * spark, base * 0.30f * AgentInfinityWebSpec.sparkSoft)
        drawBeam(canvas, centerX, centerY, horizontalLength * 0.82f, max(0.65f, base * 0.09f), false, theme, 1.16f * spark, max(0.08f, base * 0.018f * AgentInfinityWebSpec.sparkSoft))
        drawBeam(canvas, centerX, centerY, verticalLength * 0.80f, max(0.65f, base * 0.085f), true, theme, 1.10f * spark, max(0.08f, base * 0.018f * AgentInfinityWebSpec.sparkSoft))
        paint.xfermode = null
    }

    private fun drawBeam(
        canvas: NativeCanvas,
        centerX: Float,
        centerY: Float,
        length: Float,
        thickness: Float,
        vertical: Boolean,
        theme: AgentInfinityWebTheme,
        alpha: Float,
        blur: Float
    ) {
        if (alpha <= 0.002f) return
        val safeAlpha = alpha.coerceIn(0f, 1f)
        val startX = if (vertical) centerX else centerX - length / 2f
        val startY = if (vertical) centerY - length / 2f else centerY
        val endX = if (vertical) centerX else centerX + length / 2f
        val endY = if (vertical) centerY + length / 2f else centerY
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.xfermode = addMode
        paint.maskFilter = if (blur > 0f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
        paint.shader = LinearGradient(
            startX,
            startY,
            endX,
            endY,
            intArrayOf(
                NativeColor.TRANSPARENT,
                themeColor(theme, 0.05f, 0.06f * safeAlpha),
                NativeColor.argb((0.38f * safeAlpha * 255f).roundToInt(), 218, 255, 255),
                NativeColor.argb((safeAlpha * 255f).roundToInt(), 255, 255, 255),
                NativeColor.argb((0.46f * safeAlpha * 255f).roundToInt(), 255, 242, 255),
                themeColor(theme, 0.72f, 0.07f * safeAlpha),
                NativeColor.TRANSPARENT
            ),
            floatArrayOf(0f, 0.18f, 0.45f, 0.50f, 0.55f, 0.82f, 1f),
            Shader.TileMode.CLAMP
        )
        if (vertical) {
            canvas.drawRect(
                centerX - thickness / 2f,
                centerY - length / 2f,
                centerX + thickness / 2f,
                centerY + length / 2f,
                paint
            )
        } else {
            canvas.drawRect(
                centerX - length / 2f,
                centerY - thickness / 2f,
                centerX + length / 2f,
                centerY + thickness / 2f,
                paint
            )
        }
    }

    private fun drawStroke(
        canvas: NativeCanvas,
        points: List<PointF>,
        strokeWidth: Float,
        shader: Shader,
        alpha: Float,
        blur: Float,
        additive: Boolean,
        offset: Float = 0f
    ) {
        configurePaint(strokeWidth, shader, null, alpha, blur, additive, Paint.Cap.ROUND)
        canvas.drawPath(path(points, offset), paint)
    }

    private fun drawColorStroke(
        canvas: NativeCanvas,
        points: List<PointF>,
        strokeWidth: Float,
        color: Int,
        alpha: Float,
        blur: Float,
        additive: Boolean,
        offset: Float = 0f
    ) {
        configurePaint(strokeWidth, null, color, alpha, blur, additive, Paint.Cap.ROUND)
        canvas.drawPath(path(points, offset), paint)
    }

    private fun configurePaint(
        width: Float,
        shader: Shader?,
        color: Int?,
        alpha: Float,
        blur: Float,
        additive: Boolean,
        cap: Paint.Cap
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isDither = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = cap
        paint.strokeJoin = Paint.Join.ROUND
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        paint.shader = shader
        if (color != null) paint.color = color
        paint.maskFilter = if (blur > 0f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
        paint.xfermode = if (additive) addMode else null
    }

    private fun path(points: List<PointF>, offset: Float): Path {
        val result = Path()
        points.forEachIndexed { index, point ->
            var x = point.x
            var y = point.y
            if (offset != 0f && index > 0 && index < points.lastIndex) {
                val dx = points[index + 1].x - points[index - 1].x
                val dy = points[index + 1].y - points[index - 1].y
                val length = hypot(dx, dy).coerceAtLeast(0.0001f)
                x += -dy / length * offset
                y += dx / length * offset
            }
            if (index == 0) result.moveTo(x, y) else result.lineTo(x, y)
        }
        return result
    }

    private fun uniformPoint(points: List<PointF>, index: Float): PointF {
        val count = points.size
        val wrapped = ((index % count) + count) % count
        val firstIndex = floor(wrapped).toInt()
        val fraction = wrapped - firstIndex
        val first = points[firstIndex]
        val second = points[(firstIndex + 1) % count]
        return PointF(
            first.x + (second.x - first.x) * fraction,
            first.y + (second.y - first.y) * fraction
        )
    }

    private fun gradient(theme: AgentInfinityWebTheme, alpha: Float): Shader =
        LinearGradient(
            width * 0.13f,
            height * 0.18f,
            width * 0.88f,
            height * 0.82f,
            intArrayOf(
                mixColor(theme.a, theme.b, 0.08f, alpha),
                mixColor(theme.a, theme.b, 0.88f, alpha),
                mixColor(theme.b, theme.c, 0.72f, alpha),
                mixColor(theme.c, theme.a, 0.52f, alpha)
            ),
            floatArrayOf(0f, 0.42f, 0.73f, 1f),
            Shader.TileMode.CLAMP
        )

    private fun themeColor(theme: AgentInfinityWebTheme, progress: Float, alpha: Float): Int {
        val normalized = ((progress % 1f) + 1f) % 1f
        return if (normalized < 0.48f) {
            mixColor(theme.a, theme.b, normalized / 0.48f, alpha)
        } else {
            mixColor(theme.b, theme.c, (normalized - 0.48f) / 0.52f, alpha)
        }
    }

    private fun mixColor(first: Int, second: Int, amount: Float, alpha: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return NativeColor.argb(
            (alpha.coerceIn(0f, 1f) * 255f).roundToInt(),
            (NativeColor.red(first) + (NativeColor.red(second) - NativeColor.red(first)) * t).roundToInt(),
            (NativeColor.green(first) + (NativeColor.green(second) - NativeColor.green(first)) * t).roundToInt(),
            (NativeColor.blue(first) + (NativeColor.blue(second) - NativeColor.blue(first)) * t).roundToInt()
        )
    }

    private fun circularDistance(first: Float, second: Float): Float {
        val distance = abs(first - second)
        return min(distance, 1f - distance)
    }
}

@Composable
private fun AgentHeaderSwitchPill(
    label: String,
    enabled: Boolean,
    activeColors: List<Color>,
    onClick: () -> Unit
) {
    val active by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "$label-header-switch-active"
    )
    val knobOffset by animateFloatAsState(
        targetValue = if (enabled) 10f else 0f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "$label-header-switch-knob"
    )

    Row(
        modifier = Modifier
            .height(22.dp)
            .background(
                Brush.horizontalGradient(
                    if (enabled) {
                        activeColors
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.075f),
                            Color.White.copy(alpha = 0.030f)
                        )
                    }
                ),
                RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.58f + active * 0.38f),
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(23.dp)
                .height(13.dp)
                .background(
                    Color.Black.copy(alpha = 0.16f - active * 0.035f),
                    RoundedCornerShape(999.dp)
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (2f + knobOffset).dp)
                    .size(9.dp)
                    .graphicsLayer {
                        scaleX = 0.92f + active * 0.12f
                        scaleY = 0.92f + active * 0.12f
                    }
                    .background(
                        if (enabled) Color(0xFFF3FFFC) else Color(0xFF9EA8C5),
                        RoundedCornerShape(999.dp)
                    )
            )
        }
        Text(
            text = if (enabled) "开" else "关",
            color = Color.White.copy(alpha = 0.50f + active * 0.36f),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}
