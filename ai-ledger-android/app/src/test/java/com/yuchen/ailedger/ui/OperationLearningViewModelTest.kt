package com.yuchen.ailedger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationLearningViewModelTest {
    @Test
    fun validIntentCreatesDeterministicDraft() {
        val viewModel = OperationLearningViewModel()
        viewModel.updateTitle("发送日报")
        viewModel.updateGoal("在企业应用中打开日报会话并发送用户提供的内容")
        viewModel.updateAppName("企业应用")
        viewModel.updatePackageName("com.example.enterprise")

        val created = viewModel.createIntentDraft(nowMillis = 100L)

        assertTrue(created)
        assertEquals(1, viewModel.uiState.drafts.size)
        assertEquals("发送日报", viewModel.uiState.drafts.single().title)
        assertEquals("com.example.enterprise", viewModel.uiState.drafts.single().appScope.normalizedPackages.single())
        assertFalse(viewModel.uiState.editorVisible)
    }

    @Test
    fun invalidPackageKeepsEditorOpenAndReturnsIssue() {
        val viewModel = OperationLearningViewModel()
        viewModel.openIntentEditor()
        viewModel.updateTitle("发送日报")
        viewModel.updateGoal("发送用户提供的日报内容")
        viewModel.updatePackageName("不是包名")

        val created = viewModel.createIntentDraft(nowMillis = 100L)

        assertFalse(created)
        assertTrue(viewModel.uiState.editorVisible)
        assertTrue(viewModel.uiState.editorIssues.any { it.code == "workflow_app_package_invalid" })
        assertTrue(viewModel.uiState.drafts.isEmpty())
    }

    @Test
    fun missingGoalCannotCreateDraft() {
        val viewModel = OperationLearningViewModel()
        viewModel.updateTitle("打开应用")
        viewModel.updatePackageName("com.example.app")

        val created = viewModel.createIntentDraft(nowMillis = 100L)

        assertFalse(created)
        assertTrue(viewModel.uiState.editorIssues.any { it.code == "workflow_goal_missing" })
    }
}
