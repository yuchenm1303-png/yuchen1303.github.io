package com.yuchen.ailedger.service

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

internal class StorageUnlimitedFolderCollector(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun collect(treeUri: Uri): List<StorageIntelligenceFile> {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val queue = ArrayDeque<Pair<Uri, String>>()
        queue.add(rootUri to queryName(rootUri).ifBlank { "授权目录" })
        return buildList {
            while (queue.isNotEmpty()) {
                val (nodeUri, nodePath) = queue.removeFirst()
                val parentId = runCatching { DocumentsContract.getDocumentId(nodeUri) }.getOrNull() ?: continue
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
                            DocumentsContract.Document.COLUMN_FLAGS,
                        ),
                        null,
                        null,
                        null,
                    )
                }.getOrNull() ?: continue
                cursor.use {
                    val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val flagsIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                    while (it.moveToNext()) {
                        val documentId = it.safeString(idIndex) ?: continue
                        val name = it.safeString(nameIndex).orEmpty().ifBlank { "未命名文件" }
                        val mime = it.safeString(mimeIndex).orEmpty()
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        val path = "${nodePath.trimEnd('/')}/$name"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(uri to path)
                            continue
                        }
                        val flags = it.safeLong(flagsIndex).toInt()
                        add(
                            StorageIntelligenceFile(
                                uri = uri.toString(),
                                displayName = name,
                                sizeBytes = it.safeLong(sizeIndex).coerceAtLeast(0L),
                                mimeType = mime,
                                modifiedAt = it.safeLong(modifiedIndex),
                                location = path,
                                source = StorageCandidateSource.AuthorizedFolder,
                                canDelete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun queryName(uri: Uri): String = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.safeString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    private fun Cursor.safeString(index: Int): String? =
        if (index < 0 || isNull(index)) null else runCatching { getString(index) }.getOrNull()

    private fun Cursor.safeLong(index: Int): Long =
        if (index < 0 || isNull(index)) 0L else runCatching { getLong(index) }.getOrDefault(0L)
}
