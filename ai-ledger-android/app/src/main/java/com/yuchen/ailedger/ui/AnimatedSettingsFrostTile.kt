package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

private val SettingsCapsulePressEasing = CubicBezierEasing(0.18f, 0f, 0.10f, 1f)
private val SettingsCapsuleReleaseEasing = CubicBezierEasing(0.16f, 0f, 0.18f, 1f)

@Suppress("UNUSED_PARAMETER")
@Composable
fun AnimatedSettingsFrostTile(
    icon: String,
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val motionOn = state.quality.enableMotion && state.motionIntensity > 0.02f
    val seed = remember(title) { (title.sumOf { it.code } % 1000) / 1000f }
    val elapsedNanos = if (motionOn) LocalSettingsFrostMotionClock.current?.frameNanos ?: 0L else 0L
    val glintPhase = settingsTilePhase(seed, elapsedNanos, 5_600L)
    val breathPhase = settingsTilePhase(seed + 0.41f, elapsedNanos, 4_800L)
    val selectionAnim = remember { Animatable(if (selected) 1f else 0f) }
    val pressAnim = remember { Animatable(0f) }
    val releaseGlowAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var tileSize by remember { mutableStateOf(Size(1f, 1f)) }
    var pressCenter by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    LaunchedEffect(selected, motionOn) {
        val target = if (selected) 1f else 0f
        selectionAnim.stop()
        if (!motionOn) selectionAnim.snapTo(target)
        else selectionAnim.animateTo(
            target,
            spring(
                dampingRatio = if (selected) 0.74f else 0.88f,
                stiffness = if (selected) Spring.StiffnessMediumLow else Spring.StiffnessMedium,
            ),
        )
    }

    val selection = selectionAnim.value.coerceIn(0f, 1f)
    val press = pressAnim.value.coerceIn(0f, 1f)
    val releaseGlow = releaseGlowAnim.value.coerceIn(0f, 1f)
    val breath = if (motionOn && selection > 0.001f) {
        ((sin(breathPhase * (PI * 2.0).toFloat()) + 1f) * 0.5f).coerceIn(0f, 1f)
    } else 0.5f
    val radius = 23.5f
    val frostAlpha = 0.094f + selection * 0.030f + press * 0.018f
    val dimAlpha = 0.004f + press * 0.020f
    val parentLayer = LocalSettingsFrostParentLayer.current
    val useParentFrost = parentLayer != null && selection < 0.001f && press < 0.001f && releaseGlow < 0.001f
    val registeredLayer = parentLayer.takeIf { useParentFrost }
    val frostItemId = remember(title) { "settings-frost-$title" }
    SettingsFrostParentRegistrationCleanup(registeredLayer, frostItemId)

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .height(116.dp)
            .registerSettingsFrostParentItem(
                id = frostItemId,
                layerState = registeredLayer,
                radiusDp = radius,
                backdropAlpha = 1f,
                frostAlpha = frostAlpha,
                dimAlpha = dimAlpha,
            )
            .onSizeChanged {
                tileSize = Size(it.width.coerceAtLeast(1).toFloat(), it.height.coerceAtLeast(1).toFloat())
            }
            .graphicsLayer {
                val selectedLift = selection * (1f + breath * 0.35f)
                val selectedScale = 1f + selection * (0.008f + breath * 0.002f)
                val pressedScale = 1f - press * 0.018f
                scaleX = selectedScale * pressedScale
                scaleY = selectedScale * pressedScale
                translationY = -selectedLift + press * 1.8f
            }
            .pointerInput(motionOn, title) {
                awaitEachGesture {
                    fun updateCenter(position: Offset) {
                        pressCenter = Offset(
                            (position.x / tileSize.width.coerceAtLeast(1f)).coerceIn(0.05f, 0.95f),
                            (position.y / tileSize.height.coerceAtLeast(1f)).coerceIn(0.08f, 0.92f),
                        )
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateCenter(down.position)
                    scope.launch {
                        releaseGlowAnim.stop()
                        releaseGlowAnim.snapTo(0f)
                    }
                    scope.launch {
                        pressAnim.stop()
                        if (motionOn) pressAnim.animateTo(1f, tween(105, easing = SettingsCapsulePressEasing))
                        else pressAnim.snapTo(1f)
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            updateCenter(tracked.position)
                            if (!tracked.pressed) break
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    onClick()
                    scope.launch {
                        pressAnim.stop()
                        if (motionOn) pressAnim.animateTo(
                            0f,
                            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
                        ) else pressAnim.snapTo(0f)
                    }
                    if (motionOn) scope.launch {
                        releaseGlowAnim.stop()
                        releaseGlowAnim.snapTo(1f)
                        releaseGlowAnim.animateTo(0f, tween(620, easing = SettingsCapsuleReleaseEasing))
                    }
                }
            }
            .clip(RoundedCornerShape(radius.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (!useParentFrost) FrostInfoGlassPanel(
            radius = radius,
            backdropAlpha = 1f,
            frostAlpha = frostAlpha,
            dimAlpha = dimAlpha,
            modifier = Modifier.fillMaxSize(),
        ) {}
        SettingsTileCapsuleSurface(
            radius = radius,
            selection = selection,
            breath = breath,
            glintPhase = glintPhase,
            press = press,
            releaseGlow = releaseGlow,
            center = pressCenter,
            modifier = Modifier.fillMaxSize(),
        )
        SettingsTileContent(title, subtitle, value, selection, press, Modifier.fillMaxSize())
    }
}

private fun settingsTilePhase(initial: Float, elapsedNanos: Long, durationMillis: Long): Float {
    if (elapsedNanos <= 0L || durationMillis <= 0L) return initial
    val durationNanos = durationMillis * 1_000_000L
    return initial + ((elapsedNanos % durationNanos).toDouble() / durationNanos).toFloat()
}

@Composable
private fun SettingsTileContent(
    title: String,
    subtitle: String,
    value: String,
    selection: Float,
    press: Float,
    modifier: Modifier = Modifier,
) {
    val titleAlpha = (0.91f + selection * 0.09f).coerceIn(0f, 1f)
    val secondaryAlpha = (0.49f + selection * 0.18f + press * 0.04f).coerceIn(0f, 0.82f)
    val valueAlpha = (0.60f + selection * 0.30f + press * 0.05f).coerceIn(0f, 0.98f)
    Column(
        modifier = modifier.padding(horizontal = 17.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SettingsTileText(title, Color.White.copy(alpha = titleAlpha), 20.sp, 23.sp, FontWeight.Black)
            SettingsTileText(subtitle, Color.White.copy(alpha = secondaryAlpha), 11.5.sp, 15.sp, FontWeight.ExtraBold)
        }
        SettingsAnimatedTileHairline(Modifier.height(1.dp), 0.105f + selection * 0.125f + press * 0.025f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsTileText("当前", Color.White.copy(alpha = 0.34f + selection * 0.10f), 10.sp, weight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            SettingsTileText(
                value,
                Color.White.copy(alpha = valueAlpha),
                13.sp,
                16.sp,
                FontWeight.ExtraBold,
                align = TextAlign.End,
            )
        }
    }
}

@Composable
private fun SettingsTileText(
    text: String,
    color: Color,
    size: TextUnit,
    lineHeight: TextUnit = TextUnit.Unspecified,
    weight: FontWeight? = null,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        lineHeight = lineHeight,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

@Composable
private fun SettingsAnimatedTileHairline(modifier: Modifier = Modifier, alpha: Float = 0.12f) {
    Canvas(modifier) { drawRect(Color.White.copy(alpha = alpha.coerceIn(0f, 1f)), size = size, blendMode = BlendMode.Screen) }
}

@Composable
private fun SettingsTileCapsuleSurface(
    radius: Float,
    selection: Float,
    breath: Float,
    glintPhase: Float,
    press: Float,
    releaseGlow: Float,
    center: Offset,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val baseRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
        val easedSelection = FastOutSlowInEasing.transform(selection.coerceIn(0f, 1f))
        val active = maxOf(easedSelection, press * 0.72f, releaseGlow * 0.54f)
        drawRoundRect(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.050f + easedSelection * 0.035f),
                    Color.Transparent,
                    Color(0xFF050A1C).copy(alpha = 0.026f + press * 0.034f),
                ),
            ),
            size = Size(w, h),
            cornerRadius = baseRadius,
            blendMode = BlendMode.Screen,
        )
        if (active <= 0.001f) return@Canvas
        val insetX = mix(w * 0.18f, 2.7.dp.toPx(), easedSelection.coerceAtLeast(press * 0.70f))
        val insetY = mix(h * 0.25f, 2.7.dp.toPx(), easedSelection.coerceAtLeast(press * 0.70f))
        val capsuleWidth = (w - insetX * 2f).coerceAtLeast(1f)
        val capsuleHeight = (h - insetY * 2f).coerceAtLeast(1f)
        val capsuleRadius = CornerRadius(capsuleHeight * 0.5f, capsuleHeight * 0.5f)
        val capsuleTopLeft = Offset(insetX, insetY)
        val pulse = 0.82f + breath * 0.18f
        drawRoundRect(
            Brush.linearGradient(
                listOf(
                    Color(0xFF71FFF1).copy(alpha = 0.060f * active * pulse),
                    Color(0xFF6F9CFF).copy(alpha = 0.105f * active),
                    Color(0xFFB07DFF).copy(alpha = 0.080f * active),
                    Color(0xFF67F8EB).copy(alpha = 0.055f * active * pulse),
                ),
                Offset(insetX, insetY),
                Offset(w - insetX, h - insetY),
            ),
            capsuleTopLeft,
            Size(capsuleWidth, capsuleHeight),
            capsuleRadius,
            blendMode = BlendMode.Screen,
        )
        val touchX = center.x.coerceIn(0.05f, 0.95f) * w
        val touchY = center.y.coerceIn(0.08f, 0.92f) * h
        drawRoundRect(
            Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.120f * (press + releaseGlow * 0.65f)),
                    Color(0xFF7DFFF0).copy(alpha = 0.100f * (press + releaseGlow * 0.50f)),
                    Color.Transparent,
                ),
                Offset(touchX, touchY),
                maxOf(w, h) * (0.34f + releaseGlow * 0.10f),
            ),
            capsuleTopLeft,
            Size(capsuleWidth, capsuleHeight),
            capsuleRadius,
            blendMode = BlendMode.Screen,
        )
        if (easedSelection > 0.001f) {
            val phase = glintPhase - glintPhase.toInt()
            val glintCenter = mix(insetX - capsuleWidth * 0.18f, w - insetX + capsuleWidth * 0.18f, phase)
            val glintHalfWidth = capsuleWidth * 0.18f
            drawRoundRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.38f to Color.Transparent,
                    0.50f to Color.White.copy(alpha = 0.105f * easedSelection * pulse),
                    0.62f to Color.Transparent,
                    1f to Color.Transparent,
                    start = Offset(glintCenter - glintHalfWidth, insetY),
                    end = Offset(glintCenter + glintHalfWidth, h - insetY),
                ),
                capsuleTopLeft,
                Size(capsuleWidth, capsuleHeight),
                capsuleRadius,
                blendMode = BlendMode.Screen,
            )
        }
    }
}

private fun mix(start: Float, end: Float, progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return start + (end - start) * t
}
