package com.yuchen.ailedger.service

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

private const val COMPLETE_FOLDER_PREFS = "storage_resumable_folder_index"
private const val COMPLETE_FOLDER_STATE_KEY = "folder_index_state"
private const val COMPLETE_FOLDER_FILES_KEY = "folder_index_top_files"

internal data class CompleteFolderNode(
    val uri: Uri,
    val path: String,
    val depth: Int,
)

internal data class CompleteFolderWork(
    val progress: StorageFolderIndexProgress,
    val current: CompleteFolderNode?,
    val queue: List<CompleteFolderNode>,
    val files: List<StorageIndexedLargeFile>,
)

internal class StorageFolderIndexCompleteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(COMPLETE_FOLDER_PREFS, Context.MODE_PRIVATE)

    fun clear() {
        prefs.edit().clear().commit()
    }

    fun load(): CompleteFolderWork? {
        val raw = prefs.getString(COMPLETE_FOLDER_STATE_KEY, null).orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val progress = root.getJSONObject("progress").toProgress()
            val current = root.optJSONObject("current")?.toNode()
            val queueJson = root.optJSONArray("queue") ?: JSONArray()
            val queue = buildList {
                for (index in 0 until queueJson.length()) {
                    queueJson.optJSONObject(index)?.toNode()?.let(::add)
                }
            }
            CompleteFolderWork(progress, current, queue, loadFiles())
        }.getOrNull()
    }

    fun save(
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
    ): StorageFolderIndexProgress {
        val progress = StorageFolderIndexProgress(
            treeUri = treeUri.toString(),
            rootName = rootName,
            scannedFiles = scannedFiles,
            scannedBytes = scannedBytes,
            discoveredDirectories = discoveredDirectories,
            completedDirectories = completedDirectories,
            queuedDirectories = queue.size + if (current != null) 1 else 0,
            currentPath = current?.path,
            currentChildOffset = childOffset,
            startedAt = startedAt,
            updatedAt = System.currentTimeMillis(),
            complete = complete,
            interrupted = interrupted,
            errorMessage = errorMessage,
        )
        val state = JSONObject()
            .put("progress", progress.toJson())
            .put("current", current?.toJson() ?: JSONObject.NULL)
            .put("queue", JSONArray().apply { queue.forEach { put(it.toJson()) } })
        val filesJson = JSONArray().apply { files.forEach { put(it.toJson()) } }
        prefs.edit()
            .putString(COMPLETE_FOLDER_STATE_KEY, state.toString())
            .putString(COMPLETE_FOLDER_FILES_KEY, filesJson.toString())
            .commit()
        return progress
    }

    private fun loadFiles(): List<StorageIndexedLargeFile> {
        val raw = prefs.getString(COMPLETE_FOLDER_FILES_KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        StorageIndexedLargeFile(
                            uri = item.optString("uri"),
                            displayName = item.optString("displayName"),
                            sizeBytes = item.optLong("sizeBytes"),
                            mimeType = item.optString("mimeType"),
                            modifiedAt = item.optLong("modifiedAt"),
                            location = item.optString("location"),
                        ),
                    )
                }
            }.sortedByDescending { it.sizeBytes }
        }.getOrDefault(emptyList())
    }

    private fun CompleteFolderNode.toJson(): JSONObject = JSONObject()
        .put("uri", uri.toString()).put("path", path).put("depth", depth)

    private fun JSONObject.toNode(): CompleteFolderNode? {
        val value = optString("uri").takeIf(String::isNotBlank)?.let(Uri::parse) ?: return null
        return CompleteFolderNode(value, optString("path"), optInt("depth"))
    }

    private fun StorageFolderIndexProgress.toJson(): JSONObject = JSONObject()
        .put("treeUri", treeUri).put("rootName", rootName)
        .put("scannedFiles", scannedFiles).put("scannedBytes", scannedBytes)
        .put("discoveredDirectories", discoveredDirectories)
        .put("completedDirectories", completedDirectories)
        .put("queuedDirectories", queuedDirectories)
        .put("currentPath", currentPath ?: JSONObject.NULL)
        .put("currentChildOffset", currentChildOffset)
        .put("startedAt", startedAt).put("updatedAt", updatedAt)
        .put("complete", complete).put("interrupted", interrupted)
        .put("errorMessage", errorMessage ?: JSONObject.NULL)

    private fun JSONObject.toProgress(): StorageFolderIndexProgress = StorageFolderIndexProgress(
        treeUri = optString("treeUri"),
        rootName = optString("rootName"),
        scannedFiles = optInt("scannedFiles"),
        scannedBytes = optLong("scannedBytes"),
        discoveredDirectories = optInt("discoveredDirectories"),
        completedDirectories = optInt("completedDirectories"),
        queuedDirectories = optInt("queuedDirectories"),
        currentPath = optString("currentPath").takeIf { it.isNotBlank() && it != "null" },
        currentChildOffset = optInt("currentChildOffset"),
        startedAt = optLong("startedAt"),
        updatedAt = optLong("updatedAt"),
        complete = optBoolean("complete"),
        interrupted = optBoolean("interrupted"),
        errorMessage = optString("errorMessage").takeIf { it.isNotBlank() && it != "null" },
    )

    private fun StorageIndexedLargeFile.toJson(): JSONObject = JSONObject()
        .put("uri", uri).put("displayName", displayName).put("sizeBytes", sizeBytes)
        .put("mimeType", mimeType).put("modifiedAt", modifiedAt).put("location", location)
}
