package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.TransformOrigin
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
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private val SettingsTilePressEasing = CubicBezierEasing(0.12f, 0.00f, 0.08f, 1.00f)
private val SettingsTileReleaseEasing = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)

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
    val seed = remember(title, icon) {
        ((title.sumOf { it.code } + icon.sumOf { it.code } * 7) % 1000) / 1000f
    }
    val sharedClock = LocalSettingsFrostMotionClock.current
    val elapsedNanos = if (motionOn) sharedClock?.frameNanos ?: 0L else 0L
    val phaseA = settingsTilePhase(seed, elapsedNanos, if (selected) 4_300L else 8_200L)
    val phaseB = settingsTilePhase(seed + 0.37f, elapsedNanos, if (selected) 6_400L else 11_600L)
    val phaseC = settingsTilePhase(seed + 0.71f, elapsedNanos, if (selected) 5_100L else 10_100L)
    val pressAnim = remember { Animatable(0f) }
    val afterglowAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var tileSize by remember { mutableStateOf(Size(1f, 1f)) }
    var pressCenter by remember { mutableStateOf(Offset(0.50f, 0.48f)) }

    val pressValue = pressAnim.value.coerceIn(-0.18f, 1.10f)
    val press = pressValue.coerceAtLeast(0f)
    val recoil = (-pressValue).coerceAtLeast(0f)
    val afterglow = afterglowAnim.value.coerceIn(0f, 1f)
    val selectedBase = if (selected) 1f else 0f
    val tA = if (motionOn) phaseA else seed
    val tB = if (motionOn) phaseB else seed + 0.37f
    val tC = if (motionOn) phaseC else seed + 0.71f
    val tau = (PI * 2.0).toFloat()
    val selectedPulse = if (selected && motionOn) {
        ((sin(tA * tau) + 1f) * 0.5f).coerceIn(0f, 1f)
    } else {
        selectedBase * 0.50f
    }
    val slowBreath = if (selected && motionOn) {
        ((sin(tC * tau + 0.8f) + 1f) * 0.5f).coerceIn(0f, 1f)
    } else {
        selectedBase * 0.50f
    }
    val energy = (
        selectedBase * (0.88f + selectedPulse * 0.42f) +
            press * 0.90f +
            afterglow * 0.55f
        ).coerceIn(0f, 1.70f)
    val radius = 17.44f
    val frostAlpha = 0.095f + energy * 0.040f
    val dimAlpha = 0.004f + press * 0.018f
    val parentLayer = LocalSettingsFrostParentLayer.current
    val useParentFrost = parentLayer != null &&
        !selected &&
        press < 0.001f &&
        recoil < 0.001f &&
        afterglow < 0.001f
    val registeredLayer = parentLayer.takeIf { useParentFrost }
    val frostItemId = remember(title, icon) { "settings-frost-$title-$icon" }

    SettingsFrostParentRegistrationCleanup(registeredLayer, frostItemId)

    Box(
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
                transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
                scaleX = 1f + selectedBase * 0.018f +
                    slowBreath * selectedBase * 0.014f +
                    press * 0.026f -
                    recoil * 0.008f
                scaleY = 1f + selectedBase * 0.006f +
                    slowBreath * selectedBase * 0.006f -
                    press * 0.036f +
                    recoil * 0.014f
                translationY = selectedBase * (-0.8f - slowBreath * 0.9f) +
                    press * 3.2f -
                    recoil * 1.3f
                translationX = (pressCenter.x - 0.5f) * press * 4.2f
            }
            .pointerInput(motionOn, selected, title) {
                awaitEachGesture {
                    fun updateCenter(position: Offset) {
                        pressCenter = Offset(
                            x = (position.x / tileSize.width.coerceAtLeast(1f)).coerceIn(0.06f, 0.94f),
                            y = (position.y / tileSize.height.coerceAtLeast(1f)).coerceIn(0.08f, 0.92f)
                        )
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateCenter(down.position)
                    scope.launch {
                        afterglowAnim.stop()
                        afterglowAnim.snapTo(0f)
                    }
                    if (motionOn) {
                        scope.launch {
                            pressAnim.stop()
                            if (pressAnim.value < 0.18f) pressAnim.snapTo(0.18f)
                            pressAnim.animateTo(1.00f, tween(150, easing = SettingsTilePressEasing))
                            pressAnim.animateTo(
                                0.78f,
                                spring(
                                    dampingRatio = 0.66f,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                )
                            )
                        }
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
                            pressAnim.animateTo(-0.10f, tween(130, easing = SettingsTileReleaseEasing))
                            pressAnim.animateTo(
                                0.030f,
                                spring(
                                    dampingRatio = 0.48f,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                )
                            )
                            pressAnim.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                        }
                        scope.launch {
                            afterglowAnim.stop()
                            afterglowAnim.snapTo(0.88f)
                            afterglowAnim.animateTo(0f, tween(860, easing = FastOutSlowInEasing))
                        }
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
        SettingsTilePrismSurface(
            radius = radius,
            selected = selected,
            energy = energy,
            selectedPulse = selectedPulse,
            slowBreath = slowBreath,
            press = press,
            recoil = recoil,
            afterglow = afterglow,
            center = pressCenter,
            phaseA = tA,
            phaseB = tB,
            phaseC = tC,
            seed = seed,
            modifier = Modifier.fillMaxSize()
        )
        SettingsTileContent(
            icon = icon,
            title = title,
            subtitle = subtitle,
            value = value,
            selected = selected,
            energy = energy,
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
    icon: String,
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val iconAlpha = (if (selected) 0.84f else 0.58f) + energy * 0.14f
    val titleAlpha = (0.92f + energy * 0.08f).coerceIn(0f, 1f)
    val secondaryAlpha = ((if (selected) 0.66f else 0.48f) + energy * 0.10f)
        .coerceIn(0f, 0.86f)
    val valueAlpha = ((if (selected) 0.84f else 0.58f) + energy * 0.12f)
        .coerceIn(0f, 0.98f)

    Column(
        modifier.padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Text(
                    icon,
                    color = Color.White.copy(alpha = iconAlpha.coerceIn(0f, 1f)),
                    fontSize = if (icon.length > 1) 14.sp else 21.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
            SettingsAnimatedTileHairline(
                Modifier.size(1.dp, 42.dp),
                alpha = if (selected) 0.30f + energy * 0.10f else 0.14f + energy * 0.06f
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    title,
                    color = Color.White.copy(alpha = titleAlpha),
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = secondaryAlpha),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        SettingsAnimatedTileHairline(
            Modifier.height(1.dp),
            alpha = if (selected) 0.24f + energy * 0.08f else 0.10f + energy * 0.04f
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前",
                color = Color.White.copy(alpha = 0.34f + energy * 0.08f),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            Text(
                value,
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
private fun SettingsTilePrismSurface(
    radius: Float,
    selected: Boolean,
    energy: Float,
    selectedPulse: Float,
    slowBreath: Float,
    press: Float,
    recoil: Float,
    afterglow: Float,
    center: Offset,
    phaseA: Float,
    phaseB: Float,
    phaseC: Float,
    seed: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val radiusPx = radius.dp.toPx()
        val r = CornerRadius(radiusPx, radiusPx)
        val tau = (PI * 2.0).toFloat()
        val a = phaseA * tau
        val b = phaseB * tau
        val c = phaseC * tau
        val selectedBase = if (selected) 1f else 0f
        val breath = ((sin(a + seed * 1.7f) + 1f) * 0.5f).coerceIn(0f, 1f)
        val film = (
            0.68f +
                selectedBase * 0.96f +
                selectedPulse * selectedBase * 0.74f +
                press * 0.58f +
                afterglow * 0.40f
            ).coerceIn(0f, 2.30f)
        val driftX = (0.55f + 0.30f * cos(a + seed * 2.1f)).coerceIn(0.08f, 0.94f) * w
        val driftY = (0.42f + 0.25f * sin(b * 0.82f + seed * 3.2f)).coerceIn(0.08f, 0.88f) * h
        val cx = center.x.coerceIn(0.06f, 0.94f) * w
        val cy = center.y.coerceIn(0.08f, 0.92f) * h
        val sweep = (phaseA * 0.76f + phaseB * 0.24f + seed).let { it - it.toInt() }
        val softSweep = (phaseC * 0.58f + seed * 0.42f).let { it - it.toInt() }
        val rimInset = 0.82.dp.toPx()
        val rimRadius = CornerRadius(
            (radiusPx - rimInset).coerceAtLeast(0f),
            (radiusPx - rimInset).coerceAtLeast(0f)
        )
        val rimSize = Size(
            (w - rimInset * 2f).coerceAtLeast(1f),
            (h - rimInset * 2f).coerceAtLeast(1f)
        )

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF8DF9EA).copy(alpha = 0.060f * film),
                    Color(0xFF8B9DFF).copy(alpha = 0.054f * film),
                    Color(0xFFFF7BE5).copy(alpha = 0.030f * film),
                    Color.Transparent
                ),
                center = Offset(driftX, driftY),
                radius = maxOf(w, h) * (0.62f + breath * 0.22f)
            ),
            size = Size(w, h),
            cornerRadius = r,
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF75FFF0).copy(
                        alpha = (0.110f + selectedPulse * 0.090f) * selectedBase
                    ),
                    Color(0xFF9CA8FF).copy(
                        alpha = (0.090f + slowBreath * 0.075f) * selectedBase
                    ),
                    Color.Transparent
                ),
                center = Offset(
                    w * (0.72f + 0.12f * cos(b)),
                    h * (0.24f + 0.10f * sin(c))
                ),
                radius = maxOf(w, h) * (0.48f + slowBreath * 0.12f)
            ),
            size = Size(w, h),
            cornerRadius = r,
            blendMode = BlendMode.Plus
        )
        if (selected || energy > 0.08f) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.145f * energy),
                        Color(0xFF91FFF2).copy(alpha = 0.130f * energy),
                        Color(0xFFFF74D7).copy(alpha = 0.080f * energy),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = maxOf(w, h) * (0.42f + energy * 0.15f)
                ),
                size = Size(w, h),
                cornerRadius = r,
                blendMode = BlendMode.Screen
            )
        }
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF6CFFF0).copy(alpha = 0.068f * film),
                    Color(0xFF94A6FF).copy(alpha = 0.058f * film),
                    Color.Transparent
                ),
                start = Offset(w * (softSweep - 0.36f), h * 0.02f),
                end = Offset(w * (softSweep + 0.30f), h * 0.92f)
            ),
            size = Size(w, h),
            cornerRadius = r,
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.085f + selectedBase * 0.070f + energy * 0.040f),
                    Color.Transparent,
                    Color(0xFF030716).copy(alpha = 0.020f + press * 0.038f)
                )
            ),
            size = Size(w, h),
            cornerRadius = r,
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF5ED8).copy(alpha = 0.086f * film),
                    Color(0xFFFFE087).copy(alpha = 0.078f * film),
                    Color(0xFF72FFF0).copy(alpha = 0.094f * film),
                    Color(0xFF8DA2FF).copy(alpha = 0.074f * film),
                    Color.Transparent
                ),
                start = Offset(w * (sweep - 0.62f), h * -0.16f),
                end = Offset(w * (sweep + 0.44f), h * 1.12f)
            ),
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = rimRadius,
            style = Stroke(width = 1.35.dp.toPx() + energy * 0.70.dp.toPx()),
            blendMode = BlendMode.Plus
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.160f + selectedBase * 0.120f + press * 0.060f),
                    Color(0xFF8DF9EA).copy(alpha = 0.055f + selectedBase * 0.085f),
                    Color.Transparent,
                    Color(0xFFFF88E8).copy(
                        alpha = 0.035f + selectedBase * 0.060f + afterglow * 0.045f
                    )
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset(1.45.dp.toPx(), 1.45.dp.toPx()),
            size = Size(
                (w - 2.90.dp.toPx()).coerceAtLeast(1f),
                (h - 2.90.dp.toPx()).coerceAtLeast(1f)
            ),
            cornerRadius = CornerRadius(
                (radiusPx - 1.45.dp.toPx()).coerceAtLeast(0f),
                (radiusPx - 1.45.dp.toPx()).coerceAtLeast(0f)
            ),
            style = Stroke(
                width = 0.65.dp.toPx() +
                    selectedBase * 0.42.dp.toPx() +
                    energy * 0.16.dp.toPx()
            ),
            blendMode = BlendMode.Screen
        )
    }
}
