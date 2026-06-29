package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualReasoningPolicyTest {
    @Test
    fun cleanExecutionPathUsesFastReasoning() {
        val context = VisualReasoningPolicy.evaluate(baseMemory())

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertEquals(0, context.selfCheckPasses)
        assertEquals(1, context.candidateHypothesisLimit)
        assertTrue(context.directExecutionAllowed)
        assertFalse(context.deepThinkingRequested)
    }

    @Test
    fun firstExplicitReobserveUsesNormalReasoning() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf("wait|重新观察:ok:target=重新观察:result=等待 220ms"),
        )

        assertEquals(VisualReasoningDepth.Normal, context.depth)
        assertEquals(1, context.noProgressCount)
        assertEquals(1, context.sameActionCount)
        assertEquals(1, context.selfCheckPasses)
        assertTrue(VisualReasoningTrigger.FirstNoProgress in context.triggers)
    }

    @Test
    fun repeatedReobserveUsesDeepReasoning() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "wait|重新观察:ok:target=重新观察:result=等待 220ms",
                "visual_execution_observed:action=wait|重新观察|frameChanged=true|replanRequired=false",
                "wait|重新观察:ok:target=重新观察:result=等待 220ms",
                "visual_execution_observed:action=wait|重新观察|frameChanged=false|replanRequired=false",
            ),
        )

        assertEquals(VisualReasoningDepth.Deep, context.depth)
        assertEquals(2, context.noProgressCount)
        assertEquals(2, context.sameActionCount)
        assertTrue(VisualReasoningTrigger.RepeatedNoProgress in context.triggers)
        assertTrue(VisualReasoningTrigger.RepeatedAction in context.triggers)
    }

    @Test
    fun repeatedIdenticalSuccessfulActionTriggersLoopWatchdog() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "tap_xy|切换分时|0.21|0.30:ok:target=分时:result=已点击",
                "visual_execution_observed:action=tap_xy|切换分时|frameChanged=true|replanRequired=false",
                "tap_xy|切换分时|0.21|0.30:ok:target=分时:result=已点击",
                "visual_execution_observed:action=tap_xy|切换分时|frameChanged=true|replanRequired=false",
            ),
        )

        assertEquals(VisualReasoningDepth.Deep, context.depth)
        assertEquals(0, context.noProgressCount)
        assertEquals(2, context.sameActionCount)
        assertTrue(VisualReasoningTrigger.RepeatedAction in context.triggers)
    }

    @Test
    fun routeCycleDetectsReturnToSameTargetEvenWhenCoordinatesDiffer() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "tap_xy|贵州茅台|0.50|0.31:ok:target=贵州茅台:result=已点击",
                "tap_xy|日K|0.20|0.30:ok:target=日K:result=已点击",
                "tap_xy|分时|0.21|0.30:ok:target=分时:result=已点击",
                "back:ok:target=返回:result=已返回",
                "tap_xy|贵州茅台|0.49|0.32:ok:target=贵州茅台:result=已点击",
            ),
        )

        assertEquals(VisualReasoningDepth.Deep, context.depth)
        assertEquals(4, context.routeCycleLength)
        assertTrue(VisualReasoningTrigger.RouteCycle in context.triggers)
        assertTrue(VisualReasoningPolicy.deepReplanLine(context)!!.contains("avoidRepeatedRoute=true"))
    }

    @Test
    fun exactMultiActionSuffixCycleUsesDeepReasoning() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "tap_xy|日K|0.20|0.30:ok:target=日K",
                "tap_xy|分时|0.21|0.30:ok:target=分时",
                "back:ok:target=返回",
                "tap_xy|日K|0.19|0.31:ok:target=日K",
                "tap_xy|分时|0.22|0.29:ok:target=分时",
                "back:ok:target=返回",
            ),
        )

        assertEquals(VisualReasoningDepth.Deep, context.depth)
        assertEquals(3, context.routeCycleLength)
        assertTrue(VisualReasoningTrigger.RouteCycle in context.triggers)
    }

    @Test
    fun completionRollbackInvalidatesPendingCandidateAndForcesOneDeepCorrection() {
        val actions = listOf(
            "finish_verification_pending:observationId=observation-2",
            "finish_permit_rejected:reason=completion_not_confirmed|observationId=observation-3|replanRequired=false",
        )
        val context = VisualReasoningPolicy.evaluate(baseMemory(), actions)

        assertEquals(VisualReasoningDepth.Deep, context.depth)
        assertEquals(1, context.provisionalRollbackCount)
        assertFalse(VisualReasoningTrigger.CompletionCandidate in context.triggers)
        assertTrue(VisualReasoningTrigger.ProvisionalStateRollback in context.triggers)
        assertTrue(VisualReasoningPolicy.deepReplanLine(context)!!.contains("discardProvisionalState=true"))
    }

    @Test
    fun successfulCorrectionClearsRollbackPressure() {
        val actions = listOf(
            "finish_verification_pending:observationId=observation-2",
            "finish_permit_rejected:reason=completion_not_confirmed|observationId=observation-3|replanRequired=false",
            "back:ok:target=返回:executionAccepted=true:semanticOutcome=gui_plus_pending_judgement",
            "visual_execution_observed:action=back|frameChanged=true|replanRequired=false",
        )
        val context = VisualReasoningPolicy.evaluate(baseMemory(), actions)

        assertEquals(0, context.provisionalRollbackCount)
        assertFalse(VisualReasoningTrigger.ProvisionalStateRollback in context.triggers)
    }

    @Test
    fun aDifferentSuccessfulActionClearsOldMechanicalPressure() {
        val actions = listOf(
            "wait|重新观察:ok:target=重新观察:result=等待 220ms",
            "wait|重新观察:ok:target=重新观察:result=等待 220ms",
            "back:ok:target=返回:result=已返回",
            "visual_execution_observed:action=back|frameChanged=false|replanRequired=false",
        )

        val context = VisualReasoningPolicy.evaluate(baseMemory(), actions)

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertEquals(0, context.noProgressCount)
        assertEquals(1, context.sameActionCount)
    }

    @Test
    fun frameDifferenceTelemetryNeverChangesReasoningDepth() {
        val changed = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf("visual_execution_observed:action=wait|观察|frameChanged=true|replanRequired=false"),
        )
        val unchanged = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf("visual_execution_observed:action=wait|观察|frameChanged=false|replanRequired=false"),
        )

        assertEquals(VisualReasoningDepth.Fast, changed.depth)
        assertEquals(VisualReasoningDepth.Fast, unchanged.depth)
        assertEquals(0, changed.noProgressCount)
        assertEquals(0, unchanged.noProgressCount)
    }

    @Test
    fun verifiedPackageConflictUsesDeepReasoningWithoutReadingPageText() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "visual_runtime_context:v2|state=work_surface|verifiedTargetPackage=com.target.app|currentPackage=com.other.app|guiPlusEligible=false",
            ),
        )

        assertEquals(VisualReasoningDepth.Deep, context.depth)
        assertTrue(VisualReasoningTrigger.EntityConflict in context.triggers)
    }

    @Test
    fun userCorrectionUsesDeepButSupplementUsesNormal() {
        val correction = VisualReasoningPolicy.evaluate(
            baseMemory().copy(
                taskRevision = 2,
                taskRevisionPending = true,
                currentMilestoneInvalidated = true,
                latestUserUpdate = VisualUserTaskUpdate(
                    revision = 2,
                    kind = VisualUserTaskUpdateKind.GoalRevision,
                    content = "目标改为查看自选股",
                    invalidatesCurrentMilestone = true,
                ),
            ),
        )
        val supplement = VisualReasoningPolicy.evaluate(
            baseMemory().copy(
                taskRevision = 3,
                taskRevisionPending = true,
                latestUserUpdate = VisualUserTaskUpdate(
                    revision = 3,
                    kind = VisualUserTaskUpdateKind.Supplement,
                    content = "只看今天的数据",
                ),
            ),
        )

        assertEquals(VisualReasoningDepth.Deep, correction.depth)
        assertTrue(VisualReasoningTrigger.UserCorrection in correction.triggers)
        assertEquals(VisualReasoningDepth.Normal, supplement.depth)
        assertTrue(VisualReasoningTrigger.UserSupplement in supplement.triggers)
    }

    @Test
    fun liveCompletionCandidateRequiresDeepButRolledBackCandidateDoesNotRemainLive() {
        val completion = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf("finish_verification_pending:observationId=observation-2"),
        )
        val rolledBack = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "finish_verification_pending:observationId=observation-2",
                "finish_candidate_rejected:reason=invalid",
            ),
        )
        val localBudgetOnly = VisualReasoningPolicy.evaluate(
            baseMemory().copy(remainingExplorationBudget = 0),
        )

        assertEquals(VisualReasoningDepth.Deep, completion.depth)
        assertTrue(VisualReasoningTrigger.CompletionCandidate in completion.triggers)
        assertFalse(VisualReasoningTrigger.CompletionCandidate in rolledBack.triggers)
        assertTrue(VisualReasoningTrigger.ProvisionalStateRollback in rolledBack.triggers)
        assertEquals(VisualReasoningDepth.Fast, localBudgetOnly.depth)
        assertFalse(VisualReasoningTrigger.ExplorationBudgetPressure in localBudgetOnly.triggers)
    }

    @Test
    fun localFailedHypothesesAndBlockedActionsDoNotControlReasoning() {
        val memory = baseMemory().copy(
            failedHypotheses = listOf(
                VisualFailedHypothesis(
                    hypothesisId = "old-local-hypothesis",
                    milestoneId = "m1",
                    pageStateId = "old-frame",
                    actionSignature = "tap_xy",
                    actionCluster = "tap",
                    purpose = "旧本地判断",
                    failureReason = "screen_structure_unchanged",
                    count = 3,
                ),
            ),
            blockedActions = listOf(
                VisualBlockedAction(
                    milestoneId = "m1",
                    pageStateId = "old-frame",
                    actionCluster = "tap",
                    hypothesisId = "old-local-hypothesis",
                    reason = "legacy local block",
                ),
            ),
        )

        val context = VisualReasoningPolicy.evaluate(memory)

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertEquals(0, context.failedHypothesisCount)
        assertEquals(0, context.blockedActionCount)
    }

    @Test
    fun reasoningSignalContainsNoUserReplyContent() {
        val secretReply = "目标改为查看我的私密账户"
        val context = VisualReasoningPolicy.evaluate(
            baseMemory().copy(
                taskRevision = 4,
                taskRevisionPending = true,
                currentMilestoneInvalidated = true,
                latestUserUpdate = VisualUserTaskUpdate(
                    revision = 4,
                    kind = VisualUserTaskUpdateKind.Correction,
                    content = secretReply,
                    invalidatesCurrentMilestone = true,
                ),
            ),
        )

        val promptLine = context.toPromptLine()
        val replanLine = VisualReasoningPolicy.deepReplanLine(context).orEmpty()

        assertFalse(promptLine.contains(secretReply))
        assertFalse(replanLine.contains(secretReply))
        assertTrue(promptLine.startsWith(VisualReasoningContext.PROMPT_PREFIX))
        assertTrue(promptLine.contains("localProgressClassification=false"))
        assertTrue(replanLine.startsWith(VisualReasoningPolicy.DEEP_REPLAN_PREFIX))
    }

    @Test
    fun loopMemoryReplacesDeepSignalAfterDifferentSuccessfulAction() {
        val actions = mutableListOf(
            "wait|重新观察:ok:target=重新观察:result=等待 220ms",
            "wait|重新观察:ok:target=重新观察:result=等待 220ms",
        )
        VisualLoopMemorySupport.replaceMemoryLine(actions, baseMemory())
        assertTrue(actions.any { it.startsWith(VisualReasoningPolicy.DEEP_REPLAN_PREFIX) })
        assertTrue(actions.any { it.startsWith(VisualReasoningContext.PROMPT_PREFIX) && it.contains("depth=deep") })

        actions += "back:ok:target=返回:result=已返回"
        actions += "visual_execution_observed:action=back|frameChanged=false|replanRequired=false"
        VisualLoopMemorySupport.replaceMemoryLine(actions, baseMemory())

        assertFalse(actions.any { it.startsWith(VisualReasoningPolicy.DEEP_REPLAN_PREFIX) })
        assertTrue(actions.any { it.startsWith(VisualReasoningContext.PROMPT_PREFIX) && it.contains("depth=fast") })
    }

    @Test
    fun taskMemoryJsonCarriesTransactionalWatchdogAndDropsLegacyLocalState() {
        VisualReasoningRuntime.resetForTests()
        val json = baseMemory().copy(
            progressStatus = "ambiguous",
            failedHypotheses = listOf(
                VisualFailedHypothesis(
                    hypothesisId = "legacy",
                    milestoneId = "m1",
                    pageStateId = "old",
                    actionSignature = "tap",
                    actionCluster = "tap",
                    purpose = "legacy",
                    failureReason = "legacy",
                ),
            ),
        ).toJson()

        assertEquals("visual_task_memory_v5_transactional_visual_authority", json.getString("schema"))
        assertEquals(0, json.getJSONArray("failedHypotheses").length())
        assertEquals(0, json.getJSONArray("blockedActions").length())
        assertFalse(json.getBoolean("localProgressClassification"))
        assertTrue(json.getBoolean("transactionalCompletion"))
        assertFalse(json.getBoolean("provisionalStateCommitted"))
        assertEquals("fast", json.getString("reasoningDepth"))
        assertEquals("fast", json.getJSONObject("reasoningContext").getString("depth"))
        assertEquals(
            "visual_reasoning_context_v3_transaction_watchdog",
            json.getJSONObject("reasoningContext").getString("schema"),
        )
        assertFalse(json.getJSONObject("reasoningContext").getBoolean("localProgressClassification"))
    }

    private fun baseMemory(): VisualTaskMemory = VisualTaskMemory(
        originalGoal = "测试任务",
        currentMilestoneId = "m1",
        remainingExplorationBudget = 2,
        progressStatus = "surface_verified",
        legacyMode = false,
        taskContract = VisualTaskContract(
            originalGoal = "测试任务",
            currentMilestoneId = "m1",
            milestones = listOf(VisualTaskMilestone(id = "m1")),
            explorationBudgetPerMilestone = 2,
        ),
    )
}
