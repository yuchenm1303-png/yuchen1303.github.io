package com.yuchen.ailedger.service

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

private const val COMPLETE_FOLDER_CHECKPOINT_INTERVAL = 10

internal class StorageFolderIndexCompleteEngine(
    context: Context,
    private val store: StorageFolderIndexCompleteStore,
) {
    private val resolver = context.applicationContext.contentResolver

    fun scan(
        treeUri: Uri,
        work: CompleteFolderWork,
        pageFiles: Int,
        stopSignal: AtomicBoolean,
        guard: StorageDeviceGuard,
    ): StorageFolderIndexState {
        val queue = ArrayDeque(work.queue)
        var current = work.current
        var childOffset = work.progress.currentChildOffset.coerceAtLeast(0)
        var scannedFiles = work.progress.scannedFiles
        var scannedBytes = work.progress.scannedBytes
        var discoveredDirectories = work.progress.discoveredDirectories
        var completedDirectories = work.progress.completedDirectories
        val filesByUri = work.files.associateByTo(linkedMapOf()) { it.uri }
        var processedThisPage = 0
        var interrupted = false
        var firstError = work.progress.errorMessage
        val batchSize = pageFiles.coerceAtLeast(1)

        while (processedThisPage < batchSize) {
            if (stopSignal.get()) {
                interrupted = true
                break
            }
            if (current == null) {
                current = if (queue.isEmpty()) null else queue.removeFirst()
                childOffset = 0
            }
            val active = current ?: break
            val parentId = runCatching { DocumentsContract.getDocumentId(active.uri) }.getOrNull()
            if (parentId == null) {
                firstError = firstError ?: "部分目录标识已失效，已跳过。"
                current = null
                childOffset = 0
                continue
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor = runCatching {
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    ),
                    null,
                    null,
                    null,
                )
            }.getOrElse { error ->
                firstError = firstError ?: error.message.orEmpty().ifBlank { "部分目录无法读取，已跳过。" }
                null
            }
            if (cursor == null) {
                current = null
                childOffset = 0
                continue
            }

            var rowIndex = 0
            var fullyConsumed = true
            cursor.use {
                val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (it.moveToNext()) {
                    if (rowIndex < childOffset) {
                        rowIndex += 1
                        continue
                    }
                    rowIndex += 1
                    if (stopSignal.get() || processedThisPage >= batchSize) {
                        fullyConsumed = false
                        break
                    }
                    childOffset = rowIndex
                    val documentId = it.safeString(idIndex) ?: continue
                    val name = it.safeString(nameIndex).orEmpty().ifBlank { "未命名文件" }
                    val mime = it.safeString(mimeIndex).orEmpty()
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val path = "${active.path.trimEnd('/')}/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.addLast(CompleteFolderNode(uri, path, active.depth + 1))
                        discoveredDirectories += 1
                        continue
                    }
                    val size = it.safeLong(sizeIndex).coerceAtLeast(0L)
                    scannedFiles += 1
                    scannedBytes += size
                    processedThisPage += 1
                    filesByUri[uri.toString()] = StorageIndexedLargeFile(
                        uri = uri.toString(),
                        displayName = name,
                        sizeBytes = size,
                        mimeType = mime,
                        modifiedAt = it.safeLong(modifiedIndex),
                        location = path,
                    )
                    if (processedThisPage % COMPLETE_FOLDER_CHECKPOINT_INTERVAL == 0) {
                        save(
                            treeUri, work.progress.rootName, queue, active, childOffset,
                            scannedFiles, scannedBytes, discoveredDirectories, completedDirectories,
                            complete = false, interrupted = false, startedAt = work.progress.startedAt,
                            errorMessage = firstError, files = filesByUri.values.toList(),
                        )
                    }
                }
            }
            if (fullyConsumed) {
                completedDirectories += 1
                current = null
                childOffset = 0
            } else {
                current = active
                break
            }
        }

        if (stopSignal.get()) interrupted = true
        val complete = current == null && queue.isEmpty()
        val files = filesByUri.values.sortedByDescending { it.sizeBytes }
        val progress = save(
            treeUri, work.progress.rootName, queue, current, childOffset,
            scannedFiles, scannedBytes, discoveredDirectories, completedDirectories,
            complete, interrupted, work.progress.startedAt, firstError, files,
        )
        return StorageFolderIndexState(
            progress = progress,
            largestFiles = files,
            deviceGuard = guard,
            permissionValid = true,
        )
    }

    private fun save(
        treeUri: Uri,
        rootName: String,
        queue: Collection<CompleteFolderNode>,
        current: CompleteFolderNode?,
        childOffset: Int,
        scannedFiles: Int,
        scannedBytes: Long,
        discoveredDirectories: Int,
        completedDirectories: Int,
        complete: Boolean,
        interrupted: Boolean,
        startedAt: Long,
        errorMessage: String?,
        files: List<StorageIndexedLargeFile>,
    ): StorageFolderIndexProgress = store.save(
        treeUri, rootName, queue, current, childOffset, scannedFiles, scannedBytes,
        discoveredDirectories, completedDirectories, complete, interrupted,
        startedAt, errorMessage, files,
    )

    private fun android.database.Cursor.safeString(index: Int): String? =
        if (index < 0 || isNull(index)) null else runCatching { getString(index) }.getOrNull()

    private fun android.database.Cursor.safeLong(index: Int): Long =
        if (index < 0 || isNull(index)) 0L else runCatching { getLong(index) }.getOrDefault(0L)
}
