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
        val created = store.createProject(
            name = "产品官网",
            description = "测试项目",
            files = listOf(
                ProjectWorkspaceFile("index.html", "<main>第一版</main>"),
                ProjectWorkspaceFile("styles.css", "main { color: white; }"),
                ProjectWorkspaceFile("app.js", "document.body.dataset.ready = '1';"),
            ),
            revisionSummary = "创建第一版",
        )

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
    fun rejectsPathEscapeAndStaleRevision() {
        val project = store.createProject(
            name = "安全测试",
            description = "",
            files = emptyList(),
            revisionSummary = "创建项目",
        )

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

    private fun createStoreForTest(root: File): ProjectWorkspaceStore {
        val constructor = ProjectWorkspaceStore::class.java.getDeclaredConstructor(File::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(root)
    }
}
