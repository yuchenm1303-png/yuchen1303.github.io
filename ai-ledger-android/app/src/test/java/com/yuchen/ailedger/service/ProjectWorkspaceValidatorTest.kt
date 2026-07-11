package com.yuchen.ailedger.service

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProjectWorkspaceValidatorTest {
    private lateinit var tempRoot: File
    private lateinit var store: ProjectWorkspaceStore

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("ai-ledger-project-validator-test").toFile()
        store = createStoreForTest(tempRoot)
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun passesSelfContainedResponsiveProject() {
        val project = store.createProject(
            name = "响应式官网",
            description = "validator pass",
            files = listOf(
                ProjectWorkspaceFile(
                    "index.html",
                    """<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>响应式官网</title>
  <link rel="stylesheet" href="styles.css">
</head>
<body><main>完成</main><script src="app.js"></script></body>
</html>""",
                ),
                ProjectWorkspaceFile("styles.css", "main { width: min(90vw, 720px); margin: auto; }"),
                ProjectWorkspaceFile("app.js", "document.body.dataset.ready = '1';"),
            ),
            revisionSummary = "创建项目",
        )

        val report = ProjectWorkspaceValidator(store).validate(project.projectId)

        assertTrue(report.passed)
        assertEquals("passed", report.status)
        assertEquals(0, report.errorCount)
        assertEquals(AGENT_ARTIFACT_VERIFICATION_SCHEMA, report.toJson().optString("schema"))
    }

    @Test
    fun returnsGroundedErrorsForBlockedAndMissingResources() {
        val project = store.createProject(
            name = "错误官网",
            description = "validator fail",
            files = listOf(
                ProjectWorkspaceFile(
                    "index.html",
                    """<html><head><title>错误官网</title>
<script src="https://cdn.example.com/app.js"></script>
<link rel="stylesheet" href="missing.css">
</head><body><main>内容</main><script src="app.js"></script></body></html>""",
                ),
                ProjectWorkspaceFile("app.js", "fetch('https://example.com/data.json');"),
            ),
            revisionSummary = "创建项目",
        )

        val report = ProjectWorkspaceValidator(store).validate(project.projectId)
        val codes = report.issues.map(AgentVerificationIssue::code).toSet()

        assertFalse(report.passed)
        assertEquals("failed", report.status)
        assertTrue("external_resource_blocked" in codes)
        assertTrue("local_resource_missing" in codes)
        assertTrue("network_api_blocked" in codes)
        assertTrue("viewport_missing" in codes)
    }

    private fun createStoreForTest(root: File): ProjectWorkspaceStore {
        val constructor = ProjectWorkspaceStore::class.java.getDeclaredConstructor(File::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(root)
    }
}
