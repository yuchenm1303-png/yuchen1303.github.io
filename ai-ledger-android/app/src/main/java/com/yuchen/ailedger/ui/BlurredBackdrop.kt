package com.yuchen.ailedger.ui

import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_BACKDROP_BLUR_AMOUNT = 4f
private const val BASE_BACKDROP_BLUR_LEVEL_COUNT = 2
private const val FULL_BACKDROP_BLUR_LEVEL_COUNT = 3

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

/**
 * CPU blur work is intentionally kept off Dispatchers.Default. The first app frames, Compose,
 * RenderThread and the OpenGL shader compiler all compete for the default CPU pool during a cold
 * launch; a single background-priority worker keeps the final pixels identical without stealing
 * latency-sensitive cores from the UI.
 */
private object BackdropBuildRuntime {
    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            },
            "BackdropTextureBuilder"
        ).apply { isDaemon = true }
    }.asCoroutineDispatcher()
}

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

    val source = remember(customBackgroundPath) { resolveBackdropSource(customBackgroundPath) }
    val sourcePath = when (source.kind) {
        BackdropSourceKind.DefaultWallpaper -> null
        BackdropSourceKind.BuiltInTheme -> BUILTIN_THEME_BACKGROUND_PATH
        BackdropSourceKind.CustomImage -> source.customImagePath
    }
    val customFile = source.customImagePath?.let(::File)
    val useDefaultWallpaper = source.kind == BackdropSourceKind.DefaultWallpaper
    val useThemeSource = source.kind == BackdropSourceKind.BuiltInTheme
    val blurLevelCount = requiredBackdropBlurLevelCount(params.radius)
    val textureParamsKey = params.textureCacheKey(
        includeScale = !useDefaultWallpaper,
        includeCloudAlpha = useThemeSource
    )
    val sourceKey = when (source.kind) {
        BackdropSourceKind.DefaultWallpaper -> "default_wallpaper_fullres"
        BackdropSourceKind.BuiltInTheme -> "theme:${theme.storageValue}"
        BackdropSourceKind.CustomImage -> {
            val file = requireNotNull(customFile)
            "custom:${file.absolutePath}:${file.lastModified()}:${file.length()}"
        }
    }
    val textureKey = "$width×$height|$textureParamsKey|levels:$blurLevelCount|$sourceKey"
    var textures by remember(textureKey) {
        mutableStateOf(BlurredBackdropMemoryCache.get(textureKey))
    }

    LaunchedEffect(textureKey) {
        BlurredBackdropMemoryCache.get(textureKey)?.let { cached ->
            textures = cached
            return@LaunchedEffect
        }

        // Let the first Compose frame commit before beginning cold-start bitmap work.
        withFrameNanos { }
        val next = withContext(BackdropBuildRuntime.dispatcher) {
            runCatching {
                val preset = if (useDefaultWallpaper) {
                    decodePresetNightSkyBitmap(context)
                } else {
                    null
                }
                buildBackdropTextureSet(
                    fullWidth = width,
                    fullHeight = height,
                    theme = theme,
                    params = params.quantizedForTextures(),
                    customBackgroundPath = sourcePath,
                    presetBitmap = preset,
                    blurLevelCount = blurLevelCount
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

/**
 * Ordinary Compose glass always consumes the medium level, so low + medium remain mandatory.
 * The high level is only sampled by the OpenGL shader once blurAmount reaches 2.0.
 */
private fun requiredBackdropBlurLevelCount(blurAmount: Float): Int =
    if (blurAmount >= 2f) FULL_BACKDROP_BLUR_LEVEL_COUNT else BASE_BACKDROP_BLUR_LEVEL_COUNT

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
