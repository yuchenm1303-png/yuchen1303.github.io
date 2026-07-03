package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.data.CustomBackgroundToneProcessor
import com.yuchen.ailedger.data.customImageToneCacheKey
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BUILTIN_THEME_BACKGROUND_PATH
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.RenderQuality
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_BACKDROP_BLUR_AMOUNT = 4f
private const val FULL_BACKDROP_BLUR_LEVEL_COUNT = 3
private const val BACKDROP_TEXTURE_DIMENSION_BUCKET = 8
private const val CUSTOM_BACKGROUND_TONE_SETTLE_MS = 180L
private const val MIN_TEXTURE_SCALE = 0.28f
private const val MAX_TEXTURE_SCALE = 0.72f
private const val MAX_TEXTURE_ITERATIONS = 12
private const val MIN_TEXTURE_BRIGHTNESS = 0.40f
private const val MAX_TEXTURE_BRIGHTNESS = 2.20f
private const val MIN_TEXTURE_CONTRAST = 0.50f
private const val MAX_TEXTURE_CONTRAST = 1.80f
private const val MIN_TEXTURE_SATURATION = 0.30f
private const val MAX_TEXTURE_SATURATION = 1.80f

private const val OPENGL_BACKDROP_PHASE_EMPTY = 0
private const val OPENGL_BACKDROP_PHASE_CRITICAL = 1
private const val OPENGL_BACKDROP_PHASE_COMPLETE = 2

data class BlurredBackdropBitmap(
    val image: ImageBitmap,
    val fullWidthPx: Int,
    val fullHeightPx: Int,
    val scale: Float,
    val lensImage: ImageBitmap = image,
    val blurLowImage: ImageBitmap = image,
    val blurMediumImage: ImageBitmap = image,
    val blurHighImage: ImageBitmap = image,
    val blurAmount: Float = 0f,
    val isReady: Boolean = true,
    val luminanceMap: BackdropLuminanceMap = BackdropLuminanceMap.Neutral
)

internal data class BackdropTextureSet(
    val clearImage: ImageBitmap,
    val blurLowImage: ImageBitmap,
    val blurMediumImage: ImageBitmap,
    val blurHighImage: ImageBitmap,
    val luminanceMap: BackdropLuminanceMap,
    val fullWidthPx: Int,
    val fullHeightPx: Int,
    val blurScale: Float
) {
    fun withBlurAmount(amount: Float): BlurredBackdropBitmap = BlurredBackdropBitmap(
        image = blurMediumImage,
        fullWidthPx = fullWidthPx,
        fullHeightPx = fullHeightPx,
        scale = blurScale,
        // lensImage 现在只代表同一模糊金字塔的 level-0 原始背景，不再作为额外叠加层。
        lensImage = clearImage,
        blurLowImage = blurLowImage,
        blurMediumImage = blurMediumImage,
        blurHighImage = blurHighImage,
        blurAmount = amount.coerceIn(0f, MAX_BACKDROP_BLUR_AMOUNT),
        isReady = true,
        luminanceMap = luminanceMap
    )
}

internal class BackdropPixelScratch(pixelCount: Int) {
    val source = IntArray(pixelCount)
    val temp = IntArray(pixelCount)
    val output = IntArray(pixelCount)
}

val LocalBlurredBackdrop = compositionLocalOf<BlurredBackdropBitmap?> { null }

/**
 * 只有 Shell OpenGL Host 读取阶段性纹理。critical 阶段提供 level-0 和 medium；普通
 * Card/Chip/Floating/Nav/Flex 继续等待完整金字塔，不会接触半成品。
 */
internal object OpenGlStartupBackdropBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeTextureKey: String? = null
    private var publishedPhase = OPENGL_BACKDROP_PHASE_EMPTY
    private var requestedBlurAmount = 0f

    var backdrop by mutableStateOf<BlurredBackdropBitmap?>(null)
        private set

    fun activate(textureKey: String, blurAmount: Float) = onMain {
        val safeAmount = blurAmount.coerceIn(0f, MAX_BACKDROP_BLUR_AMOUNT)
        if (activeTextureKey != textureKey) {
            activeTextureKey = textureKey
            publishedPhase = OPENGL_BACKDROP_PHASE_EMPTY
            requestedBlurAmount = safeAmount
            backdrop = null
        } else {
            requestedBlurAmount = safeAmount
            val current = backdrop
            if (current != null && current.blurAmount != safeAmount) {
                backdrop = current.copy(blurAmount = safeAmount)
            }
        }
    }

    fun publishCritical(textureKey: String, value: BlurredBackdropBitmap) {
        publish(textureKey, value, OPENGL_BACKDROP_PHASE_CRITICAL)
    }

    fun publishComplete(textureKey: String, value: BlurredBackdropBitmap) {
        publish(textureKey, value, OPENGL_BACKDROP_PHASE_COMPLETE)
    }

    fun updateBlurAmount(textureKey: String, amount: Float) = onMain {
        if (activeTextureKey == textureKey) {
            val safeAmount = amount.coerceIn(0f, MAX_BACKDROP_BLUR_AMOUNT)
            requestedBlurAmount = safeAmount
            val current = backdrop
            if (current != null && current.blurAmount != safeAmount) {
                backdrop = current.copy(blurAmount = safeAmount)
            }
        }
    }

    fun clear(textureKey: String) = onMain {
        if (activeTextureKey == textureKey) {
            activeTextureKey = null
            publishedPhase = OPENGL_BACKDROP_PHASE_EMPTY
            requestedBlurAmount = 0f
            backdrop = null
        }
    }

    private fun publish(textureKey: String, value: BlurredBackdropBitmap, phase: Int) = onMain {
        if (activeTextureKey == textureKey && phase >= publishedPhase) {
            publishedPhase = phase
            backdrop = if (value.blurAmount == requestedBlurAmount) {
                value
            } else {
                value.copy(blurAmount = requestedBlurAmount)
            }
        }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }
}

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

    val scope = CoroutineScope(SupervisorJob() + dispatcher)
}

private object BackdropDiskRuntime {
    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            },
            "BackdropDiskCacheIO"
        ).apply { isDaemon = true }
    }.asCoroutineDispatcher()

    val scope = CoroutineScope(SupervisorJob() + dispatcher)
}

private object BlurredBackdropMemoryCache {
    private const val MAX_ENTRIES = 1
    private val entries = object : LinkedHashMap<String, BackdropTextureSet>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BackdropTextureSet>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): BackdropTextureSet? = entries[key]

    @Synchronized
    fun put(key: String, value: BackdropTextureSet) {
        entries[key] = value
    }
}

private data class BackdropBuildResult(
    val textures: BackdropTextureSet,
    val shouldPersist: Boolean
)

private object BackdropBuildRegistry {
    private val inFlight = mutableMapOf<String, Deferred<BackdropBuildResult?>>()
    private var latestRequestedKey: String? = null

    fun markLatest(key: String) = synchronized(inFlight) {
        latestRequestedKey = key
    }

    fun request(
        key: String,
        block: suspend () -> BackdropBuildResult?
    ): Deferred<BackdropBuildResult?> = synchronized(inFlight) {
        latestRequestedKey = key
        inFlight[key] ?: BackdropBuildRuntime.scope.async {
            try {
                if (!isLatest(key)) return@async null
                block()
            } finally {
                synchronized(inFlight) { inFlight.remove(key) }
            }
        }.also { inFlight[key] = it }
    }

    fun isLatest(key: String): Boolean = synchronized(inFlight) {
        key == latestRequestedKey
    }
}

/** V7 使零迭代和 level-0 单背景语义与旧缓存彻底隔离。 */
private object BackdropDiskCache {
    private const val CACHE_VERSION = 7
    private const val MAX_ENTRIES = 2
    private const val ROOT_DIRECTORY = "glass_backdrop_v7"
    private const val LEGACY_ROOT_DIRECTORY = "glass_backdrop_v6"
    private const val METADATA_FILE = "metadata.txt"
    private const val LUMINANCE_FILE = "luminance.bin"

    private var latestPersistGeneration = 0L

    fun load(
        context: Context,
        textureKey: String,
        onCriticalReady: ((BackdropTextureSet) -> Unit)? = null,
    ): BackdropTextureSet? = runCatching {
        val directory = entryDirectory(context, textureKey)
        val metadata = File(directory, METADATA_FILE).takeIf { it.isFile }?.readLines().orEmpty()
        if (metadata.size < 6 || metadata[0].toIntOrNull() != CACHE_VERSION) return@runCatching null

        val fullWidth = metadata[1].toIntOrNull()?.coerceAtLeast(1) ?: return@runCatching null
        val fullHeight = metadata[2].toIntOrNull()?.coerceAtLeast(1) ?: return@runCatching null
        val blurScale = metadata[3].toFloatOrNull()?.takeIf { it > 0f } ?: return@runCatching null
        if (metadata[4] != textureKey) return@runCatching null
        val highAliasesMedium = metadata[5].toBooleanStrictOrNull() ?: false

        val clear = decodeBitmap(File(directory, "clear.png")) ?: return@runCatching null
        val medium = decodeBitmap(File(directory, "medium.png")) ?: return@runCatching null
        onCriticalReady?.invoke(
            BackdropTextureSet(
                clearImage = clear,
                blurLowImage = medium,
                blurMediumImage = medium,
                blurHighImage = medium,
                luminanceMap = BackdropLuminanceMap.Neutral,
                fullWidthPx = fullWidth,
                fullHeightPx = fullHeight,
                blurScale = blurScale,
            )
        )

        val luminanceMap = readLuminanceMap(File(directory, LUMINANCE_FILE))
            ?.takeIf { it.matchesDimensions(fullWidth, fullHeight) }
            ?: return@runCatching null
        val low = decodeBitmap(File(directory, "low.png")) ?: return@runCatching null
        val high = if (highAliasesMedium) {
            medium
        } else {
            decodeBitmap(File(directory, "high.png")) ?: return@runCatching null
        }

        directory.setLastModified(System.currentTimeMillis())
        BackdropTextureSet(
            clearImage = clear,
            blurLowImage = low,
            blurMediumImage = medium,
            blurHighImage = high,
            luminanceMap = luminanceMap,
            fullWidthPx = fullWidth,
            fullHeightPx = fullHeight,
            blurScale = blurScale
        )
    }.getOrNull()

    fun persistAsync(context: Context, textureKey: String, textures: BackdropTextureSet) {
        val generation = synchronized(this) { ++latestPersistGeneration }
        BackdropDiskRuntime.scope.launch {
            StartupPerformanceGate.awaitDeferredBusinessWindow()
            if (synchronized(BackdropDiskCache) { generation != latestPersistGeneration }) {
                return@launch
            }
            runCatching { persist(context, textureKey, textures) }
        }
    }

    private fun persist(context: Context, textureKey: String, textures: BackdropTextureSet) {
        val root = cacheRoot(context)
        val target = entryDirectory(context, textureKey)
        val temporary = File(root, ".${target.name}.tmp-${System.nanoTime()}")
        temporary.deleteRecursively()
        if (!temporary.mkdirs()) return

        val highAliasesMedium = textures.blurHighImage === textures.blurMediumImage
        val wroteAll =
            writeBitmap(textures.clearImage, File(temporary, "clear.png")) &&
                writeBitmap(textures.blurLowImage, File(temporary, "low.png")) &&
                writeBitmap(textures.blurMediumImage, File(temporary, "medium.png")) &&
                (highAliasesMedium || writeBitmap(textures.blurHighImage, File(temporary, "high.png"))) &&
                writeLuminanceMap(textures.luminanceMap, File(temporary, LUMINANCE_FILE))

        if (!wroteAll) {
            temporary.deleteRecursively()
            return
        }

        File(temporary, METADATA_FILE).writeText(
            buildString {
                append(CACHE_VERSION).append('\n')
                append(textures.fullWidthPx).append('\n')
                append(textures.fullHeightPx).append('\n')
                append(textures.blurScale).append('\n')
                append(textureKey).append('\n')
                append(highAliasesMedium)
            }
        )

        target.deleteRecursively()
        if (!temporary.renameTo(target)) {
            temporary.deleteRecursively()
            return
        }
        target.setLastModified(System.currentTimeMillis())
        trimOldEntries(root)
        File(context.cacheDir, LEGACY_ROOT_DIRECTORY).deleteRecursively()
    }

    private fun decodeBitmap(file: File): ImageBitmap? {
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        ) ?: return null
        bitmap.prepareToDraw()
        return bitmap.asImageBitmap()
    }

    private fun readLuminanceMap(file: File): BackdropLuminanceMap? {
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                BackdropLuminanceMap.readFrom(input)
            }
        }.getOrNull()
    }

    private fun writeBitmap(image: ImageBitmap, file: File): Boolean =
        FileOutputStream(file).use { output ->
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }

    private fun writeLuminanceMap(map: BackdropLuminanceMap, file: File): Boolean = runCatching {
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { output ->
            map.writeTo(output)
        }
        true
    }.getOrDefault(false)

    private fun cacheRoot(context: Context): File =
        File(context.cacheDir, ROOT_DIRECTORY).apply { mkdirs() }

    private fun entryDirectory(context: Context, textureKey: String): File =
        File(cacheRoot(context), stableCacheName(textureKey))

    private fun stableCacheName(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }.take(32)
    }

    private fun trimOldEntries(root: File) {
        root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_ENTRIES)
            .forEach { it.deleteRecursively() }
        root.listFiles()
            .orEmpty()
            .filter { it.name.startsWith('.') }
            .forEach { it.deleteRecursively() }
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
    val width = quantizeTextureDimension(max(view.width, fallbackWidth).coerceAtLeast(320))
    val height = quantizeTextureDimension(max(view.height, fallbackHeight).coerceAtLeast(640))

    val source = remember(customBackgroundPath) { resolveBackdropSource(customBackgroundPath) }
    val sourcePath = when (source.kind) {
        BackdropSourceKind.DefaultWallpaper -> null
        BackdropSourceKind.BuiltInTheme -> BUILTIN_THEME_BACKGROUND_PATH
        BackdropSourceKind.CustomImage -> source.customImagePath
    }
    val customFile = source.customImagePath?.let(::File)
    val useDefaultWallpaper = source.kind == BackdropSourceKind.DefaultWallpaper
    val useThemeSource = source.kind == BackdropSourceKind.BuiltInTheme
    val useCustomImage = source.kind == BackdropSourceKind.CustomImage
    val textureParamsKey = params.textureCacheKey(
        includeScale = !useDefaultWallpaper,
        includeThemeLayers = useThemeSource,
        includeCustomTone = useCustomImage,
    )
    val sourceKey = when (source.kind) {
        BackdropSourceKind.DefaultWallpaper -> "default_wallpaper_fullres_v2"
        BackdropSourceKind.BuiltInTheme -> "theme:${theme.storageValue}"
        BackdropSourceKind.CustomImage -> {
            val file = requireNotNull(customFile)
            val identityFile = CustomBackgroundToneProcessor.sourceIdentityFile(file)
                .takeIf { it.isFile }
                ?: file
            "custom:${identityFile.absolutePath}:${identityFile.lastModified()}:${identityFile.length()}"
        }
    }
    val textureKey = "v7|$width×$height|$textureParamsKey|levels:$FULL_BACKDROP_BLUR_LEVEL_COUNT|$sourceKey"
    val blurAmount = params.radius.coerceIn(0f, MAX_BACKDROP_BLUR_AMOUNT)
    var textures by remember(textureKey) {
        mutableStateOf(BlurredBackdropMemoryCache.get(textureKey))
    }

    DisposableEffect(textureKey) {
        OpenGlStartupBackdropBridge.activate(textureKey, blurAmount)
        onDispose { OpenGlStartupBackdropBridge.clear(textureKey) }
    }

    LaunchedEffect(textureKey, blurAmount) {
        OpenGlStartupBackdropBridge.updateBlurAmount(textureKey, blurAmount)
    }

    LaunchedEffect(textureKey) {
        BackdropBuildRegistry.markLatest(textureKey)
        BlurredBackdropMemoryCache.get(textureKey)?.let { cached ->
            textures = cached
            OpenGlStartupBackdropBridge.publishComplete(textureKey, cached.withBlurAmount(blurAmount))
            StartupPerformanceGate.markBackdropWorkFinished(success = true)
            return@LaunchedEffect
        }

        val result = BackdropBuildRegistry.request(textureKey) {
            val diskCached = BackdropDiskCache.load(
                context = context,
                textureKey = textureKey,
                onCriticalReady = { critical ->
                    OpenGlStartupBackdropBridge.publishCritical(
                        textureKey,
                        critical.withBlurAmount(blurAmount)
                    )
                },
            )
            if (diskCached != null) {
                BackdropBuildResult(diskCached, shouldPersist = false)
            } else {
                if (useCustomImage) delay(CUSTOM_BACKGROUND_TONE_SETTLE_MS)
                if (!BackdropBuildRegistry.isLatest(textureKey)) {
                    null
                } else {
                    StartupPerformanceGate.awaitInitialTextureBuildWindow()
                    if (!BackdropBuildRegistry.isLatest(textureKey)) {
                        null
                    } else {
                        runCatching {
                            val preset = if (useDefaultWallpaper) decodePresetNightSkyBitmap(context) else null
                            val quantizedParams = params.quantizedForTextures()
                            val buildTextures: (String?) -> BackdropTextureSet = { resolvedPath ->
                                buildBackdropTextureSet(
                                    fullWidth = width,
                                    fullHeight = height,
                                    theme = theme,
                                    params = quantizedParams,
                                    customBackgroundPath = resolvedPath,
                                    presetBitmap = preset,
                                    blurLevelCount = FULL_BACKDROP_BLUR_LEVEL_COUNT,
                                    onCriticalReady = { critical ->
                                        OpenGlStartupBackdropBridge.publishCritical(
                                            textureKey,
                                            critical.withBlurAmount(blurAmount)
                                        )
                                    }
                                )
                            }
                            if (useCustomImage) {
                                CustomBackgroundToneProcessor.withProcessedFile(
                                    displayPath = requireNotNull(sourcePath),
                                    params = quantizedParams,
                                ) { processedFile ->
                                    buildTextures(processedFile.absolutePath)
                                }
                            } else {
                                buildTextures(sourcePath)
                            }
                        }.getOrNull()?.let { built ->
                            BackdropBuildResult(built, shouldPersist = true)
                        }
                    }
                }
            }
        }.await()

        if (result != null) {
            BlurredBackdropMemoryCache.put(textureKey, result.textures)
            textures = result.textures
            OpenGlStartupBackdropBridge.publishComplete(
                textureKey,
                result.textures.withBlurAmount(blurAmount)
            )
            StartupPerformanceGate.markBackdropWorkFinished(success = true)
            if (result.shouldPersist) {
                BackdropDiskCache.persistAsync(context, textureKey, result.textures)
            }
        } else {
            StartupPerformanceGate.markBackdropWorkFinished(success = false)
        }
    }

    return remember(textures, blurAmount) {
        textures?.withBlurAmount(blurAmount)
    }
}

private fun quantizeTextureDimension(value: Int): Int =
    ((value + BACKDROP_TEXTURE_DIMENSION_BUCKET - 1) / BACKDROP_TEXTURE_DIMENSION_BUCKET) *
        BACKDROP_TEXTURE_DIMENSION_BUCKET

private fun BackdropDebugParams.textureCacheKey(
    includeScale: Boolean,
    includeThemeLayers: Boolean,
    includeCustomTone: Boolean,
): String = buildString {
    if (includeScale) append(effectiveTextureScale().round2()).append('|')
    append(effectiveTextureIterations()).append('|')
    append(brightness.coerceIn(MIN_TEXTURE_BRIGHTNESS, MAX_TEXTURE_BRIGHTNESS).round2()).append('|')
    append(contrast.coerceIn(MIN_TEXTURE_CONTRAST, MAX_TEXTURE_CONTRAST).round2()).append('|')
    append(saturation.coerceIn(MIN_TEXTURE_SATURATION, MAX_TEXTURE_SATURATION).round2())
    if (includeThemeLayers) append('|').append(cloudAlpha.round2())
    if (includeCustomTone) append('|').append(customImageToneCacheKey())
}

private fun BackdropDebugParams.quantizedForTextures(): BackdropDebugParams = copy(
    scale = effectiveTextureScale().round2(),
    iterations = effectiveTextureIterations().toFloat(),
    brightness = brightness.coerceIn(MIN_TEXTURE_BRIGHTNESS, MAX_TEXTURE_BRIGHTNESS).round2(),
    contrast = contrast.coerceIn(MIN_TEXTURE_CONTRAST, MAX_TEXTURE_CONTRAST).round2(),
    saturation = saturation.coerceIn(MIN_TEXTURE_SATURATION, MAX_TEXTURE_SATURATION).round2(),
    customImageBrightness = customImageBrightness.round2(),
    customImageHighlightStart = customImageHighlightStart.round2(),
    customImageHighlightLimit = customImageHighlightLimit.round2(),
    cloudAlpha = cloudAlpha.round2(),
)

private fun BackdropDebugParams.effectiveTextureScale(): Float =
    scale.coerceIn(MIN_TEXTURE_SCALE, MAX_TEXTURE_SCALE)

private fun BackdropDebugParams.effectiveTextureIterations(): Int =
    iterations.roundToInt().coerceIn(0, MAX_TEXTURE_ITERATIONS)

private fun Float.round2(): Float = (this * 100f).roundToInt() / 100f
