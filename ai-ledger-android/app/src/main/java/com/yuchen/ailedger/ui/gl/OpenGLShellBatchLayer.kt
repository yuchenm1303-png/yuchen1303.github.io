package com.yuchen.ailedger.ui.gl

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.GlassCoordinateSource
import com.yuchen.ailedger.ui.LocalBackdropFrameTicker
import com.yuchen.ailedger.ui.LocalBackdropOrigin
import com.yuchen.ailedger.ui.LocalBlurredBackdrop
import com.yuchen.ailedger.ui.LocalGlassBackdrop
import com.yuchen.ailedger.ui.OpenGLFrameFinalizer
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs

private const val BATCH_ITEM_INTENSITY_EPSILON = 0.004f

/**
 * 单张 Shell 在共享 OpenGL 宿主中的稳定描述。
 *
 * 坐标保存为宿主局部坐标。设置页宿主会与卡片一起滚动；功能页宿主固定在页面坐标系，
 * LazyColumn 内的卡片独立移动。两种结构都由同一套最终帧快照处理。
 */
@Stable
internal class OpenGLShellBatchItem(
    val id: Any,
    val radiusDp: Int,
    val dynamicState: OpenGLGlassDynamicState,
    baseIntensity: Float,
) {
    private var latestCoordinates: LayoutCoordinates? = null
    private var parentCoordinates: GlassCoordinateSource? = null
    private val geometryListeners = CopyOnWriteArraySet<() -> Unit>()
    private val propertyListeners = CopyOnWriteArraySet<() -> Unit>()

    internal var localLeft = 0f
        private set
    internal var localTop = 0f
        private set
    internal var width = 0f
        private set
    internal var height = 0f
        private set
    internal var attached = false
        private set
    internal var baseIntensity = baseIntensity
        private set

    internal fun bindParent(source: GlassCoordinateSource?) {
        if (parentCoordinates === source) return
        parentCoordinates = source
        refreshPlacementNow(notify = true)
    }

    internal fun updatePlacement(coordinates: LayoutCoordinates) {
        latestCoordinates = coordinates
        refreshPlacementNow(notify = true)
    }

    internal fun refreshPlacementNow(notify: Boolean = false) {
        val item = latestCoordinates
        val parent = parentCoordinates?.coordinates
        val nextAttached = item?.isAttached == true && parent?.isAttached == true
        if (!nextAttached) {
            if (attached) {
                attached = false
                width = 0f
                height = 0f
                if (notify) notifyGeometryChanged()
            }
            return
        }

        val local = parent!!.localPositionOf(item!!, Offset.Zero)
        val nextWidth = item.size.width.toFloat()
        val nextHeight = item.size.height.toFloat()
        val changed =
            !attached ||
                abs(localLeft - local.x) > BATCH_FRAME_EPSILON_PX ||
                abs(localTop - local.y) > BATCH_FRAME_EPSILON_PX ||
                abs(width - nextWidth) > BATCH_FRAME_EPSILON_PX ||
                abs(height - nextHeight) > BATCH_FRAME_EPSILON_PX

        attached = true
        localLeft = local.x
        localTop = local.y
        width = nextWidth
        height = nextHeight
        if (changed && notify) notifyGeometryChanged()
    }

    internal fun updateBaseIntensity(value: Float) {
        val safe = value.coerceIn(0.35f, 1.35f)
        if (abs(baseIntensity - safe) <= BATCH_ITEM_INTENSITY_EPSILON) return
        baseIntensity = safe
        propertyListeners.forEach { it() }
    }

    internal fun addGeometryListener(listener: () -> Unit): () -> Unit {
        geometryListeners += listener
        return { geometryListeners -= listener }
    }

    internal fun addPropertyListener(listener: () -> Unit): () -> Unit {
        propertyListeners += listener
        return { propertyListeners -= listener }
    }

    private fun notifyGeometryChanged() {
        if (OpenGLFrameFinalizer.requestActiveTickerFrame()) return
        geometryListeners.forEach { it() }
    }
}

@Stable
internal class OpenGLShellBatchState {
    private val entries = LinkedHashMap<Any, OpenGLShellBatchItem>()
    private var cachedSnapshot: List<OpenGLShellBatchItem> = emptyList()
    private val versionState = mutableLongStateOf(0L)
    private var parentCoordinates: GlassCoordinateSource? = null

    internal fun bindParent(source: GlassCoordinateSource) {
        if (parentCoordinates === source) return
        parentCoordinates = source
        entries.values.forEach { it.bindParent(source) }
    }

    fun register(item: OpenGLShellBatchItem) {
        if (entries[item.id] === item) return
        entries[item.id] = item
        item.bindParent(parentCoordinates)
        rebuildSnapshot()
    }

    fun remove(id: Any) {
        if (entries.remove(id) != null) rebuildSnapshot()
    }

    fun clear() {
        if (entries.isEmpty()) return
        entries.clear()
        cachedSnapshot = emptyList()
        bumpVersion()
    }

    fun snapshot(): List<OpenGLShellBatchItem> {
        versionState.longValue
        return cachedSnapshot
    }

    private fun rebuildSnapshot() {
        cachedSnapshot = entries.values.take(BATCH_RENDER_LIMIT)
        bumpVersion()
    }

    private fun bumpVersion() {
        versionState.longValue =
            if (versionState.longValue == Long.MAX_VALUE) 1L else versionState.longValue + 1L
    }
}

internal val LocalOpenGLShellBatchState =
    staticCompositionLocalOf<OpenGLShellBatchState?> { null }

@Composable
internal fun rememberOpenGLShellBatchState(): OpenGLShellBatchState =
    remember { OpenGLShellBatchState() }

/**
 * 页面级唯一 TextureView / EGL / Shader / 纹理宿主。
 *
 * 多卡滚动会在同一 PreDraw 快照中读取全部最终矩形，清理旧、新矩形的联合损伤区后重绘；
 * 单卡按压只更新该卡。每张卡仍按自己的短边计算光学尺度，混合尺寸不会改变视觉。
 */
@Composable
internal fun NewOpenGLGlassBatchLayer(
    state: OpenGLShellBatchState,
    parentCoordinates: GlassCoordinateSource,
    modifier: Modifier = Modifier,
) {
    val items = state.snapshot()
    val backdrop = LocalBlurredBackdrop.current ?: return
    if (!backdrop.isReady || items.isEmpty()) return

    val baseBorder = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val styleOverride = LocalNewOpenGlGlassStyleOverride.current
    val border = remember(baseBorder, styleOverride) {
        styleOverride?.invoke(baseBorder) ?: baseBorder
    }
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val density = LocalDensity.current
    val densityScale = density.density.coerceAtLeast(0.001f)
    val clearBitmap = remember(backdrop.lensImage) { backdrop.lensImage.asAndroidBitmap() }
    val blurLowBitmap = remember(backdrop.blurLowImage) { backdrop.blurLowImage.asAndroidBitmap() }
    val blurMediumBitmap = remember(backdrop.blurMediumImage) { backdrop.blurMediumImage.asAndroidBitmap() }
    val blurHighBitmap = remember(backdrop.blurHighImage) { backdrop.blurHighImage.asAndroidBitmap() }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { SmartOpenGLGlassBatchHostView(it) },
            update = { view ->
                view.bindSources(items, parentCoordinates, backdropOrigin, frameTicker)
                view.setParentSpec(
                    width = widthPx,
                    height = heightPx,
                    rootWidth = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f),
                    rootHeight = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f),
                    densityScale = densityScale,
                    borderStyle = border,
                )
                view.setBackdropTextures(
                    clearBitmap,
                    blurLowBitmap,
                    blurMediumBitmap,
                    blurHighBitmap,
                )
                view.setBackdropBlurAmount(backdrop.blurAmount)
            },
        )
    }
}
