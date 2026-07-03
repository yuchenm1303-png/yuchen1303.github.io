package com.yuchen.ailedger.ui.gl

import android.content.Context
import android.widget.FrameLayout
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.BackdropCoordinateSource
import com.yuchen.ailedger.ui.BackdropFrameTicker
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.OpenGLFrameFinalizer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.min

internal class SmartOpenGLGlassBatchHostView(context: Context) : FrameLayout(context) {
    private val textureView = SmartOpenGLGlassBatchTextureView(context)
    private val packet = UnifiedGlassBatchPacket()
    private val nextValues = FloatArray(BATCH_RENDER_LIMIT * BATCH_RENDER_FRAME_FLOATS)

    private var items: List<OpenGLShellBatchItem> = emptyList()
    private var parentCoordinates: GlassCoordinateSource? = null
    private var backdropOrigin: BackdropCoordinateSource? = null
    private var frameTicker: BackdropFrameTicker? = null

    private var parentWidth = 1f
    private var parentHeight = 1f
    private var rootWidth = 1f
    private var rootHeight = 1f
    private var densityScale = 1f
    private var borderStyle = GlassBorderStyle()

    private var previousActiveMask = 0
    private var rootSamplingDirty = true
    private var fullDrawRequested = true
    private var clearAllRequested = true
    private var renderPosted = false

    private var removeParent: (() -> Unit)? = null
    private var removeBackdrop: (() -> Unit)? = null
    private var removeTicker: (() -> Unit)? = null
    private val removeItemListeners = CopyOnWriteArrayList<() -> Unit>()

    private val renderRunnable = Runnable {
        renderPosted = false
        if (!isAttachedToWindow) return@Runnable
        if (syncPacket()) textureView.requestRender()
    }

    init {
        clipChildren = true
        clipToPadding = true
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bindSources(
        items: List<OpenGLShellBatchItem>,
        parentCoordinates: GlassCoordinateSource,
        backdropOrigin: BackdropCoordinateSource?,
        frameTicker: BackdropFrameTicker?,
    ) {
        if (
            this.items == items &&
            this.parentCoordinates === parentCoordinates &&
            this.backdropOrigin === backdropOrigin &&
            this.frameTicker === frameTicker
        ) return

        uninstallSubscriptions()
        this.items = items.take(BATCH_RENDER_LIMIT)
        this.parentCoordinates = parentCoordinates
        this.backdropOrigin = backdropOrigin
        this.frameTicker = frameTicker
        previousActiveMask = 0
        rootSamplingDirty = true
        fullDrawRequested = true
        clearAllRequested = true
        if (isAttachedToWindow) installSubscriptions()
        requestRenderOnNextAnimationFrame()
    }

    fun setParentSpec(
        width: Float,
        height: Float,
        rootWidth: Float,
        rootHeight: Float,
        densityScale: Float,
        borderStyle: GlassBorderStyle,
    ) {
        val surfaceChanged =
            abs(parentWidth - width) > BATCH_FRAME_EPSILON_PX ||
                abs(parentHeight - height) > BATCH_FRAME_EPSILON_PX
        val rootChanged =
            abs(this.rootWidth - rootWidth) > BATCH_FRAME_EPSILON_PX ||
                abs(this.rootHeight - rootHeight) > BATCH_FRAME_EPSILON_PX
        val styleChanged =
            abs(this.densityScale - densityScale) > 0.0001f ||
                this.borderStyle != borderStyle

        parentWidth = width.coerceAtLeast(1f)
        parentHeight = height.coerceAtLeast(1f)
        this.rootWidth = rootWidth.coerceAtLeast(1f)
        this.rootHeight = rootHeight.coerceAtLeast(1f)
        this.densityScale = densityScale.coerceAtLeast(0.001f)
        this.borderStyle = borderStyle

        if (surfaceChanged || rootChanged) {
            rootSamplingDirty = true
            clearAllRequested = true
        }
        if (surfaceChanged || rootChanged || styleChanged) {
            fullDrawRequested = true
            requestRenderOnNextAnimationFrame()
        }
    }

    fun setBackdropTextures(
        clear: BatchPlatformBitmap,
        low: BatchPlatformBitmap,
        medium: BatchPlatformBitmap,
        high: BatchPlatformBitmap,
    ) {
        if (textureView.setBackdropTextures(clear, low, medium, high)) {
            fullDrawRequested = true
            clearAllRequested = true
            requestRenderOnNextAnimationFrame()
        }
    }

    fun setBackdropBlurAmount(amount: Float) {
        if (textureView.setBackdropBlurAmount(amount)) {
            fullDrawRequested = true
            requestRenderOnNextAnimationFrame()
        }
    }

    fun requestRenderOnNextAnimationFrame() {
        if (OpenGLFrameFinalizer.isDispatchingFrame) {
            if (renderPosted) {
                removeCallbacks(renderRunnable)
                renderPosted = false
            }
            if (isAttachedToWindow && syncPacket()) textureView.requestRender()
            return
        }
        if (renderPosted) return
        renderPosted = true
        postOnAnimation(renderRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        installSubscriptions()
        requestRenderOnNextAnimationFrame()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(renderRunnable)
        renderPosted = false
        uninstallSubscriptions()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        textureView.layout(0, 0, right - left, bottom - top)
        if (changed) {
            rootSamplingDirty = true
            clearAllRequested = true
            fullDrawRequested = true
        }
        requestRenderOnNextAnimationFrame()
    }

    private fun installSubscriptions() {
        if (
            removeParent != null ||
            removeBackdrop != null ||
            removeTicker != null ||
            removeItemListeners.isNotEmpty()
        ) return

        removeParent = parentCoordinates?.addPlacementListener(::requestRootSamplingFrame)
        removeBackdrop = backdropOrigin?.addPlacementListener(::requestRootSamplingFrame)
        removeTicker = frameTicker?.addFrameListener(::requestRootSamplingFrame)
        items.forEach { item ->
            removeItemListeners += item.addGeometryListener(::requestRenderOnNextAnimationFrame)
            removeItemListeners += item.addPropertyListener(::requestRenderOnNextAnimationFrame)
            removeItemListeners += item.dynamicState.addFrameListener(::requestRenderOnNextAnimationFrame)
        }
    }

    private fun requestRootSamplingFrame() {
        rootSamplingDirty = true
        fullDrawRequested = true
        requestRenderOnNextAnimationFrame()
    }

    private fun uninstallSubscriptions() {
        removeParent?.invoke()
        removeBackdrop?.invoke()
        removeTicker?.invoke()
        removeParent = null
        removeBackdrop = null
        removeTicker = null
        removeItemListeners.forEach { it.invoke() }
        removeItemListeners.clear()
    }

    private fun syncPacket(): Boolean {
        val forceRootSampling = rootSamplingDirty
        val parentRoot = parentCoordinates?.rootOffsetNow() ?: androidx.compose.ui.geometry.Offset.Zero
        val backdropRoot = backdropOrigin?.rootOffsetNow() ?: androidx.compose.ui.geometry.Offset.Zero
        val density = densityScale.coerceAtLeast(0.001f)

        nextValues.fill(0f)
        var activeMask = 0
        var geometryMask = 0
        var originMask = 0
        var propertyMask = 0

        for (index in 0 until BATCH_RENDER_LIMIT) {
            if (index >= items.size) continue
            val item = items[index]
            item.refreshPlacementNow(notify = false)
            if (!item.attached || item.width <= 1f || item.height <= 1f) continue

            val bit = 1 shl index
            val dynamic = item.dynamicState.latestSnapshot()
            val centerX = dynamic.pressCenter.x.coerceIn(0f, 1f)
            val centerY = dynamic.pressCenter.y.coerceIn(0f, 1f)
            val scaleX = 1f + dynamic.pressCompression * 0.014f - dynamic.pressRebound * 0.004f
            val scaleY = 1f - dynamic.pressCompression * 0.022f + dynamic.pressRebound * 0.008f
            val translationY = dynamic.pressCompression * 2.10f - dynamic.pressRebound * 0.80f
            val transformedWidth = (item.width * scaleX).coerceAtLeast(1f)
            val transformedHeight = (item.height * scaleY).coerceAtLeast(1f)
            val transformedLeft = item.localLeft + (1f - scaleX) * centerX * item.width
            val transformedTop = item.localTop + (1f - scaleY) * centerY * item.height + translationY
            val globalLeft = parentRoot.x + transformedLeft
            val globalTop = parentRoot.y + transformedTop
            val globallyVisible =
                globalLeft + transformedWidth > 0f &&
                    globalLeft < rootWidth &&
                    globalTop + transformedHeight > 0f &&
                    globalTop < rootHeight
            if (globallyVisible) activeMask = activeMask or bit

            val base = index * BATCH_RENDER_FRAME_FLOATS
            nextValues[base + BATCH_FRAME_LEFT] = transformedLeft
            nextValues[base + BATCH_FRAME_TOP] = transformedTop
            nextValues[base + BATCH_FRAME_WIDTH] = transformedWidth
            nextValues[base + BATCH_FRAME_HEIGHT] = transformedHeight
            nextValues[base + BATCH_FRAME_RADIUS] = item.radiusDp * density * min(scaleX, scaleY)
            nextValues[base + BATCH_FRAME_INTENSITY] =
                (item.baseIntensity * dynamic.glassIntensityScale).coerceIn(0.35f, 1.35f)
            nextValues[base + BATCH_FRAME_ORIGIN_X] = globalLeft - backdropRoot.x
            nextValues[base + BATCH_FRAME_ORIGIN_Y] = globalTop - backdropRoot.y
            nextValues[base + BATCH_FRAME_PRESS] = dynamic.openGlPress.coerceIn(0f, 1f)
            nextValues[base + BATCH_FRAME_PRESS_X] = centerX
            nextValues[base + BATCH_FRAME_PRESS_Y] = centerY
            nextValues[base + BATCH_FRAME_OPTICAL_SCALE] =
                (min(item.width, item.height) / density / BATCH_REFERENCE_SHORT_EDGE_DP)
                    .coerceIn(BATCH_MINIMUM_OPTICAL_SCALE, 1f)

            if (frameChanged(base, GEOMETRY_FIELDS)) geometryMask = geometryMask or bit
            if (frameChanged(base, ORIGIN_FIELDS)) originMask = originMask or bit
            if (frameChanged(base, PROPERTY_FIELDS)) propertyMask = propertyMask or bit
        }

        val activeChangedMask = activeMask xor previousActiveMask
        var dirtyMask = geometryMask or originMask or propertyMask or activeChangedMask
        if (forceRootSampling) dirtyMask = dirtyMask or activeMask or previousActiveMask
        val clearMask = geometryMask or activeChangedMask
        val fullDraw = fullDrawRequested || forceRootSampling || Integer.bitCount(dirtyMask) > 1
        val clearAll = clearAllRequested

        if (!fullDraw && !clearAll && dirtyMask == 0 && clearMask == 0) return false

        System.arraycopy(nextValues, 0, packet.values, 0, nextValues.size)
        packet.activeMask = activeMask
        packet.dirtyMask = dirtyMask
        packet.clearMask = clearMask
        packet.fullDraw = fullDraw
        packet.clearAll = clearAll
        packet.rootWidth = rootWidth
        packet.rootHeight = rootHeight
        packet.densityScale = density
        packet.style = borderStyle
        packet.generation = if (packet.generation == Long.MAX_VALUE) 1L else packet.generation + 1L

        textureView.setPacket(packet)
        previousActiveMask = activeMask
        rootSamplingDirty = false
        fullDrawRequested = false
        clearAllRequested = false
        return true
    }

    private fun frameChanged(base: Int, fields: IntArray): Boolean {
        for (field in fields) {
            val epsilon = if (field == BATCH_FRAME_INTENSITY) 0.004f else BATCH_VALUE_EPSILON
            if (abs(packet.values[base + field] - nextValues[base + field]) > epsilon) return true
        }
        return false
    }

    companion object {
        private val GEOMETRY_FIELDS = intArrayOf(
            BATCH_FRAME_LEFT,
            BATCH_FRAME_TOP,
            BATCH_FRAME_WIDTH,
            BATCH_FRAME_HEIGHT,
            BATCH_FRAME_RADIUS,
        )
        private val ORIGIN_FIELDS = intArrayOf(BATCH_FRAME_ORIGIN_X, BATCH_FRAME_ORIGIN_Y)
        private val PROPERTY_FIELDS = intArrayOf(
            BATCH_FRAME_INTENSITY,
            BATCH_FRAME_PRESS,
            BATCH_FRAME_PRESS_X,
            BATCH_FRAME_PRESS_Y,
            BATCH_FRAME_OPTICAL_SCALE,
        )
    }
}
