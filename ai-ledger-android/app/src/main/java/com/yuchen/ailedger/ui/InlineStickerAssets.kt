package com.yuchen.ailedger.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val INLINE_STICKER_CONNECT_TIMEOUT_MS = 5_000
private const val INLINE_STICKER_READ_TIMEOUT_MS = 12_000
private const val INLINE_STICKER_MAX_BYTES = 512 * 1024

internal object InlineStickerAssets {
    private const val CN_ENDPOINT = "https://ai-ledg-chat-cn-dnuxlrhytb.cn-hangzhou.fcapp.run"
    private const val CLOUDFLARE_ENDPOINT = "https://ai-ledger-parser.552078638.workers.dev"

    private val cache = ConcurrentHashMap<String, ImageBitmap>()
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

    @Composable
    fun rememberImageBitmap(assetKey: String): ImageBitmap? {
        val cleanKey = remember(assetKey) { normalizeKey(assetKey) }
        var image by remember(cleanKey) { mutableStateOf(cleanKey?.let(cache::get)) }

        LaunchedEffect(cleanKey) {
            val key = cleanKey ?: return@LaunchedEffect
            cache[key]?.let {
                image = it
                return@LaunchedEffect
            }
            val loaded = withContext(Dispatchers.IO) { loadFromEndpoints(key) }
            if (loaded != null) {
                cache.putIfAbsent(key, loaded)
                image = cache[key] ?: loaded
            }
        }
        return image
    }

    private fun normalizeKey(value: String): String? {
        return value.trim().lowercase().takeIf { it in supportedKeys }
    }

    private fun loadFromEndpoints(assetKey: String): ImageBitmap? {
        val path = "/chat-stickers/v1/$assetKey.webp"
        for (endpoint in listOf(CN_ENDPOINT, CLOUDFLARE_ENDPOINT)) {
            val loaded = runCatching { downloadImage(endpoint + path) }.getOrNull()
            if (loaded != null) return loaded
        }
        return null
    }

    @Throws(IOException::class)
    private fun downloadImage(url: String): ImageBitmap {
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
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("表情资源解码失败")
            return bitmap.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }
}
