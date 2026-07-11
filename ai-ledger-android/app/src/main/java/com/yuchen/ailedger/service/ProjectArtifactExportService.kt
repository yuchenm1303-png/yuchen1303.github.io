package com.yuchen.ailedger.service

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val PROJECT_EXPORT_DIRECTORY = "project-artifact-exports"
private const val PROJECT_EXPORT_BUFFER_BYTES = 16 * 1024

/**
 * Creates a user-deliverable ZIP from one immutable project revision.
 *
 * The source remains inside the validated [ProjectWorkspaceStore]. Only regular files below the
 * resolved project root are exported; workspace metadata and hidden implementation files are not.
 */
internal object ProjectArtifactExportService {
    fun createZip(
        context: Context,
        projectId: String,
        revisionId: String?,
    ): File {
        val preview = ProjectWorkspaceStore(context.applicationContext)
            .resolvePreviewEntry(projectId, revisionId)
        val root = preview.projectRoot.canonicalFile
        val exportDir = File(context.applicationContext.cacheDir, PROJECT_EXPORT_DIRECTORY)
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw ProjectWorkspaceException("export_directory_failed", "无法创建项目导出目录。")
        }
        exportDir.listFiles()
            .orEmpty()
            .filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 24L * 60L * 60L * 1000L }
            .forEach(File::delete)

        val safeName = preview.project.name
            .trim()
            .replace(Regex("[^A-Za-z0-9\\u4e00-\\u9fa5_-]+"), "-")
            .trim('-')
            .take(48)
            .ifBlank { "AI-Ledger-Project" }
        val output = File(exportDir, "$safeName-${preview.revisionId}.zip")
        val buffer = ByteArray(PROJECT_EXPORT_BUFFER_BYTES)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            root.walkTopDown()
                .filter(File::isFile)
                .filterNot { it.name == "_revision.json" || it.name.startsWith('.') }
                .forEach { source ->
                    val canonical = source.canonicalFile
                    if (!canonical.path.startsWith(root.path + File.separator)) return@forEach
                    val relative = canonical.relativeTo(root).invariantSeparatorsPath
                    if (relative.isBlank()) return@forEach
                    zip.putNextEntry(ZipEntry(relative))
                    FileInputStream(canonical).use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count > 0) zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
        }
        return output
    }

    fun copyToUri(context: Context, source: File, target: Uri) {
        context.applicationContext.contentResolver.openOutputStream(target, "w")?.use { output ->
            FileInputStream(source).use { input -> input.copyTo(output, PROJECT_EXPORT_BUFFER_BYTES) }
        } ?: throw ProjectWorkspaceException("export_target_failed", "无法写入所选下载位置。")
    }
}
