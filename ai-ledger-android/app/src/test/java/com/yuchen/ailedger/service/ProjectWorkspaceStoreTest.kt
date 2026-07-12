package com.yuchen.ailedger.service

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProjectWorkspaceStoreTest {
    private lateinit var tempRoot: File
    private lateinit var store: ProjectWorkspaceStore

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("ai-ledger-project-workspace-test").toFile()
        store = createStoreForTest(tempRoot)
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun createsEditsPreviewsAndRollsBackProject() {
        val created = createProject("产品官网")

        assertEquals("rev_000001", created.currentRevisionId)
        assertEquals(listOf("app.js", "index.html", "styles.css"), store.listFiles(created.projectId))
        assertEquals("<main>第一版</main>", store.readFile(created.projectId, "index.html").first)

        val edited = store.applyEdits(
            projectId = created.projectId,
            baseRevisionId = created.currentRevisionId,
            edits = listOf(ProjectWorkspaceEdit("index.html", "第一版", "第二版")),
            revisionSummary = "更新首屏文案",
        )
        assertEquals("rev_000002", edited.currentRevisionId)
        assertTrue(store.readFile(created.projectId, "index.html").first.contains("第二版"))

        val preview = store.buildPreview(created.projectId)
        assertEquals("rev_000002", preview.revisionId)
        assertTrue(preview.previewUrl.startsWith("https://project.ai-ledger.local/open?"))
        assertTrue(preview.entryFile.isFile)

        val rolledBack = store.rollback(
            projectId = created.projectId,
            targetRevisionId = "rev_000001",
            baseRevisionId = "rev_000002",
            revisionSummary = "恢复第一版",
        )
        assertEquals("rev_000003", rolledBack.currentRevisionId)
        assertTrue(store.readFile(created.projectId, "index.html").first.contains("第一版"))
        assertEquals(listOf(3, 2, 1), store.listRevisions(created.projectId).map(ProjectRevisionSummary::revision))
    }

    @Test
    fun rejectsEmptyInitialProjectAndMissingEntryFile() {
        val empty = assertThrows(ProjectWorkspaceException::class.java) {
            store.createProject(
                name = "空项目",
                description = "",
                files = emptyList(),
                revisionSummary = "创建项目",
            )
        }
        assertEquals("initial_files_required", empty.code)

        val missingEntry = assertThrows(ProjectWorkspaceException::class.java) {
            store.createProject(
                name = "无入口项目",
                description = "",
                files = listOf(ProjectWorkspaceFile("styles.css", "body{}")),
                revisionSummary = "创建项目",
            )
        }
        assertEquals("entry_file_missing", missingEntry.code)
        assertTrue(store.listProjects().isEmpty())
    }

    @Test
    fun rejectsPathEscapeMissingRevisionAndStaleRevision() {
        val project = createProject("安全测试")

        val pathError = assertThrows(ProjectWorkspaceException::class.java) {
            store.writeFiles(
                projectId = project.projectId,
                baseRevisionId = project.currentRevisionId,
                files = listOf(ProjectWorkspaceFile("../outside.html", "bad")),
                revisionSummary = "非法路径",
            )
        }
        assertEquals("invalid_path", pathError.code)
        assertFalse(File(tempRoot.parentFile, "outside.html").exists())

        val missingRevision = assertThrows(ProjectWorkspaceException::class.java) {
            store.writeFiles(
                projectId = project.projectId,
                baseRevisionId = null,
                files = listOf(ProjectWorkspaceFile("styles.css", "body { color: white; }")),
                revisionSummary = "无版本写入",
            )
        }
        assertEquals("base_revision_required", missingRevision.code)

        val updated = store.writeFiles(
            projectId = project.projectId,
            baseRevisionId = project.currentRevisionId,
            files = listOf(ProjectWorkspaceFile("styles.css", "body { color: white; }")),
            revisionSummary = "合法更新",
        )
        assertEquals("rev_000002", updated.currentRevisionId)

        val conflict = assertThrows(ProjectWorkspaceException::class.java) {
            store.writeFiles(
                projectId = project.projectId,
                baseRevisionId = "rev_000001",
                files = listOf(ProjectWorkspaceFile("app.js", "console.log('stale');")),
                revisionSummary = "旧版本覆盖",
            )
        }
        assertEquals("revision_conflict", conflict.code)
    }

    @Test
    fun completeFileReplacementRemovesStaleFilesAtomically() {
        val project = store.createProject(
            name = "替换测试",
            description = "",
            files = listOf(
                ProjectWorkspaceFile("index.html", "<main>旧版</main>"),
                ProjectWorkspaceFile("old.css", "body { color: red; }"),
                ProjectWorkspaceFile("old.js", "window.old = true;"),
            ),
            revisionSummary = "创建旧版",
        )

        val replaced = store.writeFiles(
            projectId = project.projectId,
            baseRevisionId = project.currentRevisionId,
            files = listOf(
                ProjectWorkspaceFile("index.html", "<main>新版</main>"),
                ProjectWorkspaceFile("styles.css", "body { color: blue; }"),
                ProjectWorkspaceFile("app.js", "window.ready = true;"),
            ),
            revisionSummary = "完整替换",
            replaceAllFiles = true,
        )

        assertEquals("rev_000002", replaced.currentRevisionId)
        assertEquals(listOf("app.js", "index.html", "styles.css"), store.listFiles(project.projectId))
        assertThrows(ProjectWorkspaceException::class.java) {
            store.readFile(project.projectId, "old.css")
        }
    }

    private fun createProject(name: String): ProjectWorkspaceSummary = store.createProject(
        name = name,
        description = "测试项目",
        files = listOf(
            ProjectWorkspaceFile("index.html", "<main>第一版</main>"),
            ProjectWorkspaceFile("styles.css", "main { color: white; }"),
            ProjectWorkspaceFile("app.js", "document.body.dataset.ready = '1';"),
        ),
        revisionSummary = "创建第一版",
    )

    private fun createStoreForTest(root: File): ProjectWorkspaceStore {
        val constructor = ProjectWorkspaceStore::class.java.getDeclaredConstructor(File::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(root)
    }
}
