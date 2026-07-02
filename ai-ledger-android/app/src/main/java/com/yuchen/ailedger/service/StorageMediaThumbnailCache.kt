package com.yuchen.ailedger.service

import android.graphics.Bitmap
import android.util.LruCache

private const val STORAGE_THUMBNAIL_CACHE_KB = 12 * 1024

private object StorageMediaThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(STORAGE_THUMBNAIL_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    @Synchronized
    fun get(key: String): Bitmap? {
        return cache.get(key)?.takeUnless(Bitmap::isRecycled)
    }

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) cache.put(key, bitmap)
    }
}

/**
 * 为列表缩略图和详情预览共用受限内存缓存。
 * 缓存淘汰时不主动 recycle，避免 Compose 仍在绘制同一 Bitmap 时发生闪退。
 */
internal fun StorageMediaOrganizationRepository.loadCachedOrganizationThumbnail(
    file: StorageOrganizationFile,
    maxEdgePx: Int,
): Bitmap? {
    if (!file.mimeType.startsWith("image/", ignoreCase = true)) return null
    val boundedEdge = maxEdgePx.coerceIn(64, 960)
    val key = "${file.uri}#$boundedEdge"
    StorageMediaThumbnailCache.get(key)?.let { return it }
    return loadPreviewBitmap(file, boundedEdge)?.also { bitmap ->
        StorageMediaThumbnailCache.put(key, bitmap)
    }
}
