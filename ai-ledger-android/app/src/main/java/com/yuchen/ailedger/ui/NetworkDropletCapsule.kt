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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.ui.gl.DropletGlassStyle
import com.yuchen.ailedger.ui.gl.OpenGLDropletGlassLayer
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

private const val NetworkInlineDropletTau = 6.2831855f
private val NetworkInlinePressEasing = CubicBezierEasing(0.10f, 0.00f, 0.05f, 1.00f)
private val NetworkInlineLightEasing = CubicBezierEasing(0.18f, 0.00f, 0.10f, 1.00f)
private val NetworkInlineReleaseEasing = CubicBezierEasing(0.16f, 0.00f, 0.10f, 1.00f)

private val NetworkInlineDropletStyle = DropletGlassStyle(
    bodyBulgePx = 44f,
    edgePullPx = 120f,
    edgeWidthPx = 32f,
    lensMix = 0.92f,
    dragStrength = 2.0f,
    bottomGlow = 1.48f,
    topGloss = 0.53f,
    cornerGloss = 1.03f,
    innerDark = 0.65f,
    alpha = 0.63f,
    activeGlow = 0.53f,
    activeRefraction = 4.0f,
    activeRimRefraction = 3.16f,
    activeLightX = 1.0f,
    activeLightSpread = 0.70f,
    activeLightY = 1.25f,
    activeEntryHeight = 0.04f,
    activeLightThickness = 0.22f,
    activeHotspot = 1.27f,
    activeEntryPearl = 1.88f,
    activeRimPearl = 1.35f,
    activeCenterClear = 0.42f,
    activeVolumeWarmth = 0.14f,
    activeRimGather = 1.21f,
    activeRimFlow = 0.89f
)

@Composable
fun NetworkDropletCapsule(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: Any
) {
    val clickAction = remember(onClick) { (onClick as? () -> Unit) ?: {} }
    val active = state.onlineEnabled
    val title = "联网"
    val value = if (state.onlineEnabled) "已开启" else "已关闭"

    val coordinates = remember { GlassCoordinateSource() }
    val pressAnim = remember { Animatable(0f) }
    val latchAnim = remember { Animatable(if (active) 1f else 0f) }
    val afterglowAnim = remember { Animatable(0f) }
    val breathAnim = remember { Animatable(0f) }
    val driftAnim = remember { Animatable(0f) }
    val shimmerAnim = remember { Animatable(0.35f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(active) {
        if (active) {
            latchAnim.stop()
            if (latchAnim.value < 0.18f) latchAnim.snapTo(0.18f)
            latchAnim.animateTo(0.72f, tween(170, easing = NetworkInlineLightEasing))
            latchAnim.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow))
        } else {
            latchAnim.stop()
            latchAnim.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
        }
    }

    val pressValue = pressAnim.value.coerceIn(-0.28f, 1.35f)
    val pressPositive = pressValue.coerceAtLeast(0f)
    val recoilValue = (-pressValue).coerceAtLeast(0f)
    val latchValue = latchAnim.value.coerceIn(0f, 1f)
    val afterglowValue = afterglowAnim.value.coerceIn(0f, 1f)
    val activeForBreath = active || pressPositive > 0.05f

    LaunchedEffect(activeForBreath) {
        if (activeForBreath) {
            while (true) {
                breathAnim.animateTo(Random.nextFloat(), tween(950 + Random.nextInt(850), easing = FastOutSlowInEasing))
            }
        } else {
            breathAnim.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(activeForBreath) {
        if (activeForBreath) {
            while (true) {
                driftAnim.animateTo(Random.nextFloat() * 0.34f - 0.17f, tween(1200 + Random.nextInt(1300), easing = FastOutSlowInEasing))
            }
        } else {
            driftAnim.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(activeForBreath) {
        if (activeForBreath) {
            while (true) {
                shimmerAnim.animateTo(0.22f + Random.nextFloat() * 0.78f, tween(520 + Random.nextInt(980), easing = FastOutSlowInEasing))
            }
        } else {
            shimmerAnim.animateTo(0.18f, tween(360, easing = FastOutSlowInEasing))
        }
    }

    val breathValue = breathAnim.value.coerceIn(0f, 1f)
    val driftValue = driftAnim.value.coerceIn(-0.22f, 0.22f)
    val shimmerValue = shimmerAnim.value.coerceIn(0f, 1f)
    val prismAmount = 2.76f
    val purpleAmount = 0.67f
    val purpleMix = (purpleAmount / 1.5f).coerceIn(0f, 1f)
    val breathEnergy = 0.88f + breathValue * 0.14f + shimmerValue * 0.08f
    val holdEnergy = maxOf(pressPositive, latchValue)
    val lightEnergy = (holdEnergy * breathEnergy + afterglowValue * 0.55f).coerceIn(0f, 1.30f)
    val refractionEnergy = (holdEnergy * (0.92f + breathValue * 0.10f + shimmerValue * 0.08f) + afterglowValue * 0.40f).coerceIn(0f, 1.18f)
    val effectiveLightX = (0.92f + driftValue * holdEnergy).coerceIn(0.04f, 0.96f)
    val prismPhase = effectiveLightX * 0.80f + breathValue * 0.21f + shimmerValue * 0.19f + afterglowValue * 0.17f
    val prismMix = (lightEnergy * prismAmount * 0.80f).coerceIn(0f, 1f)
    val prismRed = 0.36f + networkInlinePrismChannel(prismPhase + 0.00f) * 0.64f
    val prismGreen = 0.38f + networkInlinePrismChannel(prismPhase + 0.34f) * 0.62f
    val prismBlue = 0.50f + networkInlinePrismChannel(prismPhase + 0.68f) * 0.50f
    val warmRed = 0.70f + networkInlinePrismChannel(prismPhase + 0.08f) * 0.30f
    val warmGreen = 0.30f + networkInlinePrismChannel(prismPhase + 0.28f) * 0.62f
    val warmBlue = 0.58f + networkInlinePrismChannel(prismPhase + 0.58f) * 0.42f

    val animatedStyle = NetworkInlineDropletStyle.copy(
        bodyBulgePx = NetworkInlineDropletStyle.bodyBulgePx + pressPositive * 14.0f + latchValue * 5.0f - afterglowValue * 3.0f,
        edgePullPx = NetworkInlineDropletStyle.edgePullPx + pressPositive * 32.0f + latchValue * 12.0f + afterglowValue * 9.0f,
        edgeWidthPx = NetworkInlineDropletStyle.edgeWidthPx + pressPositive * 5.8f + latchValue * 2.8f,
        bottomGlow = NetworkInlineDropletStyle.bottomGlow * (0.68f + lightEnergy * (0.20f + purpleMix * 0.18f + prismAmount * 0.08f)),
        topGloss = NetworkInlineDropletStyle.topGloss + lightEnergy * (0.12f + purpleMix * 0.22f + prismAmount * 0.10f),
        cornerGloss = NetworkInlineDropletStyle.cornerGloss + lightEnergy * (0.10f + purpleMix * 0.18f) + prismAmount * shimmerValue * lightEnergy * 0.12f,
        innerDark = (NetworkInlineDropletStyle.innerDark + pressPositive * 0.10f - lightEnergy * 0.08f).coerceIn(0f, 1f),
        alpha = (NetworkInlineDropletStyle.alpha + lightEnergy * 0.13f).coerceIn(0f, 1f),
        activeGlow = NetworkInlineDropletStyle.activeGlow * lightEnergy * (0.50f + purpleMix * 0.28f + prismAmount * 0.16f),
        activeRefraction = NetworkInlineDropletStyle.activeRefraction * refractionEnergy,
        activeRimRefraction = NetworkInlineDropletStyle.activeRimRefraction * refractionEnergy * (1f + prismAmount * 0.12f),
        activeLightX = networkInlineLerp(NetworkInlineDropletStyle.activeLightX.coerceIn(0f, 1f), effectiveLightX, holdEnergy.coerceIn(0f, 1f)),
        activeLightSpread = (NetworkInlineDropletStyle.activeLightSpread * (0.50f + lightEnergy * 0.52f) + pressPositive * 0.12f + prismAmount * 0.035f).coerceIn(0f, 1f),
        activeLightThickness = (NetworkInlineDropletStyle.activeLightThickness * (0.58f + lightEnergy * 0.46f + prismAmount * 0.06f)).coerceIn(0.015f, 0.42f),
        activeHotspot = NetworkInlineDropletStyle.activeHotspot * (lightEnergy * (0.35f + purpleMix * 0.45f + prismAmount * 0.16f) + shimmerValue * holdEnergy * 0.18f),
        activeEntryPearl = NetworkInlineDropletStyle.activeEntryPearl * (lightEnergy * (0.30f + purpleMix * 0.44f + prismAmount * 0.16f) + pressPositive * 0.12f),
        activeRimPearl = NetworkInlineDropletStyle.activeRimPearl * (lightEnergy * (0.25f + purpleMix * 0.32f + prismAmount * 0.24f) + shimmerValue * holdEnergy * 0.12f),
        activeCenterClear = (NetworkInlineDropletStyle.activeCenterClear + lightEnergy * 0.24f).coerceIn(0f, 1f),
        activeVolumeWarmth = NetworkInlineDropletStyle.activeVolumeWarmth * (0.24f + lightEnergy * (purpleMix * 0.36f + prismAmount * 0.22f)),
        activeRimGather = NetworkInlineDropletStyle.activeRimGather * (lightEnergy + afterglowValue * 0.22f + prismAmount * shimmerValue * 0.07f),
        activeRimFlow = NetworkInlineDropletStyle.activeRimFlow * (0.42f + lightEnergy * 0.88f + prismAmount * 0.08f),
        accentRed = networkInlineLerp(NetworkInlineDropletStyle.accentRed, prismRed, prismMix),
        accentGreen = networkInlineLerp(NetworkInlineDropletStyle.accentGreen, prismGreen, prismMix),
        accentBlue = networkInlineLerp(NetworkInlineDropletStyle.accentBlue, prismBlue, prismMix),
        warmRed = networkInlineLerp(NetworkInlineDropletStyle.warmRed, warmRed, prismMix),
        warmGreen = networkInlineLerp(NetworkInlineDropletStyle.warmGreen, warmGreen, prismMix),
        warmBlue = networkInlineLerp(NetworkInlineDropletStyle.warmBlue, warmBlue, prismMix)
    )

    val animatedPurpleWhiteGlow = 0.53f * lightEnergy * purpleAmount
    val animatedPrismGlow = 0.53f * lightEnergy * (0.34f + prismAmount * 0.64f).coerceIn(0f, 2.2f)
    val animatedBackgroundGlow = 0.38f * (lightEnergy + recoilValue * 0.35f + prismAmount * lightEnergy * 0.16f).coerceIn(0f, 1.8f)
    val animatedOuterGlow = 0.46f * (lightEnergy + afterglowValue * 0.28f + recoilValue * 0.30f + prismAmount * lightEnergy * 0.22f).coerceIn(0f, 1.9f)
    val animatedWarmGlow = 0.54f * (lightEnergy * (0.20f + purpleMix * 0.46f + prismAmount * 0.18f) + pressPositive * 0.12f).coerceIn(0f, 1.8f)
    val contentAlpha = (0.50f + lightEnergy * 0.36f + recoilValue * 0.10f).coerceIn(0.44f, 0.98f)

    Box(modifier = modifier.height(68.dp), contentAlignment = Alignment.Center) {
        NetworkInlineDropletBackgroundGlow(animatedPrismGlow, animatedBackgroundGlow, animatedOuterGlow, animatedWarmGlow, prismAmount, prismPhase, Modifier.fillMaxSize())
        NetworkInlineDropletContactShadow(alpha = 0.18f * (0.72f + pressPositive * 0.62f + afterglowValue * 0.32f), modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(effectiveLightX.coerceIn(0f, 1f), 0.50f)
                    scaleX = 1f + pressPositive * 0.066f + latchValue * 0.018f - afterglowValue * 0.006f - recoilValue * 0.020f
                    scaleY = 1f - pressPositive * 0.074f + afterglowValue * 0.014f + recoilValue * 0.026f
                    translationX = (effectiveLightX - 0.5f) * pressPositive * 5.8f + recoilValue * if (effectiveLightX > 0.5f) 3.0f else -3.0f
                    translationY = pressPositive * 5.2f - afterglowValue * 1.1f - recoilValue * 1.8f
                    rotationZ = (effectiveLightX - 0.5f) * pressPositive * 0.72f + recoilValue * if (effectiveLightX > 0.5f) 0.36f else -0.36f
                }
                .onGloballyPositioned { coordinates.coordinates = it }
                .pointerInput(enabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!enabled) return@awaitEachGesture
                        scope.launch {
                            afterglowAnim.stop()
                            afterglowAnim.snapTo(0f)
                        }
                        scope.launch {
                            pressAnim.stop()
                            if (pressAnim.value < 0.24f) pressAnim.snapTo(0.24f)
                            pressAnim.animateTo(1.20f, tween(145, easing = NetworkInlinePressEasing))
                            pressAnim.animateTo(0.96f, spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow))
                        }
                        var releasedAt = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent()
                            val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (tracked != null && !tracked.pressed) {
                                releasedAt = tracked.uptimeMillis
                                break
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                        if (releasedAt - down.uptimeMillis < 260L) clickAction()
                        scope.launch {
                            pressAnim.stop()
                            pressAnim.animateTo(-0.22f, tween(130, easing = NetworkInlineReleaseEasing))
                            pressAnim.animateTo(0.08f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessLow))
                            pressAnim.animateTo(0f, tween(210, easing = FastOutSlowInEasing))
                        }
                        scope.launch {
                            afterglowAnim.stop()
                            afterglowAnim.snapTo(0.78f)
                            afterglowAnim.animateTo(0f, tween(820, easing = FastOutSlowInEasing))
                        }
                    }
                }
                .clip(RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center
        ) {
            OpenGLDropletGlassLayer(radius = 999, coordinateSource = coordinates, style = animatedStyle, modifier = Modifier.fillMaxSize())
            NetworkInlineDropletActiveOverlay(animatedPurpleWhiteGlow, animatedWarmGlow, purpleMix, Modifier.fillMaxSize())
            NetworkInlineDropletPrismOverlay(animatedPrismGlow, lightEnergy, prismPhase, prismAmount, effectiveLightX, shimmerValue, Modifier.fillMaxSize())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
                NetworkInlineDropDot(active = active, energy = lightEnergy)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(title, color = Color.White.copy(alpha = contentAlpha * 0.80f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(value, color = Color.White.copy(alpha = contentAlpha), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun networkInlineLerp(start: Float, end: Float, fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return start + (end - start) * t
}

private fun networkInlinePrismChannel(phase: Float): Float {
    val wrapped = phase - phase.toInt()
    return (0.5f + 0.5f * sin((wrapped * NetworkInlineDropletTau).toDouble()).toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun NetworkInlineDropletBackgroundGlow(activeGlow: Float, backgroundGlow: Float, outerGlow: Float, warmGlow: Float, prismStrength: Float, phase: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val active = activeGlow.coerceIn(0f, 2f)
        val bg = backgroundGlow.coerceIn(0f, 2f)
        val outer = outerGlow.coerceIn(0f, 2f)
        val warm = warmGlow.coerceIn(0f, 2f)
        val prism = prismStrength.coerceIn(0f, 3f)
        val radius = size.height * 0.46f
        val drift = networkInlinePrismChannel(phase)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFBFEAFF).copy(alpha = bg * active * 0.18f), Color(0xFF6CCBFF).copy(alpha = bg * active * 0.06f), Color.Transparent), Offset(size.width * 0.50f, size.height * 0.42f), size.width * 0.62f), Offset(size.width * 0.02f, size.height * 0.08f), Size(size.width * 0.96f, size.height * 0.80f), CornerRadius(radius, radius), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFFF72B7).copy(alpha = warm * active * 0.08f), Color(0xFFFFB56F).copy(alpha = warm * active * 0.025f), Color.Transparent), Offset(size.width * 0.48f, size.height * 0.78f), size.width * 0.44f), Offset(size.width * 0.08f, size.height * 0.42f), Size(size.width * 0.84f, size.height * 0.42f), CornerRadius(radius, radius), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFFF4FA7).copy(alpha = outer * active * prism * 0.035f), Color(0xFF80FFD8).copy(alpha = outer * active * prism * 0.028f), Color(0xFF78A8FF).copy(alpha = outer * active * prism * 0.020f), Color.Transparent), Offset(size.width * (0.34f + drift * 0.32f), size.height * 0.34f), size.width * 0.68f), Offset(size.width * 0.03f, size.height * 0.10f), Size(size.width * 0.94f, size.height * 0.74f), CornerRadius(radius, radius), blendMode = BlendMode.Plus)
    }
}

@Composable
private fun NetworkInlineDropletActiveOverlay(activeGlow: Float, warmGlow: Float, purpleMix: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val active = activeGlow.coerceIn(0f, 2f)
        val warm = warmGlow.coerceIn(0f, 2f)
        val purple = purpleMix.coerceIn(0f, 1f)
        val radius = size.height / 2f
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = active * purple * 0.16f), Color.White.copy(alpha = active * purple * 0.024f), Color.Transparent)), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Screen)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFFFF73C5).copy(alpha = active * warm * purple * 0.10f), Color(0xFFFFB06C).copy(alpha = active * warm * purple * 0.032f), Color.Transparent), Offset(size.width * 0.50f, size.height * 1.03f), size.width * 0.42f), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Screen)
    }
}

@Composable
private fun NetworkInlineDropletPrismOverlay(activeGlow: Float, energy: Float, phase: Float, prismStrength: Float, lightX: Float, shimmer: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val active = activeGlow.coerceIn(0f, 2.4f)
        val e = energy.coerceIn(0f, 1.35f)
        val prism = prismStrength.coerceIn(0f, 3f)
        if (active <= 0.001f || e <= 0.001f || prism <= 0.001f) return@Canvas
        val radius = size.height / 2f
        val sweep = (phase - phase.toInt()) * size.width * 0.66f
        val alpha = (active * e * prism).coerceIn(0f, 4.2f)
        drawRoundRect(brush = Brush.linearGradient(colors = listOf(Color(0xFFFF2E93).copy(alpha = alpha * 0.045f), Color(0xFFFFD84D).copy(alpha = alpha * 0.038f), Color(0xFF55FFD6).copy(alpha = alpha * 0.050f), Color(0xFF4F89FF).copy(alpha = alpha * 0.046f), Color(0xFFC05CFF).copy(alpha = alpha * 0.052f)), start = Offset(-size.width * 0.42f + sweep, 0f), end = Offset(size.width * 1.04f + sweep, size.height)), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Plus)
        drawRoundRect(brush = Brush.linearGradient(colors = listOf(Color.Transparent, Color.White.copy(alpha = alpha * (0.05f + shimmer * 0.02f)), Color(0xFF9EFFF0).copy(alpha = alpha * 0.03f), Color.Transparent), start = Offset(size.width * (lightX - 0.42f), size.height * 0.02f), end = Offset(size.width * (lightX + 0.40f), size.height * 0.28f)), topLeft = Offset(size.width * 0.06f, size.height * 0.06f), size = Size(size.width * 0.88f, size.height * 0.24f), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Screen)
        val edgeX = if (lightX > 0.5f) (lightX + 0.06f).coerceAtMost(0.92f) else (lightX - 0.06f).coerceAtLeast(0.08f)
        val edgeY = 0.42f + (shimmer - 0.5f) * 0.08f
        drawRoundRect(brush = Brush.radialGradient(colors = listOf(Color.White.copy(alpha = alpha * 0.060f), Color(0xFFFFF4B5).copy(alpha = alpha * 0.045f), Color(0xFF63FFE4).copy(alpha = alpha * 0.040f), Color.Transparent), center = Offset(size.width * edgeX, size.height * edgeY), radius = size.width * (0.18f + shimmer * 0.10f)), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Plus)
        drawRoundRect(brush = Brush.linearGradient(colors = listOf(Color.Transparent, Color(0xFFFFF0A8).copy(alpha = alpha * 0.020f), Color(0xFF69FFE8).copy(alpha = alpha * 0.028f), Color.Transparent), start = Offset(size.width * 0.10f, size.height * 0.88f), end = Offset(size.width * 0.90f, size.height * 0.18f)), cornerRadius = CornerRadius(radius, radius), blendMode = BlendMode.Plus)
    }
}

@Composable
private fun NetworkInlineDropletContactShadow(alpha: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = alpha.coerceIn(0f, 1f) * 0.45f), Color.Black.copy(alpha = alpha.coerceIn(0f, 1f) * 0.13f), Color.Transparent), Offset(size.width * 0.52f, size.height * 0.70f), size.width * 0.62f), Offset(size.width * 0.08f, size.height * 0.46f), Size(size.width * 0.84f, size.height * 0.42f), blendMode = BlendMode.Multiply)
    }
}

@Composable
private fun NetworkInlineDropDot(active: Boolean, energy: Float) {
    Canvas(Modifier.width(12.dp).height(12.dp)) {
        drawCircle(Color.White.copy(alpha = if (active) 0.78f + energy * 0.18f else 0.36f), radius = size.minDimension * 0.34f, center = center, blendMode = BlendMode.Screen)
        if (active) drawCircle(Color(0xFF7EFFE7).copy(alpha = energy * 0.50f), radius = size.minDimension * 0.50f, center = center, blendMode = BlendMode.Plus)
    }
}
