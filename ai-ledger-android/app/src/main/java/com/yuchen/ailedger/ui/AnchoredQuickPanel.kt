package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal enum class AnchoredQuickPanelPlacement {
    Above,
    Below,
}

@Immutable
internal data class AnchoredQuickPanelLayout(
    val compact: Boolean,
    val placement: AnchoredQuickPanelPlacement,
    val tailHeight: Dp,
)

@Composable
internal fun AnchoredQuickPanel(
    visible: Boolean,
    anchorBounds: Rect,
    desiredWidth: Dp,
    desiredHeight: Dp,
    minHeight: Dp,
    preferredPlacement: AnchoredQuickPanelPlacement,
    horizontalBias: Float,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 25.dp,
    tailHeight: Dp = 12.dp,
    tailHalfWidth: Dp = 15.dp,
    safeMargin: Dp = 10.dp,
    anchorGap: Dp = 7.dp,
    surfaceColor: Color = Color.Transparent,
    precomposeWhenHidden: Boolean = false,
    content: @Composable (AnchoredQuickPanelLayout) -> Unit,
) {
    if (!visible && !precomposeWhenHidden) return

    BackHandler(enabled = visible, onBack = onDismiss)

    val density = LocalDensity.current
    val revealX = remember { Animatable(1f) }
    val revealY = remember { Animatable(1f) }
    val revealAlpha = remember { Animatable(0f) }
    val revealLift = remember { Animatable(0f) }
    val panelPress = remember { Animatable(0f) }
    val pressScope = rememberCoroutineScope()
    val outsideInteraction = remember { MutableInteractionSource() }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(visible) {
        if (!visible) {
            revealX.snapTo(1f)
            revealY.snapTo(1f)
            revealAlpha.snapTo(0f)
            revealLift.snapTo(0f)
            panelPress.snapTo(0f)
            return@LaunchedEffect
        }

        revealX.snapTo(0.42f)
        revealY.snapTo(0.12f)
        revealAlpha.snapTo(0f)
        revealLift.snapTo(18f)
        panelPress.snapTo(0f)
        coroutineScope {
            launch { revealX.animateTo(1f, spring(0.50f, Spring.StiffnessMediumLow)) }
            launch { revealY.animateTo(1f, spring(0.56f, Spring.StiffnessMediumLow)) }
            launch { revealAlpha.animateTo(1f, tween(92, easing = FastOutSlowInEasing)) }
            launch { revealLift.animateTo(0f, spring(0.52f, Spring.StiffnessMediumLow)) }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBounds = it.boundsInWindow() },
    ) {
        val safePx = with(density) { safeMargin.roundToPx() }
        val gapPx = with(density) { anchorGap.roundToPx() }
        val desiredWidthPx = with(density) { desiredWidth.roundToPx() }
        val desiredHeightPx = with(density) { desiredHeight.roundToPx() }
        val minimumHeightPx = with(density) { minHeight.roundToPx() }
        val compactThresholdPx = with(density) { 20.dp.roundToPx() }

        val panelWidthPx = desiredWidthPx.coerceAtMost(
            (constraints.maxWidth - safePx * 2).coerceAtLeast(1),
        )
        val panelWidth = with(density) { panelWidthPx.toDp() }

        val anchorIsValid = anchorBounds.width > 1f &&
            anchorBounds.height > 1f &&
            rootBounds.width > 1f
        val localAnchorTop = if (anchorIsValid) {
            anchorBounds.top - rootBounds.top
        } else {
            constraints.maxHeight * 0.34f
        }
        val localAnchorBottom = if (anchorIsValid) {
            anchorBounds.bottom - rootBounds.top
        } else {
            constraints.maxHeight * 0.39f
        }
        val localAnchorCenterX = if (anchorIsValid) {
            ((anchorBounds.left + anchorBounds.right) * 0.5f - rootBounds.left).roundToInt()
        } else {
            (constraints.maxWidth * 0.78f).roundToInt()
        }

        val availableAbovePx = (localAnchorTop.roundToInt() - gapPx - safePx).coerceAtLeast(1)
        val availableBelowPx = (constraints.maxHeight - localAnchorBottom.roundToInt() - gapPx - safePx).coerceAtLeast(1)

        val placement = if (preferredPlacement == AnchoredQuickPanelPlacement.Above) {
            AnchoredQuickPanelPlacement.Above
        } else {
            when {
                availableBelowPx >= desiredHeightPx -> AnchoredQuickPanelPlacement.Below
                availableBelowPx >= minimumHeightPx -> AnchoredQuickPanelPlacement.Below
                availableAbovePx >= desiredHeightPx -> AnchoredQuickPanelPlacement.Above
                availableAbovePx >= minimumHeightPx -> AnchoredQuickPanelPlacement.Above
                availableBelowPx >= availableAbovePx -> AnchoredQuickPanelPlacement.Below
                else -> AnchoredQuickPanelPlacement.Above
            }
        }

        val availableHeightPx = when (placement) {
            AnchoredQuickPanelPlacement.Above -> availableAbovePx
            AnchoredQuickPanelPlacement.Below -> availableBelowPx
        }
        val panelHeightPx = desiredHeightPx
            .coerceAtMost(availableHeightPx)
            .coerceAtLeast(minOf(minimumHeightPx, availableHeightPx))
        val panelHeight = with(density) { panelHeightPx.toDp() }
        val compact = panelHeightPx < desiredHeightPx - compactThresholdPx

        val desiredX = localAnchorCenterX - (panelWidthPx * horizontalBias).roundToInt()
        val panelX = desiredX.coerceIn(
            safePx,
            (constraints.maxWidth - panelWidthPx - safePx).coerceAtLeast(safePx),
        )
        val panelY = when (placement) {
            AnchoredQuickPanelPlacement.Above ->
                (localAnchorTop.roundToInt() - gapPx - panelHeightPx).coerceAtLeast(safePx)
            AnchoredQuickPanelPlacement.Below ->
                (localAnchorBottom.roundToInt() + gapPx).coerceAtMost(
                    (constraints.maxHeight - panelHeightPx - safePx).coerceAtLeast(safePx),
                )
        }
        val tailFraction = ((localAnchorCenterX - panelX).toFloat() / panelWidthPx.coerceAtLeast(1)).coerceIn(0.16f, 0.84f)
        val panelShape = remember(panelWidthPx, panelHeightPx, tailFraction, placement, cornerRadius, tailHeight, tailHalfWidth) {
            AnchoredQuickPanelShape(cornerRadius, tailHeight, tailHalfWidth, tailFraction, placement)
        }

        if (visible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = outsideInteraction, indication = null, onClick = onDismiss),
            )
        }

        val renderedX = if (visible) panelX else -panelWidthPx - safePx
        val renderedY = if (visible) panelY else -panelHeightPx - safePx
        val useSharedGlassShell = surfaceColor.alpha < 0.72f
        val layout = AnchoredQuickPanelLayout(compact = compact, placement = placement, tailHeight = tailHeight)
        val shellRadius = cornerRadius.value.roundToInt()

        Box(
            modifier = Modifier
                .offset { IntOffset(renderedX, renderedY) }
                .width(panelWidth)
                .height(panelHeight)
                .graphicsLayer {
                    val press = panelPress.value
                    val compression = press.coerceAtLeast(0f)
                    val rebound = (-press).coerceAtLeast(0f)
                    alpha = if (visible) revealAlpha.value else 0f
                    scaleX = revealX.value * (1f + compression * 0.020f - rebound * 0.010f)
                    scaleY = revealY.value * (1f - compression * 0.034f + rebound * 0.020f)
                    val direction = if (placement == AnchoredQuickPanelPlacement.Above) 1f else -1f
                    translationY = if (visible) {
                        revealLift.value.dp.toPx() * direction +
                            compression * 3.2.dp.toPx() * direction -
                            rebound * 1.4.dp.toPx() * direction
                    } else {
                        0f
                    }
                    transformOrigin = TransformOrigin(tailFraction, if (placement == AnchoredQuickPanelPlacement.Above) 1f else 0f)
                }
                .shadow(
                    elevation = 10.dp,
                    shape = panelShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.30f),
                    spotColor = Color(0xFF8DFFF4).copy(alpha = 0.08f),
                )
                .clip(panelShape)
                .pointerInput(visible, panelShape) {
                    if (!visible) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressScope.launch {
                            panelPress.stop()
                            if (panelPress.value < 0.18f) panelPress.snapTo(0.18f)
                            panelPress.animateTo(1f, tween(145, easing = FastOutSlowInEasing))
                            panelPress.animateTo(0.82f, spring(0.64f, Spring.StiffnessMediumLow))
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                        pressScope.launch {
                            panelPress.stop()
                            panelPress.animateTo(-0.18f, tween(120, easing = FastOutSlowInEasing))
                            panelPress.animateTo(0.055f, spring(0.44f, Spring.StiffnessMediumLow))
                            panelPress.animateTo(0f, spring(0.72f, Spring.StiffnessLow))
                        }
                    }
                },
        ) {
            if (useSharedGlassShell) {
                GlassSceneScope(group = GlassSceneGroup.Unassigned) {
                    PressableGlass(
                        quality = quality,
                        glassIntensity = glassIntensity,
                        motionIntensity = 0f,
                        radius = shellRadius,
                        modifier = Modifier.fillMaxSize(),
                        role = GlassRole.Floating,
                        onClick = {},
                    ) {
                        Box(Modifier.fillMaxSize().background(surfaceColor)) {
                            content(layout)
                        }
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .glassSkin(
                            quality = quality,
                            radius = shellRadius,
                            shimmer = 0.16f,
                            breathe = 0.34f,
                            glassIntensity = glassIntensity,
                            role = GlassRole.Floating,
                            includeShadow = false,
                        )
                ) {
                    Box(Modifier.fillMaxSize().background(surfaceColor)) {
                        content(layout)
                    }
                }
            }
        }
    }
}

private class AnchoredQuickPanelShape(
    private val cornerRadius: Dp,
    private val tailHeight: Dp,
    private val tailHalfWidth: Dp,
    private val tailCenterFraction: Float,
    private val placement: AnchoredQuickPanelPlacement,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = with(density) { cornerRadius.toPx() }.coerceAtMost(minOf(size.width, size.height) * 0.30f)
        val tailH = with(density) { tailHeight.toPx() }.coerceIn(0f, size.height * 0.22f)
        val halfTail = with(density) { tailHalfWidth.toPx() }.coerceAtMost(size.width * 0.16f)
        val tailCenter = (size.width * tailCenterFraction.coerceIn(0.16f, 0.84f)).coerceIn(radius + halfTail, size.width - radius - halfTail)

        val path = if (placement == AnchoredQuickPanelPlacement.Above) {
            val bodyBottom = (size.height - tailH).coerceAtLeast(radius * 2f)
            Path().apply {
                moveTo(radius, 0f)
                lineTo(size.width - radius, 0f)
                quadraticBezierTo(size.width, 0f, size.width, radius)
                lineTo(size.width, bodyBottom - radius)
                quadraticBezierTo(size.width, bodyBottom, size.width - radius, bodyBottom)
                lineTo(tailCenter + halfTail, bodyBottom)
                cubicTo(tailCenter + halfTail * 0.56f, bodyBottom + tailH * 0.08f, tailCenter + halfTail * 0.28f, bodyBottom + tailH * 0.72f, tailCenter, size.height)
                cubicTo(tailCenter - halfTail * 0.28f, bodyBottom + tailH * 0.72f, tailCenter - halfTail * 0.56f, bodyBottom + tailH * 0.08f, tailCenter - halfTail, bodyBottom)
                lineTo(radius, bodyBottom)
                quadraticBezierTo(0f, bodyBottom, 0f, bodyBottom - radius)
                lineTo(0f, radius)
                quadraticBezierTo(0f, 0f, radius, 0f)
                close()
            }
        } else {
            val bodyTop = tailH.coerceAtMost(size.height - radius * 2f)
            Path().apply {
                moveTo(radius, bodyTop)
                lineTo(tailCenter - halfTail, bodyTop)
                cubicTo(tailCenter - halfTail * 0.56f, bodyTop - tailH * 0.08f, tailCenter - halfTail * 0.28f, bodyTop - tailH * 0.72f, tailCenter, 0f)
                cubicTo(tailCenter + halfTail * 0.28f, bodyTop - tailH * 0.72f, tailCenter + halfTail * 0.56f, bodyTop - tailH * 0.08f, tailCenter + halfTail, bodyTop)
                lineTo(size.width - radius, bodyTop)
                quadraticBezierTo(size.width, bodyTop, size.width, bodyTop + radius)
                lineTo(size.width, size.height - radius)
                quadraticBezierTo(size.width, size.height, size.width - radius, size.height)
                lineTo(radius, size.height)
                quadraticBezierTo(0f, size.height, 0f, size.height - radius)
                lineTo(0f, bodyTop + radius)
                quadraticBezierTo(0f, bodyTop, radius, bodyTop)
                close()
            }
        }
        return Outline.Generic(path)
    }
}
