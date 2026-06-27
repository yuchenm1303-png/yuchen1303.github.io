package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val INLINE_STICKER_CONNECT_TIMEOUT_MS = 5_000
private const val INLINE_STICKER_READ_TIMEOUT_MS = 12_000
private const val INLINE_STICKER_MAX_BYTES = 512 * 1024
private const val INLINE_STICKER_TAG_START = 0xE0001
private const val INLINE_STICKER_TAG_CANCEL = 0xE007F
private const val INLINE_STICKER_TAG_BASE = 0xE0000
private const val INLINE_STICKER_PAYLOAD_PREFIX = "ai_sticker:"
private const val INLINE_STICKER_COMPACT_PREFIX = "s"
private const val INLINE_STICKER_VISIBLE_PREFIX = "[[AI_LEDGER_INLINE_STICKER:"

internal data class InlineStickerProtocolMarker(
    val start: Int,
    val endExclusive: Int,
    val assetKey: String?
)

internal fun interface InlineStickerLoadHandle {
    fun cancel()
}

internal object InlineStickerAssets {
    private const val CN_ENDPOINT = "https://ai-ledg-chat-cn-dnuxlrhytb.cn-hangzhou.fcapp.run"
    private const val CLOUDFLARE_ENDPOINT = "https://ai-ledger-parser.552078638.workers.dev"

    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()

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

    internal fun normalizeKey(value: String): String? {
        return value.trim().lowercase().takeIf { it in supportedKeys }
    }

    internal fun cachedBitmap(assetKey: String): Bitmap? {
        val key = normalizeKey(assetKey) ?: return null
        return bitmapCache[key]
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

        val waiter = loaderScope.launch {
            val bitmap = loadBitmap(key)
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
            loaderScope.async { loadFromEndpoints(assetKey) }
        }
        return try {
            val loaded = deferred.await()
            if (loaded != null) bitmapCache.putIfAbsent(assetKey, loaded)
            bitmapCache[assetKey] ?: loaded
        } finally {
            if (deferred.isCompleted) inFlight.remove(assetKey, deferred)
        }
    }

    private fun loadFromEndpoints(assetKey: String): Bitmap? {
        val path = "/chat-stickers/v1/$assetKey.webp"
        for (endpoint in listOf(CN_ENDPOINT, CLOUDFLARE_ENDPOINT)) {
            val loaded = runCatching { downloadBitmap(endpoint + path) }.getOrNull()
            if (loaded != null) return loaded
        }
        return null
    }

    @Throws(IOException::class)
    private fun downloadBitmap(url: String): Bitmap {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = INLINE_STICKER_CONNECT_TIMEOUT_MS
            readTimeout = INLINE_STICKER_READ_TIMEOUT_MS
            useCaches = true
            setRequestProperty("Accept", "image/webp,image/*;q=0.8")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("表情资源请求失败：HTTP $status")
            val declaredLength = connection.contentLengthLong
            if (declaredLength > INLINE_STICKER_MAX_BYTES) throw IOException("表情资源过大")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(
                    declaredLength.takeIf { it in 1..INLINE_STICKER_MAX_BYTES.toLong() }?.toInt() ?: 32 * 1024
                )
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > INLINE_STICKER_MAX_BYTES) throw IOException("表情资源超过大小限制")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("表情资源解码失败")
        } finally {
            connection.disconnect()
        }
    }
}
