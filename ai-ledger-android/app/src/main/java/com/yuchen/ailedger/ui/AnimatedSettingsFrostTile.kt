package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxWidth
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
    val seed = remember(title, icon) { ((title.sumOf { it.code } + icon.sumOf { it.code } * 7) % 1000) / 1000f }
    val transition = rememberInfiniteTransition(label = "settings-frost-tile-$title")
    val phaseA by transition.animateFloat(
        initialValue = seed,
        targetValue = seed + 1f,
        animationSpec = infiniteRepeatable(tween(if (selected) 5200 else 9200, easing = LinearEasing), RepeatMode.Restart),
        label = "settings-tile-phase-a-$title"
    )
    val phaseB by transition.animateFloat(
        initialValue = seed + 0.37f,
        targetValue = seed + 1.37f,
        animationSpec = infiniteRepeatable(tween(if (selected) 7600 else 13100, easing = LinearEasing), RepeatMode.Restart),
        label = "settings-tile-phase-b-$title"
    )
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
    val tau = (PI * 2.0).toFloat()
    val selectedPulse = if (selected && motionOn) ((sin(tA * tau) + 1f) * 0.5f).coerceIn(0f, 1f) else selectedBase * 0.36f
    val energy = (selectedBase * (0.58f + selectedPulse * 0.32f) + press * 0.74f + afterglow * 0.42f).coerceIn(0f, 1.35f)
    val radius = 17.44f

    Box(
        modifier = modifier
            .height(116.dp)
            .onSizeChanged { tileSize = Size(it.width.coerceAtLeast(1).toFloat(), it.height.coerceAtLeast(1).toFloat()) }
            .graphicsLayer {
                transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
                scaleX = 1f + selectedBase * 0.010f + selectedPulse * selectedBase * 0.006f + press * 0.018f - recoil * 0.006f
                scaleY = 1f + selectedBase * 0.002f - press * 0.030f + recoil * 0.012f
                translationY = press * 2.8f - recoil * 1.2f
                translationX = (pressCenter.x - 0.5f) * press * 3.4f
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
                            pressAnim.animateTo(0.78f, spring(dampingRatio = 0.66f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
                        }
                    }
                    var shouldClick = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            updateCenter(tracked.position)
                            if (!tracked.pressed) break
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (shouldClick) onClick()
                    if (motionOn) {
                        scope.launch {
                            pressAnim.stop()
                            pressAnim.animateTo(-0.10f, tween(130, easing = SettingsTileReleaseEasing))
                            pressAnim.animateTo(0.030f, spring(dampingRatio = 0.48f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
                            pressAnim.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                        }
                        scope.launch {
                            afterglowAnim.stop()
                            afterglowAnim.snapTo(0.72f)
                            afterglowAnim.animateTo(0f, tween(760, easing = FastOutSlowInEasing))
                        }
                    }
                }
            }
            .clip(RoundedCornerShape(radius.dp)),
        contentAlignment = Alignment.Center
    ) {
        FrostInfoGlassPanel(
            radius = radius,
            backdropAlpha = 1f,
            frostAlpha = 0.080f + energy * 0.030f,
            dimAlpha = 0.006f + press * 0.020f,
            modifier = Modifier.fillMaxSize()
        ) {}
        SettingsTilePrismSurface(
            radius = radius,
            selected = selected,
            energy = energy,
            selectedPulse = selectedPulse,
            press = press,
            recoil = recoil,
            afterglow = afterglow,
            center = pressCenter,
            phaseA = tA,
            phaseB = tB,
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
    val iconAlpha = (if (selected) 0.74f else 0.54f) + energy * 0.18f
    val titleAlpha = (0.91f + energy * 0.08f).coerceIn(0f, 1f)
    val secondaryAlpha = ((if (selected) 0.56f else 0.46f) + energy * 0.10f).coerceIn(0f, 0.78f)
    val valueAlpha = ((if (selected) 0.76f else 0.56f) + energy * 0.14f).coerceIn(0f, 0.96f)

    Column(
        modifier.padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
            SettingsAnimatedTileHairline(Modifier.size(1.dp, 42.dp), alpha = if (selected) 0.23f + energy * 0.07f else 0.12f + energy * 0.05f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White.copy(alpha = titleAlpha), fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = secondaryAlpha), fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        SettingsAnimatedTileHairline(Modifier.fillMaxWidth().height(1.dp), alpha = if (selected) 0.18f + energy * 0.06f else 0.09f + energy * 0.03f)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("当前", color = Color.White.copy(alpha = 0.32f + energy * 0.06f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(value, color = Color.White.copy(alpha = valueAlpha), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun SettingsAnimatedTileHairline(modifier: Modifier = Modifier, alpha: Float = 0.12f) {
    Canvas(modifier = modifier) {
        drawRect(color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)), size = size, blendMode = BlendMode.Screen)
    }
}

@Composable
private fun SettingsTilePrismSurface(
    radius: Float,
    selected: Boolean,
    energy: Float,
    selectedPulse: Float,
    press: Float,
    recoil: Float,
    afterglow: Float,
    center: Offset,
    phaseA: Float,
    phaseB: Float,
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
        val selectedBase = if (selected) 1f else 0f
        val breath = ((sin(a + seed * 1.7f) + 1f) * 0.5f).coerceIn(0f, 1f)
        val film = (0.50f + selectedBase * 0.55f + selectedPulse * selectedBase * 0.40f + press * 0.42f + afterglow * 0.28f).coerceIn(0f, 1.65f)
        val driftX = (0.55f + 0.28f * cos(a + seed * 2.1f)).coerceIn(0.12f, 0.92f) * w
        val driftY = (0.42f + 0.22f * sin(b * 0.82f + seed * 3.2f)).coerceIn(0.10f, 0.86f) * h
        val cx = center.x.coerceIn(0.06f, 0.94f) * w
        val cy = center.y.coerceIn(0.08f, 0.92f) * h
        val sweep = (phaseA * 0.76f + phaseB * 0.24f + seed).let { it - it.toInt() }
        val rimInset = 0.82.dp.toPx()
        val rimRadius = CornerRadius((radiusPx - rimInset).coerceAtLeast(0f), (radiusPx - rimInset).coerceAtLeast(0f))
        val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF8DF9EA).copy(alpha = 0.034f * film),
                    Color(0xFF8B9DFF).copy(alpha = 0.030f * film),
                    Color.Transparent
                ),
                center = Offset(driftX, driftY),
                radius = maxOf(w, h) * (0.60f + breath * 0.18f)
            ),
            size = Size(w, h),
            cornerRadius = r,
            blendMode = BlendMode.Screen
        )
        if (selected || energy > 0.08f) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.090f * energy),
                        Color(0xFF91FFF2).copy(alpha = 0.085f * energy),
                        Color(0xFFFF74D7).copy(alpha = 0.044f * energy),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = maxOf(w, h) * (0.42f + energy * 0.12f)
                ),
                size = Size(w, h),
                cornerRadius = r,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFB8C8FF).copy(alpha = (0.070f + selectedPulse * 0.070f) * selectedBase),
                        Color(0xFF8DF9EA).copy(alpha = (0.036f + selectedPulse * 0.042f) * selectedBase),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.72f + 0.08f * cos(b)), h * (0.20f + 0.06f * sin(a))),
                    radius = maxOf(w, h) * 0.48f
                ),
                size = Size(w, h),
                cornerRadius = r,
                blendMode = BlendMode.Plus
            )
        }
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.060f + selectedBase * 0.040f + energy * 0.035f),
                    Color.Transparent,
                    Color(0xFF030716).copy(alpha = 0.024f + press * 0.040f)
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
                    Color(0xFFFF5ED8).copy(alpha = 0.052f * film),
                    Color(0xFFFFE087).copy(alpha = 0.048f * film),
                    Color(0xFF72FFF0).copy(alpha = 0.058f * film),
                    Color(0xFF8DA2FF).copy(alpha = 0.047f * film),
                    Color.Transparent
                ),
                start = Offset(w * (sweep - 0.62f), h * -0.16f),
                end = Offset(w * (sweep + 0.44f), h * 1.12f)
            ),
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = rimRadius,
            style = Stroke(width = 0.92.dp.toPx() + energy * 0.52.dp.toPx()),
            blendMode = BlendMode.Plus
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.105f + selectedBase * 0.070f + press * 0.050f),
                    Color(0xFF8DF9EA).copy(alpha = 0.030f + selectedBase * 0.050f),
                    Color.Transparent,
                    Color(0xFFFF88E8).copy(alpha = 0.020f + selectedBase * 0.035f + afterglow * 0.030f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset(1.45.dp.toPx(), 1.45.dp.toPx()),
            size = Size((w - 2.90.dp.toPx()).coerceAtLeast(1f), (h - 2.90.dp.toPx()).coerceAtLeast(1f)),
            cornerRadius = CornerRadius((radiusPx - 1.45.dp.toPx()).coerceAtLeast(0f), (radiusPx - 1.45.dp.toPx()).coerceAtLeast(0f)),
            style = Stroke(width = 0.45.dp.toPx() + selectedBase * 0.20.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
}
