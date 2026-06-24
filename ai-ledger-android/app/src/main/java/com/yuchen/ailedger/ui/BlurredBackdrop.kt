package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Process
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_BACKDROP_BLUR_AMOUNT = 4f
private const val FULL_BACKDROP_BLUR_LEVEL_COUNT = 3
private const val BACKDROP_TEXTURE_DIMENSION_BUCKET = 8

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
 * CPU 模糊、磁盘解码与缓存写入共用单个后台优先级线程，避免与 Compose、RenderThread 和
 * OpenGL 编译争抢冷启动资源。相同纹理请求会合并为同一个 Deferred。
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

    fun request(
        key: String,
        block: suspend () -> BackdropBuildResult?
    ): Deferred<BackdropBuildResult?> = synchronized(inFlight) {
        inFlight[key] ?: BackdropBuildRuntime.scope.async {
            try {
                block()
            } finally {
                synchronized(inFlight) { inFlight.remove(key) }
            }
        }.also { inFlight[key] = it }
    }
}

/**
 * V6 在 V5 纹理缓存基础上同步保存亮度积分表。磁盘命中后直接读取小型二进制表，
 * 不再缩放 medium Bitmap、读取像素并重新计算积分数据。
 */
private object BackdropDiskCache {
    private const val CACHE_VERSION = 6
    private const val MAX_ENTRIES = 2
    private const val ROOT_DIRECTORY = "glass_backdrop_v6"
    private const val METADATA_FILE = "metadata.txt"
    private const val LUMINANCE_FILE = "luminance.bin"

    fun load(context: Context, textureKey: String): BackdropTextureSet? = runCatching {
        val directory = entryDirectory(context, textureKey)
        val metadata = File(directory, METADATA_FILE).takeIf { it.isFile }?.readLines().orEmpty()
        if (metadata.size < 6 || metadata[0].toIntOrNull() != CACHE_VERSION) return@runCatching null

        val fullWidth = metadata[1].toIntOrNull()?.coerceAtLeast(1) ?: return@runCatching null
        val fullHeight = metadata[2].toIntOrNull()?.coerceAtLeast(1) ?: return@runCatching null
        val blurScale = metadata[3].toFloatOrNull()?.takeIf { it > 0f } ?: return@runCatching null
        if (metadata[4] != textureKey) return@runCatching null
        val highAliasesMedium = metadata[5].toBooleanStrictOrNull() ?: false

        val clear = decodeBitmap(File(directory, "clear.png")) ?: return@runCatching null
        val low = decodeBitmap(File(directory, "low.png")) ?: return@runCatching null
        val medium = decodeBitmap(File(directory, "medium.png")) ?: return@runCatching null
        val high = if (highAliasesMedium) {
            medium
        } else {
            decodeBitmap(File(directory, "high.png")) ?: return@runCatching null
        }
        val luminanceMap = readLuminanceMap(File(directory, LUMINANCE_FILE))
            ?: return@runCatching null

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
        BackdropBuildRuntime.scope.launch {
            StartupPerformanceGate.awaitDeferredBusinessWindow()
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
    val textureParamsKey = params.textureCacheKey(
        includeScale = !useDefaultWallpaper,
        includeCloudAlpha = useThemeSource
    )
    val sourceKey = when (source.kind) {
        BackdropSourceKind.DefaultWallpaper -> "default_wallpaper_fullres_v2"
        BackdropSourceKind.BuiltInTheme -> "theme:${theme.storageValue}"
        BackdropSourceKind.CustomImage -> {
            val file = requireNotNull(customFile)
            "custom:${file.absolutePath}:${file.lastModified()}:${file.length()}"
        }
    }
    val textureKey = "v6|$width×$height|$textureParamsKey|levels:$FULL_BACKDROP_BLUR_LEVEL_COUNT|$sourceKey"
    var textures by remember(textureKey) {
        mutableStateOf(BlurredBackdropMemoryCache.get(textureKey))
    }

    LaunchedEffect(textureKey) {
        BlurredBackdropMemoryCache.get(textureKey)?.let { cached ->
            textures = cached
            StartupPerformanceGate.markBackdropWorkFinished(success = true)
            return@LaunchedEffect
        }

        StartupPerformanceGate.awaitInitialTextureBuildWindow()
        val result = BackdropBuildRegistry.request(textureKey) {
            BackdropDiskCache.load(context, textureKey)?.let { cached ->
                BackdropBuildResult(cached, shouldPersist = false)
            } ?: runCatching {
                val preset = if (useDefaultWallpaper) decodePresetNightSkyBitmap(context) else null
                buildBackdropTextureSet(
                    fullWidth = width,
                    fullHeight = height,
                    theme = theme,
                    params = params.quantizedForTextures(),
                    customBackgroundPath = sourcePath,
                    presetBitmap = preset,
                    blurLevelCount = FULL_BACKDROP_BLUR_LEVEL_COUNT
                )
            }.getOrNull()?.let { built ->
                BackdropBuildResult(built, shouldPersist = true)
            }
        }.await()

        if (result != null) {
            BlurredBackdropMemoryCache.put(textureKey, result.textures)
            textures = result.textures
            StartupPerformanceGate.markBackdropWorkFinished(success = true)
            if (result.shouldPersist) {
                BackdropDiskCache.persistAsync(context, textureKey, result.textures)
            }
        } else {
            StartupPerformanceGate.markBackdropWorkFinished(success = false)
        }
    }

    return remember(textures, params.radius) {
        textures?.withBlurAmount(params.radius)
    }
}

private fun quantizeTextureDimension(value: Int): Int =
    ((value + BACKDROP_TEXTURE_DIMENSION_BUCKET - 1) / BACKDROP_TEXTURE_DIMENSION_BUCKET) *
        BACKDROP_TEXTURE_DIMENSION_BUCKET

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
