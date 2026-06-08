package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
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
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

data class BlurredBackdropBitmap(
    val image: ImageBitmap,
    val fullWidthPx: Int,
    val fullHeightPx: Int,
    val scale: Float,
    val lensImage: ImageBitmap = image
)

val LocalBlurredBackdrop = compositionLocalOf<BlurredBackdropBitmap?> { null }

private object BlurredBackdropMemoryCache {
    private const val MAX_ENTRIES = 4
    private val entries = object : LinkedHashMap<String, BlurredBackdropBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BlurredBackdropBitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(key: String): BlurredBackdropBitmap? = entries[key]

    @Synchronized
    fun put(key: String, value: BlurredBackdropBitmap) {
        entries[key] = value
    }
}

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
    val paramsKey = params.cacheKey()
    val customKey = when (customBackgroundPath) {
        null -> "default_wallpaper_lowres"
        BUILTIN_THEME_BACKGROUND_PATH -> "theme:${theme.storageValue}"
        else -> {
            val file = File(customBackgroundPath)
            if (file.exists()) "${file.absolutePath}:${file.lastModified()}:${file.length()}" else "missing:$customBackgroundPath"
        }
    }
    val cacheKey = "$width×$height|${theme.storageValue}|${quality.storageValue}|$paramsKey|$customKey"
    var bitmap by remember(cacheKey) { mutableStateOf(BlurredBackdropMemoryCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        BlurredBackdropMemoryCache.get(cacheKey)?.let { cached ->
            bitmap = cached
            BackdropTextureWarmup.warmUp(cached)
            return@LaunchedEffect
        }

        if (bitmap != null) delay(120)
        val next = withContext(Dispatchers.Default) {
            runCatching {
                val preset = if (customBackgroundPath == null) decodePresetNightSkyBitmap(context) else null
                buildBlurredBackdropBitmap(
                    fullWidth = width,
                    fullHeight = height,
                    theme = theme,
                    params = params.quantized(),
                    customBackgroundPath = customBackgroundPath,
                    presetBitmap = preset
                ).also { backdrop ->
                    BackdropTextureWarmup.warmUp(backdrop)
                }
            }.getOrNull()
        }
        if (next != null) {
            BlurredBackdropMemoryCache.put(cacheKey, next)
            bitmap = next
        }
    }
    return bitmap
}

private object BackdropTextureWarmup {
    @Volatile private var warmedBlur: Bitmap? = null
    @Volatile private var warmedLens: Bitmap? = null

    fun warmUp(backdrop: BlurredBackdropBitmap) {
        val blur = backdrop.image.asAndroidBitmap()
        val lens = backdrop.lensImage.asAndroidBitmap()
        if (blur === warmedBlur && lens === warmedLens) return
        synchronized(this) {
            if (blur === warmedBlur && lens === warmedLens) return
            runCatching { warmUpEglTextureUpload(blur, lens) }
            warmedBlur = blur
            warmedLens = lens
        }
    }
}

private fun warmUpEglTextureUpload(blur: Bitmap, lens: Bitmap) {
    val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    if (display == EGL14.EGL_NO_DISPLAY) return
    val version = IntArray(2)
    if (!EGL14.eglInitialize(display, version, 0, version, 1)) return

    var context: EGLContext = EGL14.EGL_NO_CONTEXT
    var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    try {
        val config = chooseWarmupConfig(display) ?: return
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0
        )
        if (context == EGL14.EGL_NO_CONTEXT) return
        surface = EGL14.eglCreatePbufferSurface(
            display,
            config,
            intArrayOf(EGL14.EGL_WIDTH, 8, EGL14.EGL_HEIGHT, 8, EGL14.EGL_NONE),
            0
        )
        if (surface == EGL14.EGL_NO_SURFACE) return
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return

        val program = buildWarmupProgram()
        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        configureWarmupTexture(textures[0])
        configureWarmupTexture(textures[1])
        uploadWarmupBitmap(textures[0], blur)
        uploadWarmupBitmap(textures[1], lens)

        GLES20.glViewport(0, 0, 8, 8)
        GLES20.glUseProgram(program)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val sampler = GLES20.glGetUniformLocation(program, "uTexture")
        val vertices = warmupVertices()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glUniform1i(sampler, 0)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDeleteTextures(2, textures, 0)
        GLES20.glDeleteProgram(program)
        GLES20.glFinish()
    } finally {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }
}

private fun chooseWarmupConfig(display: EGLDisplay): EGLConfig? {
    val attrs = intArrayOf(
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
        EGL14.EGL_RED_SIZE, 8,
        EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8,
        EGL14.EGL_ALPHA_SIZE, 8,
        EGL14.EGL_DEPTH_SIZE, 0,
        EGL14.EGL_STENCIL_SIZE, 0,
        EGL14.EGL_NONE
    )
    val configs = arrayOfNulls<EGLConfig>(1)
    val count = IntArray(1)
    val ok = EGL14.eglChooseConfig(display, attrs, 0, configs, 0, configs.size, count, 0)
    return if (ok && count[0] > 0) configs[0] else null
}

private fun configureWarmupTexture(id: Int) {
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
}

private fun uploadWarmupBitmap(id: Int, bitmap: Bitmap) {
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
    GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
}

private fun warmupVertices(): FloatBuffer {
    return ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }
}

private fun buildWarmupProgram(): Int {
    val vertex = compileWarmupShader(
        GLES20.GL_VERTEX_SHADER,
        """
            attribute vec2 aPosition;
            varying vec2 vUv;
            void main() {
                vUv = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """.trimIndent()
    )
    val fragment = compileWarmupShader(
        GLES20.GL_FRAGMENT_SHADER,
        """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vUv);
            }
        """.trimIndent()
    )
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertex)
    GLES20.glAttachShader(program, fragment)
    GLES20.glLinkProgram(program)
    GLES20.glDeleteShader(vertex)
    GLES20.glDeleteShader(fragment)
    return program
}

private fun compileWarmupShader(type: Int, source: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    return shader
}

private fun BackdropDebugParams.cacheKey(): String = buildString {
    append(scale.round2()).append('|')
    append(radius.roundToInt()).append('|')
    append(iterations.roundToInt()).append('|')
    append(brightness.round2()).append('|')
    append(contrast.round2()).append('|')
    append(saturation.round2()).append('|')
    append(cloudAlpha.round2()).append('|')
    append(cloudSoftness.round2()).append('|')
    append(cloudStretchX.round2()).append('|')
    append(cloudStretchY.round2()).append('|')
    append(cloudHighlightAlpha.round2()).append('|')
    append(moonScale.round2()).append('|')
    append(moonHaloAlpha.round2()).append('|')
    append(moonRimAlpha.round2())
}

private fun BackdropDebugParams.quantized(): BackdropDebugParams = copy(
    scale = scale.round2(),
    radius = radius.roundToInt().toFloat(),
    iterations = iterations.roundToInt().toFloat(),
    brightness = brightness.round2(),
    contrast = contrast.round2(),
    saturation = saturation.round2(),
    cloudAlpha = cloudAlpha.round2(),
    cloudSoftness = cloudSoftness.round2(),
    cloudStretchX = cloudStretchX.round2(),
    cloudStretchY = cloudStretchY.round2(),
    cloudHighlightAlpha = cloudHighlightAlpha.round2(),
    moonScale = moonScale.round2(),
    moonHaloAlpha = moonHaloAlpha.round2(),
    moonRimAlpha = moonRimAlpha.round2()
)

private fun Float.round2(): Float = (this * 100f).roundToInt() / 100f

private fun buildBlurredBackdropBitmap(
    fullWidth: Int,
    fullHeight: Int,
    theme: BackgroundTheme,
    params: BackdropDebugParams,
    customBackgroundPath: String?,
    presetBitmap: Bitmap?
): BlurredBackdropBitmap {
    val useDefaultWallpaper = customBackgroundPath == null
    val useThemePreset = customBackgroundPath == BUILTIN_THEME_BACKGROUND_PATH
    val sourceScale = if (useDefaultWallpaper) 0.24f else params.scale.coerceIn(0.18f, 0.72f)
    val smallWidth = (fullWidth * sourceScale).roundToInt().coerceAtLeast(128)
    val smallHeight = (fullHeight * sourceScale).roundToInt().coerceAtLeast(216)
    val effectiveScale = smallWidth.toFloat() / fullWidth.toFloat()

    val source = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
    val drewCustom = if (useThemePreset) false else drawCustomImageBackdropSource(source, customBackgroundPath)
    if (!drewCustom) {
        if (useThemePreset) drawAndroidBackdropSource(source, theme, params)
        else if (presetBitmap != null) drawBitmapCoverIntoTarget(presetBitmap, source)
        else drawAndroidBackdropSource(source, theme, params)
    }

    val lensTuned = tuneBitmapTone(
        input = source,
        brightness = params.brightness.coerceIn(0.70f, 1.35f),
        contrast = params.contrast.coerceIn(0.70f, 1.35f),
        saturation = params.saturation.coerceIn(0.50f, 1.60f)
    )
    val blurRadius = if (useDefaultWallpaper) params.radius.roundToInt().coerceIn(1, 18) else params.radius.roundToInt().coerceIn(1, 32)
    val blurIterations = if (useDefaultWallpaper) params.iterations.roundToInt().coerceIn(1, 3) else params.iterations.roundToInt().coerceIn(1, 8)
    val blurred = boxBlur(input = source, radius = blurRadius, iterations = blurIterations)
    val tuned = tuneBitmapTone(
        input = blurred,
        brightness = params.brightness.coerceIn(0.70f, 1.35f),
        contrast = params.contrast.coerceIn(0.70f, 1.35f),
        saturation = params.saturation.coerceIn(0.50f, 1.60f)
    )
    if (blurred !== source && !blurred.isRecycled) blurred.recycle()
    if (!source.isRecycled) source.recycle()

    return BlurredBackdropBitmap(
        image = tuned.asImageBitmap(),
        fullWidthPx = fullWidth,
        fullHeightPx = fullHeight,
        scale = effectiveScale,
        lensImage = lensTuned.asImageBitmap()
    )
}

private fun drawCustomImageBackdropSource(target: Bitmap, path: String?): Boolean {
    val file = path?.let(::File) ?: return false
    if (!file.exists()) return false
    val source = decodeCustomBitmapForTarget(file, target.width, target.height) ?: return false
    drawBitmapCoverIntoTarget(source, target)
    source.recycle()
    return true
}

private fun decodeCustomBitmapForTarget(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val targetMax = max(targetWidth, targetHeight).coerceAtLeast(1)
    val sourceMax = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (sourceMax / (sample * 2) >= targetMax * 2) sample *= 2
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    )
}

private fun drawBitmapCoverIntoTarget(source: Bitmap, target: Bitmap) {
    val canvas = Canvas(target)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    val srcW = source.width
    val srcH = source.height
    val dstW = target.width
    val dstH = target.height
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
    canvas.drawBitmap(source, Rect(cropX, cropY, cropX + cropW, cropY + cropH), RectF(0f, 0f, dstW.toFloat(), dstH.toFloat()), paint)
}

private val ANDROID_BACKDROP_CLOUD_Y_FRACTIONS = floatArrayOf(0.12f, 0.28f, 0.48f, 0.68f)

private fun drawAndroidBackdropSource(bitmap: Bitmap, theme: BackgroundTheme, params: BackdropDebugParams) {
    val canvas = Canvas(bitmap)
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val p = androidWeatherPalette(theme)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(p.top, p.upper, p.mid, p.horizon, p.bottom), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    drawGlow(canvas, paint, w * 0.82f, h * 0.12f, w * 0.52f, h * 0.26f, p.violet, 0.38f)
    drawGlow(canvas, paint, w * 0.34f, h * 0.82f, w * 0.70f, h * 0.34f, p.warm, 0.34f)
    drawGlow(canvas, paint, w * 0.24f, h * 0.44f, w * 0.46f, h * 0.28f, p.blue, 0.22f)
    val cloudAlpha = params.cloudAlpha.coerceIn(0.25f, 2.2f)
    for (index in ANDROID_BACKDROP_CLOUD_Y_FRACTIONS.indices) {
        val y = ANDROID_BACKDROP_CLOUD_Y_FRACTIONS[index]
        drawGlow(canvas, paint, w * (0.12f + index * 0.24f), h * y, w * 0.42f, h * 0.12f, p.cloudLight, 0.22f * cloudAlpha)
        drawGlow(canvas, paint, w * (0.32f + index * 0.18f), h * (y + 0.05f), w * 0.36f, h * 0.10f, p.cloudWarm, 0.16f * cloudAlpha)
    }
}

private fun drawGlow(canvas: Canvas, paint: Paint, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, alpha: Float) {
    paint.shader = RadialGradient(cx, cy, max(rx, ry), intArrayOf(withAlpha(color, alpha), withAlpha(color, alpha * 0.28f), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
    paint.shader = null
}

private fun boxBlur(input: Bitmap, radius: Int, iterations: Int): Bitmap {
    if (radius <= 0 || iterations <= 0) return input

    val width = input.width
    val height = input.height
    val pixelCount = width * height
    var source = IntArray(pixelCount)
    val temp = IntArray(pixelCount)
    var output = IntArray(pixelCount)
    input.getPixels(source, 0, width, 0, 0, width, height)

    val window = radius * 2 + 1
    repeat(iterations) { iteration ->
        boxBlurHorizontal(source, temp, width, height, radius, window)
        boxBlurVertical(temp, output, width, height, radius, window)
        if (iteration < iterations - 1) {
            val reusable = source
            source = output
            output = reusable
        }
    }
    return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
}

private fun boxBlurHorizontal(source: IntArray, temp: IntArray, width: Int, height: Int, radius: Int, window: Int) {
    for (y in 0 until height) {
        var a = 0; var r = 0; var g = 0; var b = 0
        val row = y * width
        for (i in -radius..radius) {
            val x = i.coerceIn(0, width - 1)
            val c = source[row + x]
            a += c ushr 24; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
        }
        for (x in 0 until width) {
            temp[row + x] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val remove = source[row + (x - radius).coerceIn(0, width - 1)]
            val add = source[row + (x + radius + 1).coerceIn(0, width - 1)]
            a += (add ushr 24) - (remove ushr 24)
            r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun boxBlurVertical(temp: IntArray, output: IntArray, width: Int, height: Int, radius: Int, window: Int) {
    for (x in 0 until width) {
        var a = 0; var r = 0; var g = 0; var b = 0
        for (i in -radius..radius) {
            val y = i.coerceIn(0, height - 1)
            val c = temp[y * width + x]
            a += c ushr 24; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
        }
        for (y in 0 until height) {
            output[y * width + x] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val remove = temp[(y - radius).coerceIn(0, height - 1) * width + x]
            val add = temp[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            a += (add ushr 24) - (remove ushr 24)
            r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun tuneBitmapTone(input: Bitmap, brightness: Float, contrast: Float, saturation: Float): Bitmap {
    val width = input.width
    val height = input.height
    val pixels = IntArray(width * height)
    input.getPixels(pixels, 0, width, 0, 0, width, height)
    pixels.forEachIndexed { index, color ->
        val a = color ushr 24
        val r0 = ((color shr 16) and 0xFF).toFloat()
        val g0 = ((color shr 8) and 0xFF).toFloat()
        val b0 = (color and 0xFF).toFloat()
        val gray = r0 * 0.2126f + g0 * 0.7152f + b0 * 0.0722f
        val sr = gray + (r0 - gray) * saturation
        val sg = gray + (g0 - gray) * saturation
        val sb = gray + (b0 - gray) * saturation
        val r = (((sr - 128f) * contrast + 128f) * brightness).roundToInt().coerceIn(0, 255)
        val g = (((sg - 128f) * contrast + 128f) * brightness).roundToInt().coerceIn(0, 255)
        val b = (((sb - 128f) * contrast + 128f) * brightness).roundToInt().coerceIn(0, 255)
        pixels[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private data class AndroidWeatherPalette(
    val top: Int, val upper: Int, val mid: Int, val horizon: Int, val bottom: Int,
    val violet: Int, val warm: Int, val blue: Int, val cloudLight: Int, val cloudWarm: Int
)

private fun androidWeatherPalette(theme: BackgroundTheme): AndroidWeatherPalette = when (theme) {
    BackgroundTheme.Aurora -> AndroidWeatherPalette(rgb(0x06,0x14,0x26), rgb(0x26,0x3A,0x68), rgb(0x59,0x6B,0x99), rgb(0x8B,0x71,0x86), rgb(0xB7,0x83,0x69), rgb(0xB7,0x9A,0xFF), rgb(0xFF,0xA0,0x6E), rgb(0x5C,0xA9,0xE6), rgb(0xB7,0xB6,0xE8), rgb(0xD4,0xA1,0x9A))
    BackgroundTheme.Jade -> AndroidWeatherPalette(rgb(0x07,0x1A,0x22), rgb(0x24,0x46,0x5F), rgb(0x5E,0x7E,0x95), rgb(0x83,0xA3,0x94), rgb(0xB5,0x9B,0x79), rgb(0x8E,0xC2,0xDD), rgb(0xE8,0xB3,0x7F), rgb(0x58,0xC0,0xBC), rgb(0xAE,0xC7,0xD8), rgb(0xC7,0xAE,0x92))
    BackgroundTheme.Sunset -> AndroidWeatherPalette(rgb(0x20,0x18,0x2D), rgb(0x49,0x36,0x5E), rgb(0x73,0x5C,0x83), rgb(0xA8,0x75,0x86), rgb(0xD1,0x97,0x6B), rgb(0xC0,0x98,0xFF), rgb(0xFF,0x9A,0x64), rgb(0x75,0x87,0xD5), rgb(0xC6,0xB3,0xE6), rgb(0xE0,0xA1,0x8D))
    BackgroundTheme.Dawn -> AndroidWeatherPalette(rgb(0x16,0x25,0x3C), rgb(0x52,0x6A,0x91), rgb(0x89,0xA5,0xBE), rgb(0xC1,0xA6,0xA4), rgb(0xD8,0xB2,0x87), rgb(0xE2,0xCC,0xFF), rgb(0xFF,0xC2,0x8A), rgb(0x9E,0xD4,0xFF), rgb(0xD7,0xD6,0xF0), rgb(0xE2,0xC0,0xA6))
}

private fun rgb(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)
private fun withAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (color and 0x00FFFFFF)
