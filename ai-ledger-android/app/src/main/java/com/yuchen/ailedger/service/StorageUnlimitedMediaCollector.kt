package com.yuchen.ailedger.service

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

internal class StorageUnlimitedMediaCollector(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun collect(): List<StorageIntelligenceFile> = MediaKind.entries.flatMap(::query)

    private fun query(kind: MediaKind): List<StorageIntelligenceFile> {
        val collection = kind.collectionUri()
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()
        val selection = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("${MediaStore.MediaColumns.IS_PENDING} = 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
        }.joinToString(" AND ").ifBlank { null }
        val cursor = runCatching {
            resolver.query(collection, projection, selection, null, "${MediaStore.MediaColumns.SIZE} DESC")
        }.getOrNull() ?: return emptyList()
        return buildList {
            cursor.use {
                val idIndex = it.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val mimeIndex = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val modifiedIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val pathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1
                while (it.moveToNext()) {
                    val id = it.safeLong(idIndex)
                    if (id <= 0L) continue
                    val modifiedSeconds = it.safeLong(modifiedIndex)
                    add(
                        StorageIntelligenceFile(
                            uri = ContentUris.withAppendedId(collection, id).toString(),
                            displayName = it.safeString(nameIndex).orEmpty().ifBlank { "未命名媒体" },
                            sizeBytes = it.safeLong(sizeIndex).coerceAtLeast(0L),
                            mimeType = it.safeString(mimeIndex).orEmpty().ifBlank { kind.defaultMime },
                            modifiedAt = if (modifiedSeconds > 0L) modifiedSeconds * 1000L else 0L,
                            location = it.safeString(pathIndex).orEmpty().ifBlank { kind.label },
                            source = StorageCandidateSource.MediaStore,
                            canDelete = true,
                        ),
                    )
                }
            }
        }
    }

    private fun Cursor.safeString(index: Int): String? =
        if (index < 0 || isNull(index)) null else runCatching { getString(index) }.getOrNull()

    private fun Cursor.safeLong(index: Int): Long =
        if (index < 0 || isNull(index)) 0L else runCatching { getLong(index) }.getOrDefault(0L)

    private enum class MediaKind(val label: String, val defaultMime: String) {
        Video("视频", "video/unknown"), Image("图片", "image/unknown"), Audio("音频", "audio/unknown");

        fun collectionUri(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (this) {
                Video -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                Image -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                Audio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }
        } else {
            when (this) {
                Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                Image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        }
    }
}
