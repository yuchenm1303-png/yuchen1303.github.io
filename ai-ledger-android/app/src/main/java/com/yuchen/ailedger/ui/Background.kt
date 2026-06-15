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
import androidx.compose.ui.graphics.Color
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
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class BackdropSourceKind {
    DefaultWallpaper,
    BuiltInTheme,
    CustomImage
}

internal data class ResolvedBackdropSource(
    val kind: BackdropSourceKind,
    val customImagePath: String? = null
)

/**
 * Resolves the background once so the default wallpaper, generated theme and custom image paths are
 * mutually exclusive. Missing custom files deliberately fall back to the default wallpaper instead
 * of entering the generated-theme rendering path.
 */
internal fun resolveBackdropSource(customBackgroundPath: String?): ResolvedBackdropSource = when {
    customBackgroundPath == BUILTIN_THEME_BACKGROUND_PATH ->
        ResolvedBackdropSource(BackdropSourceKind.BuiltInTheme)

    customBackgroundPath.isNullOrBlank() ->
        ResolvedBackdropSource(BackdropSourceKind.DefaultWallpaper)

    File(customBackgroundPath).isFile ->
        ResolvedBackdropSource(BackdropSourceKind.CustomImage, customBackgroundPath)

    else -> ResolvedBackdropSource(BackdropSourceKind.DefaultWallpaper)
}

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
    val source = remember(customBackgroundPath) { resolveBackdropSource(customBackgroundPath) }

    // Only the active source starts decoding. Default startup no longer initializes a custom-image
    // loader, while custom/theme modes no longer decode the bundled wallpaper in the background.
    val customImage = if (source.kind == BackdropSourceKind.CustomImage) {
        rememberCustomBackgroundImage(source.customImagePath)
    } else {
        null
    }
    val presetImage = if (source.kind == BackdropSourceKind.DefaultWallpaper) {
        rememberPresetNightSkyImage(context)
    } else {
        null
    }

    Canvas(modifier) {
        when (source.kind) {
            BackdropSourceKind.CustomImage -> {
                if (customImage != null) drawCoverImage(customImage) else drawBackdropLoadingBase()
            }

            BackdropSourceKind.BuiltInTheme -> {
                drawWeatherNightBackground(size.width, size.height, theme, 1f, params)
            }

            BackdropSourceKind.DefaultWallpaper -> {
                if (presetImage != null) drawCoverImage(presetImage) else drawBackdropLoadingBase()
            }
        }
    }
}

/**
 * A flat two-pixel-cost visual fallback while an image source is decoded. It intentionally contains
 * no theme gradient, clouds, stars or glow layers, so selecting the default wallpaper never executes
 * the generated-theme drawing chain even for a single frame.
 */
private fun DrawScope.drawBackdropLoadingBase() {
    drawRect(Color(0xFF07132D))
}

@Composable
private fun rememberCustomBackgroundImage(path: String?): ImageBitmap? {
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        image = null
        val filePath = path?.takeIf { File(it).isFile }
        if (filePath != null) {
            image = withContext(Dispatchers.IO) {
                decodeDisplaySizedBitmap(filePath)?.asImageBitmap()
            }
        }
    }
    return image
}

@Composable
private fun rememberPresetNightSkyImage(context: Context): ImageBitmap? {
    val appContext = context.applicationContext
    var image by remember(appContext) { mutableStateOf(PresetNightSkyBitmapCache.peekImage()) }
    LaunchedEffect(appContext) {
        if (image == null) {
            image = withContext(Dispatchers.IO) {
                PresetNightSkyBitmapCache.getImage(appContext)
            }
        }
    }
    return image
}

private object PresetNightSkyBitmapCache {
    @Volatile
    private var cachedBitmap: Bitmap? = null

    @Volatile
    private var cachedImage: ImageBitmap? = null

    fun peekImage(): ImageBitmap? = cachedImage

    fun getImage(context: Context): ImageBitmap? {
        cachedImage?.let { return it }
        val bitmap = getBitmap(context) ?: return null
        return synchronized(this) {
            cachedImage ?: bitmap.asImageBitmap().also { cachedImage = it }
        }
    }

    fun getBitmap(context: Context): Bitmap? {
        cachedBitmap?.let { return it }
        return synchronized(this) {
            cachedBitmap ?: BitmapFactory.decodeResource(context.resources, R.drawable.preset_night_sky)?.also { decoded ->
                cachedBitmap = decoded
            }
        }
    }
}

fun decodePresetNightSkyBitmap(context: Context): Bitmap? {
    return PresetNightSkyBitmapCache.getBitmap(context.applicationContext)
}

private fun decodeDisplaySizedBitmap(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val sourceMaxSide = max(bounds.outWidth, bounds.outHeight)
    val targetMaxSide = 2400
    var sampleSize = 1
    while (sourceMaxSide / (sampleSize * 2) >= targetMaxSide) {
        sampleSize *= 2
    }

    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    )
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
