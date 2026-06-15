package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BUILTIN_THEME_BACKGROUND_PATH
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.RenderQuality
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_BACKDROP_BLUR_AMOUNT = 4f

data class BlurredBackdropBitmap(
    val image: ImageBitmap,
    val fullWidthPx: Int,
    val fullHeightPx: Int,
    val scale: Float,
    val lensImage: ImageBitmap = image,
    val blurLowImage: ImageBitmap = image,
    val blurMediumImage: ImageBitmap = image,
    val blurHighImage: ImageBitmap = image,
    val blurAmount: Float = 0f
)

internal data class BackdropTextureSet(
    val clearImage: ImageBitmap,
    val blurLowImage: ImageBitmap,
    val blurMediumImage: ImageBitmap,
    val blurHighImage: ImageBitmap,
    val fullWidthPx: Int,
    val fullHeightPx: Int,
    val blurScale: Float
) {
    fun withBlurAmount(amount: Float): BlurredBackdropBitmap = BlurredBackdropBitmap(
        image = blurMediumImage,
        fullWidthPx = fullWidthPx,
        fullHeightPx = fullHeightPx,
        scale = blurScale,
        lensImage = clearImage,
        blurLowImage = blurLowImage,
        blurMediumImage = blurMediumImage,
        blurHighImage = blurHighImage,
        blurAmount = amount.coerceIn(0f, MAX_BACKDROP_BLUR_AMOUNT)
    )
}

internal class BackdropPixelScratch(pixelCount: Int) {
    val source = IntArray(pixelCount)
    val temp = IntArray(pixelCount)
    val output = IntArray(pixelCount)
}

val LocalBlurredBackdrop = compositionLocalOf<BlurredBackdropBitmap?> { null }

private object BlurredBackdropMemoryCache {
    private const val MAX_ENTRIES = 1
    private val entries = object : LinkedHashMap<String, BackdropTextureSet>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BackdropTextureSet>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized fun get(key: String): BackdropTextureSet? = entries[key]
    @Synchronized fun put(key: String, value: BackdropTextureSet) {
        entries[key] = value
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun rememberBlurredBackdropBitmap(
    theme: BackgroundTheme,
    quality: RenderQuality,
    params: BackdropDebugParams = BackdropDebugParams(),
    customBackgroundPath: String? = null
): BlurredBackdropBitmap? {
    val view = LocalView.current
    val context = view.context.applicationContext
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val fallbackWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val fallbackHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val width = max(view.width, fallbackWidth).coerceAtLeast(320)
    val height = max(view.height, fallbackHeight).coerceAtLeast(640)

    val customFile = customBackgroundPath
        ?.takeUnless { it == BUILTIN_THEME_BACKGROUND_PATH }
        ?.let(::File)
    val useDefaultWallpaper = customBackgroundPath == null
    val useThemeSource = customBackgroundPath == BUILTIN_THEME_BACKGROUND_PATH ||
        (customBackgroundPath != null && customFile?.exists() != true)
    val textureParamsKey = params.textureCacheKey(
        includeScale = !useDefaultWallpaper,
        includeCloudAlpha = useThemeSource
    )
    val customKey = when {
        useDefaultWallpaper -> "default_wallpaper_fullres"
        customBackgroundPath == BUILTIN_THEME_BACKGROUND_PATH -> "theme:${theme.storageValue}"
        customFile?.exists() == true ->
            "${customFile.absolutePath}:${customFile.lastModified()}:${customFile.length()}"
        else -> "missing:$customBackgroundPath|fallback-theme:${theme.storageValue}"
    }
    val textureKey = "$width×$height|$textureParamsKey|$customKey"
    var textures by remember(textureKey) {
        mutableStateOf(BlurredBackdropMemoryCache.get(textureKey))
    }

    LaunchedEffect(textureKey) {
        BlurredBackdropMemoryCache.get(textureKey)?.let { cached ->
            textures = cached
            return@LaunchedEffect
        }
        val next = withContext(Dispatchers.Default) {
            runCatching {
                val preset = if (customBackgroundPath == null) {
                    decodePresetNightSkyBitmap(context)
                } else {
                    null
                }
                buildBackdropTextureSet(
                    fullWidth = width,
                    fullHeight = height,
                    theme = theme,
                    params = params.quantizedForTextures(),
                    customBackgroundPath = customBackgroundPath,
                    presetBitmap = preset
                )
            }.getOrNull()
        }
        if (next != null) {
            BlurredBackdropMemoryCache.put(textureKey, next)
            textures = next
        }
    }

    return remember(textures, params.radius) {
        textures?.withBlurAmount(params.radius)
    }
}

private fun BackdropDebugParams.textureCacheKey(
    includeScale: Boolean,
    includeCloudAlpha: Boolean
): String = buildString {
    if (includeScale) append(scale.round2()).append('|')
    append(iterations.roundToInt()).append('|')
    append(brightness.round2()).append('|')
    append(contrast.round2()).append('|')
    append(saturation.round2())
    if (includeCloudAlpha) append('|').append(cloudAlpha.round2())
}

private fun BackdropDebugParams.quantizedForTextures(): BackdropDebugParams = copy(
    scale = scale.round2(),
    iterations = iterations.roundToInt().toFloat(),
    brightness = brightness.round2(),
    contrast = contrast.round2(),
    saturation = saturation.round2(),
    cloudAlpha = cloudAlpha.round2()
)

private fun Float.round2(): Float = (this * 100f).roundToInt() / 100f
