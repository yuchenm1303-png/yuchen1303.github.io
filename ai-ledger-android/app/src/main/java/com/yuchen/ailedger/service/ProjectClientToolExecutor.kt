package com.yuchen.ailedger.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val PROJECT_TOOL_RESULT_SCHEMA = "ai_ledger_project_tool_result_v1"
private const val PROJECT_TOOL_MAX_LIST = 50
private const val PROJECT_SOURCE_BUNDLE_MAX_FILES = 100
private const val PROJECT_SOURCE_BUNDLE_DEFAULT_CHARS = 360_000
private const val PROJECT_SOURCE_BUNDLE_MAX_CHARS = 600_000

/**
 * Executes project_* calls selected by the cloud final model.
 *
 * This class is intentionally mechanical: it never infers a project request from natural language,
 * never rewrites model-authored code, and only applies validated structured arguments.
 */
internal class ProjectClientToolExecutor(context: Context) {
    private val appContext = context.applicationContext
    private val store = ProjectWorkspaceStore(appContext)
    private val validator = ProjectWorkspaceValidator(store)

    fun execute(call: CloudClientToolCall, fallbackGoal: String = ""): JSONObject {
        val goal = call.originalUserGoal
            ?.takeIf(String::isNotBlank)
            ?: fallbackGoal.takeIf(String::isNotBlank)
            ?: call.name
        val receipt = baseReceipt(call, goal)
        val result = runCatching {
            when (call.name) {
                "project_create" -> executeCreate(call, receipt)
                "project_list" -> executeList(call, receipt)
                "project_get" -> executeGet(call, receipt)
                "project_list_files" -> executeListFiles(call, receipt)
                "project_read_file" -> executeReadFile(call, receipt)
                "project_write_files" -> executeWriteFiles(call, receipt)
                "project_apply_edits" -> executeApplyEdits(call, receipt)
                "project_delete_files" -> executeDeleteFiles(call, receipt)
                "project_validate" -> executeValidate(call, receipt)
                "project_build_preview" -> executeBuildPreview(call, receipt)
                "project_list_revisions" -> executeListRevisions(call, receipt)
                "project_rollback" -> executeRollback(call, receipt)
                else -> receipt.fail("unsupported", "Android 当前不支持项目工具：${call.name}。")
            }
        }.getOrElse { error ->
            when (error) {
                is ProjectWorkspaceException -> receipt.fail(error.code, error.message)
                else -> receipt.fail(
                    status = "failed",
                    summary = "项目工具执行异常：${error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName}",
                    technical = "project_tool_exception:${error::class.java.simpleName}",
                )
            }
        }
        if (result.optBoolean("ok")) {
            ProjectWorkspaceSessionContext.update(appContext, result.optJSONObject("project"))
        }
        return result
    }

    private fun executeCreate(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val name = args.optString("name").trim().take(80)
        if (name.isBlank()) return receipt.fail("invalid_arguments", "创建项目失败：缺少项目名称。")
        val project = store.createProject(
            name = name,
            description = args.optString("description").trim().take(400),
            files = ProjectWorkspaceStore.filesFromJson(args.optJSONArray("files")),
            revisionSummary = args.optString("revisionSummary", "创建项目").trim().take(240),
        )
        return receipt.success("created", "已创建网页项目：${project.name}。")
            .put("project", project.toJson())
            .put("projectId", project.projectId)
            .put("revisionId", project.currentRevisionId)
    }

    private fun executeList(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val limit = call.arguments.optInt("limit", 20).coerceIn(1, PROJECT_TOOL_MAX_LIST)
        val projects = store.listProjects(limit)
        return receipt.success(
            status = "listed",
            summary = if (projects.isEmpty()) "当前还没有网页项目。" else "找到 ${projects.size} 个网页项目。",
        ).put("projects", JSONArray().apply { projects.forEach { put(it.toJson()) } })
            .put("count", projects.size)
    }

    private fun executeGet(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val project = store.getProject(requireProjectId(call.arguments))
        return receipt.success("found", "已读取项目：${project.name}。")
            .put("project", project.toJson())
    }

    private fun executeListFiles(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val projectId = requireProjectId(args)
        val project = store.getProject(projectId)
        val allPaths = store.listFiles(projectId)
        if (!args.optBoolean("includeContent", false)) {
            return receipt.success("listed", "项目 ${project.name} 包含 ${allPaths.size} 个文件。")
                .put("project", project.toJson())
                .put("projectId", project.projectId)
                .put("revisionId", project.currentRevisionId)
                .put("files", JSONArray(allPaths))
                .put("count", allPaths.size)
                .put("includeContent", false)
        }

        val requestedPaths = args.optJSONArray("paths").toStringList(PROJECT_SOURCE_BUNDLE_MAX_FILES)
        val selectedPaths = if (requestedPaths.isEmpty()) allPaths else requestedPaths.distinct()
        val unknownPaths = selectedPaths.filterNot(allPaths::contains)
        if (unknownPaths.isNotEmpty()) {
            return receipt.fail(
                "invalid_arguments",
                "读取项目源码失败：项目中不存在 ${unknownPaths.joinToString()}。",
            )
        }
        val maxTotalChars = args.optInt("maxTotalChars", PROJECT_SOURCE_BUNDLE_DEFAULT_CHARS)
            .coerceIn(20_000, PROJECT_SOURCE_BUNDLE_MAX_CHARS)
        val files = JSONArray()
        val omittedPaths = JSONArray()
        var totalContentChars = 0
        var bundleComplete = true
        selectedPaths.forEach { path ->
            val (content, truncated) = store.readFile(projectId, path)
            if (totalContentChars + content.length > maxTotalChars) {
                bundleComplete = false
                omittedPaths.put(path)
            } else {
                files.put(JSONObject().apply {
                    put("path", path)
                    put("content", content)
                    put("truncated", truncated)
                    put("contentChars", content.length)
                })
                totalContentChars += content.length
                if (truncated) bundleComplete = false
            }
        }
        if (files.length() != selectedPaths.size) bundleComplete = false

        val summary = if (bundleComplete) {
            "已读取项目 ${project.name} 的 ${files.length()} 个完整源码文件。"
        } else {
            "项目源码超过安全读取上限或包含被截断文件，已返回可用部分，不能直接用于完整重写。"
        }
        return receipt.success("listed", summary)
            .put("project", project.toJson())
            .put("projectId", project.projectId)
            .put("revisionId", project.currentRevisionId)
            .put("files", files)
            .put("count", allPaths.size)
            .put("selectedCount", selectedPaths.size)
            .put("includeContent", true)
            .put("bundleComplete", bundleComplete)
            .put("totalContentChars", totalContentChars)
            .put("maxTotalChars", maxTotalChars)
            .put("omittedPaths", omittedPaths)
    }

    private fun executeReadFile(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val projectId = requireProjectId(call.arguments)
        val path = call.arguments.optString("path").trim()
        if (path.isBlank()) return receipt.fail("invalid_arguments", "读取文件失败：缺少 path。")
        val (content, truncated) = store.readFile(projectId, path)
        val project = store.getProject(projectId)
        return receipt.success("read", "已读取 $path。")
            .put("project", project.toJson())
            .put("path", path)
            .put("content", content)
            .put("truncated", truncated)
            .put("contentChars", content.length)
    }

    private fun executeWriteFiles(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val projectId = requireProjectId(args)
        val files = ProjectWorkspaceStore.filesFromJson(args.optJSONArray("files"))
        if (files.isEmpty()) return receipt.fail("invalid_arguments", "写入项目失败：files 不能为空。")
        val project = store.writeFiles(
            projectId = projectId,
            baseRevisionId = args.optString("baseRevisionId").trim().takeIf(String::isNotBlank),
            files = files,
            revisionSummary = args.optString("revisionSummary", "更新项目文件").trim().take(240),
        )
        return receipt.success("updated", "已写入 ${files.size} 个项目文件，当前版本为 ${project.currentRevisionId}。")
            .put("project", project.toJson())
            .put("writtenFiles", JSONArray(files.map(ProjectWorkspaceFile::path)))
            .put("revisionId", project.currentRevisionId)
    }

    private fun executeApplyEdits(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val projectId = requireProjectId(args)
        val edits = ProjectWorkspaceStore.editsFromJson(args.optJSONArray("edits"))
        if (edits.isEmpty()) return receipt.fail("invalid_arguments", "修改项目失败：edits 不能为空。")
        val project = store.applyEdits(
            projectId = projectId,
            baseRevisionId = args.optString("baseRevisionId").trim().takeIf(String::isNotBlank),
            edits = edits,
            revisionSummary = args.optString("revisionSummary", "修改项目代码").trim().take(240),
        )
        return receipt.success("updated", "已应用 ${edits.size} 处精确修改，当前版本为 ${project.currentRevisionId}。")
            .put("project", project.toJson())
            .put("editedFiles", JSONArray(edits.map(ProjectWorkspaceEdit::path).distinct()))
            .put("revisionId", project.currentRevisionId)
    }

    private fun executeDeleteFiles(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val projectId = requireProjectId(args)
        val paths = args.optJSONArray("paths").toStringList(100)
        if (paths.isEmpty()) return receipt.fail("invalid_arguments", "删除项目文件失败：paths 不能为空。")
        val project = store.deleteFiles(
            projectId = projectId,
            baseRevisionId = args.optString("baseRevisionId").trim().takeIf(String::isNotBlank),
            paths = paths,
            revisionSummary = args.optString("revisionSummary", "删除项目文件").trim().take(240),
        )
        return receipt.success("updated", "已删除 ${paths.size} 个项目文件，当前版本为 ${project.currentRevisionId}。")
            .put("project", project.toJson())
            .put("deletedFiles", JSONArray(paths))
            .put("revisionId", project.currentRevisionId)
    }

    private fun executeValidate(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val projectId = requireProjectId(call.arguments)
        val project = store.getProject(projectId)
        val verification = validator.validate(projectId)
        val result = if (verification.passed) {
            receipt.success(verification.status, verification.summary())
        } else {
            receipt.fail("validation_failed", verification.summary())
        }
        return result
            .put("project", project.toJson())
            .put("projectId", project.projectId)
            .put("revisionId", project.currentRevisionId)
            .put("verification", verification.toJson())
    }

    private fun executeBuildPreview(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val projectId = requireProjectId(args)
        val project = store.getProject(projectId)
        val requestedRevisionId = args.optString("revisionId").trim().takeIf(String::isNotBlank)
        if (requestedRevisionId != null && requestedRevisionId != project.currentRevisionId) {
            val preview = store.buildPreview(projectId = projectId, revisionId = requestedRevisionId)
            val blocks = previewContentBlocks(preview, verification = null)
            return receipt.success("preview_ready", "历史版本 ${preview.revisionId} 已完成本地结构校验，可以打开预览。")
                .put("project", preview.project.toJson())
                .put("projectId", preview.project.projectId)
                .put("revisionId", preview.revisionId)
                .put("previewUrl", preview.previewUrl)
                .put("recommendedContentBlocks", blocks)
                .put("presentationInstruction", "后端会安全接入 recommendedContentBlocks；请只补充简短自然说明，不要展示内部文件路径。")
        }

        val verification = validator.validate(projectId)
        if (!verification.passed) {
            return receipt.fail("validation_failed", "项目未通过确定性验证，不能生成完成预览。${verification.summary()}")
                .put("project", project.toJson())
                .put("projectId", project.projectId)
                .put("revisionId", project.currentRevisionId)
                .put("verification", verification.toJson())
        }
        val preview = store.buildPreview(projectId = projectId, revisionId = requestedRevisionId)
        val blocks = previewContentBlocks(preview, verification)
        return receipt.success("preview_ready", "项目 ${preview.project.name} 已通过确定性验证，可以打开预览。")
            .put("project", preview.project.toJson())
            .put("projectId", preview.project.projectId)
            .put("revisionId", preview.revisionId)
            .put("previewUrl", preview.previewUrl)
            .put("verification", verification.toJson())
            .put("recommendedContentBlocks", blocks)
            .put("presentationInstruction", "后端会安全接入 recommendedContentBlocks；请只补充简短自然说明，不要展示内部文件路径。")
    }

    private fun executeListRevisions(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val projectId = requireProjectId(args)
        val revisions = store.listRevisions(projectId, args.optInt("limit", 20).coerceIn(1, 24))
        return receipt.success("listed", "找到 ${revisions.size} 个项目版本。")
            .put("project", store.getProject(projectId).toJson())
            .put("revisions", JSONArray().apply { revisions.forEach { put(it.toJson()) } })
            .put("count", revisions.size)
    }

    private fun executeRollback(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val projectId = requireProjectId(args)
        val targetRevisionId = args.optString("targetRevisionId").trim()
        if (targetRevisionId.isBlank()) return receipt.fail("invalid_arguments", "恢复项目失败：缺少 targetRevisionId。")
        val project = store.rollback(
            projectId = projectId,
            targetRevisionId = targetRevisionId,
            baseRevisionId = args.optString("baseRevisionId").trim().takeIf(String::isNotBlank),
            revisionSummary = args.optString("revisionSummary", "恢复到 $targetRevisionId").trim().take(240),
        )
        return receipt.success("rolled_back", "已将项目内容恢复到 $targetRevisionId，并保存为新版本 ${project.currentRevisionId}。")
            .put("project", project.toJson())
            .put("revisionId", project.currentRevisionId)
            .put("restoredFromRevisionId", targetRevisionId)
    }

    private fun previewContentBlocks(
        preview: ProjectPreviewEntry,
        verification: AgentArtifactVerificationReport?,
    ): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("type", "key_value")
            put("id", "project-${preview.project.projectId}-${preview.revisionId}-summary")
            put("title", preview.project.name)
            put("items", JSONArray().apply {
                put(JSONObject().put("label", "项目类型").put("value", "静态网页").put("detail", "HTML · CSS · JavaScript"))
                put(JSONObject().put("label", "当前版本").put("value", preview.revisionId).put("detail", "${preview.project.fileCount} 个文件"))
                put(JSONObject().put("label", "构建状态").put("value", "预览就绪").put("detail", verification?.status ?: "历史版本结构校验"))
            })
        })
        put(JSONObject().apply {
            put("type", "action_group")
            put("id", "project-${preview.project.projectId}-${preview.revisionId}-actions")
            put("title", "项目预览")
            put("actions", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "open-project-preview")
                    put("type", "open_url")
                    put("label", "打开网页预览")
                    put("url", preview.previewUrl)
                    put("emphasis", true)
                })
            })
        })
        if (verification != null && verification.warningCount > 0) {
            put(JSONObject().apply {
                put("type", "callout")
                put("id", "project-${preview.project.projectId}-${preview.revisionId}-verification")
                put("tone", "warning")
                put("title", "验证警告")
                put("text", verification.issues
                    .filter { it.severity == AgentVerificationSeverity.Warning }
                    .take(3)
                    .joinToString("\n") { "• ${it.message}" })
            })
        }
    }

    private fun baseReceipt(call: CloudClientToolCall, goal: String): JSONObject = JSONObject().apply {
        put("protocol", call.resultProtocol)
        put("schema", PROJECT_TOOL_RESULT_SCHEMA)
        put("toolCallId", call.id)
        put("toolName", call.name)
        put("toolArguments", JSONObject(call.arguments.toString()))
        put("finalModel", call.finalModel ?: "")
        put("goal", goal.trim().take(600))
        put("stoppedForConfirmation", false)
        put("handled", true)
        put("completed", false)
        put("executionOwner", "android_local_project_workspace")
    }

    private fun JSONObject.success(status: String, summary: String): JSONObject {
        put("ok", true)
        put("status", status)
        put("completed", true)
        put("handled", true)
        put("resultSummary", summary.take(1_800))
        put("actions", JSONArray().put(actionReceipt(optString("toolName"), status, true, summary)))
        return this
    }

    private fun JSONObject.fail(status: String, summary: String, technical: String = status): JSONObject {
        put("ok", false)
        put("status", status)
        put("completed", false)
        put("handled", true)
        put("resultSummary", summary.take(1_800))
        put("actions", JSONArray().put(actionReceipt(optString("toolName"), status, false, technical)))
        return this
    }

    private fun actionReceipt(tool: String, status: String, ok: Boolean, detail: String): JSONObject = JSONObject().apply {
        put("tool", tool)
        put("toolLabel", "网页项目工具")
        put("status", status)
        put("ok", ok)
        put("verified", ok)
        put("technicalDetail", detail.take(1_800))
        put("undoAvailable", tool in setOf("project_write_files", "project_apply_edits", "project_delete_files", "project_rollback"))
    }

    private fun requireProjectId(args: JSONObject): String = args.optString("projectId").trim().takeIf(String::isNotBlank)
        ?: throw ProjectWorkspaceException("project_id_required", "缺少 projectId。")

    private fun JSONArray?.toStringList(maxItems: Int): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until minOf(length(), maxItems)) {
                optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    companion object {
        private val PROJECT_TOOLS = setOf(
            "project_create",
            "project_list",
            "project_get",
            "project_list_files",
            "project_read_file",
            "project_write_files",
            "project_apply_edits",
            "project_delete_files",
            "project_validate",
            "project_build_preview",
            "project_list_revisions",
            "project_rollback",
        )

        fun isProjectTool(name: String): Boolean = name in PROJECT_TOOLS
    }
}
