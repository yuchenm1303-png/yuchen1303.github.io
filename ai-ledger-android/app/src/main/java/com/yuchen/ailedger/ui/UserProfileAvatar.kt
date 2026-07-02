package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

private const val PROFILE_AVATAR_CACHE_EDGE_PX = 320
private const val PROFILE_AVATAR_CACHE_BYTES = 12 * 1024 * 1024

/**
 * 设置首页与账号详情会同时显示同一张头像。这里按“文件路径 + 云端版本”共享一次
 * 采样解码和一份 ImageBitmap，避免两个 Composable 各自解码原始相册大图并重复上传纹理。
 */
private object UserProfileAvatarBitmapCache {
    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val cache = object : LruCache<String, ImageBitmap>(PROFILE_AVATAR_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width.toLong() * value.height.toLong() * 4L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
    }

    fun cached(path: String, version: Long): ImageBitmap? = cache.get(cacheKey(path, version))

    suspend fun load(path: String, version: Long): ImageBitmap? {
        val key = cacheKey(path, version)
        cache.get(key)?.let { return it }

        val deferred = inFlight.computeIfAbsent(key) {
            loaderScope.async {
                decodeSampledAvatar(path)?.also { image -> cache.put(key, image) }
            }
        }
        return try {
            deferred.await()
        } finally {
            if (deferred.isCompleted) inFlight.remove(key, deferred)
        }
    }

    private fun cacheKey(path: String, version: Long): String = "$path#$version"

    private fun decodeSampledAvatar(path: String): ImageBitmap? {
        val file = File(path).takeIf(File::isFile) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) return null

        val sourceMaxEdge = maxOf(sourceWidth, sourceHeight)
        var sampleSize = 1
        while (sourceMaxEdge / (sampleSize * 2) >= PROFILE_AVATAR_CACHE_EDGE_PX) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null

        val decodedMaxEdge = maxOf(decoded.width, decoded.height)
        val bitmap = if (decodedMaxEdge > PROFILE_AVATAR_CACHE_EDGE_PX) {
            val scale = PROFILE_AVATAR_CACHE_EDGE_PX.toFloat() / decodedMaxEdge.toFloat()
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
        return bitmap.asImageBitmap()
    }
}

@Composable
internal fun UserProfileAvatar(
    localAvatarPath: String?,
    avatarVersion: Long,
    fallbackText: String,
    size: Dp,
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    val effectiveAvatarPath = localAvatarPath.takeIf { loggedIn && !it.isNullOrBlank() }
    val initialImage = effectiveAvatarPath?.let {
        UserProfileAvatarBitmapCache.cached(it, avatarVersion)
    }
    val avatarBitmap by produceState<ImageBitmap?>(
        initialValue = initialImage,
        key1 = effectiveAvatarPath,
        key2 = avatarVersion,
    ) {
        value = effectiveAvatarPath?.let {
            UserProfileAvatarBitmapCache.load(it, avatarVersion)
        }
    }

    val shape = CircleShape
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = if (loggedIn) {
                        listOf(
                            Color(0xFF86E8FF).copy(alpha = 0.62f),
                            Color(0xFF335FD7).copy(alpha = 0.74f),
                            Color(0xFF141A55).copy(alpha = 0.96f),
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color(0xFF263269).copy(alpha = 0.72f),
                            Color(0xFF11173F).copy(alpha = 0.96f),
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (loggedIn) 0.42f else 0.20f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val image = avatarBitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "用户头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color(0xFF07132D).copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = fallbackText.ifBlank { "AI" }.take(2),
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = (size.value * if (fallbackText.length > 1) 0.26f else 0.37f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
