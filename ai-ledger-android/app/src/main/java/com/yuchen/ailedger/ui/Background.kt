package com.yuchen.ailedger.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BUILTIN_THEME_BACKGROUND_PATH
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.RenderQuality
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun WeatherNightBackground(
    quality: RenderQuality,
    motionIntensity: Float = 1f,
    theme: BackgroundTheme = BackgroundTheme.Aurora,
    params: BackdropDebugParams = BackdropDebugParams(),
    customBackgroundPath: String? = null,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val useThemePreset = customBackgroundPath == BUILTIN_THEME_BACKGROUND_PATH
    val customImage = rememberCustomBackgroundImage(if (useThemePreset) null else customBackgroundPath)
    Canvas(modifier) {
        when {
            customImage != null -> drawCoverImage(customImage)
            useThemePreset -> drawWeatherNightBackground(size.width, size.height, theme, 1f, params)
            else -> drawDefaultWallpaper(size.width, size.height)
        }
    }
}

@Composable
private fun rememberCustomBackgroundImage(path: String?): ImageBitmap? {
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        image = null
        val filePath = path?.takeIf { File(it).exists() }
        if (filePath != null) {
            image = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(filePath)?.asImageBitmap()
            }
        }
    }
    return image
}

fun DrawScope.drawCoverImage(image: ImageBitmap, alpha: Float = 1f) {
    val dstW = size.width.roundToInt().coerceAtLeast(1)
    val dstH = size.height.roundToInt().coerceAtLeast(1)
    val srcW = image.width
    val srcH = image.height
    val dstAspect = dstW.toFloat() / dstH.toFloat()
    val srcAspect = srcW.toFloat() / srcH.toFloat()
    val cropW: Int
    val cropH: Int
    val cropX: Int
    val cropY: Int
    if (srcAspect > dstAspect) {
        cropH = srcH
        cropW = (srcH * dstAspect).roundToInt().coerceIn(1, srcW)
        cropX = ((srcW - cropW) / 2f).roundToInt().coerceAtLeast(0)
        cropY = 0
    } else {
        cropW = srcW
        cropH = (srcW / dstAspect).roundToInt().coerceIn(1, srcH)
        cropX = 0
        cropY = ((srcH - cropH) / 2f).roundToInt().coerceAtLeast(0)
    }
    drawImage(
        image = image,
        srcOffset = IntOffset(cropX, cropY),
        srcSize = IntSize(cropW, cropH),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(dstW, dstH),
        alpha = alpha
    )
}

fun DrawScope.drawDefaultWallpaper(w: Float, h: Float) {
    drawRect(
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF07112B),
                Color(0xFF102B66),
                Color(0xFF243A83),
                Color(0xFF5E5A9C),
                Color(0xFFD48174)
            ),
            startY = 0f,
            endY = h
        )
    )
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0xFFB7A7FF).copy(alpha = 0.28f), Color.Transparent),
            center = Offset(w * 0.70f, h * 0.12f),
            radius = w * 0.58f
        ),
        topLeft = Offset(w * 0.14f, -h * 0.08f),
        size = Size(w * 1.10f, h * 0.38f),
        blendMode = BlendMode.Screen
    )
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFF9B73).copy(alpha = 0.28f), Color.Transparent),
            center = Offset(w * 0.40f, h * 0.84f),
            radius = w * 0.72f
        ),
        topLeft = Offset(-w * 0.26f, h * 0.56f),
        size = Size(w * 1.30f, h * 0.52f),
        blendMode = BlendMode.Screen
    )
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0xFF78B8FF).copy(alpha = 0.18f), Color.Transparent),
            center = Offset(w * 0.18f, h * 0.42f),
            radius = w * 0.46f
        ),
        topLeft = Offset(-w * 0.12f, h * 0.24f),
        size = Size(w * 0.76f, h * 0.38f),
        blendMode = BlendMode.Screen
    )
    val stars = listOf(0.10f to 0.16f, 0.23f to 0.10f, 0.36f to 0.19f, 0.49f to 0.12f, 0.62f to 0.18f, 0.79f to 0.10f, 0.90f to 0.22f, 0.18f to 0.34f, 0.55f to 0.30f, 0.82f to 0.38f)
    stars.forEachIndexed { index, point ->
        drawCircle(
            color = Color.White.copy(alpha = 0.16f + (index % 3) * 0.06f),
            radius = (w.coerceAtMost(h) * 0.0024f) * (0.7f + (index % 4) * 0.18f),
            center = Offset(w * point.first, h * point.second),
            blendMode = BlendMode.Screen
        )
    }
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Transparent, Color(0xFF050914).copy(alpha = 0.12f)),
            startY = h * 0.58f,
            endY = h
        ),
        blendMode = BlendMode.Multiply
    )
}

fun DrawScope.drawWeatherNightBackground(
    w: Float,
    h: Float,
    theme: BackgroundTheme = BackgroundTheme.Aurora,
    alphaScale: Float = 1f,
    params: BackdropDebugParams = BackdropDebugParams()
) {
    drawTwilightWeatherSky(w, h, theme, alphaScale, glowOnly = false, params = params)
}

fun DrawScope.drawWeatherNightBackgroundGlow(
    w: Float,
    h: Float,
    theme: BackgroundTheme = BackgroundTheme.Aurora,
    alphaScale: Float = 1f,
    params: BackdropDebugParams = BackdropDebugParams()
) {
    drawTwilightWeatherSky(w, h, theme, alphaScale, glowOnly = true, params = params)
}

private data class BackgroundPalette(
    val deep: Color,
    val mid: Color,
    val glow: Color,
    val bottom: Color,
    val primaryAura: Color,
    val secondaryAura: Color,
    val widget: Color,
    val icons: List<Color>
)

@Suppress("unused")
private fun legacyBackgroundPalette(theme: BackgroundTheme): BackgroundPalette {
    return when (theme) {
        BackgroundTheme.Aurora -> BackgroundPalette(Color(0xFF061426), Color(0xFF0B2947), Color(0xFF164166), Color(0xFF07111F), Color(0xFF2F72AD), Color(0xFF236AA8), Color(0xFFB9C3CD), listOf(Color(0xFF18AFFF), Color(0xFFFFB51B), Color(0xFF181A28), Color.White, Color(0xFFFF5058), Color(0xFFFF941D), Color(0xFFB9C3CD), Color(0xFF1078F8)))
        BackgroundTheme.Jade -> BackgroundPalette(Color(0xFF071B21), Color(0xFF0B3A43), Color(0xFF0C5B66), Color(0xFF061419), Color(0xFF22C7A7), Color(0xFF40DCA8), Color(0xFFC8D8D2), listOf(Color(0xFF20D3B2), Color(0xFFFFC95C), Color(0xFF1D2630), Color.White, Color(0xFFFF6B7C), Color(0xFF50B7FF), Color(0xFFBFD5CE), Color(0xFF0D8E7B)))
        BackgroundTheme.Sunset -> BackgroundPalette(Color(0xFF221327), Color(0xFF4B2138), Color(0xFF7E3D4F), Color(0xFF140E1E), Color(0xFFFF7A6E), Color(0xFFFFB35B), Color(0xFFD8C6C8), listOf(Color(0xFFFF6E82), Color(0xFFFFB84A), Color(0xFF242233), Color.White, Color(0xFFFF4F6D), Color(0xFFFF8B2C), Color(0xFFCDC1D2), Color(0xFF6A79FF)))
        BackgroundTheme.Dawn -> BackgroundPalette(Color(0xFF1A2634), Color(0xFF52657A), Color(0xFF93A8B7), Color(0xFF101822), Color(0xFFEAF2FF), Color(0xFF9ED4FF), Color(0xFFE7E9EE), listOf(Color(0xFF45B8FF), Color(0xFFFFC861), Color(0xFF28303A), Color.White, Color(0xFFFF6F83), Color(0xFFFFA15C), Color(0xFFD9E0E9), Color(0xFF358CFF)))
    }
}