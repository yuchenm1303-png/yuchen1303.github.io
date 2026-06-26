package com.yuchen.ailedger.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private enum class VisualHudPhase(val label: String, val index: Int) {
    Observing("观察", 0), Analyzing("分析", 1), Moving("移动", 2), Clicking("点击", 3),
    Verifying("验证", 4), Paused("暂停", 1), Completed("完成", 4),
}

internal class VisualAgentHudOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val scaledDensity = resources.displayMetrics.scaledDensity.coerceAtLeast(1f)
    private val easing = DecelerateInterpolator(1.55f)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }
    private val shaderMatrix = Matrix()

    private var progress = AgentOverlayProgress()
    private var target: VisualAgentHudTarget? = null
    private var hidden = false
    private var taskId = 0L
    private var targetRevision = 0L
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var moveAt = 0L
    private var terminalAt = 0L
    private var resultRevision = 0L
    private var resultPulseAt = 0L
    private var animating = false
    private var edgeShader: SweepGradient? = null
    private var shaderSize = 0L

    fun submit(next: AgentOverlayProgress, nextTarget: VisualAgentHudTarget?, hide: Boolean) {
        val now = System.currentTimeMillis()
        if (next.taskId != taskId) {
            taskId = next.taskId
            targetRevision = 0L
            terminalAt = 0L
            fromX = width * .52f
            fromY = height * .46f
            toX = fromX
            toY = fromY
        }
        progress = next
        hidden = hide
        target = nextTarget
        if (nextTarget != null && nextTarget.revision != targetRevision) {
            val current = cursor(now)
            fromX = current.first
            fromY = current.second
            resolveTarget(nextTarget).also { (x, y) -> toX = x; toY = y }
            moveAt = now
            targetRevision = nextTarget.revision
        }
        if (next.logs.lastOrNull()?.startsWith("结果：") == true &&
            next.updatedAt != resultRevision && nextTarget != null
        ) {
            resultRevision = next.updatedAt
            resultPulseAt = now
        }
        if (!next.running && next.taskId > 0L && terminalAt == 0L) terminalAt = now
        if (next.running) terminalAt = 0L
        syncAnimation(now)
    }

    fun stopAnimation() {
        animating = false
        removeCallbacks(frame)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        edgeShader = null
        shaderSize = 0L
        if (toX == 0f && toY == 0f) {
            fromX = w * .52f
            fromY = h * .46f
            toX = fromX
            toY = fromY
        }
        target?.let { resolveTarget(it).also { p -> toX = p.first; toY = p.second } }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hidden || width <= 0 || height <= 0) return
        val now = System.currentTimeMillis()
        val alpha = terminalAlpha(now)
        if (!progress.running && alpha <= 0f) {
            syncAnimation(now)
            return
        }
        val phase = phase(now)
        drawEdge(canvas, now, alpha)
        drawStatus(canvas, phase, alpha)
        val p = cursor(now)
        if (progress.running && target != null) {
            drawCursor(canvas, p.first, p.second, now, phase, alpha)
            drawCard(canvas, p.first, p.second, phase, alpha)
        }
        drawStages(canvas, phase, alpha)
        if (animating) postInvalidateOnAnimation()
    }

    private fun syncAnimation(now: Long) {
        val visibleNow = !hidden && (progress.running || progress.pendingConfirmation != null ||
            progress.pendingUserInput != null || (progress.taskId > 0L && terminalAlpha(now) > 0f))
        visibility = if (visibleNow) VISIBLE else INVISIBLE
        if (visibleNow && !animating) {
            animating = true
            removeCallbacks(frame)
            post(frame)
        } else if (!visibleNow) {
            stopAnimation()
        }
    }

    private val frame = object : Runnable {
        override fun run() {
            if (animating) invalidate()
        }
    }

    private fun terminalAlpha(now: Long): Float {
        if (progress.running || terminalAt == 0L) return 1f
        val elapsed = now - terminalAt
        return when {
            elapsed < 900L -> 1f
            elapsed < 1_420L -> 1f - (elapsed - 900L) / 520f
            else -> 0f
        }
    }

    private fun phase(now: Long): VisualHudPhase {
        if (progress.pendingConfirmation != null || progress.pendingUserInput != null || progress.userTakeoverPaused) {
            return VisualHudPhase.Paused
        }
        if (!progress.running) return VisualHudPhase.Completed
        val last = progress.logs.lastOrNull().orEmpty()
        if (last == progress.currentAction && progress.currentAction.isNotBlank()) {
            val movingToTap = target?.positioned == true &&
                target?.actionType in setOf("tap_xy", "tap_node") &&
                now - progress.updatedAt < MOVE_MS
            return if (movingToTap) VisualHudPhase.Moving else VisualHudPhase.Clicking
        }
        if (last.startsWith("结果：") || progress.status == "重新规划") return VisualHudPhase.Verifying
        if (last.startsWith("模型") || progress.lastResult.contains("分析") ||
            progress.lastResult.contains("GUI Plus") || progress.lastResult.contains("VisualDirect")
        ) {
            return VisualHudPhase.Analyzing
        }
        return VisualHudPhase.Observing
    }

    private fun resolveTarget(value: VisualAgentHudTarget): Pair<Float, Float> {
        val x = if (value.normalized) value.x.coerceIn(0f, 1f) * width else value.x
        val y = if (value.normalized) value.y.coerceIn(0f, 1f) * height else value.y
        return x.coerceIn(0f, width.toFloat()) to y.coerceIn(0f, height.toFloat())
    }

    private fun cursor(now: Long): Pair<Float, Float> {
        if (moveAt <= 0L) return toX to toY
        val t = easing.getInterpolation(((now - moveAt) / MOVE_MS.toFloat()).coerceIn(0f, 1f))
        return lerp(fromX, toX, t) to lerp(fromY, toY, t)
    }

    private fun drawEdge(canvas: Canvas, now: Long, alpha: Float) {
        val scale = width / DESIGN_W
        val halo = (42f * scale).coerceAtLeast(10f)
        val cast = (120f * scale).coerceAtLeast(26f)
        val flow = (now % 7_500L) / 7_500f
        val breathPhase = (now % 1_500L) / 1_500f
        val breath = .725f + ((sin(breathPhase * PI * 2).toFloat() + 1f) * .1375f)
        val shader = edgeShader()
        shaderMatrix.reset()
        shaderMatrix.setRotate(flow * 360f, width / 2f, height / 2f)
        shader.setLocalMatrix(shaderMatrix)
        edgePaint.shader = shader
        edgeLayers(canvas, cast, 18, .8f * WINDOW_ALPHA_COMPENSATION * breath * alpha, 2.35f)
        edgeLayers(canvas, halo, 12, .58f * WINDOW_ALPHA_COMPENSATION * breath * alpha, 1.35f)
        edgePaint.shader = null
    }

    private fun edgeLayers(canvas: Canvas, depth: Float, layers: Int, opacity: Float, power: Float) {
        val step = depth / layers
        repeat(layers) { i ->
            val t = i / layers.toFloat()
            val rectInset = i * step + step / 2f
            val rect = RectF(rectInset, rectInset, width - rectInset, height - rectInset)
            if (rect.width() <= 0f || rect.height() <= 0f) return
            edgePaint.strokeWidth = step + 1.2f
            edgePaint.alpha = (opacity * (1f - t).coerceAtLeast(0f).pow(power) * 255f)
                .toInt()
                .coerceIn(0, 255)
            canvas.drawRect(rect, edgePaint)
        }
    }

    private fun edgeShader(): SweepGradient {
        val key = (width.toLong() shl 32) or height.toLong()
        if (edgeShader == null || key != shaderSize) {
            shaderSize = key
            edgeShader = SweepGradient(
                width / 2f,
                height / 2f,
                intArrayOf(
                    Color.rgb(255, 57, 174),
                    Color.rgb(255, 116, 64),
                    Color.rgb(255, 229, 70),
                    Color.rgb(91, 239, 138),
                    Color.rgb(49, 224, 229),
                    Color.rgb(85, 132, 255),
                    Color.rgb(166, 82, 255),
                    Color.rgb(255, 57, 174),
                ),
                floatArrayOf(0f, .13f, .27f, .42f, .57f, .72f, .87f, 1f),
            )
        }
        return edgeShader!!
    }

    private fun drawStatus(canvas: Canvas, phase: VisualHudPhase, alpha: Float) {
        val label = when {
            progress.pendingConfirmation != null -> "等待你的确认"
            progress.pendingUserInput != null -> "等待你的输入"
            progress.userTakeoverPaused -> "用户接管中"
            !progress.running -> progress.status.ifBlank { "本次任务结束" }
            else -> "${phase.label} · ${progress.status.ifBlank { "运行中" }}"
        }
        bold.textSize = sp(11.5f)
        val w = bold.measureText(label) + dp(48f)
        val h = dp(36f)
        val top = max(dp(24f), height * .018f)
        val rect = RectF(width / 2f - w / 2f, top, width / 2f + w / 2f, top + h)
        fill.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                Color.argb((226 * alpha).toInt(), 7, 18, 32),
                Color.argb((214 * alpha).toInt(), 19, 31, 50),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, h / 2f, h / 2f, fill)
        fill.shader = null
        stroke.strokeWidth = dp(1f)
        stroke.color = Color.argb((76 * alpha).toInt(), 144, 229, 255)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, stroke)
        fill.color = accent(phase, (230 * alpha).toInt())
        canvas.drawCircle(rect.left + dp(18f), rect.centerY(), dp(3.5f), fill)
        bold.color = Color.argb((244 * alpha).toInt(), 242, 249, 255)
        canvas.drawText(label, rect.left + dp(29f), baseline(rect.centerY(), bold), bold)
    }

    private fun drawStages(canvas: Canvas, phase: VisualHudPhase, alpha: Float) {
        val labels = arrayOf("观察", "分析", "移动", "点击", "验证")
        val w = min(width * .82f, dp(360f))
        val h = dp(38f)
        val bottom = height - max(dp(22f), height * .018f)
        val rect = RectF(width / 2f - w / 2f, bottom - h, width / 2f + w / 2f, bottom)
        fill.color = Color.argb((205 * alpha).toInt(), 5, 17, 28)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, fill)
        stroke.strokeWidth = dp(1f)
        stroke.color = Color.argb((62 * alpha).toInt(), 119, 209, 239)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, stroke)
        text.textSize = sp(10.5f)
        val itemW = rect.width() / labels.size
        labels.forEachIndexed { i, label ->
            val cx = rect.left + itemW * (i + .5f)
            val active = i == phase.index
            val done = i < phase.index
            val color = when {
                active -> Color.argb((248 * alpha).toInt(), 238, 252, 255)
                done -> Color.argb((232 * alpha).toInt(), 91, 242, 186)
                else -> Color.argb((145 * alpha).toInt(), 155, 187, 202)
            }
            fill.color = color
            canvas.drawCircle(cx - dp(15f), rect.centerY(), if (active) dp(3.2f) else dp(2.4f), fill)
            text.color = color
            canvas.drawText(
                label,
                cx + dp(3f) - text.measureText(label) / 2f,
                baseline(rect.centerY(), text),
                text,
            )
        }
    }

    private fun drawCard(canvas: Canvas, x: Float, y: Float, phase: VisualHudPhase, alpha: Float) {
        val w = min(width * .72f, dp(320f))
        val h = dp(132f)
        val gap = dp(18f)
        var left = if (x + gap + w <= width - dp(12f)) x + gap else x - gap - w
        left = left.coerceIn(dp(12f), max(dp(12f), width - w - dp(12f)))
        val stageTop = height - max(dp(22f), height * .018f) - dp(46f)
        var top = if (y + gap + h <= stageTop) y + gap else y - gap - h
        top = top.coerceIn(dp(72f), max(dp(72f), stageTop - h))
        val rect = RectF(left, top, left + w, top + h)
        fill.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                Color.argb((235 * alpha).toInt(), 5, 18, 31),
                Color.argb((225 * alpha).toInt(), 8, 30, 43),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, dp(18f), dp(18f), fill)
        fill.shader = null
        stroke.strokeWidth = dp(1f)
        stroke.color = Color.argb((82 * alpha).toInt(), 102, 220, 255)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), stroke)
        val pad = dp(14f)
        bold.textSize = sp(11.8f)
        bold.color = Color.argb((250 * alpha).toInt(), 239, 250, 255)
        canvas.drawText(headline(phase), rect.left + pad, rect.top + dp(25f), bold)
        text.textSize = sp(10.2f)
        text.color = Color.argb((205 * alpha).toInt(), 182, 221, 237)
        canvas.drawText(
            progress.currentAction.ifBlank { "正在执行视觉任务" }.oneLine().cut(44),
            rect.left + pad,
            rect.top + dp(49f),
            text,
        )
        text.color = Color.argb((232 * alpha).toInt(), 221, 240, 248)
        val targetLabel = target?.targetText
            ?.takeIf(String::isNotBlank)
            ?.let { "目标：${it.oneLine().cut(34)}" }
            ?: "目标：当前页面"
        canvas.drawText(targetLabel, rect.left + pad, rect.top + dp(72f), text)
        text.color = Color.argb((180 * alpha).toInt(), 160, 202, 220)
        val detail = when {
            target?.detail?.isNotBlank() == true -> target!!.detail
            progress.lastResult.isNotBlank() -> progress.lastResult
            else -> "正在根据页面证据选择下一步操作"
        }.oneLine().cut(54)
        canvas.drawText(detail, rect.left + pad, rect.top + dp(96f), text)
        text.color = accent(phase, (220 * alpha).toInt())
        canvas.drawText(
            progress.lastResult.takeIf(String::isNotBlank)?.oneLine()?.cut(48) ?: "等待动作结果",
            rect.left + pad,
            rect.top + dp(119f),
            text,
        )
    }

    private fun drawCursor(
        canvas: Canvas,
        x: Float,
        y: Float,
        now: Long,
        phase: VisualHudPhase,
        alpha: Float,
    ) {
        val scale = max(width / DESIGN_W, .64f)
        val left = x - 10f * scale - .5f * scale
        val top = y - 10.5f * scale * .95f - .5f * scale
        val path = cursorPath(scale)
        path.transform(Matrix().apply {
            postRotate(-2.5f, 10f * scale, 10.5f * scale * .95f)
            postTranslate(left, top)
        })
        stroke.strokeWidth = max(1f, 7f * scale)
        stroke.color = Color.argb((48 * alpha).toInt(), 87, 225, 255)
        canvas.drawPath(path, stroke)
        stroke.strokeWidth = max(1f, 3.2f * scale)
        stroke.color = Color.argb((44 * alpha).toInt(), 255, 88, 201)
        canvas.drawPath(path, stroke)
        val save = canvas.save()
        canvas.clipPath(path)
        fill.color = Color.argb((238 * alpha).toInt(), 8, 17, 33)
        canvas.drawRect(left - 8f, top - 8f, left + 80f * scale, top + 80f * scale, fill)
        radial(canvas, left, top, scale, 33.7f, 16f, 23f, Color.rgb(63, 226, 255), .92f * alpha)
        radial(canvas, left, top, scale, 29f, 28f, 18f, Color.WHITE, .86f * alpha)
        radial(canvas, left, top, scale, 30f, 50f, 18f, Color.rgb(255, 65, 190), .72f * alpha)
        radial(canvas, left, top, scale, 28.2f, 28.1f, 11.8f, Color.WHITE, .09f * alpha)
        canvas.restoreToCount(save)
        stroke.strokeWidth = max(1f, 1.02f * scale)
        stroke.color = Color.argb((242 * alpha).toInt(), 213, 252, 255)
        canvas.drawPath(path, stroke)
        stroke.strokeWidth = max(.8f, .42f * scale)
        stroke.color = Color.argb((220 * alpha).toInt(), 255, 255, 255)
        canvas.drawPath(path, stroke)
        val residual = now - resultPulseAt
        if (phase == VisualHudPhase.Clicking || residual in 0L until 620L) {
            val elapsed = if (phase == VisualHudPhase.Clicking) {
                (now - progress.updatedAt - MOVE_MS).coerceAtLeast(0L)
            } else {
                residual
            }
            val t = (elapsed % 620L) / 620f
            stroke.strokeWidth = dp(1.5f)
            stroke.color = Color.argb(((1f - t) * 175f * alpha).toInt(), 164, 242, 255)
            canvas.drawCircle(x, y, dp(8f) + dp(22f) * t, stroke)
            fill.color = Color.argb(((1f - t) * 90f * alpha).toInt(), 255, 255, 255)
            canvas.drawCircle(x, y, dp(3.2f) + dp(2f) * (1f - t), fill)
        }
    }

    private fun cursorPath(scale: Float) = Path().apply {
        moveTo(7.8f * scale, 7.8f * scale * .95f)
        cubicTo(
            8.91f * scale, 5.71f * scale * .95f,
            13.83f * scale, 7.17f * scale * .95f,
            20.4f * scale, 10.4f * scale * .95f,
        )
        cubicTo(
            26.97f * scale, 13.63f * scale * .95f,
            49.02f * scale, 25.34f * scale * .95f,
            51.1f * scale, 29.1f * scale * .95f,
        )
        cubicTo(
            53.18f * scale, 32.86f * scale * .95f,
            37.42f * scale, 31.38f * scale * .95f,
            34.1f * scale, 35.2f * scale * .95f,
        )
        cubicTo(
            30.78f * scale, 39.02f * scale * .95f,
            30.93f * scale, 52.78f * scale * .95f,
            29.2f * scale, 54.3f * scale * .95f,
        )
        cubicTo(
            27.47f * scale, 55.82f * scale * .95f,
            25.14f * scale, 49.77f * scale * .95f,
            22.7f * scale, 45.2f * scale * .95f,
        )
        cubicTo(
            20.26f * scale, 40.63f * scale * .95f,
            15.36f * scale, 29.87f * scale * .95f,
            13.1f * scale, 24.2f * scale * .95f,
        )
        cubicTo(
            10.84f * scale, 18.53f * scale * .95f,
            6.69f * scale, 9.89f * scale * .95f,
            7.8f * scale, 7.8f * scale * .95f,
        )
        close()
    }

    private fun radial(
        canvas: Canvas,
        left: Float,
        top: Float,
        scale: Float,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        opacity: Float,
    ) {
        fill.shader = RadialGradient(
            left + cx * scale,
            top + cy * scale * .95f,
            max(1f, radius * scale),
            Color.argb(
                (opacity.coerceIn(0f, 1f) * 255).toInt(),
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            ),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(left + cx * scale, top + cy * scale * .95f, radius * scale, fill)
        fill.shader = null
    }

    private fun headline(phase: VisualHudPhase): String = when (phase) {
        VisualHudPhase.Observing -> "正在观察当前页面"
        VisualHudPhase.Analyzing -> "正在分析页面证据"
        VisualHudPhase.Moving -> "正在移动到目标位置"
        VisualHudPhase.Clicking -> if (target?.actionType in setOf("tap_xy", "tap_node")) {
            "正在执行点击操作"
        } else {
            "正在执行当前操作"
        }
        VisualHudPhase.Verifying -> "正在验证执行结果"
        VisualHudPhase.Paused -> "等待用户继续"
        VisualHudPhase.Completed -> "本次动作已结束"
    }

    private fun accent(phase: VisualHudPhase, alpha: Int): Int = when (phase) {
        VisualHudPhase.Paused -> Color.argb(alpha, 255, 213, 116)
        VisualHudPhase.Completed -> Color.argb(alpha, 111, 244, 186)
        VisualHudPhase.Clicking -> Color.argb(alpha, 255, 225, 248)
        else -> Color.argb(alpha, 103, 232, 255)
    }

    private fun baseline(centerY: Float, paint: Paint): Float =
        centerY - (paint.ascent() + paint.descent()) / 2f

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount
    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * scaledDensity
    private fun String.oneLine(): String = trim().replace(Regex("\\s+"), " ")
    private fun String.cut(limit: Int): String =
        if (length <= limit) this else take((limit - 1).coerceAtLeast(1)).trimEnd() + "…"

    companion object {
        private const val DESIGN_W = 1600f
        private const val MOVE_MS = 90L
        private const val WINDOW_ALPHA_COMPENSATION = 1.25f
    }
}
