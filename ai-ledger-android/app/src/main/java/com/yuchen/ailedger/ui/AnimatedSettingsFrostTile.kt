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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

private val SettingsCapsulePressEasing = CubicBezierEasing(0.18f, 0.00f, 0.10f, 1.00f)
private val SettingsCapsuleReleaseEasing = CubicBezierEasing(0.16f, 0.00f, 0.18f, 1.00f)

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
    onClick: () -> Unit
) {
    val motionOn = state.quality.enableMotion && state.motionIntensity > 0.02f
    val seed = remember(title) {
        (title.sumOf { it.code } % 1000) / 1000f
    }
    val sharedClock = LocalSettingsFrostMotionClock.current
    val elapsedNanos = if (motionOn) sharedClock?.frameNanos ?: 0L else 0L
    val glintPhase = settingsTilePhase(seed, elapsedNanos, 5_600L)
    val breathPhase = settingsTilePhase(seed + 0.41f, elapsedNanos, 4_800L)

    val selectionAnim = remember { Animatable(if (selected) 1f else 0f) }
    val pressAnim = remember { Animatable(0f) }
    val releaseGlowAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var tileSize by remember { mutableStateOf(Size(1f, 1f)) }
    var pressCenter by remember { mutableStateOf(Offset(0.50f, 0.50f)) }

    LaunchedEffect(selected, motionOn) {
        val target = if (selected) 1f else 0f
        selectionAnim.stop()
        if (!motionOn) {
            selectionAnim.snapTo(target)
        } else {
            selectionAnim.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = if (selected) 0.74f else 0.88f,
                    stiffness = if (selected) Spring.StiffnessMediumLow else Spring.StiffnessMedium
                )
            )
        }
    }

    val selection = selectionAnim.value.coerceIn(0f, 1f)
    val press = pressAnim.value.coerceIn(0f, 1f)
    val releaseGlow = releaseGlowAnim.value.coerceIn(0f, 1f)
    val tau = (PI * 2.0).toFloat()
    val breath = if (motionOn && selection > 0.001f) {
        ((sin(breathPhase * tau) + 1f) * 0.5f).coerceIn(0f, 1f)
    } else {
        0.5f
    }
    val radius = 23.5f
    val frostAlpha = 0.094f + selection * 0.030f + press * 0.018f
    val dimAlpha = 0.004f + press * 0.020f
    val parentLayer = LocalSettingsFrostParentLayer.current
    val useParentFrost = parentLayer != null &&
        selection < 0.001f &&
        press < 0.001f &&
        releaseGlow < 0.001f
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
                dimAlpha = dimAlpha
            )
            .onSizeChanged {
                tileSize = Size(
                    it.width.coerceAtLeast(1).toFloat(),
                    it.height.coerceAtLeast(1).toFloat()
                )
            }
            .graphicsLayer {
                val selectedLift = selection * (1.0f + breath * 0.35f)
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
                            x = (position.x / tileSize.width.coerceAtLeast(1f)).coerceIn(0.05f, 0.95f),
                            y = (position.y / tileSize.height.coerceAtLeast(1f)).coerceIn(0.08f, 0.92f)
                        )
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateCenter(down.position)
                    scope.launch {
                        releaseGlowAnim.stop()
                        releaseGlowAnim.snapTo(0f)
                    }
                    if (motionOn) {
                        scope.launch {
                            pressAnim.stop()
                            pressAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(105, easing = SettingsCapsulePressEasing)
                            )
                        }
                    } else {
                        scope.launch { pressAnim.snapTo(1f) }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            updateCenter(tracked.position)
                            if (!tracked.pressed) break
                        }
                        if (event.changes.none { it.pressed }) break
                    }

                    onClick()
                    if (motionOn) {
                        scope.launch {
                            pressAnim.stop()
                            pressAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.72f,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                        scope.launch {
                            releaseGlowAnim.stop()
                            releaseGlowAnim.snapTo(1f)
                            releaseGlowAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(620, easing = SettingsCapsuleReleaseEasing)
                            )
                        }
                    } else {
                        scope.launch { pressAnim.snapTo(0f) }
                    }
                }
            }
            .clip(RoundedCornerShape(radius.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!useParentFrost) {
            FrostInfoGlassPanel(
                radius = radius,
                backdropAlpha = 1f,
                frostAlpha = frostAlpha,
                dimAlpha = dimAlpha,
                modifier = Modifier.fillMaxSize()
            ) {}
        }
        SettingsTileCapsuleSurface(
            radius = radius,
            selection = selection,
            breath = breath,
            glintPhase = glintPhase,
            press = press,
            releaseGlow = releaseGlow,
            center = pressCenter,
            modifier = Modifier.fillMaxSize()
        )
        SettingsTileContent(
            title = title,
            subtitle = subtitle,
            value = value,
            selection = selection,
            press = press,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun settingsTilePhase(
    initial: Float,
    elapsedNanos: Long,
    durationMillis: Long
): Float {
    if (elapsedNanos <= 0L || durationMillis <= 0L) return initial
    val durationNanos = durationMillis * 1_000_000L
    val fraction = (elapsedNanos % durationNanos).toDouble() / durationNanos.toDouble()
    return initial + fraction.toFloat()
}

@Composable
private fun SettingsTileContent(
    title: String,
    subtitle: String,
    value: String,
    selection: Float,
    press: Float,
    modifier: Modifier = Modifier
) {
    val titleAlpha = (0.91f + selection * 0.09f).coerceIn(0f, 1f)
    val secondaryAlpha = (0.49f + selection * 0.18f + press * 0.04f).coerceIn(0f, 0.82f)
    val valueAlpha = (0.60f + selection * 0.30f + press * 0.05f).coerceIn(0f, 0.98f)

    Column(
        modifier = modifier.padding(horizontal = 17.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                color = Color.White.copy(alpha = titleAlpha),
                fontSize = 20.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = secondaryAlpha),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        SettingsAnimatedTileHairline(
            modifier = Modifier.height(1.dp),
            alpha = 0.105f + selection * 0.125f + press * 0.025f
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "当前",
                color = Color.White.copy(alpha = 0.34f + selection * 0.10f),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                color = Color.White.copy(alpha = valueAlpha),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun SettingsAnimatedTileHairline(
    modifier: Modifier = Modifier,
    alpha: Float = 0.12f
) {
    Canvas(modifier = modifier) {
        drawRect(
            color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
            size = size,
            blendMode = BlendMode.Screen
        )
    }
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val radiusPx = radius.dp.toPx()
        val baseRadius = CornerRadius(radiusPx, radiusPx)
        val easedSelection = FastOutSlowInEasing.transform(selection.coerceIn(0f, 1f))
        val active = maxOf(easedSelection, press * 0.72f, releaseGlow * 0.54f)

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.050f + easedSelection * 0.035f),
                    Color.Transparent,
                    Color(0xFF050A1C).copy(alpha = 0.026f + press * 0.034f)
                )
            ),
            size = Size(w, h),
            cornerRadius = baseRadius,
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.105f + easedSelection * 0.070f),
                    Color(0xFF8DF9EA).copy(alpha = 0.030f + easedSelection * 0.055f),
                    Color.Transparent,
                    Color(0xFFA99BFF).copy(alpha = 0.028f + easedSelection * 0.048f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset(0.85.dp.toPx(), 0.85.dp.toPx()),
            size = Size(
                (w - 1.70.dp.toPx()).coerceAtLeast(1f),
                (h - 1.70.dp.toPx()).coerceAtLeast(1f)
            ),
            cornerRadius = CornerRadius(
                (radiusPx - 0.85.dp.toPx()).coerceAtLeast(0f),
                (radiusPx - 0.85.dp.toPx()).coerceAtLeast(0f)
            ),
            style = Stroke(width = 0.72.dp.toPx() + easedSelection * 0.22.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        if (active <= 0.001f) return@Canvas

        val startInsetX = w * 0.18f
        val startInsetY = h * 0.25f
        val endInsetX = 2.7.dp.toPx()
        val endInsetY = 2.7.dp.toPx()
        val capsuleInsetX = mix(startInsetX, endInsetX, easedSelection.coerceAtLeast(press * 0.70f))
        val capsuleInsetY = mix(startInsetY, endInsetY, easedSelection.coerceAtLeast(press * 0.70f))
        val capsuleWidth = (w - capsuleInsetX * 2f).coerceAtLeast(1f)
        val capsuleHeight = (h - capsuleInsetY * 2f).coerceAtLeast(1f)
        val capsuleRadius = CornerRadius(capsuleHeight * 0.5f, capsuleHeight * 0.5f)
        val capsuleTopLeft = Offset(capsuleInsetX, capsuleInsetY)
        val pulse = 0.82f + breath * 0.18f

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF71FFF1).copy(alpha = 0.060f * active * pulse),
                    Color(0xFF6F9CFF).copy(alpha = 0.105f * active),
                    Color(0xFFB07DFF).copy(alpha = 0.080f * active),
                    Color(0xFF67F8EB).copy(alpha = 0.055f * active * pulse)
                ),
                start = Offset(capsuleInsetX, capsuleInsetY),
                end = Offset(w - capsuleInsetX, h - capsuleInsetY)
            ),
            topLeft = capsuleTopLeft,
            size = Size(capsuleWidth, capsuleHeight),
            cornerRadius = capsuleRadius,
            blendMode = BlendMode.Screen
        )

        val touchX = center.x.coerceIn(0.05f, 0.95f) * w
        val touchY = center.y.coerceIn(0.08f, 0.92f) * h
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.120f * (press + releaseGlow * 0.65f)),
                    Color(0xFF7DFFF0).copy(alpha = 0.100f * (press + releaseGlow * 0.50f)),
                    Color.Transparent
                ),
                center = Offset(touchX, touchY),
                radius = maxOf(w, h) * (0.34f + releaseGlow * 0.10f)
            ),
            topLeft = capsuleTopLeft,
            size = Size(capsuleWidth, capsuleHeight),
            cornerRadius = capsuleRadius,
            blendMode = BlendMode.Screen
        )

        if (easedSelection > 0.001f) {
            val phase = glintPhase - glintPhase.toInt()
            val glintCenter = mix(capsuleInsetX - capsuleWidth * 0.18f, w - capsuleInsetX + capsuleWidth * 0.18f, phase)
            val glintHalfWidth = capsuleWidth * 0.18f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.38f to Color.Transparent,
                        0.50f to Color.White.copy(alpha = 0.105f * easedSelection * pulse),
                        0.62f to Color.Transparent,
                        1.00f to Color.Transparent
                    ),
                    start = Offset(glintCenter - glintHalfWidth, capsuleInsetY),
                    end = Offset(glintCenter + glintHalfWidth, h - capsuleInsetY)
                ),
                topLeft = capsuleTopLeft,
                size = Size(capsuleWidth, capsuleHeight),
                cornerRadius = capsuleRadius,
                blendMode = BlendMode.Screen
            )
        }

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.245f * active * pulse),
                    Color(0xFF72FFF0).copy(alpha = 0.160f * active),
                    Color(0xFF7D9CFF).copy(alpha = 0.120f * active),
                    Color(0xFFD895FF).copy(alpha = 0.145f * active),
                    Color.White.copy(alpha = 0.110f * active)
                ),
                start = Offset(capsuleInsetX, capsuleInsetY),
                end = Offset(w - capsuleInsetX, h - capsuleInsetY)
            ),
            topLeft = capsuleTopLeft,
            size = Size(capsuleWidth, capsuleHeight),
            cornerRadius = capsuleRadius,
            style = Stroke(width = 1.08.dp.toPx() + active * 0.44.dp.toPx()),
            blendMode = BlendMode.Plus
        )

        drawRoundRect(
            color = Color(0xFF84FDEE).copy(alpha = 0.050f * active * pulse),
            topLeft = Offset(capsuleInsetX - 1.7.dp.toPx(), capsuleInsetY - 1.7.dp.toPx()),
            size = Size(
                (capsuleWidth + 3.4.dp.toPx()).coerceAtLeast(1f),
                (capsuleHeight + 3.4.dp.toPx()).coerceAtLeast(1f)
            ),
            cornerRadius = CornerRadius(
                (capsuleHeight + 3.4.dp.toPx()) * 0.5f,
                (capsuleHeight + 3.4.dp.toPx()) * 0.5f
            ),
            style = Stroke(width = 3.2.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
}

private fun mix(start: Float, end: Float, progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return start + (end - start) * t
}
