package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.yuchen.ailedger.R
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
    val context = LocalContext.current
    val useThemePreset = customBackgroundPath == BUILTIN_THEME_BACKGROUND_PATH
    val customImage = rememberCustomBackgroundImage(if (useThemePreset) null else customBackgroundPath)
    val presetImage = rememberPresetNightSkyImage(context)

    Canvas(modifier) {
        when {
            customImage != null -> drawCoverImage(customImage)
            useThemePreset -> drawWeatherNightBackground(size.width, size.height, theme, 1f, params)
            presetImage != null -> drawCoverImage(presetImage)
            else -> drawWeatherNightBackground(size.width, size.height, theme, 1f, params)
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

@Composable
private fun rememberPresetNightSkyImage(context: Context): ImageBitmap? {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        image = withContext(Dispatchers.IO) {
            decodePresetNightSkyBitmap(context)?.asImageBitmap()
        }
    }
    return image
}

fun decodePresetNightSkyBitmap(context: Context): Bitmap? {
    return BitmapFactory.decodeResource(context.resources, R.drawable.preset_night_sky)
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
