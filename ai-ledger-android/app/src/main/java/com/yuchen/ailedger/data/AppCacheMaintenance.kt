package com.yuchen.ailedger.data

import android.content.Context
import java.io.File

/**
 * Performs bounded maintenance only after the first UI has reached its deferred-business window.
 * It never touches active attachment payload files or user-owned media.
 */
internal object AppCacheMaintenance {
    private const val CAMERA_DIRECTORY = "camera"
    private const val ATTACHMENT_DIRECTORY = "chat_attachment_payloads"
    private const val CAMERA_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
    private const val CAMERA_MAX_BYTES = 64L * 1024L * 1024L
    private const val CAMERA_MAX_FILES = 16
    private const val TEMP_FILE_MAX_AGE_MS = 60L * 60L * 1_000L
    private const val EMPTY_FILE_MAX_AGE_MS = 10L * 60L * 1_000L

    fun run(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val cacheRoot = context.applicationContext.cacheDir
        cleanupCameraDirectory(File(cacheRoot, CAMERA_DIRECTORY), nowMillis)
        cleanupAttachmentTemporaryFiles(File(cacheRoot, ATTACHMENT_DIRECTORY), nowMillis)
    }

    private fun cleanupCameraDirectory(directory: File, nowMillis: Long) {
        if (!directory.isDirectory) return
        directory.listFiles().orEmpty()
            .filter(File::isFile)
            .filter { file ->
                nowMillis - file.lastModified() > CAMERA_MAX_AGE_MS ||
                    (file.length() == 0L && nowMillis - file.lastModified() > EMPTY_FILE_MAX_AGE_MS)
            }
            .forEach { file -> runCatching { file.delete() } }

        val newestFirst = directory.listFiles().orEmpty()
            .filter(File::isFile)
            .sortedByDescending(File::lastModified)
        var retainedBytes = 0L
        newestFirst.forEachIndexed { index, file ->
            retainedBytes += file.length()
            if (index >= CAMERA_MAX_FILES || retainedBytes > CAMERA_MAX_BYTES) {
                runCatching { file.delete() }
            }
        }
    }

    private fun cleanupAttachmentTemporaryFiles(directory: File, nowMillis: Long) {
        if (!directory.isDirectory) return
        directory.listFiles().orEmpty()
            .filter { file ->
                file.isFile &&
                    ".tmp-" in file.name &&
                    nowMillis - file.lastModified() > TEMP_FILE_MAX_AGE_MS
            }
            .forEach { file -> runCatching { file.delete() } }
    }
}
