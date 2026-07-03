package com.yuchen.ailedger.service

import android.net.Uri
import android.os.Build
import android.provider.MediaStore

internal enum class MediaKind(
    val label: String,
    val defaultMime: String,
) {
    Video("视频", "video/unknown"),
    Image("图片", "image/unknown"),
    Audio("音频", "audio/unknown");

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
