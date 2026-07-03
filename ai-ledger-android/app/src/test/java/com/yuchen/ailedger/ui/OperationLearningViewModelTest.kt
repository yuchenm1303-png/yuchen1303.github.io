package com.yuchen.ailedger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationLearningViewModelTest {
    @Test
    fun selectedApplicationIsStoredInEditorState() {
        val viewModel = OperationLearningViewModel()

        viewModel.updateAppName("企业应用")
        viewModel.updatePackageName("com.example.enterprise")

        assertEquals("企业应用", viewModel.uiState.appNameInput)
        assertEquals("com.example.enterprise", viewModel.uiState.packageNameInput)
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
