package com.yuchen.ailedger.service

private const val PROJECT_VALIDATION_MAX_ISSUES = 40
private const val PROJECT_VALIDATION_MAX_TEXT_FILES = 100

/**
 * Deterministic validation adapter for the static web workspace.
 *
 * It never changes project files and never makes aesthetic decisions. The cloud model remains the
 * author; Android only returns environment facts that can drive an evaluator/optimizer loop.
 */
internal class ProjectWorkspaceValidator(
    private val store: ProjectWorkspaceStore,
) {
    fun validate(projectId: String): AgentArtifactVerificationReport {
        val project = store.getProject(projectId)
        val files = store.listFiles(projectId).toSet()
        val issues = mutableListOf<AgentVerificationIssue>()

        if (project.entryFile !in files) {
            issues += issue(
                code = "entry_file_missing",
                severity = AgentVerificationSeverity.Error,
                message = "项目缺少入口文件 ${project.entryFile}。",
                file = project.entryFile,
                suggestion = "创建有效的 ${project.entryFile} 后重新验证。",
            )
            return report(project, issues)
        }

        val readableFiles = files.asSequence()
            .filter(::isTextProjectFile)
            .take(PROJECT_VALIDATION_MAX_TEXT_FILES)
            .mapNotNull { path ->
                runCatching { path to store.readFile(projectId, path).first }.getOrElse { error ->
                    issues += issue(
                        code = "file_read_failed",
                        severity = AgentVerificationSeverity.Error,
                        message = "无法读取项目文件：$path。",
                        file = path,
                        suggestion = error.message?.take(160),
                    )
                    null
                }
            }
            .toMap()

        val html = readableFiles[project.entryFile].orEmpty()
        validateEntryDocument(project.entryFile, html, issues)
        validateHtmlReferences(project.entryFile, html, files, issues)

        readableFiles.forEach { (path, content) ->
            when (path.substringAfterLast('.', "").lowercase()) {
                "css" -> validateCss(path, content, files, issues)
                "js", "mjs" -> validateJavaScript(path, content, issues)
            }
        }

        if (files.size > readableFiles.size && readableFiles.size >= PROJECT_VALIDATION_MAX_TEXT_FILES) {
            issues += issue(
                code = "validation_file_limit",
                severity = AgentVerificationSeverity.Warning,
                message = "项目文件较多，本次只检查了前 $PROJECT_VALIDATION_MAX_TEXT_FILES 个文本文件。",
                suggestion = "减少无关文件或分批检查关键文件。",
            )
        }
        return report(project, issues.take(PROJECT_VALIDATION_MAX_ISSUES))
    }

    private fun validateEntryDocument(
        path: String,
        html: String,
        issues: MutableList<AgentVerificationIssue>,
    ) {
        if (html.isBlank()) {
            issues += issue(
                code = "entry_file_empty",
                severity = AgentVerificationSeverity.Error,
                message = "入口 HTML 为空。",
                file = path,
                suggestion = "写入完整的 HTML 页面结构。",
            )
            return
        }
        if (!HTML_ELEMENT_PATTERN.containsMatchIn(html)) {
            issues += issue(
                code = "html_root_missing",
                severity = AgentVerificationSeverity.Warning,
                message = "入口文件没有显式的 <html> 根元素。",
                file = path,
                suggestion = "补齐标准 HTML 文档结构，避免不同 WebView 解析差异。",
            )
        }
        if (!BODY_ELEMENT_PATTERN.containsMatchIn(html)) {
            issues += issue(
                code = "html_body_missing",
                severity = AgentVerificationSeverity.Error,
                message = "入口文件没有可渲染的 <body>。",
                file = path,
                suggestion = "添加 <body> 并把页面内容放入其中。",
            )
        }
        if (!TITLE_ELEMENT_PATTERN.containsMatchIn(html)) {
            issues += issue(
                code = "document_title_missing",
                severity = AgentVerificationSeverity.Warning,
                message = "页面没有 <title>。",
                file = path,
                suggestion = "添加简洁明确的页面标题。",
            )
        }
        if (!VIEWPORT_META_PATTERN.containsMatchIn(html)) {
            issues += issue(
                code = "viewport_missing",
                severity = AgentVerificationSeverity.Warning,
                message = "页面没有移动端 viewport 声明。",
                file = path,
                suggestion = "添加 width=device-width, initial-scale=1 的 viewport meta。",
            )
        }
        FIXED_WIDE_SIZE_PATTERN.findAll(html).take(4).forEach { match ->
            issues += issue(
                code = "fixed_width_risk",
                severity = AgentVerificationSeverity.Warning,
                message = "检测到可能超出手机屏幕的固定宽度：${match.value.trim()}。",
                file = path,
                line = lineNumber(html, match.range.first),
                suggestion = "优先使用 max-width、百分比、vw 或响应式断点。",
            )
        }
        if (OVERFLOW_HIDDEN_PATTERN.containsMatchIn(html)) {
            issues += issue(
                code = "overflow_hidden_risk",
                severity = AgentVerificationSeverity.Warning,
                message = "页面包含 overflow:hidden，超出视口的内容可能被裁切。",
                file = path,
                suggestion = "确认隐藏溢出是有意设计，并在手机宽度下检查完整内容。",
            )
        }
    }

    private fun validateHtmlReferences(
        path: String,
        html: String,
        files: Set<String>,
        issues: MutableList<AgentVerificationIssue>,
    ) {
        HTML_REFERENCE_PATTERN.findAll(html).forEach { match ->
            val tag = match.groupValues[1].lowercase()
            val raw = match.groupValues[2].trim()
            when {
                raw.isBlank() || isInlineReference(raw) -> Unit
                isExternalReference(raw) -> {
                    val navigational = tag == "a"
                    issues += issue(
                        code = if (navigational) "external_navigation_blocked" else "external_resource_blocked",
                        severity = if (navigational) AgentVerificationSeverity.Warning else AgentVerificationSeverity.Error,
                        message = if (navigational) {
                            "本地隔离预览不会打开外部链接：$raw"
                        } else {
                            "本地隔离预览无法加载外部资源：$raw"
                        },
                        file = path,
                        line = lineNumber(html, match.range.first),
                        suggestion = if (navigational) "保留为展示链接或改为项目内页面。" else "把资源保存到项目 assets 目录并使用相对路径。",
                    )
                }
                else -> validateLocalReference(path, raw, files, html, match.range.first, issues)
            }
        }
    }

    private fun validateCss(
        path: String,
        css: String,
        files: Set<String>,
        issues: MutableList<AgentVerificationIssue>,
    ) {
        CSS_URL_PATTERN.findAll(css).forEach { match ->
            val raw = match.groupValues[1].trim().trim('"', '\'')
            when {
                raw.isBlank() || isInlineReference(raw) -> Unit
                isExternalReference(raw) -> issues += issue(
                    code = "external_resource_blocked",
                    severity = AgentVerificationSeverity.Error,
                    message = "本地隔离预览无法加载 CSS 外部资源：$raw",
                    file = path,
                    line = lineNumber(css, match.range.first),
                    suggestion = "把资源保存到项目 assets 目录并使用相对路径。",
                )
                else -> validateLocalReference(path, raw, files, css, match.range.first, issues)
            }
        }
        FIXED_WIDE_SIZE_PATTERN.findAll(css).take(4).forEach { match ->
            issues += issue(
                code = "fixed_width_risk",
                severity = AgentVerificationSeverity.Warning,
                message = "检测到可能超出手机屏幕的固定宽度：${match.value.trim()}。",
                file = path,
                line = lineNumber(css, match.range.first),
                suggestion = "使用响应式尺寸或添加移动端断点。",
            )
        }
        if (OVERFLOW_HIDDEN_PATTERN.containsMatchIn(css)) {
            issues += issue(
                code = "overflow_hidden_risk",
                severity = AgentVerificationSeverity.Warning,
                message = "样式表包含 overflow:hidden，可能造成内容无法拖动查看。",
                file = path,
                suggestion = "检查固定尺寸容器，并仅在确有需要时隐藏溢出。",
            )
        }
    }

    private fun validateJavaScript(
        path: String,
        javascript: String,
        issues: MutableList<AgentVerificationIssue>,
    ) {
        NETWORK_API_PATTERN.findAll(javascript).take(6).forEach { match ->
            issues += issue(
                code = "network_api_blocked",
                severity = AgentVerificationSeverity.Error,
                message = "本地隔离预览禁止网络 API：${match.value.substringBefore('(').trim()}。",
                file = path,
                line = lineNumber(javascript, match.range.first),
                suggestion = "改用项目内静态数据，或等待后续受控云端运行环境。",
            )
        }
    }

    private fun validateLocalReference(
        sourcePath: String,
        rawReference: String,
        files: Set<String>,
        sourceText: String,
        offset: Int,
        issues: MutableList<AgentVerificationIssue>,
    ) {
        val resolved = resolveLocalReference(sourcePath, rawReference)
        if (resolved == null) {
            issues += issue(
                code = "unsafe_local_reference",
                severity = AgentVerificationSeverity.Error,
                message = "项目引用路径越过工作区边界：$rawReference",
                file = sourcePath,
                line = lineNumber(sourceText, offset),
                suggestion = "只使用项目根目录内的安全相对路径。",
            )
            return
        }
        if (resolved !in files) {
            issues += issue(
                code = "local_resource_missing",
                severity = AgentVerificationSeverity.Error,
                message = "项目引用的本地文件不存在：$resolved",
                file = sourcePath,
                line = lineNumber(sourceText, offset),
                suggestion = "创建该文件或修正引用路径。",
            )
        }
    }

    private fun resolveLocalReference(sourcePath: String, rawReference: String): String? {
        val clean = rawReference.substringBefore('#').substringBefore('?').trim()
        if (clean.isBlank()) return ""
        val combined = if (clean.startsWith('/')) {
            clean.trimStart('/')
        } else {
            val parent = sourcePath.substringBeforeLast('/', "")
            if (parent.isBlank()) clean else "$parent/$clean"
        }
        val stack = ArrayDeque<String>()
        combined.replace('\\', '/').split('/').forEach { segment ->
            when {
                segment.isBlank() || segment == "." -> Unit
                segment == ".." -> if (stack.isEmpty()) return null else stack.removeLast()
                segment.startsWith('.') -> return null
                else -> stack.addLast(segment)
            }
        }
        return stack.joinToString("/")
    }

    private fun report(
        project: ProjectWorkspaceSummary,
        issues: List<AgentVerificationIssue>,
    ): AgentArtifactVerificationReport = AgentArtifactVerificationReport(
        domain = "project.static_web",
        workspaceId = project.projectId,
        revisionId = project.currentRevisionId,
        issues = issues.take(PROJECT_VALIDATION_MAX_ISSUES),
    )

    private fun issue(
        code: String,
        severity: AgentVerificationSeverity,
        message: String,
        file: String? = null,
        line: Int? = null,
        suggestion: String? = null,
    ): AgentVerificationIssue = AgentVerificationIssue(
        code = code,
        severity = severity,
        message = message.take(500),
        file = file,
        line = line,
        suggestion = suggestion?.take(500),
    )

    private fun isTextProjectFile(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in TEXT_FILE_EXTENSIONS

    private fun isInlineReference(value: String): Boolean {
        val lower = value.trim().lowercase()
        return lower.startsWith('#') || lower.startsWith("data:") || lower.startsWith("blob:") ||
            lower.startsWith("javascript:") || lower.startsWith("mailto:") || lower.startsWith("tel:")
    }

    private fun isExternalReference(value: String): Boolean = EXTERNAL_SCHEME_PATTERN.containsMatchIn(value.trim()) || value.startsWith("//")

    private fun lineNumber(text: String, offset: Int): Int = text.take(offset.coerceAtLeast(0)).count { it == '\n' } + 1

    companion object {
        private val TEXT_FILE_EXTENSIONS = setOf("html", "htm", "css", "js", "mjs", "json", "svg", "txt", "md")
        private val HTML_ELEMENT_PATTERN = Regex("<html\\b", RegexOption.IGNORE_CASE)
        private val BODY_ELEMENT_PATTERN = Regex("<body\\b", RegexOption.IGNORE_CASE)
        private val TITLE_ELEMENT_PATTERN = Regex("<title\\b[^>]*>.*?</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val VIEWPORT_META_PATTERN = Regex("<meta[^>]+name\\s*=\\s*['\"]viewport['\"][^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val HTML_REFERENCE_PATTERN = Regex("<\\s*([a-zA-Z0-9]+)\\b[^>]*?\\b(?:src|href)\\s*=\\s*['\"]([^'\"]+)['\"]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val CSS_URL_PATTERN = Regex("url\\(\\s*([^)]*?)\\s*\\)", RegexOption.IGNORE_CASE)
        private val EXTERNAL_SCHEME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
        private val NETWORK_API_PATTERN = Regex("\\b(?:fetch|XMLHttpRequest|WebSocket|EventSource)\\s*\\(", RegexOption.IGNORE_CASE)
        private val FIXED_WIDE_SIZE_PATTERN = Regex("(?:width|min-width)\\s*:\\s*(?:[7-9]\\d{2}|[1-9]\\d{3,})px", RegexOption.IGNORE_CASE)
        private val OVERFLOW_HIDDEN_PATTERN = Regex("overflow(?:-[xy])?\\s*:\\s*hidden", RegexOption.IGNORE_CASE)
    }
}
