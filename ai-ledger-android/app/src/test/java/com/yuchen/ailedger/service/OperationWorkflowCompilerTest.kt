package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowActionType
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowStateCheckType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationWorkflowCompilerTest {
    @Test
    fun clickWithStableSelectorCreatesVerifiedStep() {
        val result = OperationWorkflowCompiler.compile(
            baseDraft = baseDraft(),
            records = listOf(
                snapshot(900L, listOf(node(text = "首页"))),
                event(
                    time = 1_000L,
                    type = "view_clicked",
                    source = node(
                        viewId = "$PACKAGE:id/submit",
                        text = "提交",
                        role = "Button",
                        clickable = true,
                    ),
                ),
                snapshot(1_250L, listOf(node(text = "提交成功"))),
            ),
            nowMillis = 2_000L,
        )

        assertNotNull(result.draft)
        val draft = requireNotNull(result.draft)
        assertEquals(WorkflowDraftStatus.ReadyForReview, draft.status)
        assertEquals(2, draft.steps.size)
        assertEquals(WorkflowActionType.Tap, draft.steps.last().action.type)
        assertTrue(draft.steps.last().target?.hasStableCandidate == true)
        assertEquals(WorkflowStateCheckType.TextVisible, draft.completionChecks.single().type)
        assertFalse(result.issues.any { it.severity == WorkflowCompilationSeverity.Blocking })
    }

    @Test
    fun consecutiveTextChangesMergeIntoOneVariableStep() {
        val source = node(
            viewId = "$PACKAGE:id/message",
            hint = "消息内容",
            role = "TextField",
            editable = true,
        )
        val result = OperationWorkflowCompiler.compile(
            baseDraft = baseDraft(),
            records = listOf(
                event(1_000L, "view_text_changed", source),
                event(1_350L, "view_text_changed", source),
                snapshot(1_600L, listOf(source)),
            ),
        )

        val draft = requireNotNull(result.draft)
        assertEquals(2, draft.steps.size)
        assertEquals(WorkflowActionType.InputText, draft.steps.last().action.type)
        assertEquals(1, draft.variables.size)
        assertEquals("消息内容", draft.variables.single().label)
    }

    @Test
    fun credentialInputIsNeverCompiledAsAutomaticTextEntry() {
        val result = OperationWorkflowCompiler.compile(
            baseDraft = baseDraft(),
            records = listOf(
                event(
                    time = 1_000L,
                    type = "view_text_changed",
                    source = node(
                        viewId = "$PACKAGE:id/password",
                        role = "TextField",
                        editable = true,
                        sensitive = true,
                        riskHints = setOf("password"),
                    ),
                ),
            ),
        )

        val draft = requireNotNull(result.draft)
        assertEquals(WorkflowActionType.RequestUserConfirmation, draft.steps.last().action.type)
        assertTrue(draft.variables.isEmpty())
        assertTrue(result.issues.any { it.code == "compilation_sensitive_action_manual" })
    }

    @Test
    fun coordinateOnlyActionBecomesManualConfirmation() {
        val result = OperationWorkflowCompiler.compile(
            baseDraft = baseDraft(),
            records = listOf(
                event(
                    time = 1_000L,
                    type = "view_clicked",
                    source = node(
                        role = "Button",
                        bounds = "100,200,400,320",
                        clickable = true,
                    ),
                ),
            ),
        )

        val draft = requireNotNull(result.draft)
        assertEquals(WorkflowActionType.RequestUserConfirmation, draft.steps.last().action.type)
        assertTrue(result.issues.any { it.code == "compilation_selector_insufficient" })
    }

    @Test
    fun scrollDirectionUsesRecordedDelta() {
        val result = OperationWorkflowCompiler.compile(
            baseDraft = baseDraft(),
            records = listOf(
                event(
                    time = 1_000L,
                    type = "view_scrolled",
                    source = node(
                        viewId = "$PACKAGE:id/list",
                        role = "Scrollable",
                        scrollable = true,
                    ),
                    scrollDeltaY = 480,
                ),
            ),
        )

        val draft = requireNotNull(result.draft)
        assertEquals(WorkflowActionType.Scroll, draft.steps.last().action.type)
        assertEquals("forward", draft.steps.last().action.fixedArgument)
    }

    private fun baseDraft(): LearnedWorkflowDraft = LearnedWorkflowDraft(
        id = "workflow-test",
        title = "测试流程",
        goal = "完成测试操作",
        appScope = WorkflowAppScope(
            packageNames = listOf(PACKAGE),
            displayNames = listOf("测试应用"),
        ),
        status = WorkflowDraftStatus.Compiling,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
        sourceDemonstrationId = "demo-test",
    )

    private fun event(
        time: Long,
        type: String,
        source: OperationNodeEvidence,
        scrollDeltaY: Int = 0,
    ): OperationAccessibilityEventRecord = OperationAccessibilityEventRecord(
        capturedAtMillis = time,
        eventType = 0,
        eventTypeLabel = type,
        packageName = PACKAGE,
        className = source.className,
        windowTitle = "测试页面",
        contentChangeTypes = 0,
        source = source,
        eventText = null,
        inputLengthBucket = source.inputLengthBucket,
        redactionApplied = source.editable || source.sensitive,
        scrollDeltaY = scrollDeltaY,
    )

    private fun snapshot(
        time: Long,
        nodes: List<OperationNodeEvidence>,
    ): OperationNodeSnapshotRecord = OperationNodeSnapshotRecord(
        capturedAtMillis = time,
        packageName = PACKAGE,
        windowTitle = "测试页面",
        nodes = nodes,
        rawNodeCount = nodes.size,
        truncated = false,
    )

    private fun node(
        viewId: String? = null,
        text: String? = null,
        hint: String? = null,
        role: String? = null,
        bounds: String = "20,40,260,120",
        clickable: Boolean = false,
        editable: Boolean = false,
        scrollable: Boolean = false,
        sensitive: Boolean = false,
        riskHints: Set<String> = emptySet(),
    ): OperationNodeEvidence = OperationNodeEvidence(
        viewId = viewId,
        className = role,
        role = role,
        text = text,
        hint = hint,
        bounds = bounds,
        screenWidth = 1080,
        screenHeight = 2400,
        clickable = clickable,
        editable = editable,
        scrollable = scrollable,
        sensitive = sensitive,
        inputLengthBucket = if (editable) "5-8" else null,
        riskHints = riskHints,
    )

    private companion object {
        const val PACKAGE = "com.example.test"
    }
}
