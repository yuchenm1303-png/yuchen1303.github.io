package com.yuchen.ailedger.service

import android.content.Context
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

private const val PROJECT_WORKSPACE_SCHEMA = "ai_ledger_local_web_project_v1"
private const val PROJECT_WORKSPACE_ROOT = "project-workspaces-v1"
private const val PROJECT_ENTRY_FILE = "index.html"
private const val PROJECT_MAX_COUNT = 24
private const val PROJECT_MAX_FILES = 100
private const val PROJECT_MAX_FILE_BYTES = 512 * 1024
private const val PROJECT_MAX_TOTAL_BYTES = 5 * 1024 * 1024
private const val PROJECT_MAX_REVISIONS = 24
private const val PROJECT_MAX_PATH_CHARS = 180
private const val PROJECT_MAX_READ_CHARS = 512_000

internal data class ProjectWorkspaceFile(
    val path: String,
    val content: String,
)

internal data class ProjectWorkspaceEdit(
    val path: String,
    val oldText: String,
    val newText: String,
    val replaceAll: Boolean = false,
)

internal data class ProjectWorkspaceSummary(
    val projectId: String,
    val name: String,
    val description: String,
    val entryFile: String,
    val currentRevisionId: String,
    val currentRevision: Int,
    val status: String,
    val fileCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", PROJECT_WORKSPACE_SCHEMA)
        put("projectId", projectId)
        put("name", name)
        put("description", description)
        put("projectType", "static_web")
        put("framework", "html_css_javascript")
        put("entryFile", entryFile)
        put("currentRevisionId", currentRevisionId)
        put("currentRevision", currentRevision)
        put("status", status)
        put("fileCount", fileCount)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }
}

internal data class ProjectRevisionSummary(
    val revisionId: String,
    val revision: Int,
    val summary: String,
    val createdAt: Long,
    val fileCount: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("revisionId", revisionId)
        put("revision", revision)
        put("summary", summary)
        put("createdAt", createdAt)
        put("fileCount", fileCount)
    }
}

internal data class ProjectPreviewEntry(
    val project: ProjectWorkspaceSummary,
    val revisionId: String,
    val entryFile: File,
    val projectRoot: File,
    val previewUrl: String,
)

internal class ProjectWorkspaceException(
    val code: String,
    override val message: String,
) : IllegalStateException(message)

/**
 * Durable, app-private static web workspace used by project_* client tools.
 *
 * The final model owns project planning and file content. Android only validates paths/quotas,
 * applies structured mutations, keeps immutable revision snapshots and exposes a local preview.
 * No natural-language routing or source-code inference happens here.
 */
internal class ProjectWorkspaceStore private constructor(
    private val rootDir: File,
) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, PROJECT_WORKSPACE_ROOT))

    init {
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw ProjectWorkspaceException("workspace_unavailable", "无法创建项目工作区。")
        }
    }

    fun createProject(
        name: String,
        description: String,
        files: List<ProjectWorkspaceFile>,
        revisionSummary: String,
    ): ProjectWorkspaceSummary = synchronized(globalLock) {
        if (files.isEmpty()) {
            throw ProjectWorkspaceException("initial_files_required", "创建项目必须提供完整初版文件。")
        }
        val existingCount = rootDir.listFiles()?.count { it.isDirectory && it.name.startsWith("project_") } ?: 0
        if (existingCount >= PROJECT_MAX_COUNT) {
            throw ProjectWorkspaceException("project_limit_reached", "本地项目数量已达到上限。")
        }
        val projectId = "project_${UUID.randomUUID().toString().replace("-", "").take(24)}"
        val projectDir = projectDirectory(projectId)
        val currentDir = File(projectDir, "current")
        val now = System.currentTimeMillis()
        try {
            if (!currentDir.mkdirs()) {
                throw ProjectWorkspaceException("project_create_failed", "无法创建项目目录。")
            }
            writeFilesInto(currentDir, files)
            validateProjectTree(currentDir)
            val revision = 1
            writeRevisionSnapshot(
                projectDir = projectDir,
                sourceDir = currentDir,
                revision = revision,
                summary = revisionSummary.ifBlank { "创建项目" },
                createdAt = now,
            )
            val manifest = JSONObject().apply {
                put("schema", PROJECT_WORKSPACE_SCHEMA)
                put("projectId", projectId)
                put("name", name.trim().take(80).ifBlank { "未命名网页项目" })
                put("description", description.trim().take(400))
                put("entryFile", PROJECT_ENTRY_FILE)
                put("currentRevision", revision)
                put("status", "draft")
                put("createdAt", now)
                put("updatedAt", now)
            }
            writeManifest(projectDir, manifest)
            summaryFrom(projectDir, manifest)
        } catch (error: Throwable) {
            projectDir.deleteRecursively()
            throw error
        }
    }

    fun listProjects(limit: Int = 20): List<ProjectWorkspaceSummary> = synchronized(globalLock) {
        rootDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && it.name.startsWith("project_") }
            .mapNotNull { projectDir ->
                runCatching { readManifest(projectDir) }.getOrNull()?.let { summaryFrom(projectDir, it) }
            }
            .sortedByDescending(ProjectWorkspaceSummary::updatedAt)
            .take(limit.coerceIn(1, PROJECT_MAX_COUNT))
            .toList()
    }

    fun getProject(projectId: String): ProjectWorkspaceSummary = synchronized(globalLock) {
        val projectDir = requireProjectDirectory(projectId)
        summaryFrom(projectDir, readManifest(projectDir))
    }

    fun listFiles(projectId: String): List<String> = synchronized(globalLock) {
        val currentDir = File(requireProjectDirectory(projectId), "current")
        listRelativeFiles(currentDir)
    }

    fun readFile(projectId: String, path: String): Pair<String, Boolean> = synchronized(globalLock) {
        val currentDir = File(requireProjectDirectory(projectId), "current")
        val file = resolveSafeFile(currentDir, path)
        if (!file.isFile) throw ProjectWorkspaceException("file_not_found", "项目中没有找到文件：$path")
        val text = file.readText(Charsets.UTF_8)
        if (text.length <= PROJECT_MAX_READ_CHARS) text to false else text.take(PROJECT_MAX_READ_CHARS) to true
    }

    fun writeFiles(
        projectId: String,
        baseRevisionId: String?,
        files: List<ProjectWorkspaceFile>,
        revisionSummary: String,
        replaceAllFiles: Boolean = false,
    ): ProjectWorkspaceSummary = mutateProject(
        projectId = projectId,
        baseRevisionId = baseRevisionId,
        revisionSummary = revisionSummary.ifBlank { "更新项目文件" },
    ) { staging ->
        if (files.isEmpty()) throw ProjectWorkspaceException("files_required", "没有提供需要写入的文件。")
        if (replaceAllFiles) clearProjectFiles(staging)
        writeFilesInto(staging, files)
    }

    fun applyEdits(
        projectId: String,
        baseRevisionId: String?,
        edits: List<ProjectWorkspaceEdit>,
        revisionSummary: String,
    ): ProjectWorkspaceSummary = mutateProject(
        projectId = projectId,
        baseRevisionId = baseRevisionId,
        revisionSummary = revisionSummary.ifBlank { "修改项目代码" },
    ) { staging ->
        if (edits.isEmpty()) throw ProjectWorkspaceException("edits_required", "没有提供需要应用的代码修改。")
        edits.forEach { edit ->
            val file = resolveSafeFile(staging, edit.path)
            if (!file.isFile) throw ProjectWorkspaceException("file_not_found", "项目中没有找到文件：${edit.path}")
            val current = file.readText(Charsets.UTF_8)
            if (edit.oldText.isEmpty()) throw ProjectWorkspaceException("old_text_required", "修改 ${edit.path} 时 oldText 不能为空。")
            val occurrences = current.windowed(edit.oldText.length, 1, partialWindows = false).count { it == edit.oldText }
            if (occurrences == 0) throw ProjectWorkspaceException("edit_conflict", "${edit.path} 中找不到要替换的原文，请先重新读取最新文件。")
            if (!edit.replaceAll && occurrences != 1) {
                throw ProjectWorkspaceException("edit_ambiguous", "${edit.path} 中匹配到多处原文，请提供更精确的 oldText 或显式 replaceAll。")
            }
            val updated = if (edit.replaceAll) current.replace(edit.oldText, edit.newText) else current.replaceFirst(edit.oldText, edit.newText)
            writeTextFile(file, updated)
        }
    }

    fun deleteFiles(
        projectId: String,
        baseRevisionId: String?,
        paths: List<String>,
        revisionSummary: String,
    ): ProjectWorkspaceSummary = mutateProject(
        projectId = projectId,
        baseRevisionId = baseRevisionId,
        revisionSummary = revisionSummary.ifBlank { "删除项目文件" },
    ) { staging ->
        if (paths.isEmpty()) throw ProjectWorkspaceException("paths_required", "没有提供需要删除的文件。")
        paths.distinct().forEach { path ->
            val file = resolveSafeFile(staging, path)
            if (file.name == PROJECT_ENTRY_FILE && file.parentFile?.canonicalFile == staging.canonicalFile) {
                throw ProjectWorkspaceException("entry_file_protected", "不能删除项目入口 index.html。")
            }
            if (file.exists() && !file.deleteRecursively()) {
                throw ProjectWorkspaceException("file_delete_failed", "删除文件失败：$path")
            }
        }
    }

    fun listRevisions(projectId: String, limit: Int = PROJECT_MAX_REVISIONS): List<ProjectRevisionSummary> = synchronized(globalLock) {
        val projectDir = requireProjectDirectory(projectId)
        val revisionsDir = File(projectDir, "revisions")
        revisionsDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .mapNotNull { revisionDir ->
                val metadata = File(revisionDir, "_revision.json")
                if (!metadata.isFile) return@mapNotNull null
                runCatching {
                    val json = JSONObject(metadata.readText(Charsets.UTF_8))
                    ProjectRevisionSummary(
                        revisionId = revisionDir.name,
                        revision = json.optInt("revision"),
                        summary = json.optString("summary"),
                        createdAt = json.optLong("createdAt"),
                        fileCount = json.optInt("fileCount"),
                    )
                }.getOrNull()
            }
            .sortedByDescending(ProjectRevisionSummary::revision)
            .take(limit.coerceIn(1, PROJECT_MAX_REVISIONS))
            .toList()
    }

    fun rollback(
        projectId: String,
        targetRevisionId: String,
        baseRevisionId: String?,
        revisionSummary: String,
    ): ProjectWorkspaceSummary = mutateProject(
        projectId = projectId,
        baseRevisionId = baseRevisionId,
        revisionSummary = revisionSummary.ifBlank { "恢复到 $targetRevisionId" },
    ) { staging ->
        val projectDir = requireProjectDirectory(projectId)
        val targetDir = revisionDirectory(projectDir, targetRevisionId)
        if (!targetDir.isDirectory) throw ProjectWorkspaceException("revision_not_found", "没有找到版本：$targetRevisionId")
        clearProjectFiles(staging)
        copyProjectFiles(targetDir, staging, excludeRevisionMetadata = true)
    }

    fun buildPreview(projectId: String, revisionId: String? = null): ProjectPreviewEntry = synchronized(globalLock) {
        resolvePreviewEntryLocked(projectId, revisionId, markPreviewReady = true)
    }

    fun resolveRuntimeEntry(projectId: String, revisionId: String? = null): ProjectPreviewEntry = synchronized(globalLock) {
        resolvePreviewEntryLocked(projectId, revisionId, markPreviewReady = false)
    }

    fun resolvePreviewEntry(projectId: String, revisionId: String?): ProjectPreviewEntry = buildPreview(projectId, revisionId)

    private fun resolvePreviewEntryLocked(
        projectId: String,
        revisionId: String?,
        markPreviewReady: Boolean,
    ): ProjectPreviewEntry {
        val projectDir = requireProjectDirectory(projectId)
        val manifest = readManifest(projectDir)
        val currentRevisionId = revisionId(manifest.optInt("currentRevision"))
        val resolvedRevisionId = revisionId?.takeIf(String::isNotBlank) ?: currentRevisionId
        val sourceDir = if (resolvedRevisionId == currentRevisionId) {
            File(projectDir, "current")
        } else {
            revisionDirectory(projectDir, resolvedRevisionId)
        }
        if (!sourceDir.isDirectory) throw ProjectWorkspaceException("revision_not_found", "没有找到版本：$resolvedRevisionId")
        validateProjectTree(sourceDir, ignoreRevisionMetadata = true)
        val entry = resolveSafeFile(sourceDir, manifest.optString("entryFile", PROJECT_ENTRY_FILE))
        if (!entry.isFile) throw ProjectWorkspaceException("entry_file_missing", "项目缺少 index.html，无法生成预览。")
        if (markPreviewReady && resolvedRevisionId == currentRevisionId) {
            manifest.put("status", "preview_ready")
            manifest.put("updatedAt", System.currentTimeMillis())
            writeManifest(projectDir, manifest)
        }
        return ProjectPreviewEntry(
            project = summaryFrom(projectDir, manifest),
            revisionId = resolvedRevisionId,
            entryFile = entry,
            projectRoot = sourceDir,
            previewUrl = previewUrl(manifest.optString("projectId"), resolvedRevisionId),
        )
    }

    private fun mutateProject(
        projectId: String,
        baseRevisionId: String?,
        revisionSummary: String,
        mutation: (File) -> Unit,
    ): ProjectWorkspaceSummary = synchronized(globalLock) {
        val projectDir = requireProjectDirectory(projectId)
        val manifest = readManifest(projectDir)
        val currentRevision = manifest.optInt("currentRevision", 1).coerceAtLeast(1)
        val currentRevisionId = revisionId(currentRevision)
        if (baseRevisionId.isNullOrBlank()) {
            throw ProjectWorkspaceException("base_revision_required", "修改项目必须提供当前 baseRevisionId。")
        }
        if (baseRevisionId != currentRevisionId) {
            throw ProjectWorkspaceException("revision_conflict", "项目已经更新到 $currentRevisionId，请先读取最新版本后再修改。")
        }
        val currentDir = File(projectDir, "current")
        val staging = File(projectDir, ".staging-${UUID.randomUUID()}")
        val backup = File(projectDir, ".backup-${UUID.randomUUID()}")
        try {
            if (!staging.mkdirs()) throw ProjectWorkspaceException("staging_create_failed", "无法创建项目修改暂存区。")
            copyProjectFiles(currentDir, staging)
            mutation(staging)
            validateProjectTree(staging)
            val nextRevision = currentRevision + 1
            val now = System.currentTimeMillis()
            writeRevisionSnapshot(projectDir, staging, nextRevision, revisionSummary.take(240), now)
            if (!currentDir.renameTo(backup)) {
                copyProjectFiles(currentDir, backup)
                currentDir.deleteRecursively()
            }
            if (!staging.renameTo(currentDir)) {
                if (!currentDir.mkdirs()) throw ProjectWorkspaceException("project_commit_failed", "无法提交项目文件。")
                copyProjectFiles(staging, currentDir)
            }
            backup.deleteRecursively()
            manifest.put("currentRevision", nextRevision)
            manifest.put("status", "draft")
            manifest.put("updatedAt", now)
            writeManifest(projectDir, manifest)
            pruneRevisions(projectDir)
            summaryFrom(projectDir, manifest)
        } catch (error: Throwable) {
            if (!currentDir.exists() && backup.exists()) backup.renameTo(currentDir)
            throw error
        } finally {
            staging.deleteRecursively()
            backup.deleteRecursively()
        }
    }

    private fun clearProjectFiles(root: File) {
        root.listFiles().orEmpty().forEach { child ->
            if (!child.deleteRecursively()) {
                throw ProjectWorkspaceException("project_clear_failed", "无法清理旧项目文件：${child.name}")
            }
        }
    }

    private fun writeFilesInto(root: File, files: List<ProjectWorkspaceFile>) {
        if (files.size > PROJECT_MAX_FILES) throw ProjectWorkspaceException("too_many_files", "单次写入文件数量过多。")
        files.forEach { source ->
            val path = normalizeRelativePath(source.path)
            val file = resolveSafeFile(root, path)
            writeTextFile(file, source.content)
        }
    }

    private fun writeTextFile(file: File, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > PROJECT_MAX_FILE_BYTES) {
            throw ProjectWorkspaceException("file_too_large", "文件 ${file.name} 超过 ${PROJECT_MAX_FILE_BYTES / 1024}KB 上限。")
        }
        val parent = file.parentFile ?: throw ProjectWorkspaceException("invalid_path", "文件路径无效。")
        if (!parent.exists() && !parent.mkdirs()) throw ProjectWorkspaceException("directory_create_failed", "无法创建文件目录。")
        file.writeText(content, Charsets.UTF_8)
    }

    private fun validateProjectTree(root: File, ignoreRevisionMetadata: Boolean = false) {
        val entry = File(root, PROJECT_ENTRY_FILE)
        if (!entry.isFile) throw ProjectWorkspaceException("entry_file_missing", "项目缺少入口文件 index.html。")
        val files = root.walkTopDown()
            .filter(File::isFile)
            .filterNot { ignoreRevisionMetadata && it.name == "_revision.json" }
            .toList()
        if (files.size > PROJECT_MAX_FILES) throw ProjectWorkspaceException("too_many_files", "项目文件数量超过 $PROJECT_MAX_FILES 个。")
        var totalBytes = 0L
        files.forEach { file ->
            val canonical = file.canonicalFile
            if (!canonical.path.startsWith(root.canonicalFile.path + File.separator)) {
                throw ProjectWorkspaceException("path_escape", "项目文件路径越界。")
            }
            if (file.length() > PROJECT_MAX_FILE_BYTES) throw ProjectWorkspaceException("file_too_large", "文件 ${file.name} 超过大小限制。")
            totalBytes += file.length()
            if (totalBytes > PROJECT_MAX_TOTAL_BYTES) throw ProjectWorkspaceException("project_too_large", "项目总大小超过 ${PROJECT_MAX_TOTAL_BYTES / 1024 / 1024}MB。")
        }
    }

    private fun summaryFrom(projectDir: File, manifest: JSONObject): ProjectWorkspaceSummary {
        val revision = manifest.optInt("currentRevision", 1).coerceAtLeast(1)
        val currentDir = File(projectDir, "current")
        return ProjectWorkspaceSummary(
            projectId = manifest.optString("projectId"),
            name = manifest.optString("name", "未命名网页项目"),
            description = manifest.optString("description"),
            entryFile = manifest.optString("entryFile", PROJECT_ENTRY_FILE),
            currentRevisionId = revisionId(revision),
            currentRevision = revision,
            status = manifest.optString("status", "draft"),
            fileCount = listRelativeFiles(currentDir).size,
            createdAt = manifest.optLong("createdAt"),
            updatedAt = manifest.optLong("updatedAt"),
        )
    }

    private fun writeRevisionSnapshot(projectDir: File, sourceDir: File, revision: Int, summary: String, createdAt: Long) {
        val revisionsDir = File(projectDir, "revisions")
        if (!revisionsDir.exists() && !revisionsDir.mkdirs()) throw ProjectWorkspaceException("revision_create_failed", "无法创建版本目录。")
        val target = File(revisionsDir, revisionId(revision))
        if (target.exists()) target.deleteRecursively()
        if (!target.mkdirs()) throw ProjectWorkspaceException("revision_create_failed", "无法创建版本快照。")
        copyProjectFiles(sourceDir, target)
        val metadata = JSONObject().apply {
            put("revision", revision)
            put("revisionId", revisionId(revision))
            put("summary", summary)
            put("createdAt", createdAt)
            put("fileCount", listRelativeFiles(sourceDir).size)
        }
        File(target, "_revision.json").writeText(metadata.toString(), Charsets.UTF_8)
    }

    private fun pruneRevisions(projectDir: File) {
        val revisions = File(projectDir, "revisions").listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .sortedByDescending { parseRevision(it.name) }
        revisions.drop(PROJECT_MAX_REVISIONS).forEach(File::deleteRecursively)
    }

    private fun copyProjectFiles(source: File, target: File, excludeRevisionMetadata: Boolean = false) {
        if (!source.exists()) return
        source.walkTopDown().forEach { item ->
            if (item == source) return@forEach
            if (excludeRevisionMetadata && item.name == "_revision.json") return@forEach
            val relative = item.relativeTo(source).path
            val destination = File(target, relative)
            if (item.isDirectory) {
                if (!destination.exists() && !destination.mkdirs()) throw ProjectWorkspaceException("copy_failed", "无法复制项目目录。")
            } else if (item.isFile) {
                destination.parentFile?.mkdirs()
                item.copyTo(destination, overwrite = true)
            }
        }
    }

    private fun listRelativeFiles(root: File): List<String> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter(File::isFile)
            .filterNot { it.name == "_revision.json" }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
    }

    private fun readManifest(projectDir: File): JSONObject {
        val file = File(projectDir, "manifest.json")
        if (!file.isFile) throw ProjectWorkspaceException("project_manifest_missing", "项目元数据不存在。")
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .getOrElse { throw ProjectWorkspaceException("project_manifest_invalid", "项目元数据损坏。") }
    }

    private fun writeManifest(projectDir: File, manifest: JSONObject) {
        File(projectDir, "manifest.json").writeText(manifest.toString(), Charsets.UTF_8)
    }

    private fun requireProjectDirectory(projectId: String): File {
        val normalized = normalizeProjectId(projectId)
        val dir = projectDirectory(normalized)
        if (!dir.isDirectory) throw ProjectWorkspaceException("project_not_found", "没有找到项目：$normalized")
        return dir
    }

    private fun projectDirectory(projectId: String): File = File(rootDir, normalizeProjectId(projectId))

    private fun revisionDirectory(projectDir: File, revisionId: String): File {
        val normalized = revisionId.takeIf { it.matches(Regex("rev_\\d{6}")) }
            ?: throw ProjectWorkspaceException("invalid_revision_id", "版本编号格式无效。")
        return File(File(projectDir, "revisions"), normalized)
    }

    private fun resolveSafeFile(root: File, path: String): File {
        val normalized = normalizeRelativePath(path)
        val candidate = File(root, normalized).canonicalFile
        val canonicalRoot = root.canonicalFile
        if (candidate == canonicalRoot || !candidate.path.startsWith(canonicalRoot.path + File.separator)) {
            throw ProjectWorkspaceException("path_escape", "文件路径越过了项目边界。")
        }
        return candidate
    }

    private fun normalizeProjectId(value: String): String {
        val clean = value.trim()
        if (!clean.matches(Regex("project_[a-zA-Z0-9]{8,40}"))) {
            throw ProjectWorkspaceException("invalid_project_id", "项目编号格式无效。")
        }
        return clean
    }

    private fun normalizeRelativePath(value: String): String {
        val clean = value.trim().replace('\\', '/').removePrefix("./")
        if (clean.isBlank() || clean.length > PROJECT_MAX_PATH_CHARS || clean.startsWith('/') || clean.contains(':')) {
            throw ProjectWorkspaceException("invalid_path", "文件路径无效：${value.take(80)}")
        }
        val segments = clean.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." || it.startsWith('.') }) {
            throw ProjectWorkspaceException("invalid_path", "文件路径包含不安全目录：${value.take(80)}")
        }
        if (!clean.matches(Regex("[A-Za-z0-9_@+.,()/\\-]+"))) {
            throw ProjectWorkspaceException("invalid_path", "文件路径包含不支持的字符：${value.take(80)}")
        }
        return clean
    }

    private fun previewUrl(projectId: String, revisionId: String): String {
        val encodedProject = URLEncoder.encode(projectId, StandardCharsets.UTF_8.name())
        val encodedRevision = URLEncoder.encode(revisionId, StandardCharsets.UTF_8.name())
        return "https://project.ai-ledger.local/open?projectId=$encodedProject&revision=$encodedRevision"
    }

    private fun revisionId(revision: Int): String = "rev_${revision.coerceAtLeast(1).toString().padStart(6, '0')}"

    private fun parseRevision(value: String): Int = value.removePrefix("rev_").toIntOrNull() ?: 0

    companion object {
        private val globalLock = Any()

        fun filesFromJson(array: JSONArray?): List<ProjectWorkspaceFile> {
            if (array == null) return emptyList()
            return buildList {
                for (index in 0 until minOf(array.length(), PROJECT_MAX_FILES)) {
                    val item = array.optJSONObject(index) ?: continue
                    val path = item.optString("path").trim()
                    if (path.isBlank()) continue
                    add(ProjectWorkspaceFile(path = path, content = item.optString("content")))
                }
            }
        }

        fun editsFromJson(array: JSONArray?): List<ProjectWorkspaceEdit> {
            if (array == null) return emptyList()
            return buildList {
                for (index in 0 until minOf(array.length(), 24)) {
                    val item = array.optJSONObject(index) ?: continue
                    val path = item.optString("path").trim()
                    val oldText = item.optString("oldText")
                    if (path.isBlank() || oldText.isEmpty()) continue
                    add(
                        ProjectWorkspaceEdit(
                            path = path,
                            oldText = oldText,
                            newText = item.optString("newText"),
                            replaceAll = item.optBoolean("replaceAll", false),
                        )
                    )
                }
            }
        }
    }
}
