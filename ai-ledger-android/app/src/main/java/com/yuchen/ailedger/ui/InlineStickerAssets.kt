package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.yuchen.ailedger.AiLedgerApplication
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val INLINE_STICKER_TAG_START = 0xE0001
private const val INLINE_STICKER_TAG_CANCEL = 0xE007F
private const val INLINE_STICKER_TAG_BASE = 0xE0000
private const val INLINE_STICKER_PAYLOAD_PREFIX = "ai_sticker:"
private const val INLINE_STICKER_COMPACT_PREFIX = "s"
private const val INLINE_STICKER_VISIBLE_PREFIX = "[[AI_LEDGER_INLINE_STICKER:"
private const val INLINE_STICKER_PACK_ASSET = "inline_stickers_v1.zip"
private const val INLINE_STICKER_MAX_ENTRY_BYTES = 512 * 1024
private const val INLINE_STICKER_MIN_DIMENSION = 32
private const val INLINE_STICKER_MAX_DIMENSION = 2048
private const val INLINE_STICKER_WARMUP_DELAY_MS = 420L
private const val INLINE_STICKER_MAX_DECODE_CONCURRENCY = 2
private const val INLINE_STICKER_PLACEHOLDER_SIZE_PX = 96

internal data class InlineStickerProtocolMarker(
    val start: Int,
    val endExclusive: Int,
    val assetKey: String?
)

internal fun interface InlineStickerLoadHandle {
    fun cancel()
}

internal object InlineStickerAssets {
    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private val decodeSemaphore = Semaphore(INLINE_STICKER_MAX_DECODE_CONCURRENCY)
    private val warmUpStarted = AtomicBoolean(false)
    private val loadingPlaceholderBitmap: Bitmap by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createLoadingPlaceholderBitmap()
    }

    private val supportedKeys = setOf(
        "joy_burst",
        "affection_hug",
        "health_check",
        "thinking_soft",
        "cheer_power",
        "pout_no",
        "comfort_friend",
        "red_packet_congrats",
        "gift_for_you",
        "sparkle_excited",
        "soft_smile",
        "got_it_point",
        "heart_thanks",
        "confident_ready",
        "playful_wink",
        "confused_study",
        "confirm_yes",
        "idea_drawing",
        "reject_no"
    )

    private val keyByCompactCode = mapOf(
        "0" to "joy_burst",
        "1" to "affection_hug",
        "2" to "health_check",
        "3" to "thinking_soft",
        "4" to "cheer_power",
        "5" to "pout_no",
        "6" to "comfort_friend",
        "7" to "red_packet_congrats",
        "8" to "gift_for_you",
        "9" to "sparkle_excited",
        "a" to "soft_smile",
        "b" to "got_it_point",
        "c" to "heart_thanks",
        "d" to "confident_ready",
        "e" to "playful_wink",
        "f" to "confused_study",
        "g" to "confirm_yes",
        "h" to "idea_drawing",
        "i" to "reject_no"
    )

    private val visibleMarkerRegex =
        Regex("""\[\[AI_LEDGER_INLINE_STICKER:([a-z0-9_]{2,48})]]""", RegexOption.IGNORE_CASE)

    @Volatile
    private var bundledStickerBytesCache: Map<String, ByteArray>? = null

    @Volatile
    private var bundledStickerPackLoadAttempted = false

    internal fun normalizeKey(value: String): String? {
        return value.trim().lowercase().takeIf { it in supportedKeys }
    }

    internal fun cachedBitmap(assetKey: String): Bitmap? {
        val key = normalizeKey(assetKey) ?: return null
        return bitmapCache[key]
    }

    /**
     * Warms the small built-in sticker pack away from the first chat render. Decoding stays bounded
     * and sequential from this caller, so opening the app does not create a burst of 19 concurrent
     * WebP decodes or main-thread layout callbacks.
     */
    internal fun warmUpAll() {
        if (!warmUpStarted.compareAndSet(false, true)) return
        loaderScope.launch {
            delay(INLINE_STICKER_WARMUP_DELAY_MS)
            var loadedAny = false
            for (assetKey in supportedKeys) {
                val loaded = runCatching { loadBitmap(assetKey) }.getOrNull()
                if (loaded != null) loadedAny = true
            }
            if (!loadedAny && bitmapCache.isEmpty()) {
                warmUpStarted.set(false)
            }
        }
    }

    internal fun containsProtocolMarker(text: String): Boolean {
        if (text.indexOf(INLINE_STICKER_VISIBLE_PREFIX, ignoreCase = true) >= 0) return true
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            if (codePoint == INLINE_STICKER_TAG_START) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    internal fun findProtocolMarkers(text: String): List<InlineStickerProtocolMarker> {
        if (text.isEmpty()) return emptyList()
        val markers = mutableListOf<InlineStickerProtocolMarker>()

        visibleMarkerRegex.findAll(text).forEach { match ->
            markers += InlineStickerProtocolMarker(
                start = match.range.first,
                endExclusive = match.range.last + 1,
                assetKey = normalizeKey(match.groupValues[1])
            )
        }

        var visibleSearchFrom = 0
        while (visibleSearchFrom < text.length) {
            val markerStart = text.indexOf(INLINE_STICKER_VISIBLE_PREFIX, visibleSearchFrom, ignoreCase = true)
            if (markerStart < 0) break
            val alreadyCovered = markers.any { markerStart >= it.start && markerStart < it.endExclusive }
            if (!alreadyCovered) {
                val markerEnd = text.indexOf("]]", markerStart + INLINE_STICKER_VISIBLE_PREFIX.length)
                markers += InlineStickerProtocolMarker(
                    start = markerStart,
                    endExclusive = if (markerEnd >= 0) markerEnd + 2 else text.length,
                    assetKey = null
                )
            }
            visibleSearchFrom = markerStart + INLINE_STICKER_VISIBLE_PREFIX.length
        }

        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            if (codePoint != INLINE_STICKER_TAG_START) {
                index += Character.charCount(codePoint)
                continue
            }

            val markerStart = index
            index += Character.charCount(codePoint)
            val payload = StringBuilder()
            var completed = false
            while (index < text.length) {
                val taggedCodePoint = Character.codePointAt(text, index)
                index += Character.charCount(taggedCodePoint)
                if (taggedCodePoint == INLINE_STICKER_TAG_CANCEL) {
                    completed = true
                    break
                }
                if (taggedCodePoint !in 0xE0020..0xE007E) {
                    payload.clear()
                    break
                }
                payload.append((taggedCodePoint - INLINE_STICKER_TAG_BASE).toChar())
            }

            val decoded = payload.toString().lowercase()
            val key = if (completed) {
                when {
                    decoded.startsWith(INLINE_STICKER_PAYLOAD_PREFIX) -> {
                        normalizeKey(decoded.removePrefix(INLINE_STICKER_PAYLOAD_PREFIX))
                    }
                    decoded.startsWith(INLINE_STICKER_COMPACT_PREFIX) -> {
                        keyByCompactCode[decoded.removePrefix(INLINE_STICKER_COMPACT_PREFIX)]
                    }
                    else -> null
                }
            } else {
                null
            }
            markers += InlineStickerProtocolMarker(markerStart, index, key)
        }

        return markers
            .sortedBy { it.start }
            .fold(mutableListOf<InlineStickerProtocolMarker>()) { accepted, marker ->
                if (accepted.none { marker.start < it.endExclusive && marker.endExclusive > it.start }) {
                    accepted += marker
                }
                accepted
            }
    }

    internal fun requestBitmap(
        assetKey: String,
        onResult: (Bitmap?) -> Unit
    ): InlineStickerLoadHandle {
        val key = normalizeKey(assetKey)
        val cancelled = AtomicBoolean(false)
        if (key == null) {
            mainHandler.post { if (!cancelled.get()) onResult(null) }
            return InlineStickerLoadHandle { cancelled.set(true) }
        }

        bitmapCache[key]?.let { bitmap ->
            mainHandler.post { if (!cancelled.get()) onResult(bitmap) }
            return InlineStickerLoadHandle { cancelled.set(true) }
        }

        mainHandler.post {
            if (!cancelled.get()) onResult(loadingPlaceholderBitmap)
        }

        val waiter = loaderScope.launch {
            val bitmap = runCatching { loadBitmap(key) }.getOrNull()
            mainHandler.post {
                if (!cancelled.get()) onResult(bitmap)
            }
        }
        return InlineStickerLoadHandle {
            cancelled.set(true)
            waiter.cancel()
        }
    }

    @Composable
    fun rememberImageBitmap(assetKey: String): ImageBitmap? {
        val cleanKey = remember(assetKey) { normalizeKey(assetKey) }
        var image by remember(cleanKey) {
            mutableStateOf(cleanKey?.let(::cachedBitmap)?.asImageBitmap())
        }

        DisposableEffect(cleanKey) {
            val key = cleanKey
            if (key == null) {
                onDispose { }
            } else {
                val handle = requestBitmap(key) { bitmap ->
                    image = bitmap?.asImageBitmap()
                }
                onDispose { handle.cancel() }
            }
        }
        return image
    }

    private suspend fun loadBitmap(assetKey: String): Bitmap? {
        bitmapCache[assetKey]?.let { return it }

        val deferred = inFlight.computeIfAbsent(assetKey) {
            val created = loaderScope.async {
                decodeSemaphore.withPermit {
                    loadBundledBitmap(assetKey)
                }
            }
            created.invokeOnCompletion {
                inFlight.remove(assetKey, created)
            }
            created
        }
        return try {
            val loaded = deferred.await()
            if (loaded != null) bitmapCache.putIfAbsent(assetKey, loaded)
            bitmapCache[assetKey] ?: loaded
        } finally {
            if (deferred.isCompleted) inFlight.remove(assetKey, deferred)
        }
    }

    private fun loadBundledBitmap(assetKey: String): Bitmap? {
        val bytes = bundledStickerBytes()[assetKey] ?: return null
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null

        if (!isSupportedTransparentBitmap(bitmap)) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    private fun bundledStickerBytes(): Map<String, ByteArray> {
        bundledStickerBytesCache?.let { return it }
        if (AiLedgerApplication.contextOrNull() == null) return emptyMap()

        synchronized(this) {
            bundledStickerBytesCache?.let { return it }
            if (bundledStickerPackLoadAttempted) return emptyMap()

            val loaded = loadBundledStickerPack()
            bundledStickerPackLoadAttempted = true
            bundledStickerBytesCache = loaded
            return loaded
        }
    }

    private fun loadBundledStickerPack(): Map<String, ByteArray> {
        val context = AiLedgerApplication.contextOrNull() ?: return emptyMap()
        val assets = context.assets

        return runCatching {
            assets.open(INLINE_STICKER_PACK_ASSET).use { input ->
                val loaded = LinkedHashMap<String, ByteArray>(supportedKeys.size)
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }

                        val fileName = entry.name.substringAfterLast('/')
                        if (!fileName.endsWith(".webp", ignoreCase = true)) {
                            throw IllegalStateException("内置表情资源格式异常：${entry.name}")
                        }
                        val assetKey = normalizeKey(
                            fileName.substring(0, fileName.length - ".webp".length)
                        ) ?: throw IllegalStateException("内置表情资源名称异常：${entry.name}")
                        if (loaded.containsKey(assetKey)) {
                            throw IllegalStateException("内置表情资源重复：$assetKey")
                        }

                        val bytes = readZipEntry(zip, assetKey)
                        if (!isWebP(bytes)) {
                            throw IllegalStateException("内置表情资源格式异常：$assetKey")
                        }
                        loaded[assetKey] = bytes
                        zip.closeEntry()
                    }
                }

                validateCompletePack(loaded)
                loaded
            }
        }.getOrDefault(emptyMap())
    }

    private fun readZipEntry(zip: ZipInputStream, assetKey: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            if (total > INLINE_STICKER_MAX_ENTRY_BYTES) {
                throw IllegalStateException("内置表情资源大小异常：$assetKey")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray().also {
            if (it.isEmpty()) {
                throw IllegalStateException("内置表情资源为空：$assetKey")
            }
        }
    }

    private fun validateCompletePack(loaded: Map<String, ByteArray>) {
        if (loaded.keys != supportedKeys) {
            throw IllegalStateException(
                "内置表情资源不完整：缺少=${supportedKeys - loaded.keys}，多余=${loaded.keys - supportedKeys}"
            )
        }
    }

    private fun createLoadingPlaceholderBitmap(): Bitmap {
        val size = INLINE_STICKER_PLACEHOLDER_SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x408DF9EA.toInt()
            maskFilter = BlurMaskFilter(size * 0.12f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(center, center + size * 0.02f, size * 0.19f, glowPaint)

        val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xA68DF9EA.toInt()
        }
        canvas.drawCircle(center, center, size * 0.14f, shellPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xD0B8FFF5.toInt()
        }
        canvas.drawCircle(center, center, size * 0.095f, corePaint)

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEFFFFFFF.toInt()
        }
        canvas.drawCircle(
            center - size * 0.035f,
            center - size * 0.04f,
            size * 0.035f,
            highlightPaint
        )

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x385A74FF.toInt()
        }
        canvas.drawCircle(
            center + size * 0.045f,
            center + size * 0.05f,
            size * 0.025f,
            shadowPaint
        )
        return bitmap
    }

    private fun isSupportedTransparentBitmap(bitmap: Bitmap): Boolean {
        if (bitmap.width !in INLINE_STICKER_MIN_DIMENSION..INLINE_STICKER_MAX_DIMENSION) return false
        if (bitmap.height !in INLINE_STICKER_MIN_DIMENSION..INLINE_STICKER_MAX_DIMENSION) return false
        if (!bitmap.hasAlpha()) return false

        var hasTransparentPixel = false
        var hasVisiblePixel = false
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (pixel in row) {
                val alpha = pixel ushr 24
                if (alpha != 0xFF) hasTransparentPixel = true
                if (alpha >= 0x08) hasVisiblePixel = true
                if (hasTransparentPixel && hasVisiblePixel) return true
            }
        }
        return false
    }

    private fun isWebP(bytes: ByteArray): Boolean {
        return bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
    }
}
